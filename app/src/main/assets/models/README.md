# Optional on-device models

Nothing in here is required to build or run Dugan. Both detectors fall back to a
pure-Kotlin implementation when their asset is missing, and the fallback is what
ships by default.

| File | Purpose | Loaded by | Fallback |
|---|---|---|---|
| `silero_vad.tflite` | Silero VAD v5, frame-level voice activity (16 kHz, 512-sample chunks) | `SileroVadEngine.loadOrNull` | `EnergyVadEngine` (adaptive energy threshold) |
| `smart_turn_v3.2.onnx` | Smart Turn v3.2 semantic end-of-turn, 8 s context | `SmartTurnEotDetector.loadOrNull` | `SilenceEotDetector` (silence timer + grammar heuristics) |

Drop the file in with exactly the name above and restart the app. `DuganApplication`
logs what it found:

```
Dugan: models: silero=true smartTurn=false
```

Both loaders use reflection, so the LiteRT / ONNX Runtime dependencies stay
optional -- add them to `gradle/libs.versions.toml` only if you ship a model.

Do not commit model weights to this repository. They are large binaries with
their own licences (Silero VAD is MIT, Smart Turn is BSD-2-Clause) and belong in
your own asset pipeline or a release artifact.
