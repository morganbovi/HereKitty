# HereKitty

A logcat viewer for Compose Desktop, built to read noisy Android logs across several tags at once.

Ordinary logcat gives you one stream and one filter. HereKitty records everything a device emits for
the whole session and lets you open as many independently filtered panes as you need, side by side —
so data leaving the app, data the hardware receives, data the hardware sends, and data the app
receives are all readable against each other at the same time.

## What it does

- **Push-based device discovery.** Talks the ADB host protocol directly, so devices appear and
  disappear as events. Sessions survive an unplug and resume when the device comes back.
- **Records the whole session.** Everything is kept up to a configurable memory limit, oldest lines
  dropped first. The session toolbar shows how much of that limit is in use.
- **A running list of tags.** Every tag the session has seen is searchable in the pane's tag picker,
  and stays listed even after its lines have been evicted.
- **Panes are views, not copies.** Each pane holds an index into the one shared buffer, so adding a
  pane costs almost nothing.
- **Saveable, shareable views.** Name a pane setup and reapply it to any device, or to a recording.
  A view holds only the panes and their filters, never a device, so exporting one and handing it to a
  colleague just works on their machine.
- **Tabs.** Keep the same view on two devices in separate tabs, each tab its own layout.
- **Picks up where you left off.** Tabs, splits and views come back on launch, and reattach to the
  devices they were watching if those are still plugged in.
- **Record to a file and open it again.** Export the logs alone, or export logs *and* view together as
  one `.hkbundle` — an ordinary zip you can hand to someone, which opens with the view already
  applied. Real captures compress to about 23 bytes per line.
- **Two levels of splitting.** Split beside or below to watch several sources at once; split a session
  into panes to watch several tags, and drag panes into the order you want.
- **Choose what a line shows.** Timestamp, level, tag, process and thread id, and line wrapping all
  toggle from the settings popup.
- **IntelliJ look and density**, via [Jewel](https://github.com/JetBrains/intellij-community/tree/master/platform/jewel)
  and the [IntelliJ Platform UI Guidelines](https://plugins.jetbrains.com/docs/intellij/ui-guidelines-welcome.html).

## Running it

Requires JDK 25 — Gradle provisions it for you — and `adb` on the machine.

```bash
./gradlew :desktopApp:run
```

If adb is not on `PATH` or under `ANDROID_HOME`, point `HEREKITTY_ADB` at the binary.

## Layout

```
:desktopApp   main(), the window, starts Koin
:app          Compose UI and presenters
:domain       models, repository interfaces, coroutine base
:adb          the ADB host protocol and logcat parsing
:repository   session buffers, tag registry, filtered views
:injector     wires the modules together
```

See `CLAUDE.md` for architecture and build notes, and `PRESENTERS.md` for the presenter pattern.
