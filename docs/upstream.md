# Keeping up with GrapheneOS Camera

This fork exists to add suite features, not to freeze GrapheneOS Camera. The rule is simple: **upstream remains the source of truth for the camera core**, and every suite change is kept small enough that a merge is boring.

## State

| | |
| --- | --- |
| Upstream | `https://github.com/GrapheneOS/Camera.git` |
| Upstream branch | `main` |
| Fork branch | `main` (this repository's product branch) |
| Base commit | `0aa4cb0dccf7f32919a35a431771cec67716dd7e` (2026-10-23, versionCode 94) |
| Recorded in | [`../UPSTREAM`](../UPSTREAM) |

The local clone has **`upstream`** as its only remote. Add your own fork as `origin` once it exists:

```bash
git remote add origin git@github.com:<you>/Holtzman.git
git fetch origin
git branch --set-upstream-to=origin/main main   # optional
```

Until then, leave `main` tracking `upstream/main` deliberately, so a plain `git pull` is a sync rather than a surprise.

`git rerere` is enabled in this clone, so repeated conflicts keep their resolutions. `merge.conflictStyle=zdiff3` is set for readable conflicts.

## Branch model

- **`main`** — the fork's product branch. It contains upstream plus suite commits.
- **`upstream/main`** — the untouched base fetched from GrapheneOS.
- **`upstream/<topic>`** — a temporary branch cut from `upstream/main` for a fix intended to go back to GrapheneOS.
- **Release tags** — `v0.x.y` on `main`. Every tag records the upstream base in its notes.

Do not maintain a separate long-lived "vendor" branch; `upstream/main` is already the vendor branch. If a pristine local branch helps for a diff, create it temporarily and delete it.

## Sync runbook

### Weekly (5 minutes)

```bash
git fetch upstream --tags
git log --oneline --no-merges main..upstream/main | head -30
git diff --stat main...upstream/main
```

If there is a security fix, a CameraX upgrade, or a change to a hot file, schedule the merge now rather than at release time.

### Watch: `upstream/compose-prototype`

Upstream carries a `compose-prototype` branch. If it becomes the main UI, two decisions change: the View-token layer (ADR-004) and the "no Glass composite build" rule (ADR-003). Review that branch before any major merge; do not build suite UI on an assumption upstream is about to invalidate.

### Monthly, before a release, or on a security fix

```bash
git switch main
git status                              # must be clean
git fetch upstream --tags
git merge upstream/main                 # merge, never rebase published history
```

Resolve conflicts with the policy below, then:

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest        # once the fork's tests exist
```

Run the device checklist for any camera-path change. Update `UPSTREAM` and the sync log, then commit:

```bash
git commit -am "sync: upstream/main at <short-sha>"
```

Tag the upstream base locally for easier diffs (optional):

```bash
git tag upstream-base-$(date +%Y-%m-%d) upstream/main
```

## Conflict policy

1. **Upstream wins on camera behaviour.** If the conflict is in the capture path and there is no suite requirement to change it, take upstream's version. Re-apply the suite feature in `suite/` afterwards.
2. **Suite behaviour wins only where the product differs** (manual mode, RAW/Ultra HDR, styles, Sietch handoff, Uno metadata).
3. **New code in new files.** Engine work lives in the `:ix` module; app-side work in `app/grapheneos/camera/suite/`. Only the adapter crosses the boundary.
4. **No mass formatting, no package rename, no dependency churn.** Every changed upstream line should have a sentence of justification in the commit message.
5. **Use resource/flavor overlays instead of editing upstream resources** where possible. Overriding `colors.xml`/`themes.xml` in a source set is cheaper to merge than editing them.
6. **Keep the upstream tests green.** A suite change that breaks an upstream instrumented test is not finished.
7. **Do not "fix" upstream comments or whitespace.** It turns a one-line merge into a whole-file conflict.

## Hot files

These are the upstream files the fork is expected to touch. Keep the list short, and review it monthly.

| File | Why | Expected conflict |
| --- | --- | --- |
| `settings.gradle.kts` | `include(":ix")`, root project name | Low |
| `app/build.gradle.kts` | `applicationId`, `resValue` name, `minSdk`, `implementation(project(":ix"))`, test dependencies, future flavors | Low–medium |
| `gradle/libs.versions.toml` | JUnit for the engine tests; no upstream dependency changes otherwise | Low |
| `gradle/verification-metadata.xml` | 32 lines for the Kotlin JVM plugin (Kotlin scripting artifacts). Do not regenerate the whole file; add only what the build reports missing | Low–medium (upstream Kotlin bumps also touch it) |
| `ix/` | New pure JVM module; no upstream file is changed | None (new files) |
| `app/src/main/AndroidManifest.xml` | Label/category/description, shortcuts meta-data, user-build intent removal | Medium (upstream changes intents) |
| `app/src/main/java/app/grapheneos/camera/CamConfig.kt` | Mode list, capture builder (output format, manual interop), settings keys | **High** (active file) |
| `app/src/main/java/app/grapheneos/camera/capturer/ImageCapturer.kt` | Choose saver path, pass style/output options | Medium |
| `app/src/main/java/app/grapheneos/camera/capturer/ImageSaver.kt` | Debug Ix pass (D0.7), later RAW/Ultra HDR bytes, style application, Sietch handoff | **High** |
| `app/src/main/java/app/grapheneos/camera/ui/activities/MainActivity.kt` | Mode entry points, manual panel wiring, fixed settings-dialog theme | Medium–high |
| `app/src/main/java/app/grapheneos/camera/App.kt` | Dynamic colour disabled (ADR-014) | Low |
| `app/src/main/java/app/grapheneos/camera/ui/activities/MoreSettings.kt` | About row and dialog | Low |
| `app/src/main/java/app/grapheneos/camera/ui/SettingsDialog.kt` | Manual/style/RAW settings rows | Medium |
| `app/src/main/res/values/strings.xml`, `colors.xml`, `themes.xml`, `dimens.xml` | Glass token parity | Low (prefer overlay) |
| `app/src/main/res/values/holtzman.xml`, `res/font/inter_variable.ttf`, `res/drawable/ic_launcher_*`, `res/mipmap-anydpi*` | New Glass palette, Inter, aperture icon | None (new files) |
| `app/src/main/res/layout/*.xml` | New panels/controls | Low (new files preferred) |

**Manifest removals:** D1 edited `AndroidManifest.xml` directly (filters and secure activities) and removed `DISABLE_KEYGUARD`. The helper classes stay. A `user` source-set overlay with `tools:node="remove"` is deferred until a `system` flavor exists; see [ADR-009](decisions.md#adr-009--remove-system-camera-intents-from-the-user-build).

## Upstreamable work

Some suite work is generally useful and should go back:

- Fixes to upstream bugs found while adding manual/RAW support.
- General CameraX 1.6 compatibility fixes.
- Accessibility or lifecycle fixes.
- Documentation corrections.

Process:

```bash
git switch -c upstream/<topic> upstream/main
# one focused commit
git push origin upstream/<topic>
# open a PR against GrapheneOS/Camera
```

Once merged upstream, drop the local branch and let the next sync bring it in. Keep suite-specific branding, Uno integration and Sietch handoff out of upstream PRs.

Commit messages for upstreamable work start with `upstream:`; suite work starts with `suite:`.

## CI

Upstream already has `build.yml` (JDK 21, `./gradlew build`) and `validate-gradle-wrapper.yml`. The fork adds:

1. **`build.yml`** — keep upstream's job; add `:app:testDebugUnitTest` once JVM tests exist.
2. **`upstream-sync.yml`** — scheduled (weekly) job:

   ```bash
   git fetch upstream main
   git switch -c sync-check main
   git merge --no-commit --no-ff upstream/main
   ./gradlew :app:assembleDebug
   ```

   Fail on conflicts or build failure, so the fork never discovers a bad merge on release day.
3. **`upstream-diff.yml`** (optional) — post `git diff --stat upstream/main` on PRs and fail if the number of touched upstream files exceeds the budget in this document.

Do not run instrumented camera tests in CI without a device; keep them in a documented device checklist.

## Divergence budget

Aim for **no more than ~12 upstream files touched** at any time. New behaviour should add files, not lines to old ones. Review monthly:

```bash
git diff --stat upstream/main -- ':!app/src/main/java/app/grapheneos/camera/suite' ':!docs' ':!PROJECT.md'
```

If the budget is exceeded, stop and refactor before the next feature. A fork that cannot merge is no longer a fork; it is a hard split.

## Release discipline

- Sync before tagging, even if nothing looks urgent.
- The release notes and `UPSTREAM` name the exact base commit.
- The About screen credits GrapheneOS Camera and links to the upstream repository.
- Keep the MIT licence and copyright notice in every release artifact.
- Signing keys stay outside the repository; use `keystore.properties` locally (upstream supports it) or CI secrets.

## Sync log

| Date | Upstream commit | Action | Notes |
| --- | --- | --- | --- |
| 2026-10-27 | `0aa4cb0` | Initial fork base | Cloned upstream; `upstream` remote configured; no suite commits yet |
