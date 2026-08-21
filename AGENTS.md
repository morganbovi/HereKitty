# Repository Guidelines

## Project Structure & Module Organization

HereKitty is a multi-module Kotlin/JVM Compose Desktop project built with Gradle Kotlin DSL.
`desktopApp/` is the launcher, `app/` holds all Compose UI and presenters, `domain/` holds models and
contracts, `adb/` speaks the ADB host protocol, `repository/` owns the session buffers, and
`injector/` wires the modules together. Ignore generated output under `*/build/`.

There is no Android target and no Kotlin Multiplatform source layout — every module is a plain
Kotlin/JVM module with `src/main/kotlin`.

## Build, Test, and Development Commands

Use the Gradle wrapper from the repository root:

- `./gradlew :desktopApp:run` runs the app.
- `./gradlew build -x test` compiles every module.
- `./gradlew test` runs the JVM unit tests across modules.
- `./gradlew :desktopApp:packageDmg` builds a macOS distribution.

The build requires JDK 25 and provisions it automatically; see the build quirks in `CLAUDE.md` before
changing any JVM version.

## Coding Style & Naming Conventions

Follow Kotlin official style (`kotlin.code.style=official`) with 4-space indentation. Keep package
names under `io.sweatshop.herekitty.*`, use `PascalCase` for types and `camelCase` for members,
and match the feature layout `FooContent.kt`, `FooPresenter.kt`, `FooUiModel.kt`.

This codebase uses presenters, not ViewModels: presenters are Koin `@Factory` classes with a
`@Composable fun present()`, and `UiModel` types live in separate files. Keep DI in `di/` packages.

Prefer self-documenting code over comments. The comments that exist earn their place by recording an
external constraint or a non-obvious "why" — why a socket is closed from a sibling coroutine, why a
tag is not trimmed, why memory is only an estimate. Do not add comments that narrate what the code
does.

Build UI from Jewel components only. Material and Material3 are excluded from the runtime on purpose.

## Architecture Notes

Use Koin annotations with the Koin compiler plugin in `:app`, `:domain`, `:adb`, and `:repository`;
keep `:injector` annotation free. Use `@Factory` for presenters and `@Single(binds = [...])` for
bindings. The generated module accessor is a function: `AppGeneratedModule().module()`.

`:adb` knows the wire protocol and holds no state. `:repository` holds all session state. A pane is a
filtered index over the one shared buffer, never a copy of it.

## Testing Guidelines

Place JVM tests in `src/test/kotlin` mirroring the production package and name files
`FeatureNameTest.kt`. The suite deliberately covers the code that fails quietly: logcat parsing,
device-list parsing, buffer eviction, snapshot consistency, index pruning, filter matching, and icon
key resolution.

The parsing tests use output captured from a real device, including a tag with a leading slash and
internal padding. Keep those cases.

`./gradlew test` requires no hardware. `LiveAdbPipelineTest` exercises the whole capture-to-filter
path against an attached device and only runs with `-Dherekitty.liveAdb=true`; run it after touching
capture, the buffer, or filtering. Keep any new device-dependent test behind the same flag.

## Commit & Pull Request Guidelines

Use short, imperative commit subjects such as `Add tag exclusion to the pane filter`. PRs should
describe the user-visible change, list touched modules, note any dependency or JVM version changes,
and include a screenshot for UI updates.

## Configuration Tips

`local.properties` and machine-local overrides are environment specific; do not commit secrets.
Point `HEREKITTY_ADB` at an adb binary if it is not discoverable from `ANDROID_HOME` or `PATH`.
