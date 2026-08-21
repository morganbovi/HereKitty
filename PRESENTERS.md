# Presenter Spec

HereKitty presenters follow the same pattern as `Fundlings`, `phonejail`, and `AutoPi`.

## Responsibilities

- A presenter owns screen state, event reduction, and async work.
- A content composable reads a single `UiModel` and forwards user events.
- Keep transient rendering-only state in the composable when it is not domain relevant:
  `LazyListState`, `TextFieldState`, popup anchors.
- Keep filter and form state in the presenter when it affects what is shown, saved, or navigated to.

## Structure

- One `Presenter` class and one sibling `UiModel` file per feature.
- `present()` should read top to bottom:
    1. open long-lived resources with `remember`, releasing them in `DisposableEffect`
    2. read repositories and flows with `collectAsState()`
    3. declare presenter-owned state with `remember`
    4. derive computed values
    5. return one `UiModel`
- Import the `UiModel.Event.*` aliases so the reducer reads cleanly.
- Runtime arguments and callbacks are parameters of `present()`, not constructor dependencies.
  `LogPanePresenter.present(session, canClose, onClose)` is the reference example.

## Event handling

- Events represent user intent, not widget details: `OnTagToggled`, `OnFollowTailToggled`,
  `OnMinLevelChanged` — not raw lambdas passed down the tree.
- Use `EventHandler<Event>` as the single reducer entry point.
- Pass a stable `key` to `EventHandler` when the handler's identity matters for recomposition, as
  `SessionPresenter` does with the session id.

## Async work

- Use `rememberCoroutineScope()` plus `launchCoroutine` from `:domain`'s `CoroutineExtensions`.
- Set loading flags before work starts and clear them on both the success and error paths.
- Log failures with `Log.e(throwable) { "…" }` and expose user-facing text on the `UiModel`.
- Debounce anything that triggers expensive work. Changing a pane's filter walks the whole session
  buffer, so `LogPanePresenter` settles the query input before building a new `LogFilter`.

## State shape

- Prefer a data `UiModel` with explicit fields.
- Derive booleans in the presenter — `hasActiveFilter`, `isPaused`, `canCloseSession` — rather than
  recomputing them in the composable. Trivial derivations may be computed properties on the UiModel.
- Use sealed `UiModel` variants only when the whole pane changes shape.
- A `UiModel` may hold a non-equatable value such as `LogSnapshot`. That is deliberate: the snapshot
  changes every revision and should not be compared.

## Current repo conventions

- Presenters are Koin `@Factory` classes.
- `UiModel` types stay in separate files.
- Composables are named `…Content`, because every feature renders into a resizable pane rather than
  owning the window.
- There is no navigator or backstack. Layout is state: a workspace is a list of slots, a session is a
  list of panes.
