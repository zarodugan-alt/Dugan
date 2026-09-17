#!/usr/bin/env python3
"""
Static consistency checker for Dugan.

This sandbox has no JDK, no Gradle and no Android SDK (dl.google.com,
repo.maven.apache.org and services.gradle.org are all unreachable), so a real
`./gradlew compileDebugKotlin` cannot run here. This script is the next best
thing: it verifies the properties that actually break an Android build and that
a compiler-independent tool can decide locally.

Hard errors (exit 1):
  1. package declaration disagrees with the file's directory
  2. unbalanced () [] {} outside strings/comments
  3. two top-level declarations collide in the same package
  4. an `import com.dugan.agent...` names something that is never declared
  5. a class referenced by AndroidManifest.xml / DI wiring does not exist
  6. a version-catalog alias used in build.gradle.kts is not declared
  7. a res/ XML file is malformed

Warnings (reported, non-fatal): capitalised identifiers that resolve to nothing
in-project and are not on the known-external allowlist -- the classic symptom of
a missing import or a renamed type.
"""

from __future__ import annotations

import os
import re
import sys
import tomllib
import xml.dom.minidom
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_ROOTS = [
    "app/src/main/java",
    "app/src/test/java",
    "app/src/androidTest/java",
    "app/src/firebase/java",
]
PKG_PREFIX = "com.dugan.agent"
GENERATED_CLASSES = {"R", "BuildConfig", "Manifest"}

# Packages we do not compile, so any capitalised name reachable from them is fine.
EXTERNAL_PREFIXES = (
    "android.", "androidx.", "com.google.", "kotlin.", "kotlinx.", "java.", "javax.",
    "okhttp3.", "okio.", "dagger.", "hilt.", "app.cash.", "org.junit.", "org.jetbrains.",
    "org.conscrypt.", "org.bouncycastle.", "org.openjsse.", "sun.", "com.android.",
    "com.dugan.agent.BuildConfig",
)

KOTLIN_BUILTINS = {
    "String", "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char",
    "Unit", "Any", "Nothing", "Number", "Comparable", "Iterable", "Collection",
    "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap", "Array",
    "IntArray", "LongArray", "ShortArray", "ByteArray", "FloatArray", "DoubleArray",
    "BooleanArray", "CharArray", "ArrayDeque", "Pair", "Triple", "Result", "Enum",
    "Throwable", "Exception", "RuntimeException", "Error", "IllegalArgumentException",
    "IllegalStateException", "UnsupportedOperationException", "IndexOutOfBoundsException",
    "ArithmeticException", "NullPointerException", "ClassCastException", "TODO",
    "UByte", "UInt", "ULong", "UShort", "Lazy", "Companion", "StringBuilder",
    "CharSequence", "Override", "Deprecated", "Suppress", "JvmStatic", "JvmField",
    "Volatile", "Transient", "SafeVarargs", "Retention", "Target", "Annotation",
    "RequiresOptIn", "OptIn", "Experimental",
    "ArrayList", "LinkedList", "HashMap", "LinkedHashMap", "HashSet", "LinkedHashSet",
    "Regex", "StringBuilder", "Appendable", "Sequence", "Iterator", "Comparator",
    "IntRange", "ClosedRange", "Progression", "Nothing?", "T", "R", "E", "K", "V",
    "ByteOrder", "FloatBuffer", "UUID", "Date", "Calendar", "Locale", "TimeZone",
    "File", "URI", "URL", "InputStream", "OutputStream", "ByteArrayOutputStream",
    "IOException", "InterruptedException", "CancellationException",
    "Charsets", "Math", "Class", "System", "Runtime", "Thread", "Object", "Void",
    "AssetManager", "Process", "StrictMode", "Looper", "Handler", "Bundle",
    # Members of Compose/Android scope receivers (BoxScope, ExposedDropdownMenuBoxScope,
    # RowScope, ...) resolve at the call site without an import, which this checker
    # cannot model.
    "ExposedDropdownMenu", "Modifier", "Alignment",
    # kotlin.* default imports -- always in scope, never imported explicitly.
    "AnnotationRetention", "AnnotationTarget", "JvmSuppressWildcards", "JvmStatic",
    "Synchronized", "ClosedFloatingPointRange", "Comparable", "Lazy", "Nothing",
}

errors: list[str] = []
warnings: list[str] = []


def strip_code(src: str) -> str:
    """Blank out comments and string/char literals, preserving length and newlines."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("/*", i):
            end = src.find("*/", i + 2)
            end = n if end == -1 else end + 2
            out.append(re.sub(r"[^\n]", " ", src[i:end]))
            i = end
        elif src.startswith("//", i):
            end = src.find("\n", i)
            end = n if end == -1 else end
            out.append(" " * (end - i))
            i = end
        elif src.startswith('"""', i):
            end = src.find('"""', i + 3)
            end = n if end == -1 else end + 3
            out.append(re.sub(r"[^\n]", " ", src[i:end]))
            i = end
        elif c == "`":
            # Kotlin escaped identifier (`a test name with spaces`). Blank the body
            # so the words inside are not mistaken for type references.
            j = src.find("`", i + 1)
            j = i + 1 if j == -1 else j + 1
            out.append("`" + " " * max(0, j - i - 2) + "`")
            i = j
        elif c in ('"', "'"):
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == c:
                    j += 1
                    break
                if src[j] == "\n":
                    break
                j += 1
            out.append(c + " " * max(0, j - i - 2) + c)
            i = j
        else:
            out.append(c)
            i += 1
    return "".join(out)


DECL_RE = re.compile(
    r"^\s*(?:(?:public|internal|private|protected|abstract|open|sealed|data|value|inner|"
    r"enum|annotation|inline|external|suspend|actual|expect|fun|operator|infix|const|lateinit|tailrec|override|companion)\s+)*"
    r"(?P<kind>class|object|interface|fun|val|var|typealias)\s+"
    # Optional generic parameter list, e.g. `suspend fun <T> withRetry(...)`.
    r"(?:<[^<>]*(?:<[^<>]*>)?[^<>]*>\s+)?"
    # `Receiver.name` for extension members -- the member name is what matters.
    r"(?P<name>(?:`?[A-Za-z_]\w*`?\.)*`?[A-Za-z_]\w*`?)",
    re.MULTILINE,
)
ENUM_RE = re.compile(r"enum\s+class\s+(\w+)[^{]*\{")
PACKAGE_RE = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
IMPORT_RE = re.compile(r"^\s*import\s+([\w.]+)(?:\s+as\s+(\w+))?", re.MULTILINE)


def kotlin_files():
    for root in SRC_ROOTS:
        base = os.path.join(ROOT, root)
        if not os.path.isdir(base):
            continue
        for dirpath, _dirs, files in os.walk(base):
            for f in sorted(files):
                if f.endswith(".kt"):
                    yield root, os.path.join(dirpath, f)


JAVA_KEYWORDS = {
    "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
    "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
    "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
    "interface", "long", "native", "new", "package", "private", "protected", "public",
    "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
    "throw", "throws", "transient", "try", "void", "volatile", "while",
}


def check_dagger_method_names(path, text, errors):
    """KSP generates code from Dagger @Provides / @Binds method names.

    Kotlin happily accepts `fun default()`, but `default` is a Java reserved word,
    so KSP fails the build with
        e: [ksp] java.lang.IllegalArgumentException: not a valid name: default
    which costs a two-minute Gradle run to discover. Catch it here instead.
    """
    if not re.search(r"@(?:Provides|Binds|Multibinds|BindsInstance)\b", text):
        return
    rel = os.path.relpath(path, ROOT)
    for i, line in enumerate(text.split("\n"), 1):
        m = re.search(r"\bfun\s+`?([A-Za-z_]\w*)`?\s*\(", line)
        if m and m.group(1) in JAVA_KEYWORDS:
            errors.append(
                f"{rel}:{i}: `fun {m.group(1)}()` is a Java reserved word and cannot "
                f"be a Dagger @Provides/@Binds method name (KSP cannot generate it)"
            )



def main() -> int:
    files = list(kotlin_files())
    if not files:
        print("no .kt files found")
        return 1

    # symbol table: fqn -> (file, kind)
    symbols: dict[str, tuple[str, str]] = {}
    pkg_of_file: dict[str, str] = {}
    file_syms: dict[str, set[str]] = defaultdict(set)
    pkg_members: dict[str, set[str]] = defaultdict(set)
    raw: dict[str, str] = {}
    declared_extra: set[str] = set()
    code: dict[str, str] = {}
    imports_of: dict[str, list[str]] = {}

    for _root, path in files:
        rel = os.path.relpath(path, ROOT)
        src = open(path, encoding="utf-8").read()
        raw[path] = src
        clean = strip_code(src)
        code[path] = clean

        m = PACKAGE_RE.search(clean)
        if not m:
            errors.append(f"{rel}: no `package` declaration")
            continue
        pkg = m.group(1)
        pkg_of_file[path] = pkg

        expected_tail = os.path.dirname(os.path.relpath(path, os.path.join(ROOT, _root)))
        expected = expected_tail.replace(os.sep, ".") if expected_tail != "." else ""
        if not pkg.endswith(expected) or (expected and pkg[-len(expected):] != expected):
            errors.append(f"{rel}: package `{pkg}` does not match path `...{os.sep}{expected_tail}`")

        # balance check
        depth = {"(": 0, "[": 0, "{": 0}
        pairs = {")": "(", "]": "[", "}": "{"}
        for ch in clean:
            if ch in depth:
                depth[ch] += 1
            elif ch in pairs:
                depth[pairs[ch]] -= 1
        for op, d in depth.items():
            if d != 0:
                errors.append(f"{rel}: unbalanced `{op}` (net {d:+d})")

        imports = IMPORT_RE.findall(clean)
        imports_of[path] = [a for a, _ in imports]

        for em in ENUM_RE.finditer(clean):
            open_brace = clean.index("{", em.start())
            depth2 = 0
            end = open_brace
            for idx in range(open_brace, len(clean)):
                if clean[idx] == "{":
                    depth2 += 1
                elif clean[idx] == "}":
                    depth2 -= 1
                    if depth2 == 0:
                        end = idx
                        break
            body = clean[open_brace + 1:end]
            # Entries are the leading comma-separated identifiers before any ';'.
            body = body.split(";", 1)[0]
            for entry in body.split(","):
                ident = entry.strip().split("(")[0].strip().split(":")[0].strip()
                if re.fullmatch(r"[A-Za-z_]\w*", ident):
                    file_syms[path].add(ident)
                    pkg_members[pkg].add(ident)
                    declared_extra.add(ident)

        for dm in DECL_RE.finditer(clean):
            name = dm.group("name").split(".")[-1].strip("`")
            fqn = f"{pkg}.{name}"
            file_syms[path].add(name)
            pkg_members[pkg].add(name)
            if fqn in symbols and symbols[fqn][0] != rel and dm.group("kind") in (
                "class", "object", "interface"
            ):
                errors.append(
                    f"{rel}: duplicate top-level `{name}` "
                    f"(also in {symbols[fqn][0]})"
                )
            symbols.setdefault(fqn, (rel, dm.group("kind")))
            # nested/inner declarations are also reachable unqualified inside the file

    # internal import resolution
    known_fqns = set(symbols)
    known_pkgs = set(pkg_members)
    for path, imps in imports_of.items():
        rel = os.path.relpath(path, ROOT)
        for imp in imps:
            if not imp.startswith(PKG_PREFIX):
                continue
            # Generated by AGP; never present in the source tree.
            if imp.rsplit(".", 1)[-1] in GENERATED_CLASSES:
                continue
            if imp.endswith(".*"):
                if imp[:-2] not in known_pkgs:
                    errors.append(f"{rel}: wildcard import of unknown package `{imp}`")
                continue
            parent = imp.rsplit(".", 1)[0]
            leaf = imp.rsplit(".", 1)[1]
            if imp in known_fqns:
                continue
            # member import (fun/val) or nested class
            if parent in known_fqns and symbols[parent][1] in ("class", "object", "interface"):
                continue
            if leaf in pkg_members.get(parent, set()):
                continue
            # top-level fun/val import: com.dugan.agent.x.nameOfFun
            if parent in known_pkgs and any(
                symbols.get(f"{parent}.{leaf}", (None, None))[1] in ("fun", "val", "var", "typealias")
                for _ in [0]
            ):
                continue
            errors.append(f"{rel}: unresolved internal import `{imp}`")

    # manifest-referenced classes
    manifest = os.path.join(ROOT, "app/src/main/AndroidManifest.xml")
    if os.path.exists(manifest):
        msrc = open(manifest, encoding="utf-8").read()
        for attr in ("android:name",):
            for val in re.findall(rf'{attr}="\.([\w.]+)"', msrc):
                fqn = f"{PKG_PREFIX}.{val}"
                if fqn not in known_fqns:
                    errors.append(f"AndroidManifest.xml: `{val}` has no matching class ({fqn})")

    # version catalog vs build files
    catalog = os.path.join(ROOT, "gradle/libs.versions.toml")
    if os.path.exists(catalog):
        with open(catalog, "rb") as fh:
            toml = tomllib.load(fh)
        aliases = {k.replace("-", ".") for k in toml.get("libraries", {})}
        plugin_aliases = {k.replace("-", ".") for k in toml.get("plugins", {})}
        versions = set(toml.get("versions", {}))
        for section, entries in (("libraries", toml.get("libraries", {})), ("plugins", toml.get("plugins", {}))):
            for name, entry in entries.items():
                vref = entry.get("version", {}).get("ref") if isinstance(entry.get("version"), dict) else None
                if vref and vref not in versions:
                    errors.append(f"libs.versions.toml: {section}.{name} refs unknown version `{vref}`")
        for gradle_file in ("build.gradle.kts", "app/build.gradle.kts", "settings.gradle.kts"):
            p = os.path.join(ROOT, gradle_file)
            if not os.path.exists(p):
                continue
            gsrc = strip_code(open(p, encoding="utf-8").read())
            for used in re.findall(r"libs\.plugins\.([\w.]+)", gsrc):
                if used not in plugin_aliases:
                    errors.append(f"{gradle_file}: libs.plugins.{used} not in version catalog")
            for used in re.findall(r"libs\.([\w.]+)", gsrc):
                if used.startswith("plugins."):
                    continue
                if used not in aliases:
                    errors.append(f"{gradle_file}: libs.{used} not in version catalog")

    # resource XML validity
    for dirpath, _d, fnames in os.walk(os.path.join(ROOT, "app/src/main/res")):
        for fn in fnames:
            if fn.endswith(".xml"):
                fp = os.path.join(dirpath, fn)
                try:
                    xml.dom.minidom.parse(fp)
                except Exception as exc:  # noqa: BLE001
                    errors.append(f"{os.path.relpath(fp, ROOT)}: malformed XML ({exc})")

    # Dagger/KSP method-name validity -- cheap here, expensive to find in a build
    for path in code:
        check_dagger_method_names(path, code[path], errors)


    # unresolved capitalised identifiers -> warnings
    declared_names: set[str] = set()
    for s in file_syms.values():
        declared_names |= s
    ident_re = re.compile(r"\b([A-Z][A-Za-z0-9_]{2,})\b")
    for path in code:
        rel = os.path.relpath(path, ROOT)
        local = file_syms[path]
        imported_leaves = {i.rsplit(".", 1)[-1] for i in imports_of[path]}
        seen = set()
        for m in ident_re.finditer(code[path]):
            name = m.group(1)
            if name in seen or name in local or name in imported_leaves:
                continue
            if name.isupper():  # constant on an imported type
                continue
            before = code[path][:m.start()].rstrip()
            if before.endswith("."):  # member of an imported receiver
                continue
            if name in KOTLIN_BUILTINS:
                continue
            seen.add(name)
            if name not in declared_names and name not in declared_extra:
                warnings.append(f"{rel}: `{name}` not declared in-project and not imported")

    print(f"scanned {len(files)} Kotlin files, {len(known_fqns)} declarations, "
          f"{len(known_pkgs)} packages\n")

    if warnings:
        uniq = sorted(set(warnings))
        print(f"--- {len(uniq)} warnings (unresolved capitalised identifiers) ---")
        for w in uniq:
            print("  ", w)
        print()

    if errors:
        print(f"--- {len(errors)} ERRORS ---")
        for e in errors:
            print("  ", e)
        return 1

    print("OK: no static errors")
    return 0


if __name__ == "__main__":
    sys.exit(main())
