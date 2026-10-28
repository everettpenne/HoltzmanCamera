# Architecture

The fork keeps GrapheneOS Camera's architecture: one `:app` module, CameraX for capture, Views + ViewBinding for UI, and a storage layer that already understands MediaStore and SAF. The suite's work is added in a `suite` package and, where possible, in new resources rather than edits to upstream files.

## 1. What upstream already gives us

| Area | Where | What it does |
| --- | --- | --- |
| App entry / lifecycle | `App.kt`, `ActivityLifeCycleHelper.kt` | Process-level setup, location, secure-mode helpers |
| Camera configuration | `CamConfig.kt` | Mode tabs, CameraX binding, extensions, resolution, flash, ZSL, aspect ratio, settings keys |
| Modes | `CamConfig.kt` (`CameraMode`) | QR, AUTO, FACE_RETOUCH, PORTRAIT, NIGHT, HDR, CAMERA, VIDEO; extension modes are applied through `ExtensionsManager` |
| Still capture | `capturer/ImageCapturer.kt` | Builds `ImageCapture.Metadata`, calls `ImageCapture.takePicture`, drives the shutter UI |
| Saving | `capturer/ImageSaver.kt` | JPEG/YUV extraction, EXIF handling, MediaStore or SAF tree writes, thumbnails |
| Video | `capturer/VideoCapturer.kt` | CameraX `VideoCapture`/`Recorder`, audio, EIS |
| Gallery | `CapturedItems.kt`, `ui/activities/InAppGallery.kt` | In-app last-shot preview and gallery |
| Settings | `ui/SettingsDialog.kt`, `ui/activities/MoreSettings.kt`, `util/SharedPrefs.kt` | The existing settings surfaces |
| QR | `analyzer/QRAnalyzer.kt`, `ui/QROverlay.kt` | ZXing analysis and overlay |
| Vendored patches | `androidxc/` | Patched CameraX/Exif classes **owned by upstream**; do not edit |

Key facts that the fork builds on:

- `ImageCapture` is built in `CamConfig` with `CAPTURE_MODE_ZERO_SHUTTER_LAG`, `MAXIMIZE_QUALITY` or `MINIMIZE_LATENCY`, a resolution strategy, flash mode and JPEG quality.
- Storage already supports a **SAF tree** as well as MediaStore; the "storage location" setting is exactly the hook Sietch can share.
- EXIF removal is already a setting, on by default.
- CameraX **1.6.2** exposes `OUTPUT_FORMAT_RAW`, `OUTPUT_FORMAT_RAW_JPEG` and `OUTPUT_FORMAT_JPEG_ULTRA_HDR`; the fork can use these instead of writing a second capture engine.
- `Camera2CameraControl.addCaptureRequestOptions` and `Camera2CameraInfo.getCameraCharacteristic` are available for manual controls without rebinding the camera.

## 2. Modules and packages

The engine is the only new Gradle module:

```
ix/                                # pure Kotlin/JVM, JVM 17 bytecode, no Android
└── src/main/kotlin/com/everettpenne/ix/
    ├── IxEngine.kt                # interface + version
    ├── IxModel.kt                 # source, capture, file, request, result, warnings
    ├── IxColor.kt                 # colour + 4x5 matrix maths
    ├── IxStyle.kt                 # presets, renderer, naming
    └── PassThroughIxEngine.kt     # skeleton, not shippable
```

App-side code goes under `app/src/main/java/app/grapheneos/camera/suite/`:

```
suite/
├── ix/          # adapter
│   ├── IxSources.kt    # ImageProxy / Bitmap / Uri -> IxSource
│   ├── IxWriter.kt     # IxFile -> MediaStore or SAF, pending + cleanup
│   └── IxPipeline.kt   # source -> engine -> files, one blocking call
├── manual/      # ManualMath, ranges/state, Camera2CameraControl interop
│   ├── ManualMath.kt       # slider maths, labels, approximate WB gains
│   └── ManualController.kt # attach to Camera2 control, apply/clear options
├── handoff/     # Sietch intent, FileProvider URIs, MediaStore/SAF helpers
└── uno/         # deep links, shortcut routing, Live Update
    ├── HoltzmanLinks.kt      # pure parser: holtzman:// -> mode + lens
    ├── HoltzmanShortcuts.kt  # four dynamic shortcuts, package-scoped
    └── ...
```

**Ix** is the engine that owns the image: bytes in, files and warnings out. Its contract and internal layout are in [ix.md](ix.md). It has no `Context`, `Uri` or UI types; the app decides where the bytes go. The module boundary is compile-time enforcement, not just documentation. The adapter is the only code that knows both worlds; it is blocking and annotated `@WorkerThread`.

A shortcut/deep link is applied in `MainActivity.onCreate` **before** the first camera bind via `CamConfig.prepareMode()`, so a cold start lands directly in the requested mode. A delivery to an existing activity goes through `onNewIntent` and `switchMode()`; the intent data is cleared so the same shortcut works twice. `launchMode="singleTop"` keeps one camera activity, and the shortcut intents add `CLEAR_TOP | SINGLE_TOP` so tapping from Settings returns to it.

Plus new resources (`res/values/holtzman.xml`, `themes.xml` overrides, `res/font/inter_variable.ttf`, the aperture icon, and `strings.xml` additions). The user build removes the system-camera activities and filters directly in `AndroidManifest.xml`; a source-set overlay remains the cleaner option once the `system` flavor exists (see ADR-009).

### 2.1 Manual mode

Manual is a normal camera mode (`CameraMode.MANUAL`), not a separate activity. On entry `CamConfig` shows the manual panel and keeps the camera bound; `ManualController` attaches to the current `Camera` after every bind and applies a `CaptureRequestOptions` set:

- `CONTROL_AE_MODE_OFF`, `SENSOR_SENSITIVITY`, `SENSOR_EXPOSURE_TIME`
- `CONTROL_AF_MODE_OFF`, `LENS_FOCUS_DISTANCE` when the lens has a focus range
- `CONTROL_AWB_MODE_OFF`, `COLOR_CORRECTION_MODE_TRANSFORM_MATRIX` and approximate `COLOR_CORRECTION_GAINS` when AWB-off is advertised

ISO and exposure use logarithmic sliders over the device's advertised ranges; focus is linear in dioptres (∞ at zero). **Auto** clears the options, and any slider moves back into manual. ZSL is disabled in Manual mode because it cannot combine with fixed exposure. Leaving Manual clears the options before the next bind.

If the camera is not a Camera2 implementation, `ManualController.attach` leaves the panel inert with "Manual controls are unavailable"; the camera still captures in auto. The white-balance gains are an approximation and should be calibrated on a real device before the mode is described as colour-accurate.

### 2.2 Identity and theme

| Item | Value |
| --- | --- |
| Application ID | `com.everettpenne.holtzman`; debug suffix `.dev` |
| Kotlin package | `app.grapheneos.camera` (kept for upstream merges) |
| Launcher label | `Holtzman Camera` (`Holtzman Camera d` in debug) |
| Version | `0.1.0` / code `1` |
| Icon | Aperture glyph from the Glass icon recipe; adaptive, monochrome on API 33+ |
| About | "Holtzman Camera · Ix engine", version, upstream MIT credit, roadmap note |
| Theme | Dark glass palette, Inter, 8/16/24dp corners; **Material You disabled** |
| Manifest | `category="image"`, description, no system-camera intents, no `DISABLE_KEYGUARD` |

The `minSdk` is 31. The theme deliberately does not use `DynamicColors`: the suite identity is fixed, and wallpaper-derived colours made the mode pill and switches blue. `App.kt` no longer applies dynamic colours; the settings dialog uses `Theme.SettingsDialog` directly.

The build-file cost is small and deliberate: `settings.gradle.kts` (`include(":ix")`), `app/build.gradle.kts` (`implementation(project(":ix"))`), `gradle/libs.versions.toml` (JUnit) and 32 lines of dependency-verification metadata for the Kotlin JVM plugin. No Android dependency is added to the engine.

## 3. Capture pipeline

### Auto (upstream path)

```
CameraX bind (Preview + ImageCapture [+ VideoCapture])
  → ImageCapture (JPEG)
  → ImageSaver: EXIF, then [debug-only Ix pass through IxJpegDebug]
  → IxResult (bytes + metadata)
  → normal write (MediaStore or SAF)
  → thumbnail / last-shot preview
```

Debug builds pass the finished JPEG through `IxJpegDebug` before the write; it is fail-open, so any Ix error leaves upstream's bytes in place. Release builds take upstream's path untouched. This is the D0.7 experiment; the real style/format stage arrives with D4/D5.

### Manual

```
Manual panel (sliders + Auto)
  → ManualState (validated ranges)
  → Camera2CameraControl.setCaptureRequestOptions(
        CONTROL_AE_MODE_OFF, SENSOR_EXPOSURE_TIME, SENSOR_SENSITIVITY,
        CONTROL_AWB_MODE_OFF, COLOR_CORRECTION_GAINS,
        CONTROL_AF_MODE_OFF, LENS_FOCUS_DISTANCE)
  → CameraX ImageCapture (JPEG)
  → Ix (if a style/format is requested)
  → handoff / ImageSaver
```

Rules:

- Manual mode uses `ExtensionMode.NONE` and disables ZSL: manual exposure and the extension/ZSL paths do not coexist.
- Ranges come from `CameraCharacteristics` (`SENSOR_INFO_EXPOSURE_TIME_RANGE`, `SENSOR_INFO_SENSITIVITY_RANGE`, `LENS_INFO_MINIMUM_FOCUS_DISTANCE`, `CONTROL_AWB_AVAILABLE_MODES`). Never hard-code a fake maximum.
- "Auto" calls `clearCaptureRequestOptions()` and restores the mode's normal capture mode/extensions.
- The panel is a View (bottom sheet or overlay) driven by a small state holder; no Compose in v1.

**Risk:** CameraX may apply its own AE/AWB defaults after interop options. The D2/D3 spike must verify that per-request options actually change the output. If they do not, fall back to a **separate Camera2 manual activity** rather than fighting the framework.

### RAW / RAW+JPEG

```
ImageCapture.Builder().setOutputFormat(OUTPUT_FORMAT_RAW or OUTPUT_FORMAT_RAW_JPEG)
  → ImageProxy (verify format)
  → Ix RAW path (DNG bytes, naming, MIME, DNG/EXIF metadata)
  → IxResult
  → handoff writes MediaStore/SAF
```

- DNG is a first-class output, not a JPEG with a different extension. Use the CameraX DNG output if it works; otherwise a `DngCreator` path fed by `RAW_SENSOR` is the fallback.
- RAW+JPEG writes two files with the same base name and different suffixes; group them in the last-shot UI but keep them separate MediaStore items.
- Styles never modify the DNG.
- EXIF stripping must not remove DNG-required tags; use a separate raw path.

### Ultra HDR

```
ImageCapture.Builder().setOutputFormat(OUTPUT_FORMAT_JPEG_ULTRA_HDR)
  → Ix Ultra HDR path (preserve JPEG + gain map bytes)
  → IxResult
  → handoff writes MediaStore/SAF
```

- Verify that the gain map survives `ImageSaver`'s JPEG trimming/EXIF handling. If it does not, Ix supplies a byte-preserving path for Ultra HDR.
- Ultra HDR requires a capable device/OS; if the format is unsupported, fall back to JPEG with a one-time notice, not a crash.
- Styles on Ultra HDR are a later problem: v1 styles apply to SDR JPEG. Do not silently drop the gain map when a style is selected.

### Styles

```
JPEG (SDR) → Ix style stage (ColorMatrix + tone curve) → JPEG
```

- v1: matrix-based looks (Standard/Warm/Cool/Rich/Soft) with a strength slider. This is fast and predictable; it is not a full 3D LUT.
- The post-capture preview shows the styled result; a true live preview is out of scope for v1.
- Record the style id and strength in EXIF `UserComment`; the RAW (if any) remains the untouched original.
- A native 3D LUT can replace the matrix later without changing the UI or metadata contract.
- Tune skin tones deliberately: protect hue and luminance in skin ranges before increasing saturation/contrast.

## 4. Data and storage

| Path | Use |
| --- | --- |
| `MediaStore` `DCIM/Holtzman` (name TBD) | Default; visible to Gallery, Uno's Photos widget and Glass |
| SAF tree | The existing "storage location" setting; ideal when the user wants photos in a Sietch-visible folder |
| `FileProvider` | Sharing a just-saved file with Sietch or the share sheet |
| App private storage | Settings, last-shot state, thumbnails |

- Use `IS_PENDING` while writing to MediaStore; clear it only after the file is complete.
- File names: `IMG_yyyyMMdd_HHmmss` for JPEG, `IMG_…` + `.dng` for RAW; avoid collisions when RAW+JPEG.
- No database in v1. Last-shot state is already upstream's `SharedPrefs` (URI, type, date).
- The camera's **SAF storage location** is the zero-friction Sietch path: if the user picks a folder both apps can see, Sietch will show the files without any cross-app code.

## 5. Sietch handoff

Two paths, shipped together:

1. **Direct save (no new code in Sietch):** set the camera's storage location to a folder Sietch has been granted. Photos land there; Sietch sees them when it lists the folder.
2. **One-tap send (new Sietch code):** after capture, "Sietch" sends an explicit `ACTION_SEND` with the file URI. Sietch's new receiver shows its folder list, copies the file into the chosen folder with its All-files access, remembers the folder and opens it.

The second is what makes it feel instant. It is a Sietch deliverable, not a camera-only one; see [integration](integration.md#sietch).

## 6. Uno integration

- The app declares `android:category="image"` and a description, so Uno's App Library files it under Images and search finds "camera", "manual", "raw", "scan".
- Shortcuts: Photo · Selfie · Manual · Scan, each a `holtzman://…` deep link handled by the launcher activity.
- A saving/processing job can post an Android 16 **Live Update** (promoted ongoing notification), which Uno's island already renders. Clear it on completion; do not promote idle state.
- Uno-side additions (separate repository): island long-press camera action, control-panel tile, search action, optional widget.
- **Not possible:** being the system camera, the power-double-press camera or the lock-screen camera. Android 11+ reserves those intents; the user build must not claim them.

## 7. UI and Glass

Upstream is Views; Glass is Compose and on an older toolchain. For v1:

- Apply **Glass tokens** in XML: colours, Inter, corner scale (8/16/24/32), hairline separators, translucent bars, press/settle motion, haptic cues.
- Keep the viewfinder clean: no live refraction over the preview; glass-like chrome only at the edges.
- New panels are Views, matching upstream's Material components, styled with the tokens.
- Copy the Inter variable font from Glass with its SIL OFL licence (`docs/licenses/Inter-OFL.txt`).
- Do **not** add `includeBuild("../Glass")` in v1. Revisit when Glass moves to AGP 9 / Kotlin 2.4 / compileSdk 37.

## 8. Permissions and privacy

| Permission | Why | Default |
| --- | --- | --- |
| `CAMERA` | The camera | Required |
| `RECORD_AUDIO` | Audio in video | Asked when recording with audio; optional |
| `ACCESS_COARSE/FINE_LOCATION` | Geo-tagging | Off; asked only when enabled |
| `POST_NOTIFICATIONS` / `POST_PROMOTED_NOTIFICATIONS` | Live Update for saving/processing | Optional; no notifications otherwise |
| `VIBRATE` | Haptics | No prompt |
| `WAKE_LOCK` | Keep the screen/session during capture | No prompt |
| `DISABLE_KEYGUARD` | Secure camera activities | Removed in D1 with the system intents |

Never add `INTERNET`. Never add storage permissions; use MediaStore/SAF. Strip EXIF by default (upstream behaviour); location only if explicitly enabled. No analytics, no accounts, no cloud.

## 9. Testing

- **JVM tests:** `ix/src/test/` covers the engine (model, colour maths, presets, naming, pass-through); `app/src/test/` covers the deep-link parser (`HoltzmanLinksTest`, 5 tests) and manual slider/label maths (`ManualMathTest`, 5 tests). Share-intent construction follows.
- **Instrumented tests** (`app/src/androidTest/`): the Ix adapter writes a Bitmap through MediaStore and reads it back, the debug hook is fail-open, JPEG padding is trimmed, and the four dynamic shortcuts are published with package-scoped `VIEW` intents. Photo, Selfie, Scan, Video and Manual links were also checked on the emulator, cold and via `onNewIntent`; a manual capture applied the panel values.
- **Device checklist** (Pixel, current GrapheneOS): every mode, manual changes, DNG opens elsewhere, Ultra HDR on/off, styles, EXIF, 30-minute thermal run, Sietch handoff, Uno shortcuts and island action.
- **Emulator** is for UI/intents only. It cannot validate focus, HDR, thermal or image quality.

## 10. Build

| Item | Value |
| --- | --- |
| Gradle | 9.8.2 (wrapper) |
| AGP | 9.4.1 |
| Kotlin | 2.4.21 (AGP built-in Kotlin in `app`; Kotlin/JVM plugin in `:ix`) |
| compileSdk / targetSdk | 37 |
| minSdk | 31 (upstream 29; one-line divergence) |
| Build tools | 37.0.0 |
| NDK | 29.0.14206865 (declared; no native code yet) |
| JDK | 17 toolchain; CI uses 21 |

No Glass composite build, no Compose plugin, no second module in v1.
