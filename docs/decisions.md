# Decisions

Short records of choices that are expensive to reverse. Status is **proposed**, **accepted**, **superseded** or **rejected**.

---

## ADR-001 — `main` is the fork product; upstream is merged, never rebased

**Status:** accepted.

`upstream` is GrapheneOS Camera. `main` carries upstream plus suite commits. Sync merges `upstream/main` into `main`. Published history is never rebased; upstream PRs are separate branches cut from `upstream/main`.

**Consequences:** merge conflicts are resolved once and preserved; `git diff upstream/main` is the divergence report; the fork can always be diffed against a known base.

---

## ADR-002 — Keep upstream's Kotlin package; change only the application ID

**Status:** accepted.

The source stays under `app.grapheneos.camera`; the installed identity becomes `com.everettpenne.holtzman`. Renaming the package would touch every file and make every upstream merge a conflict.

**Consequences:** some class names say "grapheneos" in a fork; that is acceptable. The application ID must be final before release (see ADR-011).

---

## ADR-003 — No Glass composite build in v1; use Glass tokens

**Status:** accepted for v1.

Glass `v0.6.0` is Gradle 8.14.3 / AGP 8.11 / Kotlin 2.1.20 / compileSdk 36 / minSdk 31. The camera fork is Gradle 9.8.2 / AGP 9.4.1 / Kotlin 2.4.21 / compileSdk 37; its `minSdk` is now 31 too. `includeBuild("../Glass")` will not work.

**Decision:** match the look with XML resources (colours, Inter, corner scale, haptics, motion) and new Views. Revisit when Glass moves to AGP 9 — which its own README anticipates for Backdrop 2.x.

**Consequences:** visual parity is close but not literal; no shared component fixes. A future Glass upgrade is a separate project, not a blocker for the camera.

---

## ADR-004 — Keep upstream's View UI; do not rewrite the viewfinder in Compose

**Status:** accepted for v1.

Upstream is Views + ViewBinding + Material Components. A Compose viewfinder would rewrite the largest, most latency-sensitive surface and make upstream merges impossible.

**Decision:** new panels and sheets are Views styled with suite tokens. Compose can be used later for a new, isolated screen if the toolchain supports it; the viewfinder stays upstream.

**Consequences:** new UI code looks like upstream, not like Glass's Compose. That is the price of mergeability.

---

## ADR-005 — Stay on the CameraX capture path; manual via Camera2 interop

**Status:** proposed; depends on the D2/D3 spike.

Use `Camera2CameraControl.addCaptureRequestOptions` and `Camera2CameraInfo.getCameraCharacteristic` for manual shutter/ISO/focus/WB. Keep `ImageCapture` for the file. Disable ZSL and extensions in manual mode. If interop cannot change the output reliably, fall back to a **separate Camera2 manual activity** rather than forking the whole capture path.

**Consequences:** one camera owner at a time; mode transitions must be explicit. The fallback is contained but is a second viewfinder to maintain.

---

## ADR-006 — Use CameraX output formats for RAW and Ultra HDR

**Status:** proposed; verify in a spike.

CameraX 1.6.2 exposes `OUTPUT_FORMAT_RAW`, `OUTPUT_FORMAT_RAW_JPEG` and `OUTPUT_FORMAT_JPEG_ULTRA_HDR`. Prefer them over a custom Camera2/DNG pipeline. The spike must confirm:

- what `ImageProxy` format/bytes actually arrive,
- whether the existing `ImageSaver` JPEG trimming/EXIF path preserves DNG and gain-map data,
- whether the device falls back cleanly when a format is unsupported.

**Consequences:** if the formats are not usable, D4 grows a dedicated raw saver. That is still cheaper than replacing the capture stack.

---

## ADR-007 — Styles are SDR-first; RAW is never modified

**Status:** accepted.

v1 applies photographic looks to SDR JPEG at save time using a colour matrix plus tone curve, with a strength value recorded in EXIF. RAW/DNG remains the untouched original. Ultra HDR + styles is deferred because it requires processing the gain map, not just the SDR image.

**Consequences:** styles are non-destructive only if RAW is on; a true 3D LUT is later work. Live style preview is out of scope for v1; the preview is the post-capture review.

---

## ADR-008 — Sietch handoff is an explicit share intent, plus a shared folder

**Status:** accepted.

Two paths:

1. Camera storage location points at a folder Sietch can browse (works today, no Sietch change).
2. After capture, an explicit `ACTION_SEND` to `com.everettpenne.sietch` with a `content://` URI; Sietch 1.0 copies the stream into a chosen folder using its All-files access and remembers the folder.

**Consequences:** Sietch gets a new exported `ACTION_SEND` activity and a folder picker. The camera needs a post-capture Sietch action and a graceful fallback when Sietch is absent. No new permissions on either side.

---

## ADR-009 — Remove system-camera intents from the user build

**Status:** accepted.

Android 11+ will not deliver `IMAGE_CAPTURE`, `IMAGE_CAPTURE_SECURE`, `VIDEO_CAPTURE`, `STILL_IMAGE_CAMERA` or `STILL_IMAGE_CAMERA_SECURE` to a user-installed app. Keeping the filters misrepresents what the app can do.

**Decision:** D1 removes the filters and the secure/lock-screen activities directly from `AndroidManifest.xml`, and removes `DISABLE_KEYGUARD`. The helper classes stay for source compatibility. A source-set overlay with `tools:node="remove"` is deferred until a `system` flavor exists; a future system build can restore the upstream declarations.

**Consequences:** the fork cannot be the default/lock-screen/power-double-press camera. Uno and Sietch must launch it explicitly. The manifest is now a deliberate hot file, so every upstream intent change is reviewed at merge time. Document this in the README so nobody expects otherwise.

---

## ADR-010 — Raise `minSdk` from 29 to 31

**Status:** accepted (2026-10-27); implemented in D1.

GrapheneOS targets current Pixels on Android 12+; the rest of the suite is `minSdk 31`. Raising it removes legacy branches and allows real Glass later. It is a one-line divergence.

**Consequences:** the fork drops Android 10/11 devices, which GrapheneOS no longer supports in practice. If upstream still supports 29, the merge is trivial and intentional.

---

## ADR-011 — Name: Holtzman Camera, engine: Ix

**Status:** accepted (2026-10-27); implemented in D1.

- **Product:** **Holtzman Camera**.
- **Imaging engine:** **Ix** (pronounced "icks"), the Dune planet of invention and technology.
- **Application ID:** `com.everettpenne.holtzman` (reserved).
- **Launcher label:** `Holtzman Camera`; the debug build appends ` d` (upstream's pattern).
- **Deep-link scheme:** `holtzman://`.
- **Credit:** "Holtzman Camera · Ix engine" in About; no visible watermark by default.

The long launcher label may truncate on a home screen. If it does, the short label can be **Holtzman** while the About page, README and store listing keep the full product name. Do not rename the application ID to shorten a label.

**Consequences:** after the first public release the application ID is a one-way door; changing it loses installed data and breaks Sietch's explicit intents and Uno shortcuts. Before then it can still change. The engine name is independent of the app version and can be reused if Ix ever serves another app.

---

## ADR-012 — Upstreamable fixes live on their own branches

**Status:** accepted.

Generic fixes (bugs, CameraX compatibility, accessibility) are cut from `upstream/main`, prefixed `upstream:` and offered as PRs. Suite-specific work never goes upstream.

**Consequences:** when a fix lands upstream, the local branch is dropped and the next sync brings it in. This keeps the fork a good citizen and reduces permanent divergence.

---

## ADR-013 — Ix is the imaging engine, not the app

**Status:** accepted (2026-10-27).

Ix owns the image: output formats (JPEG, RAW/DNG, RAW+JPEG, Ultra HDR), colour and photographic styles, encoding, image metadata and fallbacks. It does **not** own camera control, the UI, permissions, storage destinations, Sietch handoff or Uno integration.

The contract is deliberately narrow: bytes and capture facts in; files and warnings out. No `Context`, `Uri` or UI types. The app decides where the files go.

**Consequences:** Ix can be tested with fixture images; the camera can be tested without the engine's internals; future computational work (burst HDR, Night, portrait) has a home that does not require renaming the product. It lives in the **pure JVM `:ix` module** from the start, which enforces the no-Android boundary at compile time and lets its tests run without the Android SDK. The cost is a small build-file divergence: `settings.gradle.kts`, `app/build.gradle.kts`, `gradle/libs.versions.toml` and 32 lines of dependency-verification metadata for the Kotlin JVM plugin. The app-side adapter stays in `app/.../suite/ix/`. See [`ix.md`](ix.md).

---

## ADR-014 — Fixed Glass palette, not Material You

**Status:** accepted (2026-10-27).

Upstream calls `DynamicColors.applyToActivitiesIfAvailable` and wraps the settings dialog in a dynamic context. That makes the mode pill, switches and selected chips take the wallpaper's colours; on the test device the Holtzman palette became blue.

**Decision:** Holtzman disables dynamic colour. `Theme.Camera` and `Theme.App` use the fixed ink/surface/amber palette in `values/holtzman.xml`, Inter, and the 8/16/24dp corner scale. The settings dialog uses `Theme.SettingsDialog` directly.

**Consequences:** the camera keeps the suite identity on every wallpaper and theme setting. There is no per-user Material You tint; a future Glass theme system can provide deliberate variants instead. This is a small upstream edit in `App.kt` and `MainActivity.kt`, recorded in the hot-file list.

---

## ADR-015 — Dynamic shortcuts, not static shortcuts.xml

**Status:** accepted (2026-10-27).

A static launcher shortcut's `<intent>` needs a target package. Holtzman has two installed packages — `com.everettpenne.holtzman` and the debug `.dev` — so a single `shortcuts.xml` would point at the wrong one in one variant.

**Decision:** publish four **dynamic** shortcuts from `HoltzmanShortcuts` with `packageName`-scoped `VIEW` intents (`photo`, `selfie`, `scan`, `video`). The launcher reads them through `ShortcutManager`, and Uno's `AppShortcuts` does the same. Manual joins the set in D3, replacing Video if the four-shortcut limit bites.

**Consequences:** shortcuts appear after the app has been opened once, not immediately on install; a launcher without shortcut support shows none. The intents carry `CLEAR_TOP | SINGLE_TOP`, and `MainActivity` is `singleTop`, so a warm delivery goes through `onNewIntent` instead of stacking camera activities.
