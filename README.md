# Holtzman Camera

A camera for the [Glass](https://github.com/everettpenne/Glass) suite, forked from [GrapheneOS Camera](https://github.com/GrapheneOS/Camera) (MIT). Its imaging engine is **Ix**.

**Status: D3 manual controls.** Holtzman Camera installs as `com.everettpenne.holtzman` (debug `…dev`), with its aperture icon, "Holtzman Camera" label, About page ("Holtzman Camera · Ix engine", upstream credit), and a fixed Glass palette in place of Material You. The user manifest no longer declares the system-camera intents. **D3 adds a Manual mode with a live shutter/ISO/focus/white-balance panel and an Auto reset; Manual replaces Video in the four dynamic shortcuts (Photo, Selfie, Manual, Scan).** D2 added `holtzman://photo|selfie|manual|scan|video` deep links; each opens the requested mode and clears the intent so the same shortcut works twice. The `:ix` module contains the engine contract, colour/style maths, naming, warnings and a pass-through implementation with 27 JVM tests. The adapter (`IxSources`, `IxWriter`, `IxPipeline`) converts CameraX/`Bitmap`/`Uri` inputs and writes files to MediaStore or SAF. In **debug builds only**, the finished JPEG takes one fail-open pass through Ix before upstream writes it; release builds never enter the hook. The app has 10 JVM tests (5 link, 5 manual-controls maths) and 5 instrumented tests on an API 36 emulator (4 Ix adapter/hook, 1 shortcut publication), plus emulator checks of the Photo, Selfie, Scan, Video and Manual links and a manual capture. RAW, Ultra HDR and styles are next; the manual controls still need a physical-device image validation.

The fork keeps GrapheneOS Camera's camera core and adds the things the suite needs: manual controls, RAW/DNG, Ultra HDR, photographic styles, a one-tap handoff to Sietch, and the metadata and shortcuts that make it a first-class app in Uno Launcher.

**Test build:** [Holtzman Camera 0.3.0-test](https://github.com/everettpenne/HoltzmanCamera/releases/tag/v0.3.0-test) — debug, installs beside GrapheneOS Camera.

- [Project outline](PROJECT.md) — goals, deliverables, milestones, risks
- [Architecture](docs/architecture.md) — structure, capture pipeline, data flow
- [Ix engine](docs/ix.md) — what the imaging engine owns and why
- [Upstream strategy](docs/upstream.md) — branches, sync runbook, conflict policy
- [Suite integration](docs/integration.md) — Uno and Sietch contracts
- [Test builds and releases](docs/release.md) — what can be published now, and the gates for a public release
- [Decisions](docs/decisions.md) — the choices made so far

## Relationship to upstream

This is a fork, not a reimplementation. The base is recorded in [`UPSTREAM`](UPSTREAM); the plan is to merge `upstream/main` regularly rather than let the fork drift. Upstream is MIT-licensed and the original copyright is kept in [`LICENSE`](LICENSE). Holtzman is not affiliated with or endorsed by GrapheneOS.

## Building

Upstream currently needs:

- A full **JDK 17** for the Gradle toolchain (not a JRE); a newer JDK may run Gradle, but the toolchain still needs 17
- Android SDK platform 37, build-tools 37.0.0, NDK 29.0.14206865
- Gradle 9.8.2 (the wrapper downloads it)
- `minSdk` is **31**; the app targets GrapheneOS/current Pixels

```bash
./gradlew :ix:test            # the Ix engine; no Android SDK needed
./gradlew :app:assembleDebug  # the camera app
```

`local.properties` with `sdk.dir=...` is required for the app. If your home directory is not writable (CI, sandbox), set `ANDROID_USER_HOME` to a writable path so the debug keystore can be created. See the [toolchain section](PROJECT.md#7-toolchain) for the current machine's setup.

## Licence

MIT. See [`LICENSE`](LICENSE) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
