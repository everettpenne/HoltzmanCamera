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
├── ix/          # adapter: ImageProxy/Bitmap -> IxSource, IxFile -> MediaStore/SAF
├── manual/      # ManualState, sensor ranges, Camera2 interop, auto<->manual transitions
├── handoff/     # Sietch intent, FileProvider URIs, MediaStore/SAF helpers
└── uno/         # deep links, shortcut routing, Live Update
```

**Ix** is the engine that owns the image: bytes in, files and warnings out. Its contract and internal layout are in [ix.md](ix.md). It has no `Context`, `Uri` or UI types; the app decides where the bytes go. The module boundary is compile-time enforcement, not just documentation.

Plus new resources (`res/values/suite_colors.xml`, `suite_themes.xml`, `suite_strings.xml`, `res/layout/suite_*.xml`, `res/font/inter_variable.ttf`) and a `user` source-set overlay for the build without system-camera intents.

The build-file cost is small and deliberate: `settings.gradle.kts` (`include(":ix")`), `app/build.gradle.kts` (`implementation(project(":ix"))`), `gradle/libs.versions.toml` (JUnit, JVM plugin alias) and 32 lines of dependency-verification metadata for the Kotlin JVM plugin. No Android dependency is added to the engine.

## 3. Capture pipeline

### Auto (upstream path)

```
CameraX bind (Preview + ImageCapture [+ VideoCapture])
  → ImageCapture (JPEG)
  → [Ix style/format stage, when enabled]
  → IxResult (bytes + metadata)
  → ImageSaver / handoff (MediaStore or SAF)
  → thumbnail / last-shot preview
```

With no style and JPEG output, this is upstream's path untouched. Ix only enters when the user selects a style, RAW, Ultra HDR or another output format.

### Manual

```
Manual panel (sliders + Auto)
  → ManualState (validated ranges)
  → Camera2CameraControl.addCaptureRequestOptions(
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
| `DISABLE_KEYGUARD` | Secure camera activities | Remove with the system intents in the user build |

Never add `INTERNET`. Never add storage permissions; use MediaStore/SAF. Strip EXIF by default (upstream behaviour); location only if explicitly enabled. No analytics, no accounts, no cloud.

## 9. Testing

- **JVM tests:** `ix/src/test/` covers the engine (model, colour maths, presets, naming, pass-through). `app/src/test/` is added later for manual range/step clamping, deep-link parsing and share-intent construction.
- **Instrumented tests** (existing `app/src/androidTest/`): shortcuts resolve to the right mode, permissions flow, save to MediaStore, Sietch intent targets the package, Live Update starts/stops.
- **Device checklist** (Pixel, current GrapheneOS): every mode, manual changes, DNG opens elsewhere, Ultra HDR on/off, styles, EXIF, 30-minute thermal run, Sietch handoff, Uno shortcuts and island action.
- **Emulator** is for UI/intents only. It cannot validate focus, HDR, thermal or image quality.

## 10. Build

| Item | Value |
| --- | --- |
| Gradle | 9.8.2 (wrapper) |
| AGP | 9.4.1 |
| Kotlin | 2.4.21 (AGP built-in Kotlin in `app`; Kotlin/JVM plugin in `:ix`) |
| compileSdk / targetSdk | 37 |
| minSdk | 29 upstream; proposed 31 for suite parity |
| Build tools | 37.0.0 |
| NDK | 29.0.14206865 (declared; no native code yet) |
| JDK | 17 toolchain; CI uses 21 |

No Glass composite build, no Compose plugin, no second module in v1.
