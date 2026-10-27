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

Glass `v0.6.0` is Gradle 8.14.3 / AGP 8.11 / Kotlin 2.1.20 / compileSdk 36 / minSdk 31. The camera fork is Gradle 9.8.2 / AGP 9.4.1 / Kotlin 2.4.21 / compileSdk 37 / minSdk 29. `includeBuild("../Glass")` will not work.

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

**Decision:** the `user` build removes those filters (preferably via a source-set manifest overlay with `tools:node="remove"`, not by deleting upstream lines). A future `system` flavor can restore them for a GrapheneOS image build.

**Consequences:** the fork cannot be the default/lock-screen/power-double-press camera. Uno and Sietch must launch it explicitly. Document this in the README so nobody expects otherwise.

---

## ADR-010 — Raise `minSdk` from 29 to 31

**Status:** proposed.

GrapheneOS targets current Pixels on Android 12+; the rest of the suite is `minSdk 31`. Raising it removes legacy branches and allows real Glass later. It is a one-line divergence.

**Consequences:** the fork drops Android 10/11 devices, which GrapheneOS no longer supports in practice. If upstream still supports 29, the merge is trivial and intentional.

---

## ADR-011 — Name and application ID are placeholders

**Status:** proposed.

`Holtzman` and `com.everettpenne.holtzman` are working values. The name should fit the suite's Dune vocabulary and be pronounceable; the application ID must be settled before the first public release.

**Consequences:** renaming before release is cheap; after release it loses data and breaks intents. Decide in D1, not D8.

---

## ADR-012 — Upstreamable fixes live on their own branches

**Status:** accepted.

Generic fixes (bugs, CameraX compatibility, accessibility) are cut from `upstream/main`, prefixed `upstream:` and offered as PRs. Suite-specific work never goes upstream.

**Consequences:** when a fix lands upstream, the local branch is dropped and the next sync brings it in. This keeps the fork a good citizen and reduces permanent divergence.
