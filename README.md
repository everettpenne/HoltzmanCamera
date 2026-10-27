# Holtzman (working name)

A camera for the [Glass](https://github.com/everettpenne/Glass) suite, forked from [GrapheneOS Camera](https://github.com/GrapheneOS/Camera) (MIT).

**Status: planning.** This repository currently contains the upstream code plus the project plan. No suite feature has been implemented yet.

The fork keeps GrapheneOS Camera's camera core and adds the things the suite needs: manual controls, RAW/DNG, Ultra HDR, photographic styles, a one-tap handoff to Sietch, and the metadata and shortcuts that make it a first-class app in Uno Launcher.

- [Project outline](PROJECT.md) — goals, deliverables, milestones, risks
- [Architecture](docs/architecture.md) — structure, capture pipeline, data flow
- [Upstream strategy](docs/upstream.md) — branches, sync runbook, conflict policy
- [Suite integration](docs/integration.md) — Uno and Sietch contracts
- [Decisions](docs/decisions.md) — the choices made so far

## Relationship to upstream

This is a fork, not a reimplementation. The base is recorded in [`UPSTREAM`](UPSTREAM); the plan is to merge `upstream/main` regularly rather than let the fork drift. Upstream is MIT-licensed and the original copyright is kept in [`LICENSE`](LICENSE). Holtzman is not affiliated with or endorsed by GrapheneOS.

## Building

Upstream currently needs:

- A full **JDK 17** for the Gradle toolchain (not a JRE); a newer JDK may run Gradle, but the toolchain still needs 17
- Android SDK platform 37, build-tools 37.0.0, NDK 29.0.14206865
- Gradle 9.8.2 (the wrapper downloads it)

```bash
./gradlew :app:assembleDebug
```

`local.properties` with `sdk.dir=...` is required. See the [toolchain section](PROJECT.md#7-toolchain) for the current machine's gaps.

## Licence

MIT. See [`LICENSE`](LICENSE) and [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
