/// Deep-casts platform-channel maps (which arrive as `Map<Object?, Object?>`)
/// into strongly-typed `Map<String, dynamic>` / `List<dynamic>` structures
/// safe for `json`-style access.
library;

dynamic castDeep(dynamic value) {
  if (value is Map) {
    return value.map((k, v) => MapEntry(k.toString(), castDeep(v)));
  }
  if (value is List) {
    return value.map(castDeep).toList();
  }
  return value;
}

Map<String, dynamic> asStringKeyedMap(dynamic value) {
  final casted = castDeep(value);
  if (casted is Map<String, dynamic>) {
    return casted;
  }
  return <String, dynamic>{};
}

List<Map<String, dynamic>> asMapList(dynamic value) {
  if (value is List) {
    return value.whereType<Map>().map(asStringKeyedMap).toList();
  }
  return const <Map<String, dynamic>>[];
}
