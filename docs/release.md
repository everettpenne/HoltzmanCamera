# Test builds and public releases

Short answer:

- **A debug test build can go to GitHub now** as a pre-release. It installs beside GrapheneOS Camera, turns the Ix JPEG hook on, and is for testing only.
- **A public release should wait.** The app is still upstream's identity (`app.grapheneos.camera`, label "Camera d", upstream icon), not signed for release, and the Ix hook is debug-only by design. Publishing it as a normal release would be a misleading build.

## Readiness

| Gate | State | Notes |
| --- | --- | --- |
| Fork base and docs | done | Upstream `0aa4cb0`; `UPSTREAM`, plan, sync runbook |
| Ix skeleton | done | `:ix`, 27 JVM tests |
| Ix adapter | done | Sources, writer, pipeline; 4 instrumented tests on API 36 |
| Debug JPEG hook | done | `BuildConfig.DEBUG` only; fail-open; release path untouched |
| App builds | emulator only | `:app:assembleDebug` green; no Pixel/GrapheneOS device pass yet |
| Holtzman branding | **not done** | `applicationId` is still `app.grapheneos.camera`; label "Camera d"; upstream icon |
| User-build manifest | **not done** | System-camera intents still declared; a user install cannot receive them |
| Release signing | **not done** | No `keystore.properties`, no CI signing |
| Version and release notes | **not done** | versionCode 94 (upstream); no Holtzman release note |
| Privacy page | **not done** | Upstream has no `PRIVACY.md` |
| Ix behaviour in release | **not yet** | Hook is debug-only until D4/D5 |
| GitHub repository | **not created** | The local clone has `upstream` only; no `origin` |

## Path A — test build now

The APK at `app/build/outputs/apk/debug/app-debug.apk`:

- package `app.grapheneos.camera.dev` (installs beside GrapheneOS Camera)
- debug-signed, debuggable
- Ix JPEG hook active
- label "Camera d" until D1

Build it with the documented toolchain, then either sideload it or attach it to a GitHub pre-release:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a GitHub pre-release, create a repository, push `main`, and attach the APK (do not commit the APK):

```bash
git remote add origin git@github.com:<you>/HoltzmanCamera.git
git push -u origin main
gh release create v0.0.1-test \
  --prerelease \
  --title "Holtzman Camera 0.0.1 test build" \
  --notes "Debug build for testing the Ix JPEG pass. Debug key, package .dev, not for daily use." \
  app/build/outputs/apk/debug/app-debug.apk \
  SHA256SUMS.txt
```

Include `SHA256SUMS.txt` produced with:

```bash
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk > SHA256SUMS.txt
```

**Warnings for testers:** debug key, debuggable, package `.dev`; it cannot be updated by a future release-signed APK without uninstalling first. The debug hook is fail-open, so a failure keeps upstream's JPEG.

## Path B — public alpha

Do these before a normal GitHub release:

1. **D1 branding:** `applicationId = "com.everettpenne.holtzman"`, label "Holtzman Camera", new icon, About ("Holtzman Camera · Ix engine", upstream credit), and the `user` manifest overlay removing the system-camera intents. Decide `minSdk` 29 vs 31.
2. **Release signing:** create a Holtzman keystore outside the repository, add `keystore.properties` locally (git-ignored) or CI secrets; verify `assembleRelease` is signed and `apksigner verify` passes.
3. **Version and notes:** choose `versionCode`/`versionName` (start `0.1.0`), write `docs/releases/<version>.md`, and record the upstream base.
4. **Privacy page:** `PRIVACY.md` listing CAMERA, optional MIC, optional location, notifications; no INTERNET, no storage, no analytics.
5. **Device pass:** install on the Pixel running current GrapheneOS; test capture, save, MediaStore, SAF, debug hook on/off, permissions and a 30-minute thermal run.
6. **CI:** build + `:ix:test` + `:app:assembleDebugAndroidTest`; add the upstream-sync job. Tag only after the same checks pass on the tag.
7. **Release:** tag `v0.1.0`, attach the signed APK and `SHA256SUMS.txt`, publish release notes with caveats.

Estimate: **1–2 weeks** after the branding and signing decisions, assuming device access; the code work for D1 is 3–5 days.

## What I need from you

- The GitHub owner and repository name (for example `everettpenne/HoltzmanCamera`), and whether it is public or private.
- Whether to publish a **debug pre-release now** or wait for the signed alpha.
- The signing plan: a new Holtzman key, or an existing suite key. Do not paste secrets into chat; put them in `keystore.properties` or CI secrets.

I cannot push without a repository URL and credentials. Once the repository exists, the remote and release commands above are all that is left.
