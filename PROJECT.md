# Holtzman Camera — project outline

**Product:** Holtzman Camera. **Imaging engine:** Ix. **Package:** `com.everettpenne.holtzman`.
**Status:** D1 done; the D0.5 Ix skeleton, D0.6 adapter and D0.7 debug JPEG hook are also done. D2 is next. **Base:** GrapheneOS Camera `main` at `0aa4cb0` (2026-10-23, versionCode 94), recorded in [`UPSTREAM`](UPSTREAM).
**Licence:** MIT, upstream copyright preserved.

> The name is chosen: **Holtzman Camera**, with **Ix** as the imaging engine. The application ID is reserved as `com.everettpenne.holtzman`; it can still change before the first public release, but not after — that would orphan installed data. See [the naming decisions](docs/decisions.md).

---

## 1. Why this exists

GrapheneOS Camera is a strong, privacy-first camera, but it is deliberately not an enthusiast camera and it is not part of the Glass suite. This fork keeps its camera core and adds four things:

1. **Deliberate control** — manual shutter, ISO, focus and white balance, with the ranges the sensor actually reports.
2. **Files worth keeping** — RAW/DNG and Ultra HDR alongside JPEG, with the suite's EXIF habits.
3. **A look of its own** — photographic styles with a strength control, tuned for skin tones.
4. **A place in the suite** — one-tap handoff to Sietch, Uno shortcuts and surfaces, and the same privacy posture as the other apps.

**Holtzman Camera** is the product; **Ix** is the imaging engine inside it (file formats, colour, styles, and later the computational pipeline). The engine has its own contract in [docs/ix.md](docs/ix.md) so it can grow — burst HDR, Night, portrait — without changing the app's identity or the upstream capture plumbing.

It is a **camera for people who already chose GrapheneOS**, not a Pixel Camera replacement. Google's HDR+/Night Sight/Super Res pipelines are proprietary and sensor-tuned; this app should not promise to match them. Its differentiators are control, file formats, privacy and integration.

## 2. Product definition

**Who it is for:** a GrapheneOS user on a Pixel who wants more than the stock camera's automatic path, keeps RAW or HDR files, organises photos in Sietch, and launches the camera from Uno.

**The three jobs:**

| Job | What "done" looks like |
| --- | --- |
| Take a good photo quickly | Cold start to a usable viewfinder; 4:3 default; tap to focus and meter; volume-key shutter; save without a permission dialog |
| Take a deliberate photo | Manual shutter/ISO/focus/WB with live feedback; RAW and RAW+JPEG; the settings survive a restart |
| Get the photo where it belongs | Save to MediaStore (so Gallery, Uno's Photos widget and Glass see it) or a SAF folder; one tap to send to Sietch; share to the rest of the suite |

**Non-goals for v1:**

- HDR+ burst stacking, Night Sight, Portrait, Super Res Zoom, Top Shot, Magic Eraser, semantic search. These are later phases (and some are not worth chasing).
- Replacing the system camera, the power-double-press camera, or the lock-screen camera. Android 11+ restricts those intents to preinstalled system camera apps.
- Cloud processing, accounts, analytics, or a network permission.
- A Compose/Glass rewrite of the viewfinder.

## 3. Constraints that shape the design

| Constraint | Consequence |
| --- | --- |
| Upstream is **Views + ViewBinding + Material Components**, not Compose | Keep the upstream UI. Add new surfaces as Views; do not rewrite the viewfinder in Compose. |
| Upstream is on **AGP 9.4.1 / Gradle 9.8.2 / Kotlin 2.4.21 / compileSdk 37 / minSdk 29**; the fork raises `minSdk` to **31** | The suite's Glass `v0.6.0` is AGP 8.11 / Gradle 8.14.3 / Kotlin 2.1.20 / compileSdk 36 / minSdk 31. `includeBuild("../Glass")` is **not** compatible in v1. Match Glass with resources and tokens instead; revisit when Glass moves to AGP 9. |
| Android 11+ reserves `IMAGE_CAPTURE`, `IMAGE_CAPTURE_SECURE`, `VIDEO_CAPTURE`, `STILL_IMAGE_CAMERA` and the secure variant for **preinstalled system camera apps** | A user-installed fork cannot be the default camera. Remove those intent filters from the user build (or move them behind a future `system` flavor). |
| Pixels expose **only the Night** CameraX extension (per GrapheneOS) | Keep upstream's mode tabs honest: Night may work; HDR, Portrait and Face Retouch are not available through the extension on Pixels. Do not fake them. |
| CameraX 1.6.2 supports `OUTPUT_FORMAT_RAW`, `OUTPUT_FORMAT_RAW_JPEG`, `OUTPUT_FORMAT_JPEG_ULTRA_HDR` and `CAPTURE_MODE_ZERO_SHUTTER_LAG` | RAW and Ultra HDR can stay on the CameraX capture path, reducing the need for a second Camera2 engine. Verify each in a spike. |
| Upstream already has a SAF storage location, EXIF stripping, ZSL, grid, timer, EIS and QR scanning | Build on them; do not duplicate. |
| One camera device, one owner | Manual interop must not fight the CameraX session. Changing to manual stops ZSL and extension modes; changing back restores them. |
| Real device required for camera validation | Emulator tests cover intents and UI only. Every milestone has a Pixel device checklist. |

## 4. Deliverables

Estimates are for one developer, including tests and a device pass. D3/D4/D5 can overlap once D2 is stable.

| ID | Deliverable | Scope | Depends on | Done when | Est. |
| --- | --- | --- | --- | --- | --- |
| **D0** | **Fork, docs and sync baseline** | Upstream clone, `upstream` remote, `UPSTREAM`, project outline, architecture, upstream runbook, decisions | — | Docs exist; base commit recorded; `git fetch upstream` works | done |
| **D0.5** | **Ix skeleton** | Pure `:ix` JVM module: contract, colour/style maths, naming, warnings, pass-through engine; 27 JVM tests | D0 | `:ix:test` green; no Android imports; verification metadata updated | done |
| **D0.6** | **Ix adapter** | `IxSources` (`ImageProxy`/`Bitmap`/`Uri`), `IxWriter` (MediaStore/SAF, pending and cleanup), `IxPipeline`; instrumented adapter test | D0.5 | app and `debugAndroidTest` compile; adapter has no new permissions; 3 instrumented tests pass on the API 36 emulator | done |
| **D0.7** | **Debug JPEG hook** | Debug-only pass in `ImageSaver` after EXIF, before the normal write; fail-open; release builds never enter it | D0.6 | debug capture keeps the original bytes on any Ix failure; 4 instrumented tests pass; release path unchanged | done |
| **D1** | **Buildable, rebranded fork** | `applicationId` `com.everettpenne.holtzman` (debug `.dev`), label, aperture icon, About ("Holtzman Camera · Ix engine", upstream credit); fixed Glass palette + Inter; remove system-camera intents; CI builds | D0 | `:app:assembleDebug` green; installs as `com.everettpenne.holtzman.dev` beside GrapheneOS Camera; no system intents; About names Ix and credits upstream; debug capture passes through Ix | done |
| **D2** | **v1 camera core** | Upstream features retained (photo, video, QR, Night where available); 4:3 default; tap focus/meter; volume shutter; MediaStore + SAF save; EXIF strip default; shortcuts and deep links; category/description | D1 | Photo/video save and appear in Gallery/Uno; shortcuts open the right mode; no permission beyond CAMERA (+ optional MIC/location) | 1–2 w |
| **D3** | **Manual controls** | Shutter, ISO, focus and WB panels; sensor ranges from `CameraCharacteristics`; auto/manual switch; live histogram; ZSL and extensions disabled while manual; restore on exit | D2 | Each control visibly changes the capture; ranges are the device's; auto restores cleanly; JVM tests for range/step logic | 2–3 w |
| **D4** | **Ix: RAW/DNG and Ultra HDR** | `OUTPUT_FORMAT_RAW`/`RAW_JPEG`; DNG save and EXIF; Ultra HDR `OUTPUT_FORMAT_JPEG_ULTRA_HDR` with gain map; graceful fallback when unsupported; storage naming | D2 (parallel with D3) | DNG opens in darktable/RawTherapee; Ultra HDR shows on an HDR display and falls back; files land in MediaStore/SAF correctly | 2–3 w |
| **D5** | **Ix: photographic styles** | Standard / Warm / Cool / Rich / Soft; strength; post-capture preview; apply to SDR JPEG at save; style name in metadata; RAW untouched; skin-tone-safe tuning | D4 | Post-capture preview matches the saved output; strength works; original recoverable from RAW; no crash on 12 MP | 1–2 w |
| **D6** | **Sietch handoff** | "Save to Sietch" after capture (explicit intent), plus a shared-folder save via the existing storage location; Sietch `ACTION_SEND` receiver with folder picker, remembered folder, progress | D2 + Sietch change | One tap sends a JPEG/DNG; Sietch saves into a chosen folder and opens it; no share sheet needed | 1–2 w (+3–5 d Sietch) |
| **D7** | **Uno integration** | Category `image`, description; shortcuts Photo/Selfie/Manual/Scan; deep links; processing Live Update; Uno island quick action, control-panel tile, search action; optional camera widget | D5, D6 | Long-press shows shortcuts; island action opens a mode; Live Update shows and clears; Photos widget sees new files | 1–2 w |
| **D8** | **Hardening and v1 release** | JVM tests, instrumented intent tests, device checklist, thermal/battery pass, accessibility, privacy audit, release notes, signed APK | all | Checklist signed off; no ANRs over a 30-minute burst; permissions audited; release notes name the upstream base and the Ix version | 1–2 w |

**v1.0 = D1–D8**, on top of the finished D0.5 skeleton, D0.6 adapter and D0.7 debug hook. Internal previews (`v0.1`, `v0.2`, `v0.3`) follow D2, D4 and D6 so the camera is usable and testable before the full set lands.

### Acceptance criteria, in plain language

- **D1:** The app installs next to GrapheneOS Camera, says Holtzman, looks like the suite, and builds from a clean checkout on the documented toolchain.
- **D2:** I can take a 4:3 photo, a video and a QR scan; photos appear in Gallery and Uno's Photos widget; the file has no EXIF unless I asked for geo-tagging; long-press offers Photo/Selfie/Manual/Scan.
- **D3:** I can set 1/8 s at ISO 1600, focus at 0.5 m, and lock white balance; the result is visibly different from auto; "Auto" restores everything.
- **D4:** I can capture DNG and open it elsewhere; I can turn on Ultra HDR and see it on a capable display; unsupported devices fall back to JPEG without an error.
- **D5:** I pick "Warm" at 60% and the saved photo matches the post-capture preview within a reasonable tolerance; the DNG is untouched; skin does not go orange or waxy.
- **D6:** After a shot, "Sietch" saves into my chosen folder in one tap; next time it remembers the folder.
- **D7:** In Uno, the camera appears under Images, long-press shows the shortcuts, the island's camera action opens the right mode, and a processing job shows in the island.
- **D8:** The privacy page lists CAMERA, optional MIC and optional location; there is no INTERNET permission; a 30-minute shooting session does not overheat or ANR.

## 5. Sequence

```
D0 ──► D1 ──► D2 ──┬──► D3 (manual) ──► D4 (RAW/HDR) ──► D5 (styles) ──┐
                   └──► D6 (Sietch handoff) ────────────────────────────┴──► D7 (Uno) ──► D8 (release)
```

- **D1 before D2** because the app must be installable and attributable before features.
- **D3 and D4 share a spike**: confirm CameraX manual interop does not break the capture path; confirm RAW/Ultra HDR `ImageProxy` handling before designing the saver.
- **D5 depends on D4** because Ultra HDR and styles interact; styles are SDR-first.
- **D6 can run in parallel** with D3/D4 once D2 saves files reliably.
- **D7 after D5/D6** so the shortcuts and handoff are real.

## 6. Structure

The fork keeps upstream's `:app` module and adds one pure JVM module, `:ix`, for the imaging engine. New app-side code lives in a `suite` package so upstream files stay thin. See [architecture](docs/architecture.md) for the full map.

```
Holtzman/
├── ix/                                   # NEW: pure JVM imaging engine (Ix)
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/everettpenne/ix/       # contract, colour, styles, pass-through
├── app/
│   ├── build.gradle.kts                  # hot: applicationId, label, minSdk, test deps
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml       # hot: label/category/description/shortcuts
│       │   ├── java/
│       │   │   ├── app/grapheneos/camera/        # upstream core
│       │   │   │   ├── CamConfig.kt              # hot: modes, capture builder, settings
│       │   │   │   ├── capturer/                 # hot: ImageCapturer, ImageSaver
│       │   │   │   ├── ui/activities/            # hot: MainActivity, settings
│       │   │   │   └── suite/                    # NEW: app-side fork code
│       │   │   │       ├── ix/                   # adapter: ImageProxy/Bitmap <-> Ix, writes files
│       │   │   │       ├── manual/               # ranges, interop, state
│       │   │   │       ├── handoff/              # Sietch, MediaStore, SAF
│       │   │   │       └── uno/                  # deep links, Live Update
│       │   │   └── androidxc/                    # vendored patched CameraX/Exif (do not touch)
│       │   └── res/                              # hot: themes/colors/strings/layouts
│       ├── user/                                 # NEW: flavor overlay (remove system intents)
│       └── androidTest/                          # upstream tests + new intent tests
├── docs/
│   ├── architecture.md
│   ├── ix.md
│   ├── upstream.md
│   ├── integration.md
│   └── decisions.md
├── PROJECT.md
├── UPSTREAM
├── README.md
├── LICENSE
└── THIRD_PARTY_NOTICES.md
```

**`:ix` is the only new module.** It is pure Kotlin/JVM with JVM 17 bytecode; no Android types, no UI. It exists so the engine is testable without the Android SDK and cannot accidentally grow a `Context`. The app depends on it (`implementation(project(":ix"))`); the adapter in `suite/ix/` is the only place that touches Android image types.

## 7. Toolchain

Upstream needs a newer toolchain than the other suite apps:

| Requirement | Notes |
| --- | --- |
| JDK 17 full JDK | Required as a Gradle toolchain; a newer JDK may run Gradle |
| Android platform 37 | `sdkmanager "platforms;android-37.0"` if missing |
| Build-tools 37.0.0 | `sdkmanager "build-tools;37.0.0"` if missing; the app build needs it |
| NDK 29.0.14206865 | Not needed for the current app build; install it when native code lands |
| Gradle 9.8.2 | The wrapper downloads it; use a writable `GRADLE_USER_HOME` |
| Android SDK home | Set `ANDROID_USER_HOME` to a writable path if `~/.android` is not writable (the debug keystore lives there) |
| `local.properties` | Create with `sdk.dir=...`; it is git-ignored |

The engine builds and tests without the Android SDK:

```bash
JAVA_HOME=/path/to/jdk-21 \
GRADLE_USER_HOME=/path/to/gradle-home \
  ./gradlew :ix:test \
  -Porg.gradle.java.installations.paths=/path/to/jdk-17
```

The app build additionally needs build-tools 37, the JDK 17 toolchain and a writable Android home:

```bash
ANDROID_USER_HOME=/path/to/writable-android-home \
JAVA_HOME=/path/to/jdk-21 \
GRADLE_USER_HOME=/path/to/gradle-home \
  ./gradlew :app:assembleDebug \
  -Porg.gradle.java.installations.paths=/path/to/jdk-17
```

**Verified on 2026-10-27:** `:ix:test` (27 tests), `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, and the 4 `IxAdapterInstrumentedTest` tests on an API 36 emulator.

**Read this before adding Glass:** the camera's AGP/Gradle/Kotlin are ahead of Glass's. A composite build of Glass will not work in v1. The plan is resource/token parity now, real Glass when Glass moves to AGP 9 (which the Glass README already anticipates for Backdrop 2.x).

## 8. Upstream strategy (summary)

The full runbook is in [docs/upstream.md](docs/upstream.md). The rules:

- `upstream` is `GrapheneOS/Camera`; `main` is this fork's product branch.
- **Merge** `upstream/main` into `main`; never rebase published history.
- Keep upstream edits to a small **hot-file** set; put engine code in `:ix`, app-side code in `suite/`, and use resource/flavor overlays where possible.
- **Never rename the upstream package** (`app.grapheneos.camera`); change only `applicationId`.
- Prefix our commits `suite:`; prefix upstreamable fixes `upstream:` and keep them on their own branch so they can be PR'd and dropped.
- Sync before every release; record the new base in `UPSTREAM`.
- CI runs a scheduled "merge upstream into scratch and build" job so conflicts are found on a Tuesday, not release day.
- Divergence budget: review `git diff --stat upstream/main` monthly; if the touched-upstream-file count grows, refactor into `suite/` or an overlay.

## 9. Risks

| Risk | Likelihood | Mitigation |
| --- | --- | --- |
| Upstream moves fast and conflicts pile up | High | Weekly fetch, monthly merge, small hot-file set, CI sync job, `rerere` |
| CameraX manual interop changes the capture path or breaks extensions | Medium | Spike before D3; keep a fallback plan for a separate Camera2 manual activity |
| RAW/Ultra HDR `ImageProxy` formats are not what `ImageSaver` expects | Medium | Spike first; add a dedicated saver path rather than bending the JPEG one |
| Glass and the camera's toolchains stay incompatible | Medium | Token layer now; track Glass's AGP 9 move; do not block v1 on Glass |
| Upstream's `compose-prototype` branch becomes the UI | Low–medium | Watch it each sync; if it lands, revisit the View-token layer and Glass compatibility together |
| Dependency-verification metadata conflicts on a Kotlin bump | Medium | Keep the addition to the 4 Kotlin scripting components; when upstream updates Kotlin, re-add only what the build reports missing, never regenerate the whole file |
| The user build cannot be the system camera | Certain | Remove system intents; integrate through Uno, shortcuts and explicit intents; consider a future `system` flavor |
| Sietch handoff needs a Sietch change | Certain | Treat it as a separate 3–5 day workstream; ship the SAF-folder path first |
| Device-only behaviour (HDR, focus, thermal) | High | Pixel device checklist each milestone; no release on emulator evidence alone |
| Licence/attribution mistakes | Low | Keep MIT header, credit GrapheneOS in About and README, add notices |
| Scope creep into HDR+/Night | Medium | Non-goals are explicit; revisit only after v1 |

## 10. Open decisions

1. **UI strategy.** Recommended: upstream Views + a Glass token/resource layer. Alternative: a Compose `suite-ui` module later. Do not rewrite the viewfinder.
2. **Manual engine.** Recommended: `Camera2CameraControl.addCaptureRequestOptions` with CameraX; fallback: a separate Camera2 manual activity.
3. **RAW/Ultra HDR path.** Recommended: CameraX output formats; fallback: custom Camera2/DNG path. Decide after the D2 spike.
4. **Styles implementation.** Recommended: ColorMatrix + tone curve in Ix v1; native 3D LUT later.
5. **Sietch handoff.** Recommended: explicit `ACTION_SEND` to Sietch with a remembered folder, plus the existing SAF storage location for zero-friction direct saves. Both.
6. **minSdk.** Resolved in D1: **31**, matching the suite and current GrapheneOS. Upstream remains 29; the one-line divergence is recorded in ADR-010.
7. **System flavor.** A future `system` build could keep the system-camera intents for a GrapheneOS ROM integration. Not in v1.
8. **Distribution.** GitHub releases initially; see [test builds and releases](docs/release.md) for the debug-pre-release vs signed-alpha path. Do not commit keys.

## 11. Working agreements

- **One owner per file.** Upstream files are edited surgically; new behaviour goes in `suite/`.
- **Commit prefixes:** `suite:` for fork changes, `upstream:` for fixes intended for GrapheneOS, `docs:` for docs.
- **Tests:** pure rules (ranges, style maths, file naming, intent parsing) get JVM tests. Android behaviour gets an instrumented test or a device-checklist line. Upstream has no JVM tests today; this fork adds them.
- **No mass reformatting**, no package rename, no dependency churn without a reason recorded in `docs/decisions.md`.
- **Privacy copy matches code.** CAMERA is the only required permission; MIC is optional and only while recording with audio; location is opt-in and off by default; no INTERNET.
- **Every release notes its upstream base.**
- **Docs change with the code** when a decision, hot file or sync rule changes.

## 12. Definition of done for v1

- All D1–D8 acceptance criteria met on a physical Pixel running current GrapheneOS.
- `:ix:test` (engine) and `:app:assembleDebug` pass on a clean checkout.
- The app installs beside GrapheneOS Camera, declares no system-camera intents, and requests only the documented permissions.
- Manual, RAW/DNG, Ultra HDR and styles each have a device-tested happy path and a graceful fallback.
- "Save to Sietch" works with the Sietch change; SAF direct-save is documented.
- Uno shows the camera's shortcuts, category and island action; a processing job appears as a Live Update.
- `UPSTREAM` points at the base commit used for the release; `docs/upstream.md` has the sync log entry.
