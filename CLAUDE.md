# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

HereKitty is a Compose Desktop logcat viewer. It connects to an Android device over ADB, records
everything that device emits for the whole session, and shows it through any number of independently
filtered panes side by side — so traffic leaving the app, traffic the hardware receives, traffic the
hardware sends, and traffic the app receives can be read against each other at the same time.

## Build Commands

```bash
./gradlew :desktopApp:run          # run the app
./gradlew build -x test            # compile everything
./gradlew test                     # run all unit tests
./gradlew :adb:test                # logcat + device list parsing
./gradlew :repository:test         # buffer, view index, filter matching
./gradlew :app:test                # icon keys still resolve
./gradlew :desktopApp:packageDmg   # build a macOS distribution
```

## Known Build Quirks

- **JDK 25 is required**, not optional. Jewel is built against the IntelliJ 262 platform and ships
  class file version 69, so a JDK 21 runtime fails with `UnsupportedClassVersionError`. Both
  `gradle/gradle-daemon-jvm.properties` and the `jvmToolchain(25)` in the root build must stay on 25
  — Compose's `run` task uses the daemon's JVM, so bumping only the toolchain is not enough. Gradle
  provisions the JDK itself through the foojay resolver.
- **The app runs on the JetBrains Runtime.** `DecoratedWindow` — the themed title bar — calls
  `error()` outright if `JBR.isAvailable()` is false, so this is a hard requirement rather than a
  nicety. `:desktopApp` resolves a `JvmVendorSpec.JETBRAINS` toolchain and hands its path to
  `compose.desktop.application.javaHome`, because the run task would otherwise use the daemon's JVM.
  First build downloads it (~350 MB).
- **A decorated window needs its own styling.** `IntUiTheme` alone provides no title bar styling and
  the window fails with *"No DecoratedWindowStyle provided"*. `HereKittyTheme` passes
  `ComponentStyling.decoratedWindow()`, and it wraps *windows* rather than content, so it adds no
  layout of its own — a window cannot be nested inside a `Box`.
- There is no decorated *dialog* in Jewel, only a decorated window, so dialogs keep the platform's
  own title bar.
- **Jewel and the icon artifact are versioned together.** Jewel ships icon *keys*; the SVGs behind
  them live in `com.jetbrains.intellij.platform:icons`, which is only published in JetBrains' own
  repository (see `settings.gradle.kts`) and versioned by IntelliJ build number. Keep the trailing
  build number of `jewel` and `intellijIcons` in the version catalog aligned. `:app`'s `AppIconsTest`
  fails if a key stops resolving; without it a missing icon silently renders as nothing.
- **Koin uses its compiler plugin, not KSP.** Koin 4.2 replaced the KSP processor with a Kotlin
  compiler plugin. Two consequences: there is no `org.koin.ksp.generated.module` import, and the
  generated accessor is a *function* — write `AppGeneratedModule().module()`, not `.module`.
- The Koin plugin's Gradle marker artifact is unpublished, so `settings.gradle.kts` maps the plugin
  id to `io.insert-koin:koin-compiler-gradle-plugin` in a `resolutionStrategy`.
- The Koin plugin warns that Kotlin 2.4.10 is newer than its newest tested version. It works; the
  warning is expected.
- Compose Multiplatform pulls `androidx.*` transitives, so Google's Maven repository is required
  alongside Maven Central.
- **A packaged app is not the same classpath as `./gradlew run`, and two bugs only appear there.**
  `run` uses the whole JDK and Gradle's own dependency ordering; `packageDmg` jlinks a minimal runtime
  and lists jars in its own order. Both fixes are load-bearing:
  - `nativeDistributions` must declare `modules("java.instrument", "java.naming", "jdk.unsupported")`.
    Without `jdk.unsupported` there is no `sun.misc.Unsafe`, and Jewel's macOS platform services throw
    `NoClassDefFoundError` on the first frame. The list comes from `:desktopApp:suggestRuntimeModules`.
  - The root build excludes `org.jetbrains.intellij.deps.kotlinx`. The IntelliJ icon artifacts pull in a
    forked kotlinx-coroutines under that group, which Gradle cannot dedupe against the real
    `org.jetbrains.kotlinx` one, so both end up in `Contents/app` and the launcher binds to whichever
    sorts first — giving `NoSuchMethodError: Job.cancel$default` in `:adb`.

  After changing dependencies, run the *bundle* rather than trusting `run`:
  `./gradlew :desktopApp:createDistributable && desktopApp/build/compose/binaries/main/app/HereKitty.app/Contents/MacOS/HereKitty`
- **The distributable is arm64-only and unsigned.** `spctl` rejects it, so a recipient needs
  *Open Anyway* in Privacy & Security (or `xattr -dr com.apple.quarantine`). jpackage cannot
  cross-build: the `.msi` and `.deb` have to be built on Windows and Linux respectively.

## Module Structure

```
:desktopApp   — main(), the window, starts Koin. Thin launcher only.
:app          — all Compose UI: features/, ui/. Presenters live here.
:domain       — models, repository interfaces, coroutine base. No implementations.
:adb          — the ADB host protocol: device tracking, logcat streaming, parsing.
:repository   — stateful composition over :adb. Owns the session buffers, the recording
                file format, and saved view configs.
:injector     — wires the modules together. Annotation free.
```

Dependency graph:

```
:desktopApp → :app → :injector → :domain
                              → :adb        → :domain
                              → :repository → :domain, :adb
```

`:domain` exposes its dependencies with `api()`, so every module gets `koin-core`,
`koin-annotations`, `kotlinx-coroutines-core`, and the logging facade transitively.

`:adb` is the analogue of a data-source layer: it knows the wire protocol and nothing about state.
`:repository` is where state lives.

## Dependency Injection (Koin)

- Annotation based. Use `@Single`, `@Factory`, `@Module`, `@ComponentScan` from
  `org.koin.core.annotation`, exactly as in Fundlings.
- Every module that owns annotated classes applies the `koinCompiler` plugin. `:injector` does not.
- Each module has a `di/` package with a `@Module @ComponentScan` class and a hand-written `module {}`
  that includes the generated one plus anything needing manual construction.
- The graph starts in `main()` via `startKoin { modules(appModule) }`; composition reads it through
  `KoinContext { }`.
- Use `@Factory` for presenters, never `@Single` — they hold Compose state.
- Use `@Single(binds = [Interface::class])` in implementation modules to bind domain interfaces.

## Presenter Architecture

This project uses the **Presenter pattern**, not ViewModels. See `PRESENTERS.md` for the spec.

- Presenters are `@Factory` Koin classes with a `@Composable fun present()` returning a UiModel.
- **UiModels live in their own file** — `LogPaneUiModel.kt` beside `LogPanePresenter.kt`.
- State uses `mutableStateOf` + `remember` and `collectAsState()` for flows — not `StateFlow` in the
  presenter, no `viewModelScope`.
- Events go through `EventHandler<E>` (see `ui/presenter/EventHandler.kt`).
- Runtime arguments are parameters of `present()`, e.g. `present(session, canClose, onClose)`.

**File layout per feature:**

```
features/foo/
  FooContent.kt     — @Composable, injects FooPresenter via koinInject()
  FooPresenter.kt   — @Factory, present() returns FooUiModel
  FooUiModel.kt     — data class; Events nested inside
```

Screens are named `…Content` rather than `…Screen` because nothing here is a full screen; every
feature renders into a resizable pane.

## UI toolkit: Jewel, not Material

The UI is built on [Jewel](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel),
JetBrains' Compose implementation of the IntelliJ New UI, following
<https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html>.

- **Do not add Material or Material3 components.** `:desktopApp` excludes
  `org.jetbrains.compose.material` from `compose.desktop.currentOs` on purpose. Material's default
  metrics (40dp buttons, 56dp rows) are far too large for a dense desktop tool.
- Import components from `org.jetbrains.jewel.ui.component`: `Text`, `TextField`, `IconActionButton`,
  `ToggleableIconActionButton`, `ListComboBox`, `Divider`, `Tooltip`, `PopupContainer`,
  `VerticallyScrollableContainer`, `HorizontalProgressBar`, `Checkbox`, `GroupHeader`.
- Text styles come from `JewelTheme.typography` (`regular`, `small`, `h3TextStyle`). Log lines use
  `JewelTheme.typography.consoleTextStyle`, which is JetBrains Mono.
- The app has no top bar of its own: its two global controls live in the window's title bar
  (`AppTitleBarContent`), which is the row the platform was going to draw anyway.
- Theme choice is a `ThemeMode` of Light, Dark or System. It resolves in composition
  (`resolveDarkTheme`) rather than in the repository, because following the system means recomposing
  when the OS setting changes — `isSystemInDarkTheme()` watches it.
- Which columns a log line shows is one global `LogColumns` in `SettingsRepository`, edited from the
  settings popup in the top toolbar. `effectiveColumnsFor` then applies the one per-pane exception: a
  stacked pane watching several tags always shows the tag, because otherwise there is no way to tell
  which of them a line came from.
- `LogLineLayout.Stacked` puts a tiny metadata line above each message so the message gets the pane's
  full width. Consecutive lines sharing tag, level and process within a second share one header
  (`needsMetadataHeader`), which makes a burst cost *less* vertical space than the columnar layout
  rather than more.
- Colours come from `JewelTheme.globalColors`. Only log level colours are hardcoded, in
  `ui/theme/LogLevelColors.kt`.
- Icons come from `AllIconsKeys`. Add a new key to `AppIconsTest` when you use it.
- Jewel is a 0.x library with unstable APIs. `:app` opts in to the experimental Compose and Jewel
  annotations in its build file rather than annotating every call site.
- **The window is Jewel's `DecoratedWindow`,** so the app has no OS title bar of its own. That is what
  makes the JetBrains Runtime a hard requirement rather than a preference — see the build quirks above.
- **Notices are balloons in the bottom-right corner** (`ui/notification/`), not a banner. A banner
  across the top pushed the panes down and back up every time something was exported. They stack
  newest-last, capped at a handful so a burst of failures cannot bury the window, and each carries an
  id because two identical messages are otherwise indistinguishable. `dismissesOnItsOwn` keeps errors
  up until dismissed whatever the timeout setting says: losing "could not export" to a timer is worse
  than a balloon that overstays.
- **`Cmd +` / `Cmd -` / `Cmd 0` scale the log font**, handled on the window so focus does not matter.
  `Cmd +` is really `Cmd =` on most layouts, so both are bound. `LogFontScale` is a fixed ladder rather
  than a step size, because repeated multiplication drifts onto arbitrary sizes and never settles at the
  ends. The console `TextStyle` is resolved once in `LogLines` and passed down, which is what gives the
  zoom one place to apply.

## Views, tabs, and sources

A **view config** (`domain/features/views`) is a named, ordered list of `PaneConfig`s — each pane's
filter — and deliberately holds **no device and no file**. That is the whole point: the same view
applies to a phone on one desk, to a different phone, or to an imported recording on someone else's
machine with nothing to remap. Saved views live in `~/.herekitty/views/*.hkview`; export writes the
same JSON anywhere.

Because of that, **the pane filter is owned by the slot, not by the pane presenter**.
`LogPanePresenter` renders the `PaneConfig` it is handed and reports changes upward; only the text
editor buffer and the tag popup are local to it. Without that, a setup could not be named, reapplied,
or shared.

A **session's source** is either a device or a recording (`SessionSource`). `BaseLogSession` holds all
the recording machinery, and `DeviceLogSession` and `RecordedLogSession` differ only in where lines
come from — which is why every filter and every view works identically against an imported file.
Switching a session's source closes the old session and opens a new one in the same slot, keeping the
slot's view.

**Tabs** are a list of layout trees owned by `WorkspacePresenter`, which owns all layout state: tabs,
each tab's tree, and each slot's view. Keeping it in one place is what makes applying a saved view,
switching a source, and reordering panes the same kind of operation.

**The layout is restored on launch** from `~/.herekitty/layout.json`: tabs, splits, each slot's view,
and the serial each slot was attached to. Recorded lines are not persisted — only an export keeps
those. Slots come back empty and sessions are reattached *after* adb reports what is actually plugged
in, so a device that has gone missing leaves its slot on the source picker with the view still
applied. `WorkspaceLayoutMappingTest` covers the round trip; divider positions are not persisted yet
and come back evenly split.

Exports contain the lines the session still **holds**, not everything it ever saw. A capped buffer
drops its oldest lines, and the tag registry outlives them, so a long live session can list tags that
its own export will not contain.

Two export shapes, and the same bytes underneath:

- `*.hklog.gz` — the recording alone, gzipped.
- `*.hkbundle` — an ordinary zip holding `view.hkview` and `session.hklog` side by side, so a session
  and the view it was read through travel together. There is no manifest: the two entry names are the
  format. The log inside is stored uncompressed and left to the zip's own deflate rather than being
  gzipped first.

`LogRecordingCodec` is therefore stream-based, not file-based — `RecordingFiles` wraps it in gzip and
`SessionBundleFiles` writes it into a zip entry, so neither shape can drift from the other.
`ViewConfigCodec` exists for the same reason: a view is written both as a file and as a bundle entry.

Opening a bundle applies its view to the slot; opening a bare recording leaves the slot's view alone.

## Layout

Both levels of splitting go through `ui/split/ResizableSplit`, a custom `Layout` that arranges
children along one axis by weight with a draggable divider between each pair. Weights are keyed by
item, so adding, closing, or reordering a child leaves the others' sizes alone.

- **The workspace is a tree** (`features/workspace/WorkspaceNode`), so a session can be split either
  beside or below another. A `Split` holds *any number* of children rather than exactly two:
  splitting along an axis the parent already uses adds a sibling, which keeps three panes in a row as
  one row instead of a lopsided chain of nested pairs. Closing a slot collapses any split left with a
  single child. All of this is pure functions over the tree, covered by `WorkspaceNodeTest`.
- **Panes and tags are rearranged by dropping them on a target's five regions** — the four edge bands
  insert beside it, the middle swaps panes or merges a tag in. `dropTargetAt` is one pure function
  shared by both, so the two gestures read the same way. A pane excludes itself as a target (it cannot
  land beside where it already is); a *tag* excludes nothing, because its own pane's edge is where you
  send it to get a pane of its own — which makes `splitTagOut` just `splitTagOnto` aimed at itself.
- **The gesture only reports where the drop landed**; the tree changes once, on release. A layout that
  rearranged mid-drag would tear down the very gesture doing the dragging.
- **A session's panes are a flat row** and can be dragged into a new order by their `ReorderGrip`.
  The split owns the geometry and hit-testing; each child only decides where its drag handle lives.
- Reordering is tracked by item *key*, not index, and drag callbacks read siblings through
  `rememberUpdatedState`. Both matter because the list changes underneath a live gesture.
- `SplitGeometry` is deliberately not snapshot state: drag hit-testing must not wait on a
  recomposition.

## The ADB layer (`:adb`)

Talks the adb host protocol directly over a socket to 127.0.0.1:5037. No third-party ADB library.

- `AdbHostConnection` frames requests as four hex digits of length plus the ASCII service name, and
  reads the `OKAY`/`FAIL` reply.
- Device presence comes from `host:track-devices-l`, so connects and disconnects **arrive as events**
  rather than being polled. Falls back to `host:track-devices` if the long form is unsupported.
- Logs stream over `host:transport:<serial>` then `exec:logcat -v long,epoch`. `exec:` rather than
  `shell:` because the latter allocates a pty and rewrites newlines.
- The adb binary is only used to run `start-server` when nothing is listening.
  `AdbBinaryLocator` searches `HEREKITTY_ADB`, `ANDROID_HOME`, the platform default SDK paths, `PATH`,
  Homebrew, and **last** `~/.herekitty/platform-tools`. An adb the machine already has always wins; ours
  is only ever the fallback, so nobody ends up on a second adb their other tooling does not use.
- **A miss is deliberately not cached.** adb can appear *after* launch — that is exactly what the
  in-app install does — so `locate()` remembers a hit but re-scans on a miss.
- **`PlatformToolsInstaller` downloads Google's platform-tools** into `~/.herekitty/` when the source
  picker has no adb to offer. Downloaded rather than shipped: the SDK terms do not grant
  redistribution, and a bundled copy would be three platforms of binaries going stale in the repo.
  Extraction refuses any entry resolving outside the target, and re-sets the executable bit that
  `ZipInputStream` drops.

**Cancelling blocking socket reads.** Socket reads ignore coroutine cancellation, so
`withConnection` parks a sibling coroutine on `awaitCancellation()` that closes the socket when
cancellation arrives. A completion handler cannot do this: a coroutine blocked in IO is *cancelling*,
never *completed*, so the handler would wait on the read it needs to break. Consumers of a cancelled
read see `SocketException`, not `CancellationException` — that is why the tracking and capture loops
call `ensureActive()` before treating an exception as a real dropout.

**Tags are taken verbatim.** Real tags contain punctuation and padding, e.g. `/      ExampleTagImpl`.
`LogcatParser` trims only the surrounding whitespace of the header field; do not "clean up" tags, or
tag filtering stops matching. `LogcatParserTest` pins this.

## The buffer engine (`:repository/logs`)

One capture per device fans out to many filtered panes. Nothing is copied per pane.

- `LogBuffer` is append-only in 4096-line chunks. Only the newest chunk is mutated, so `snapshot()`
  hands readers a consistent view with no lock held while they read it. Hitting the memory cap drops
  whole chunks from the front rather than shifting elements.
- **Lookup by sequence number binary-searches the chunks; it does not do offset arithmetic.** Sequence
  numbers ascend but are not guaranteed to be gapless, and arithmetic turns a gap into a confidently
  wrong line — which shows up as a pane displaying lines that do not match its own filter.
- `LogViewIndex` is one pane's matching sequence numbers plus a repeat count each — twelve bytes per
  row, not a copy of the line. Growing and compacting always allocate fresh arrays so snapshots
  already handed out stay valid.
- Collapsing repeats folds a line into the row above rather than filtering it out, so it happens at
  index time (`LogViewImpl.record`) and is part of `LogViewSpec`, not `LogFilter`. The newest row's
  count is intentionally live inside an existing snapshot: a still-repeating line should show its
  count climbing, and isolating it would mean copying the counts every frame.
- `LogSessionImpl` runs a reader (parse adb output onto a channel), an ingest loop (commit in batches,
  evict, prune pane indices), and paces UI revisions from that same loop. **Revisions are capped at
  roughly one per frame** — without that, a noisy device recomposes the panes tens of thousands of
  times a second.
- A single lock covers the buffer, the tag registry, and every pane index, held only for the batch
  commit and for capturing a snapshot.
- Memory is an estimate: `LINE_OVERHEAD_BYTES` plus one byte per message character, assuming the
  JVM's Latin-1 compact strings. Tags are interned per session, so they are not counted.
- `TagRegistry` is never evicted, so a tag seen once early can still be picked long after its lines
  are gone. It is ordered alphabetically on the first letter or digit, because real tags carry padding
  and punctuation and `/      ExampleTagImpl` belongs under E.
- Recordings are gzipped JSON lines (`LogRecordingCodec`): one header, then one short-keyed object per
  line. Real captures compress to roughly 23 bytes per line.
- Changing a pane's filter rebuilds its index by walking the buffer, which is why the query input is
  debounced and the pane shows `refiltering` while it happens.

## Package Layout (`:app`)

```
io.sweatshop.herekitty
├── app/                  HereKittyApp, presenter, UiModel — theme and top toolbar
├── di/                   AppGeneratedModule + appModule
├── features/
│   ├── workspace/        tabs, the layout tree, and all layout state
│   ├── sourcepicker/     what an empty slot shows: devices, or open a recording
│   ├── session/          one source: toolbar, memory meter, its row of panes
│   │   ├── dialogs/      the close prompt and the rule for when to show it
│   │   └── pane/         one filtered view, plus the tag picker popup
│   │       └── line/     how a single log line is drawn
│   ├── views/            the view menu: save, apply, export, import
│   └── settings/         the settings window and its category panes
└── ui/
    ├── presenter/        EventHandler, SingleEventHandler
    ├── component/        icon buttons that carry their own tooltip
    ├── dialog/           the dialog scaffold every dialog is built on
    ├── split/            ResizableSplit, SplitDivider, SplitGeometry, ReorderGrip
    ├── notification/     the corner balloons and their queue
    ├── keyboard/         the font zoom shortcuts
    ├── files/            native open and save dialogs
    ├── theme/            HereKittyTheme, LogLevelColors
    └── format/           byte, count, and timestamp formatting

Packages follow sub-features, not types. There is no `model/` or `util/` bucket: a file lives beside
the thing it serves. `PaneOperations` sits at the session level rather than under `pane/` because it
rearranges the session's *list* of panes; `pane/line/` holds the decisions about drawing one line.
```

## Testing

Unit tests cover the parts that are easy to break silently: logcat and device-list parsing, buffer
eviction and snapshot consistency, view index pruning, filter matching, and icon key resolution.
Place tests in `src/test/kotlin` mirroring the production package. The parsing tests use output
captured from a real device — keep those cases when changing the parser.

**The fixtures are shaped like real capture but carry no real identifiers.** This is a public repo, so
device serials are `EXAMPLE0001` and the padded-tag case is `/      ExampleTagImpl` rather than the
class it came from. Replacements are kept the *same length* as the originals on purpose: the parser
test pins the exact 21-character tag width and `TagRegistryTest` pins alphabetical position, so a
different-length name silently changes what those tests assert. Never paste raw capture straight in.

`./gradlew test` needs no hardware. `LiveAdbPipelineTest` and `LiveRecordingRoundTripTest` drive the
pipeline and the export/import round trip against an attached device, and are skipped unless you opt
in:

```bash
./gradlew :repository:test -Dherekitty.liveAdb=true
```

`PlatformToolsInstallerTest` is opt-in the same way, because it downloads the real archive from Google
and then runs the `adb` it extracted:

```bash
./gradlew :adb:test -Dherekitty.network=true
```

Run it after changing capture, the buffer, or filtering. It is what caught the ingest loop dropping
lines and the buffer resolving the resulting sequence gaps to the wrong line — neither of which any
unit test noticed, because both only appear under a real firehose.

## Decisions taken without confirmation

Each of these was a judgement call made while building, not something asked for. They work and are
tested; they are listed so they can be revisited as choices rather than assumed to be requirements.

- **A saved view applied over an existing setup only prompts when there are unsaved changes.** The
  brief was "more than one pane"; the extra condition was added because a setup already saved under
  its name is one click away again, so prompting for it is nagging. See `shouldConfirmApply`.
- **Dropping a tag on a pane's edge splits, on its middle merges.** The suggestion was "top half
  merges, bottom half splits"; the five-region scheme was used instead so all four split directions
  survive and the gesture matches dragging a whole pane.
- **Dragging a pane's last tag away closes that pane.** An empty tag set means *every* tag, so the
  alternative was a filtered pane silently turning into a firehose.
- **Dropping a tag on an unfiltered pane pins it to that tag.** This is the opposite of
  `mergePaneInto`, which keeps an unfiltered pane unfiltered.
- **Errors never auto-dismiss**, whatever the notification timeout says.
- **The delete-view prompt has no "Don't ask again"**, unlike every other prompt, because deleting a
  `.hkview` is the one action with no undo.
- **Closing the last tab empties it rather than removing it.** A workspace with no tabs at all would
  have needed an empty-window screen that was never asked for.
- **A restored layout whose device is absent keeps its view and says so on the source picker.**
  Three related questions were never answered: whether that view should apply silently, whether the
  tab strip should be able to empty completely, and whether a clean quit should differ from a crash.
  The last one is not currently possible — nothing records a clean exit.

## Still open

- **An in-app updater over GitHub Releases** is planned but unbuilt; see `docs/app-updater-plan.md`
  for the constraints that shape it and the three decisions still outstanding.

- **Cross-pane connectors.** Timestamp-synced scrolling with IntelliJ-merge-style lines between panes.
  Shape agreed; the pairing rule — connect only within a time window, or always to the next line — was
  never settled.
- **Process filtering.** `logcat -v long,epoch,uid` plus `pm list packages -U` was confirmed available
  on the target device; it needs a `LogcatParser` change for the extra uid column.
- **Display-only tag trimming.** Filters must keep the verbatim tag, but the leading punctuation run
  could be hidden when drawing.
- **Dialog title bars are the platform's.** Jewel has no decorated *dialog*, only a decorated window.
- **A pane move loses its scroll position.** `rememberLazyListState` is local to the pane, so dragging
  one to a different split resets it. Invisible while following the tail.
