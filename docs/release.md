# Test builds and public releases

Short answer:

- **D1 is done**, and its debug build can go to GitHub now as `v0.1.0-test`. It installs as **Holtzman Camera d** (`com.everettpenne.holtzman.dev`), with the aperture icon, About page, Glass palette and the Ix JPEG hook.
- **A public release still waits.** The build is debug-signed, the Ix hook is debug-only by design, and there is no privacy page or signed release pipeline yet.

## Readiness

| Gate | State | Notes |
| --- | --- | --- |
| Fork base and docs | done | Upstream `0aa4cb0`; `UPSTREAM`, plan, sync runbook |
| Ix skeleton | done | `:ix`, 27 JVM tests |
| Ix adapter | done | Sources, writer, pipeline; 4 instrumented tests on API 36 |
| Debug JPEG hook | done | `BuildConfig.DEBUG` only; fail-open; release path untouched |
| App builds | emulator only | `:app:assembleDebug` green; no Pixel/GrapheneOS device pass yet |
| Holtzman branding | **done** | `com.everettpenne.holtzman` (debug `.dev`), label, aperture icon, About |
| User-build manifest | **done** | No system-camera intents, no secure/lock-screen activities, no `DISABLE_KEYGUARD` |
| Fixed Glass palette | **done** | Inter, dark ink/surface, amber accent, 8/16/24dp corners; Material You disabled |
| Release signing | **not done** | No `keystore.properties`, no CI signing |
| Version and release notes | debug `0.1.0` | `v0.1.0-test` note exists; signed release note pending |
| Privacy page | **not done** | Upstream has no `PRIVACY.md` |
| Ix behaviour in release | **not yet** | Hook is debug-only until D4/D5 |
| GitHub repository | **done** | Public `everettpenne/HoltzmanCamera`; CI green |

## Path A — the D1 test build

The APK at `app/build/outputs/apk/debug/app-debug.apk`:

- package `com.everettpenne.holtzman.dev` (installs beside GrapheneOS Camera)
- debug-signed, debuggable
- label "Holtzman Camera d"; aperture icon; About names Ix and credits upstream
- fixed Glass palette, Inter, dark settings
- Ix JPEG hook active

Build it with the documented toolchain, then either sideload it or attach it to a GitHub pre-release:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For a GitHub pre-release (do not commit the APK):

```bash
git push origin main
gh release create v0.1.0-test \
  --prerelease \
  --title "Holtzman Camera 0.1.0 test build" \
  --notes-file docs/releases/0.1.0-test.md \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/debug/SHA256SUMS.txt
```

If `gh` asks for `workflow` scope on this repository, create the release through the API and upload the assets separately:

```bash
gh api -X POST repos/everettpenne/HoltzmanCamera/releases \
  -f tag_name=v0.1.0-test -f target_commitish=main \
  -f name="Holtzman Camera 0.1.0 test build" -F prerelease=true \
  -f body="$(cat docs/releases/0.1.0-test.md)"
gh release upload --repo everettpenne/HoltzmanCamera v0.1.0-test \
  app/build/outputs/apk/debug/app-debug.apk \
  app/build/outputs/apk/debug/SHA256SUMS.txt
```

Include `SHA256SUMS.txt` produced with:

```bash
shasum -a 256 app/build/outputs/apk/debug/app-debug.apk > app/build/outputs/apk/debug/SHA256SUMS.txt
```

**Warnings for testers:** debug key, debuggable, package `.dev`; it cannot be updated by a future release-signed APK without uninstalling first. The debug hook is fail-open, so a failure keeps upstream's JPEG.

## Path B — public alpha

Remaining before a normal GitHub release:

1. **Release signing:** create a Holtzman keystore outside the repository, add `keystore.properties` locally (git-ignored) or CI secrets; verify `assembleRelease` is signed and `apksigner verify` passes.
2. **Privacy page:** `PRIVACY.md` listing CAMERA, optional MIC, optional location, notifications; no INTERNET, no storage, no analytics.
3. **Device pass:** install on the Pixel running current GrapheneOS; test capture, save, MediaStore, SAF, debug hook on/off, permissions and a 30-minute thermal run.
4. **CI:** build + `:ix:test` + `:app:assembleDebugAndroidTest`; add the upstream-sync job. Tag only after the same checks pass on the tag.
5. **Release notes and tag:** record the upstream base and Ix version, then publish the signed APK and `SHA256SUMS.txt` with the caveats above.

Estimate: **1–2 weeks** after the signing decision, assuming device access.

## What I need from you

- Whether to use a **new Holtzman key** or an existing suite key for the signed alpha.
- Confirmation that the Pixel/GrapheneOS device pass is done; do not paste secrets into chat.

The public repository is already at [everettpenne/HoltzmanCamera](https://github.com/everettpenne/HoltzmanCamera); pushing `main` and creating the pre-release are the only remaining steps for the D1 build.
