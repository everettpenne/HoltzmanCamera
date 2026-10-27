# Ix — the imaging engine

**Ix** is the name of the engine inside Holtzman Camera. The app is the camera; Ix is what turns a capture into a file worth keeping.

The name comes from the Dune planet of invention and technology — the place that makes the instruments. It is deliberately not a second app, a service or a brand on the icon: it is the part that does the imaging work, and the part that can grow into computational photography without changing the camera's identity.

## 1. What Ix owns

| Area | Responsibility |
| --- | --- |
| Output formats | JPEG, RAW/DNG, RAW+JPEG, Ultra HDR (gain map); MIME, suffix, naming |
| Colour and style | Photographic presets, strength, tone/colour maths, skin-tone-safe tuning |
| Encoding | The final bytes, quality, metadata that belongs to the image |
| Fallbacks | When a format or style is unsupported, choose a lossless fallback and say so |
| Future compute | Burst HDR, Night, portrait, and other multi-frame work — if it is ever built |

## 2. What Ix does not own

- **Camera control** — manual shutter/ISO/focus/WB lives in `suite/manual`, because it talks to Camera2 and the camera session.
- **The UI** — viewfinder, panels, settings and previews are the app's.
- **Storage and handoff** — MediaStore, SAF, Sietch and sharing live in `suite/handoff`; Ix returns bytes/metadata, not destinations.
- **Permissions** — the app asks; Ix never does.
- **Uno integration** — shortcuts, deep links and Live Updates live in `suite/uno`.

This boundary is the point: the engine can be tested with files, and the camera can be tested without the engine's internals.

## 3. Where it lives

**v1:** the pure JVM module `:ix`, source under `ix/src/main/kotlin/com/everettpenne/ix/`.

```
ix/
├── build.gradle.kts             # Kotlin/JVM, JVM 17 bytecode, no Android
├── src/main/kotlin/com/everettpenne/ix/
│   ├── IxEngine.kt              # the interface and version
│   ├── IxModel.kt               # source, capture, file, request, result, warnings
│   ├── IxColor.kt               # colour value and 4x5 matrix maths
│   ├── IxStyle.kt               # presets and the ARGB style renderer
│   ├── IxNaming.kt              # file names
│   └── PassThroughIxEngine.kt   # skeleton implementation (not shippable)
└── src/test/kotlin/com/everettpenne/ix/
```

**Why a module at all?** It enforces the boundary at compile time: Ix cannot accidentally grow a `Context` or a `Uri`, and its tests run without the Android SDK. The cost is a small, deliberate divergence in `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml` and the Gradle verification metadata.

**Later:** the internal packages (`format/`, `color/`, `style/`, `meta/`) can be split inside the module as the encoders land; native code can move into `ix/src/main/cpp/` without changing the contract. The app-side adapter (Android `ImageProxy`/`Bitmap` to `IxSource`, `IxFile` to MediaStore/SAF) lives in `app/.../suite/ix/`.

## 4. Contract (sketch, not final)

```kotlin
data class IxRequest(
    val source: IxSource,          // bytes, source format, dimensions
    val capture: IxCaptureInfo,    // orientation, timestamp, sensor/exposure facts
    val style: IxStyle?,           // preset + strength; null for "no style"
    val output: IxOutput,          // JPEG, RAW, RAW_JPEG, ULTRA_HDR
    val baseName: String,          // "IMG_yyyyMMdd_HHmmss"
)

data class IxResult(
    val files: List<IxFile>,       // bytes, name, MIME, kind, primary
    val warnings: List<IxWarning>, // fallbacks, skipped style, unsupported source
    val engineVersion: String,
)

interface IxEngine {
    val version: String
    fun render(request: IxRequest): IxResult
}
```

The contract is deliberately narrow: **bytes in, files out, warnings in the result**. No `Context`, no `Uri`, no UI types. The app calls `render` on a background thread and decides where the bytes are written.

**Skeleton status (2026-10-27):** `PassThroughIxEngine` implements the contract without decoding: it passes JPEG, DNG and Ultra HDR bytes through, reports style requests as `StyleSkipped`, and refuses RAW→JPEG. `IxStyleRenderer` already applies the style matrices to an ARGB buffer. 27 JVM tests cover the model, colour maths, presets, naming and pass-through behaviour. It is a contract scaffold, **not** Ix 1; the real encoders and style stage replace it before release.

**Adapter (2026-10-27):** the app module now has `suite/ix/IxSources` (`ImageProxy`, `Bitmap`, `Uri` → `IxSource`), `IxWriter` (`IxFile` → MediaStore or SAF, with `IS_PENDING` and cleanup) and `IxPipeline` (source → engine → files). Three instrumented tests pass on the API 36 emulator: Bitmap → Ix → MediaStore → read back, a skipped style still writes the file, and JPEG padding is trimmed. The capture path is not switched over yet; that is D4/D5.

## 5. Pipeline

```
source bytes
  → decode / normalise (linear where the format allows)
  → [RAW path]      DNG container + required tags (no style, no destructive edit)
  → [Ultra HDR]     preserve the gain map; no SDR-only re-encode
  → [SDR JPEG]      colour matrix + tone curve + strength
  → encode          JPEG / DNG / JPEG+gain map
  → metadata        orientation, timestamp, style id + strength, maker note
  → IxResult
```

Rules:

- **The original is never lost.** If RAW is on, the DNG is untouched by styling.
- **No silent downgrade.** If Ultra HDR is unavailable, return a warning and a JPEG; do not pretend.
- **The style is data, not a mystery.** Record its id and strength in metadata so a future editor can reproduce it.
- **One decision per stage.** Format choice is the app's; encoding is Ix's; destination is handoff's.

## 6. Quality and performance

- 12 MP SDR style target: **under ~2 seconds** on a Tensor device, with a Live Update while it runs. If it cannot meet that, the style applies in the post-capture review with a progress state, not on the shutter path.
- RAW/DNG save: no full decode/re-encode; write the container and required metadata.
- Ultra HDR: byte-preserving where possible; verify the gain map survives.
- Golden-image tests for each style; JVM tests for the colour maths where the code is pure; device tests for the encoders.
- Tune skin tones before saturation and contrast. A style that flatters a landscape and turns faces orange is a bug.

## 7. Naming and credit

- About: **Holtzman Camera · Ix engine**.
- Capture metadata: a maker note with the engine version and style, not a visible watermark.
- A "Shot on Ix" watermark is a future, off-by-default option; do not bake branding into files silently.
- The engine version starts with the app version; split it only if Ix is reused elsewhere.

## 8. Roadmap

| Ix version | Scope |
| --- | --- |
| **Ix 1** (v1) | JPEG, RAW/DNG, RAW+JPEG, Ultra HDR, styles, metadata, fallbacks |
| **Ix 2** (later) | Burst HDR (ZSL ring buffer), Night stacking, local tone mapping |
| **Ix 3** (maybe) | Portrait segmentation/bokeh, Top Shot, cleanup — only if the device budget allows |

HDR+/Night are explicitly out of v1; naming the engine now means those features have a home later without renaming the product or rewriting the app.
