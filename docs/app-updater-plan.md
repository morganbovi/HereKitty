# Plan: in-app updater over GitHub Releases

Nothing here is built yet. Three decisions are still open — see the end.

## What makes this project's version of the problem specific

- **The app does not know its own version.** `packageVersion = "1.0.0"` is a hardcoded literal in
  `desktopApp/build.gradle.kts` and is exposed nowhere at runtime. Everything else depends on fixing
  that first.
- **`java.net.http` is not in the jlink module set.** `modules(...)` lists only `java.instrument`,
  `java.naming` and `jdk.unsupported`, so `HttpClient` compiles, works under `./gradlew run`, and
  throws `NoClassDefFoundError` in the packaged app. `PlatformToolsInstaller` already uses
  `HttpURLConnection` from `java.base` for exactly this reason — follow it rather than adding a module.
- **The app is unsigned** (ad-hoc signature, `spctl` rejects it). There is no silent self-update story
  without a Developer ID: Sparkle and friends require either a signed app or EdDSA-signed payloads.
  This is what makes the "how is it applied" decision the load-bearing one.

## Phase 0 — the app learns its own version

Single source in `gradle.properties`; the build feeds both `packageVersion` and a generated Kotlin
constant in `:desktopApp`. Without this there is nothing to compare against.

## Phase 1 — the check

A new `:updates` module, the analogue of `:adb`: it speaks the GitHub Releases API and holds no state.

- `GET /repos/morganbovi/HereKitty/releases/latest` needs **no authentication** on a public repo.
  Unauthenticated limit is 60 requests/hour per IP, far above a per-launch check. `/latest` already
  excludes drafts and pre-releases.
- `ReleaseParser` — `tag_name` plus `assets[]`, picking this platform's asset by name. Pure, and
  testable off captured JSON exactly like `LogcatParser`.
- `AppVersion` — **semver comparison, not string comparison**, or `1.10.0` sorts below `1.9.0`. Pure.
- `:repository` owns the state, per the existing layering: Idle / Checking / UpToDate / Available /
  Downloading / Failed.

## Phase 2 — surfacing it

Reuse `ui/notification/` — an "available" balloon in the corner, which is what IntelliJ does. Plus a
manual check action and a settings toggle for the startup check, alongside the existing prompt
settings.

## Phase 3 — applying it

`PlatformToolsInstaller` is most of this already: the same download-with-progress over
`HttpURLConnection`, the same "verify then use" shape. Factor rather than duplicate.

**Integrity.** Publish a `SHA256SUMS` asset and verify after download. Be honest about what that
buys: protection against corruption and a bad mirror, *not* against a compromised GitHub account.
Real authenticity needs codesigning, which is its own decision.

**One pleasant side effect.** An in-app download does not carry `com.apple.quarantine` — that is set
by browsers, not by `HttpURLConnection` (confirmed when the adb installer executed the binary it
fetched). So an update fetched in-app skips the *Open Anyway* dance that DMG recipients hit today.

## Release automation

`.github/workflows/build.yml` exists: `test` on every push and PR, then on a `v*` tag a `package`
matrix (macOS arm64 `.dmg`, Linux `.deb`, Windows `.msi`) feeding a `release` job that uploads the
artifacts plus a `SHA256SUMS` the updater can verify against.

- **It installs the JetBrains Runtime via `setup-java`'s `jetbrains` distribution** rather than
  letting Gradle provision it, which saves a ~350 MB download per run.
- **Every package job runs `verifyDistributable`.** A green `packageDmg` means nothing on its own:
  both packaging bugs this project has hit built cleanly and died at first paint. That task is what
  makes the job meaningful.
- **Windows installs WiX 3.11 first.** jpackage shells out to it for an `.msi` and fails without it.
  This is the step most likely to need adjusting on the first real run.
- **Artifacts are renamed per platform** — `HereKitty-<version>-macos-arm64.dmg` — because
  jpackage names them all identically and the updater has to pick the right one.
- **Not covered:** Intel macOS (would need a second `macos-13` job), and signing. Artifacts stay
  unsigned, so a browser download still gets quarantined and still needs *Open Anyway*. An update
  fetched in-app does not.

**This workflow depends on Phase 0.** `packageVersion` is still the literal `"1.0.0"`, so tagging
`v1.1.0` today produces a file *named* 1.1.0 containing an app that reports 1.0.0 — the version has
to come from one place before a release is trustworthy.

The workflow's YAML parses, but nothing in it has run on a real runner yet.

## Decisions still open

1. **How the update is applied.** Recommended: download the `.dmg`, verify it, mount it, reveal it in
   Finder, and let the user drag across — roughly a day, and nothing can half-replace a running
   bundle. The alternative, replacing the bundle in place and relaunching, needs a detached helper
   that waits for exit, write access to the install location, and accepts that an unsigned app
   silently overwriting itself looks exactly like malware. Its failure mode is a broken install.
2. **Where it surfaces.** Recommended: the corner balloon, optionally also a title-bar button so it
   survives the balloon being dismissed.
3. **How artifacts get published.** By hand (`packageDmg` plus a checksum file, uploaded to a release)
   keeps this scoped to the updater. A GitHub Actions workflow is nicer but jpackage cannot
   cross-build, so covering all three formats means three runner jobs. Note only the `.dmg` has ever
   actually been built.
