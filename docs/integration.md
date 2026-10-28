# Suite integration

The camera is only half the feature. This document is the contract between the camera, Uno Launcher and Sietch, including what is possible and what is not.

## 1. Camera → Uno Launcher

### What the camera publishes

| Thing | Value | Status |
| --- | --- | --- |
| `android:category` | `image` (Uno's App Library files it under Images) | done in D1 |
| `android:description` | "A camera for the Glass suite: photo, video, scan, manual, RAW and Ultra HDR." | done in D1 |
| Shortcuts | Photo · Selfie · Scan · Video (dynamic; Manual joins in D3) | done in D2 |
| Deep links | `holtzman://photo`, `holtzman://selfie`, `holtzman://scan`, `holtzman://video` (`manual` in D3) | done in D2 |
| Live Update | While saving a RAW/Ultra HDR/style job: short text plus progress; cleared when done | D7 |
| Widget (stretch) | Photo / Selfie / Manual / Scan buttons and the last shot; RemoteViews cannot host a live preview | not in v1 |

The shortcuts are **dynamic**, not a static `shortcuts.xml`: a static intent needs a target package, and the debug build's package (`.dev`) differs from release. `HoltzmanShortcuts` builds package-scoped `VIEW` intents with `CLEAR_TOP | SINGLE_TOP` and publishes four of them on launch. `MainActivity` applies the data before the first camera bind (cold) or through `onNewIntent` (warm), then clears it so the same shortcut works twice. Dynamic shortcuts appear after the app has been opened once; a launcher that does not support them simply shows none.

### What Uno must add (separate repository)

| Surface | File / area | Change |
| --- | --- | --- |
| Island quick actions | `island/IslandQuickActions.kt` | Add **Camera** beside flashlight, timer and stopwatch; opens `holtzman://photo` |
| Control panel | `panel/ControlPanel.kt`, `panel/LiquidControls.kt` | A camera tile that opens the last-used mode |
| Search | `search/UnoActions.kt` | "camera" / "scan" / "manual" typed actions open the deep link |
| App Library | `search/AppLibrary.kt`, `search/AppCategories.kt` | Nothing to do: the manifest category and description are read automatically |
| Long-press | `AppShortcuts.kt` | Nothing to do: manifest shortcuts are read automatically |
| Widget | `widgets/` or the camera app's own widget | Optional; prefer the camera app shipping its own, so Uno stays generic |
| Live Update | `island/` | Nothing to do: promoted ongoing notifications are already rendered |

Uno's island already shows the camera privacy dot while the camera is in use. That is automatic and needs no integration.

### What cannot work

Android 11+ reserves `IMAGE_CAPTURE`, `IMAGE_CAPTURE_SECURE`, `VIDEO_CAPTURE`, `STILL_IMAGE_CAMERA` and `STILL_IMAGE_CAMERA_SECURE` for **preinstalled system camera apps**. A user-installed fork therefore cannot be:

- the power-double-press camera,
- the lock-screen camera,
- the app that opens when another app asks for a photo,
- the target of Android's camera intents in general.

The user build must **not** declare those filters: claiming an intent the OS will not deliver is a lie in the manifest. D1 removed the filters and the secure activities directly from `AndroidManifest.xml`; the helper classes remain for source compatibility. A future `system` flavor, built into a GrapheneOS image, can restore them (see ADR-009).

Uno can still target the camera **explicitly** for its own flows (`Intent.setPackage("com.everettpenne.holtzman")`), which is how Sietch's "Scan a document" should launch it.

## 2. Camera → Sietch

Sietch 1.0 is a full file manager with "All files access" and plain `File` operations (`LocalFs`), not the SAF-only 0.5 model. That makes handoff simpler and faster.

### Path A — save into a Sietch-visible folder (no Sietch change)

The camera already supports a **SAF storage location**. If the user picks `Pictures/Holtzman` (or any folder Sietch can browse), every shot lands there, and Sietch's Images/Recents see it through its media index. This is the zero-friction path and should be documented in the camera's storage setting.

This is not a "handoff" in the UI sense; it is a shared folder. Ship it anyway because it costs nothing and is the most reliable behaviour.

### Path B — "Save to Sietch" after a shot (new Sietch code)

1. The camera saves the file (MediaStore or SAF) and has a content URI.
2. The post-capture sheet shows **Sietch**.
3. The camera sends an explicit intent:

   ```kotlin
   Intent(Intent.ACTION_SEND)
       .setType("image/jpeg")            // or image/x-adobe-dng
       .putExtra(Intent.EXTRA_STREAM, uri)
       .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
       .setPackage("com.everettpenne.sietch")
   ```

4. Sietch receives it in a new `ShareReceiverActivity` (exported, `ACTION_SEND`, `image/*` and `image/x-adobe-dng`).
5. Sietch shows its folder picker (reuse the Browse/Home UI), defaulting to the last folder or `Pictures/Sietch`.
6. Sietch copies the stream into the chosen directory with a unique name, then opens that folder.

Sietch work items:

- Manifest filter for `ACTION_SEND` (`image/*`, `image/x-adobe-dng`) on the new activity.
- A small `SharedImport` helper that streams a `content://` URI into a `File` (Sietch has All files access; no SAF needed).
- A folder picker with a remembered default (`Settings`).
- A success screen/folder open and a failure message that names the reason.
- No new permissions: All files access and media read already exist.

If Sietch is not installed, the camera falls back to the system share sheet or the SAF save, and says so rather than showing a dead button.

### Path C — Sietch "Scan a document" (later)

Sietch's scan flow should explicitly target the camera package (`holtzman://scan` or an explicit capture intent), because the system `IMAGE_CAPTURE` intent will go to GrapheneOS Camera. The camera returns a JPEG/PDF; Sietch files it. This is a later feature, not v1.

## 3. Camera → Glass / the rest of the suite

- **v1:** no Glass dependency (toolchain mismatch). Match Glass with resource tokens: colours, Inter, corner scale, haptics, motion.
- **v1:** photos land in MediaStore, so Uno's Photos widget, GrapheneOS Gallery and Glass's `:media` viewer see them without integration.
- **Later:** when Glass moves to AGP 9 / Kotlin 2.4 / compileSdk 37, swap the token layer for the real Glass components and share the media viewer.
- **Sharing:** after a capture, the share sheet can offer Notes, Seekur, Thumper and Sietch. The camera should not invent its own share UI beyond the Sietch shortcut.

## 4. Live Update contract

- Permission: `POST_NOTIFICATIONS` (Android 13+) and `POST_PROMOTED_NOTIFICATIONS` (Android 16) — the latter only if the app actually promotes.
- Post only while work is happening: saving a DNG, applying a style, writing an Ultra HDR file.
- Title: "Saving photo"; short critical text: "RAW" / "HDR" / "Style"; progress when known.
- Clear on success and on failure; never leave a stale promoted notification.
- Uno's island consumes promoted notifications automatically (Spice and Thumper already do this for downloads).

## 5. Privacy ledger

Uno's permission ledger will show:

| Permission | What it can see | Why |
| --- | --- | --- |
| Camera | The camera | The app's purpose |
| Microphone | Audio while recording video | Only when "Include audio" is on |
| Location | Approximate/precise location | Only when geo-tagging is enabled, off by default |
| Notifications | — | Live Update for save/processing; optional |

No Internet, no storage, no contacts, no accounts. The camera must never add a permission without updating this table, the app's privacy page and Uno's ledger.

## 6. Naming and IDs

| Item | Value |
| --- | --- |
| Product | **Holtzman Camera** |
| Imaging engine | **Ix** (no separate package or app) |
| Application ID | `com.everettpenne.holtzman` (reserved) |
| Launcher label | `Holtzman Camera`; short label `Holtzman` if the launcher truncates |
| Debug suffix | `.dev` (upstream's pattern) |
| Deep-link scheme | `holtzman://` |
| About credit | "Holtzman Camera · Ix engine" |
| Sietch package | `com.everettpenne.sietch` |
| Uno package | `com.jake.duolauncher` (dev variant `com.jake.duolauncher.dev`) |

The application ID is a one-way door. It is settled as `com.everettpenne.holtzman`; changing it after release loses the app's data and breaks shortcuts and Sietch's explicit intents.
