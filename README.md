# Dugan — Personal AI Voice Agent for Android

A single-device Android voice agent in Kotlin + Jetpack Compose. It listens to a
phone call, understands the conversation, and answers in synthesised speech
through the loudspeaker.

**No backend.** The phone calls Groq, Gemini and Unreal Speech directly. Keys
live in `EncryptedSharedPreferences` on the device and nowhere else.

> ⚠️ **Read [Known limitations](#known-limitations) before you build this.** The
> headline one: Android 9+ denies third-party apps access to SIM call audio, so
> a cellular call can only be heard by putting it on the loudspeaker. That is a
> platform restriction, not a bug in this code, and no amount of engineering
> around it changes that without root.

---

## Contents

- [Quick start](#quick-start)
- [Getting your API keys](#getting-your-api-keys)
- [Building](#building)
- [Architecture](#architecture)
- [Echo defence](#echo-defence-five-layers)
- [Latency budget](#latency-budget)
- [Telecom integration](#telecom-integration)
- [Optional on-device models](#optional-on-device-models)
- [VoIP signalling (optional)](#voip-signalling-optional)
- [Tests](#tests)
- [Known limitations](#known-limitations)

---

## Quick start

```bash
# 1. Generate the Gradle wrapper (the wrapper JAR is not committed).
gradle wrapper --gradle-version 8.9

# 2. Build a debug APK.
./gradlew assembleDebug

# 3. Install.
./gradlew installDebug
```

On first launch the onboarding wizard walks you through the three API keys,
runtime permissions, and a theme. Nothing works until the keys test green.

---

## Getting your API keys

Dugan is BYOK. There is no proxy, no relay, and no key held server-side — the
device talks to each provider directly.

| Provider | Used for | Where to sign up | Free tier (at time of writing) |
|---|---|---|---|
| **Groq** | Speech-to-text (Whisper) | https://console.groq.com/keys | ~2,000 requests/day |
| **Google Gemini** | Reasoning / thinking | https://aistudio.google.com/apikey | ~15 RPM, ~1,500 requests/day |
| **Unreal Speech** | Text-to-speech | https://unrealspeech.com | ~250K characters/month |

**Save and Test are separate buttons**, deliberately. Save is a local write to
the encrypted vault, works offline, and costs nothing. Test additionally spends
one request proving the key is live — the smallest call each provider accepts.
Requiring a green Test before a key could be stored would make the app unusable
offline and burn quota on every edit. Keys are validated structurally first
(Groq must start `gsk_`, Gemini `AIza` or `ya29.`), masked in every UI surface,
and never written to a log line.

**Where they live:** `EncryptedSharedPreferences` (`byok_vault.xml`) under an
AES256-GCM master key held by the Android Keystore. The file is excluded from
auto-backup and device-to-device transfer in `res/xml/data_extraction_rules.xml`
— restoring that ciphertext onto another device would be undecryptable *and*
would wedge the vault.

Optionally, copy `keys.properties.example` to `keys.properties` (gitignored) to
seed a debug build.

---

## Building

| | |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Min SDK | 26 (Android 8.0) |
| Target / compile SDK | 35 |
| JDK | 17 |
| DI | Hilt 2.52 (KSP) |
| Persistence | EncryptedSharedPreferences · DataStore · Room |
| Networking | OkHttp 4.12 (+ SSE) · kotlinx.serialization |

The Gradle wrapper JAR is **not** committed. Run `gradle wrapper --gradle-version 8.9`
once, then commit the generated `gradle/wrapper/gradle-wrapper.jar`.

---

## Continuous integration

`.github/workflows/build.yml` runs on every push and pull request. Because the
wrapper JAR is not committed, CI bootstraps Gradle with `gradle/actions/setup-gradle`
(pinned to 8.9) rather than calling `./gradlew`.

| Job | Needs | What it proves |
|---|---|---|
| `static-analysis` | — | Package paths, internal imports, manifest class references, version-catalog aliases, resource XML. Runs in seconds with no JVM. |
| `unit-tests` | static-analysis | All 131 JVM unit tests across 19 test classes. |
| `assemble-debug` | unit-tests | The debug APK builds, and the Gradle wrapper is regenerated and shipped alongside it as an artifact. |
| `assemble-release` | unit-tests | R8 + resource shrinking succeed — this is the only job that proves `proguard-rules.pro` really keeps the serializers, Room DAOs, Telecom services and Hilt classes. Uploads `mapping.txt`. |
| `assemble-firebase` | unit-tests | The opt-in `src/firebase` source set still compiles. It synthesises a throwaway `google-services.json` for both application ids, since that file is deliberately not committed. |

Artifacts land under the run's **Artifacts** section: `dugan-debug-apk`,
`dugan-release-apk`, `unit-test-report`.

---

## Architecture

```
UI (Compose)  →  ViewModels  →  Domain  →  Data  →  Network
```

```
app/src/main/java/com/dugan/agent/
├── data/
│   ├── api/          GroqSttClient, GeminiLlmClient, GroqLlmClient,
│   │                 UnrealSpeechTtsClient, EdgeTtsClient, AgentLlm, AgentTts
│   ├── local/        BYOKVault, SettingsStore, ChatHistoryDb, TtsCache
│   ├── repository/   Key, Settings, History, Contact
│   └── signaling/    SignalingClient (+ optional Firebase in src/firebase)
├── domain/
│   ├── model/        AgentState, CallState, DuganSettings, ModelCatalog, …
│   ├── orchestrator/ VoiceAgentOrchestrator, AgentStateMachine, ChunkedStt
│   ├── audio/        AudioCaptureManager, AudioPlaybackEngine, AecProcessor,
│   │                 VadEngine, SemanticEotDetector, AudioDecoder, Pcm
│   ├── echo/         EchoDefenseEngine, FuzzyMatcher, EmbeddingMatcher,
│   │                 LlmEchoVerifier
│   ├── latency/      SentenceSplitter, SpeculativeExecutor, ModelRouter,
│   │                 ConnectionWarmup
│   ├── telecom/      CallManager, CallController, VoIPConnection,
│   │                 VoiceConnectionService, VoiceInCallService, PhoneAccountRegistrar
│   └── command/      VoiceCommandParser, VoiceCommandHandler
├── ui/               theme, components, onboarding, main, dialer, call, settings
├── service/          AgentForegroundService
└── di/               Hilt modules
```

The orchestrator is a single state machine:

```
Idle → Listening → Thinking → Speaking → Idle
                     ↓ Pause / Continue ↑
```

`AgentStateMachine` is a pure transition table with no Android or coroutine
dependencies, so the whole surface is unit tested. An orchestrator that strands
itself in `Thinking` is the worst failure mode this app can have, and that is
what the tests pin down.

### A note on the AI libraries

The original brief named `groq-kt` and `google-genai-kotlin`. Both clients here
are implemented directly against the providers' documented REST/SSE endpoints
with OkHttp and kotlinx.serialization instead. That is a deliberate trade: the
wire formats are stable and public, the streaming contract is identical either
way, and it removes two dependency coordinates I could not verify resolve from
Maven Central. Swapping in the SDKs later is a contained change — everything
downstream depends on the `SttClient` / `LlmClient` / `TtsClient` interfaces,
not on either client.

---

## Echo defence: five layers

The agent must not hear itself. Left unsolved, it answers its own question and
loops forever.

| # | Layer | Where | Cost |
|---|---|---|---|
| 1 | **Hardware AEC** — `VOICE_COMMUNICATION` + `AcousticEchoCanceler` + `NoiseSuppressor` | `AudioCaptureManager` | free |
| 2 | **Software AEC** — 256-tap normalised LMS against the loudspeaker reference | `SoftwareAec` | ~4M MAC/s |
| 3 | **Mic gating** — discard mic frames while the agent is audibly speaking | `EchoDefenseEngine.shouldGate` | free |
| 4 | **Echo-aware barge-in** — normalised cross-correlation against the far-end reference decides whether speech during playback is a human | `EchoDefenseEngine.isBargeIn` | free |
| 5 | **Text-level** — 5a lexical (Levenshtein + token overlap) → 5b semantic (embedding cosine) → 5c LLM verifier, escalating only when 5a/5b disagree | `EchoDefenseEngine.classifyTranscript` | ~1 ms / ~1 ms / ~250 ms |

**Honest scope on layer 2.** This is a time-domain NLMS filter, not WebRTC AEC3.
It cancels the direct loudspeaker path — the dominant echo term on speakerphone
— but it will not handle long room reverberation the way a partitioned
frequency-domain AEC3 does. `AecProcessor` is an interface: build the NDK port of
`kyralo/android-webrtc-aec3` and implement it, and the orchestrator picks it up
with no other change.

Settings → Audio shows which layers are actually live on your device.

---

## Latency budget

Target: sub-400 ms time-to-first-audio. Every item below is individually
switchable in Settings → Latency.

| Stage | Naive | Optimised | Technique |
|---|---|---|---|
| End-of-turn | 700 ms | ~150 ms | Semantic EOT instead of a fixed silence timer |
| STT | 800 ms | ~150 ms | 3 s chunks, 500 ms overlap, overlap de-duplicated |
| LLM TTFT | 400 ms | ~100 ms | SSE streaming, greedy decoding, stable system prompt for KV prefix reuse |
| TTS first chunk | 300 ms | 5 ms / 80 ms | LRU phrase cache; sentence-level dispatch |
| AudioTrack | 150 ms | 20 ms | `PERFORMANCE_MODE_LOW_LATENCY`, 20 ms write frames |
| Connection | 100 ms | ~0 | HTTP/2 keep-alive, warmed at session start |

**Speculative execution** fires the LLM on the first STT partial. Greedy decoding
makes this sound: if the final transcript matches the partial, the answer already
generated is still the answer. Guard rails in `SpeculativeExecutor` — never on a
non-idempotent turn, never past 8 s of speech, max 2 discarded retries per turn.

---

## Telecom integration

Two independent halves:

**`InCallService`** — makes Dugan able to act as the default dialer UI. Request
the role via `RoleManager.ROLE_DIALER`. Without it, the app still works as a
standalone assistant; it just does not draw the system's in-call screen.

**Self-managed `ConnectionService`** — the VoIP path. `VoIPConnection` sets
`PROPERTY_SELF_MANAGED` and `setAudioModeIsVoip(true)`, which is what makes
`VOICE_COMMUNICATION` capture legal and clean for the call's duration. This is
the good path. The SIM path never reaches it.

For a SIM call: the user answers manually (Android will not let a third-party app
answer a cellular call), turns on the loudspeaker, then flips **Let Agent Handle**.
The switch is disabled until the loudspeaker is on, because without it there is
no audio to capture.

---

## Optional on-device models

Nothing is required. See `app/src/main/assets/models/README.md`.

| Asset | Replaces | Fallback shipped |
|---|---|---|
| `silero_vad.tflite` | frame VAD | `EnergyVadEngine` (adaptive energy threshold) |
| `smart_turn_v3.2.onnx` | semantic end-of-turn | `SilenceEotDetector` (silence + grammar heuristics) |

Both load by reflection, so LiteRT and ONNX Runtime stay optional dependencies.

---

## VoIP signalling (optional)

Off by default. Signalling only — **audio never flows through Firebase**; media
is peer-to-peer WebRTC once the SDP/ICE exchange completes.

```bash
# Drop your real app/google-services.json in first, then:
./gradlew assembleDebug -Pdugan.firebase=true
```

That flag adds the `src/firebase` source set and applies the `google-services`
plugin. Without it, `LocalOnlySignalingClient` is bound and the rest of the app
is unaffected.

Security rules ship in `database.rules.json` (deploy with
`firebase deploy --only database`). They scope every read and write to the two
participants' anonymous-auth uids, cap SDP blobs at 16 KB so the signalling
channel cannot be used as free storage, and reject unknown fields. Note that RTDB
rules cannot read a clock, so the 5-minute expiry for unanswered calls is
enforced client-side and by a scheduled cleanup, not by the rules.

The wiring is a Hilt multibinding: `SignalingModule` declares the set, and
`FirebaseSignalingContributorModule` (only compiled under the flag) contributes
into it.

---

## Tests

```bash
./gradlew test            # JVM unit tests
```

Covered:

| Test | What it pins |
|---|---|
| `AgentStateMachineTest` | the full phase × command transition table |
| `BYOKVaultTest` | trimming, blank rejection, complete key redaction, structural validation |
| `EchoDefenseTest` | Levenshtein, token overlap, suffix echo matching, embeddings, layer 5a/5b verdicts, correlation |
| `SentenceSplitterTest` | sentence boundaries, decimals, abbreviations, char-at-a-time streaming |
| `SpeculativeExecutorTest` | every guard rail |
| `VoiceCommandParserTest` | dial detection, "call" as a noun, confirmations, fuzzy names |
| `ChunkedSttTest` | overlap de-duplication, utterance buffer bounds |
| `PcmTest` | PCM round-trip, RMS, resampling, WAV header |
| `VadAndEotTest` | energy VAD open/close, EOT heuristics and controller |
| `ModelAndCallStateTest` | router heuristics, model catalogue integrity, SIM/VoIP capture gating |
| `RetryTest` | backoff on 5xx/429/transport, and that 400/401 are **not** retried |
| `AgentLogRedactTest` | key shapes are scrubbed from log output |
| `KeyRowTest` | Save button enablement — the dirty check that keeps a stored key from looking unsaved |

### Static analysis

```bash
python3 tools/static_check.py
```

This sandbox has no JDK, no Gradle and no Android SDK, and the hosts needed to
fetch them (`services.gradle.org`, `dl.google.com`, `repo.maven.apache.org`,
`objects.githubusercontent.com`) are unreachable — so **`./gradlew build` has not
been run against this tree.** `tools/static_check.py` is the substitute: it
verifies package/path agreement, bracket balance, duplicate declarations,
internal import resolution, manifest class references, version-catalog alias
consistency, and resource XML well-formedness. It currently reports
`OK: no static errors` across 100 Kotlin files.

That is a real check and it caught real bugs during development, but it is not a
compiler. Expect to fix ordinary type errors on the first real build.

---

## Known limitations

1. **SIM call audio requires the loudspeaker.** Android 9+ blocks
   `VOICE_CALL` / `VOICE_UPLINK` / `VOICE_DOWNLINK` for third-party apps. Both
   voices arrive mixed through the mic, with room noise and echo.
2. **SIM calls cannot be answered programmatically.** The user answers manually,
   then enables the agent.
3. **Auto-answer is VoIP-only**, for the same reason.
4. **Barge-in depends on layer 4.** With mic gating on and barge-in off, you
   cannot interrupt the agent while it speaks.
5. **Layer 2 is NLMS, not AEC3.** Direct-path echo only; reverberation survives.
6. **Free-tier limits apply:** Gemini ~15 RPM, Groq ~2,000 RPD, Unreal Speech
   ~250K characters/month. TTS degrades in two steps: keyless Edge TTS on
   429/402, then the platform's own `TextToSpeech` if the network is gone
   entirely. The final tier always works but sounds markedly worse.
7. **Echo cancellation quality is device-dependent.** Some OEMs report
   `AcousticEchoCanceler.isAvailable() == true` and do not apply it. Check
   Settings → Audio for what is actually live.
8. **On-device models add ~85 MB** if you bundle both. They are not included.
9. **Default dialer role is required** for the full `InCallService` experience.
10. **VoIP media transport is not included.** The signalling layer exchanges
    SDP/ICE; you need to attach your own WebRTC peer connection.
11. **Platform TTS fallback needs a TTS engine installed.** Most devices ship
    one; a stripped AOSP image may not.
12. **Auto-answer is best-effort.** It waits `answerDelaySeconds` then checks the
    call is still ringing; a caller who hangs up during the delay gets nothing.

### Resilience behaviour

- STT, LLM and TTS calls retry transport errors, 408, 429 and 5xx with
  exponential backoff (250 ms → 4 s, capped). **401 and 400 are never retried** —
  a bad key is a permanent answer and retrying it only burns quota.
- Audio focus is requested before the agent speaks and released after. Transient
  loss pauses the pipeline; regain resumes it, so the agent does not talk over a
  navigation prompt or an incoming ring.
- Everything logged goes through `AgentLog`, which redacts anything shaped like
  an API key at the point of logging. Settings → About → Export Logs shares the
  last 800 entries.

---

## Explicit non-goals

- No direct SIM call audio capture (blocked by the platform).
- No root, Shizuku or ADB requirement.
- No backend server.
- No hardcoded API keys.
- No AccessibilityService-based call recording (Play policy violation).
- No Twilio / LiveKit / rawhq / Micdrop bundling — direct API calls only.
- No routing of audio media through Firebase.

---

## Licence

Personal-use project. Third-party components retain their own licences; model
weights are not redistributed here.
