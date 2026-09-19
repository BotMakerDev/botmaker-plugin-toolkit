# Changelog

All notable changes to `botmaker-plugin-toolkit`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this module uses
[semantic versioning](https://semver.org/). `release.sh` refuses to cut a version with no section here.

## [0.1.4] — 2026-09-19

No source changes since v0.1.3; re-released for updated upstream pins.

No source changes since v0.1.2; re-released for updated upstream pins.

No source changes since v0.1.1; re-released for updated upstream pins.

No source changes since v0.1.0; re-released for updated upstream pins.

### Added

- **`Region`**, moved here from `com.botmaker.plugin.api.Region` unchanged. `ScreenPicks` is the only thing
  that produces one and `Editors` the only thing that consumes one, so it is this module's type. Update the
  import; nothing else changes.

### Changed

- **This module is a widget kit and its one dependency is still JavaPoet.** For part of 2026-09-09 it was
  not: `com.botmaker.plugin.toolkit.config` — `Settings`, `ProjectValues`, `ValueGrammar`, how a running bot
  reads its own parameters — lived here with `jackson-databind` beside it, on the argument that a plugin
  declares this module at `compile` scope and it is therefore the one artifact that travels all the way onto
  a bot's classpath. Both moved to **`botmaker-plugin-basics`** the same evening, and nothing of that package
  ever appeared in a release.

  The reason is worth recording rather than quietly dropping. A widget kit **owns no value types**, so it
  could hold that mechanism only under a standing promise never to use it — *ship no grammar here, or the
  toolkit becomes a vocabulary* — and it made a plugin that wanted to read one parameter resolve a widget kit
  and a JSON parser to do it. `botmaker-plugin-basics` is a plugin, owns the nine JDK value types, ships
  `BasicsGrammar` for them like any plugin ships one for its own, and reaches a bot through the SDK's
  ordinary `compile`-scope dependency on it. Nothing about how a bot reads a parameter changed; the package
  a plugin imports it from did, before anybody could have imported it.

## [0.1.3] — 2026-09-19

No source changes since v0.1.2; re-released for updated upstream pins.

No source changes since v0.1.1; re-released for updated upstream pins.

No source changes since v0.1.0; re-released for updated upstream pins.

### Added

- **`Region`**, moved here from `com.botmaker.plugin.api.Region` unchanged. `ScreenPicks` is the only thing
  that produces one and `Editors` the only thing that consumes one, so it is this module's type. Update the
  import; nothing else changes.

### Changed

- **This module is a widget kit and its one dependency is still JavaPoet.** For part of 2026-09-09 it was
  not: `com.botmaker.plugin.toolkit.config` — `Settings`, `ProjectValues`, `ValueGrammar`, how a running bot
  reads its own parameters — lived here with `jackson-databind` beside it, on the argument that a plugin
  declares this module at `compile` scope and it is therefore the one artifact that travels all the way onto
  a bot's classpath. Both moved to **`botmaker-plugin-basics`** the same evening, and nothing of that package
  ever appeared in a release.

  The reason is worth recording rather than quietly dropping. A widget kit **owns no value types**, so it
  could hold that mechanism only under a standing promise never to use it — *ship no grammar here, or the
  toolkit becomes a vocabulary* — and it made a plugin that wanted to read one parameter resolve a widget kit
  and a JSON parser to do it. `botmaker-plugin-basics` is a plugin, owns the nine JDK value types, ships
  `BasicsGrammar` for them like any plugin ships one for its own, and reaches a bot through the SDK's
  ordinary `compile`-scope dependency on it. Nothing about how a bot reads a parameter changed; the package
  a plugin imports it from did, before anybody could have imported it.

## [0.1.2] — 2026-09-18

No source changes since v0.1.1; re-released for updated upstream pins.

No source changes since v0.1.0; re-released for updated upstream pins.

### Added

- **`Region`**, moved here from `com.botmaker.plugin.api.Region` unchanged. `ScreenPicks` is the only thing
  that produces one and `Editors` the only thing that consumes one, so it is this module's type. Update the
  import; nothing else changes.

### Changed

- **This module is a widget kit and its one dependency is still JavaPoet.** For part of 2026-09-09 it was
  not: `com.botmaker.plugin.toolkit.config` — `Settings`, `ProjectValues`, `ValueGrammar`, how a running bot
  reads its own parameters — lived here with `jackson-databind` beside it, on the argument that a plugin
  declares this module at `compile` scope and it is therefore the one artifact that travels all the way onto
  a bot's classpath. Both moved to **`botmaker-plugin-basics`** the same evening, and nothing of that package
  ever appeared in a release.

  The reason is worth recording rather than quietly dropping. A widget kit **owns no value types**, so it
  could hold that mechanism only under a standing promise never to use it — *ship no grammar here, or the
  toolkit becomes a vocabulary* — and it made a plugin that wanted to read one parameter resolve a widget kit
  and a JSON parser to do it. `botmaker-plugin-basics` is a plugin, owns the nine JDK value types, ships
  `BasicsGrammar` for them like any plugin ships one for its own, and reaches a bot through the SDK's
  ordinary `compile`-scope dependency on it. Nothing about how a bot reads a parameter changed; the package
  a plugin imports it from did, before anybody could have imported it.

## [0.1.1] — 2026-09-17

No source changes since v0.1.0; re-released for updated upstream pins.

### Added

- **`Region`**, moved here from `com.botmaker.plugin.api.Region` unchanged. `ScreenPicks` is the only thing
  that produces one and `Editors` the only thing that consumes one, so it is this module's type. Update the
  import; nothing else changes.

### Changed

- **This module is a widget kit and its one dependency is still JavaPoet.** For part of 2026-09-09 it was
  not: `com.botmaker.plugin.toolkit.config` — `Settings`, `ProjectValues`, `ValueGrammar`, how a running bot
  reads its own parameters — lived here with `jackson-databind` beside it, on the argument that a plugin
  declares this module at `compile` scope and it is therefore the one artifact that travels all the way onto
  a bot's classpath. Both moved to **`botmaker-plugin-basics`** the same evening, and nothing of that package
  ever appeared in a release.

  The reason is worth recording rather than quietly dropping. A widget kit **owns no value types**, so it
  could hold that mechanism only under a standing promise never to use it — *ship no grammar here, or the
  toolkit becomes a vocabulary* — and it made a plugin that wanted to read one parameter resolve a widget kit
  and a JSON parser to do it. `botmaker-plugin-basics` is a plugin, owns the nine JDK value types, ships
  `BasicsGrammar` for them like any plugin ships one for its own, and reaches a bot through the SDK's
  ordinary `compile`-scope dependency on it. Nothing about how a bot reads a parameter changed; the package
  a plugin imports it from did, before anybody could have imported it.

## [0.1.0] — 2026-09-16

### Added

- **`Region`**, moved here from `com.botmaker.plugin.api.Region` unchanged. `ScreenPicks` is the only thing
  that produces one and `Editors` the only thing that consumes one, so it is this module's type. Update the
  import; nothing else changes.

### Changed

- **This module is a widget kit and its one dependency is still JavaPoet.** For part of 2026-09-09 it was
  not: `com.botmaker.plugin.toolkit.config` — `Settings`, `ProjectValues`, `ValueGrammar`, how a running bot
  reads its own parameters — lived here with `jackson-databind` beside it, on the argument that a plugin
  declares this module at `compile` scope and it is therefore the one artifact that travels all the way onto
  a bot's classpath. Both moved to **`botmaker-plugin-basics`** the same evening, and nothing of that package
  ever appeared in a release.

  The reason is worth recording rather than quietly dropping. A widget kit **owns no value types**, so it
  could hold that mechanism only under a standing promise never to use it — *ship no grammar here, or the
  toolkit becomes a vocabulary* — and it made a plugin that wanted to read one parameter resolve a widget kit
  and a JSON parser to do it. `botmaker-plugin-basics` is a plugin, owns the nine JDK value types, ships
  `BasicsGrammar` for them like any plugin ships one for its own, and reaches a bot through the SDK's
  ordinary `compile`-scope dependency on it. Nothing about how a bot reads a parameter changed; the package
  a plugin imports it from did, before anybody could have imported it.

## [0.0.5] — 2026-09-05

### Fixed

- **`0.0.4` failed on JitPack too, on a third plugin.** The compiler pin it added was correct; this pom also
  pinned `flatten-maven-plugin` at **1.6.0**, which declares a Maven 3.6.3 prerequisite, and JitPack's
  builder runs **Apache Maven 3.6.1**. Maven refuses to execute such a plugin at all —
  `The plugin org.codehaus.mojo:flatten-maven-plugin:1.6.0 requires Maven version 3.6.3` — before anything
  is published. Pinned to **1.4.1**, which is what `botmaker-session` and `botmaker-sdk` have carried since
  2026-08-22, with the comment that explains it; this module was written later and copied the version
  instead of the reason.
- **The umbrella's `release.sh` now refuses this class of tag before it is pushed.** `check_jitpack_plugins`
  reads every pinned plugin's own pom for its `<prerequisites><maven>` and stops the release if any exceeds
  JitPack's 3.6.1. It reads the prerequisite rather than keeping a list, so it covers the plugin nobody has
  hit yet — and it found one immediately, `maven-shade-plugin` 3.5.3 in `botmaker-cli`.

  Use `0.0.5`; `0.0.4` was never published.

## [0.0.4] — 2026-09-04

### Fixed

- **The pin `0.0.3` added was itself unbuildable on JitPack.** `maven-compiler-plugin` raised its own Maven
  prerequisite to 3.6.3 in 3.12.0, and JitPack's Maven is older — so 3.13.0 failed with
  `requires Maven version 3.6.3`. Pinned to **3.11.0**, which is what `botmaker-shared` has always used.
  Use `0.0.4`; `0.0.3` was never published.

## [0.0.3] — 2026-09-04

### Fixed

- **This module is resolvable from JitPack again**, and so is the contract it compiles against. Neither pom
  pinned `maven-compiler-plugin`, and JitPack's Maven defaults it to 3.1 — which predates
  `maven.compiler.release` and builds with `source 5` (`Source option 5 is no longer supported`). From
  2026-09-02 to 2026-09-04 `botmaker-studio-api` did not build there, so this module could not resolve it
  and `v0.0.2` was never published either. Both are pinned to 3.13.0 now.

### Removed

- **`Source.call(Class, String, Expr...)`**, which composed `Type.method(a, b)` with the type fully
  qualified. It had **no caller in its whole life**: the one place that wanted it declined it, because the
  text it produces is read by a person before being pasted into a file where the type is already imported.
  `Source.requireMethod` — the half that *was* used, to check a method name against its class — is unchanged
  and still public, so composing a call by hand is exactly as safe as it was.

  A compile-checked method reference (`call(Mouse::click)`) was considered as the replacement and is not
  possible: a method reference binds to a functional interface whose shape matches the method, so arbitrary
  arity needs one interface per parameter count, and it still could not name a specific overload. `Source`'s
  own javadoc records this so it is not re-proposed.

## [0.0.2] — 2026-09-02

### Changed

- **Compiled for Java 25 (LTS), against JavaFX 25.0.4**, matching the contract. A plugin using these widgets
  needs a JDK 25 or newer to build and a 25 runtime to load.

## [0.0.1] — 2026-09-02

First release. `0.x` because the contract it compiles against is still `0.x`; this module is nothing but
implementation and is expected to move fast, which is the whole reason it is not part of the contract.

### Declare it at `compile` scope — the host does not supply a copy

**A plugin brings its own toolkit.** `botmaker-studio` deliberately does **not** depend on this module, so
there is no host copy to fall back on, and `botmaker-cli`'s `pom-scopes` check **refuses** a plugin that
declares the toolkit `provided`. A plugin either declares it at `compile` scope — which is what
`botmaker-plugin-archetype` generates — or uses no widget of ours at all, which the same check passes as
*"contract provided, no toolkit"* and which needs nothing from anybody.

That is what makes the child-first arm of `PluginLoader` mean something: **two plugins may hold two toolkit
versions**, each resolving its own. A host copy would not take that away — the loader is child-first here —
but it would silently serve the one plugin that brought none, binding it to *the host's* version rather than
the one it compiled against, and turning an honest failure to load into a `NoSuchMethodError` deferred to
whichever method moved. (Studio carried exactly such a copy for five days while it still bundled a plugin of
its own; it does not any more.)

### Added

- **`Editors.choiceSlot(ctx, options, prompt)`** — a dropdown over a set that moves, over a value that may be
  a slot. It is `choice` written through `Slots`, so the value is a Java string literal in a bot's source and
  the characters themselves in a Parameters row; the options are a `Supplier` read when the list opens, so a
  name added elsewhere a moment ago is offered without redrawing the block; and the box is editable, because
  a value naming something that does not exist yet is a real state and an editor that could not say it would
  make writing the code before the thing it names impossible.
- **The module** — the eighth BotMaker repository, and the second one a plugin compiles against. It holds
  what `botmaker-studio-api` deliberately cannot: implementation. The contract is interfaces and records and
  must be allowed to version slowly, because a plugin's compiled classes cannot be rewritten; a widget kit is
  nothing but implementation and will move every time an editor does. Keeping them apart is what lets a
  plugin take a new toolkit without taking a new contract.
- **`Slots`** — reads and writes a value that is a Java expression in a bot's source and a row of stored
  strings in the Parameters window, so one editor serves both. Lifted out of the SDK, where it had been by
  accident of who wrote the first editor.
- **`CallSites`** — predicates for an editor chosen by the *call* around a value rather than by its type,
  which is the only way to tell a Steam app id from a window title when both are `String`. Four shapes:
  `firstArgumentOf`, `argumentOf`, `firstArgumentWhere`, `trailingArgumentOf`. Every one declines a
  Parameters row, because a row has no call behind it.
- **`Codecs`** — `ValueCodec`s from three lambdas, with `or` to make a partial parser total and `seeded` for
  a type whose starting value is a choice rather than a fallback.
- **`Source`** — Java source, spelled correctly: a string literal, a char literal, a number as a person
  would have typed it, a constructor, a static call and a qualified type name. A plugin emits Java whether
  it means to or not — a `ValueCodec`'s third function returns the literal a bot compiles, and every slot
  write is an expression — and this project had already written **three** hand-rolled escapers, each of
  which escaped the backslash and the quote and stopped, so a pasted tab produced a slot that would not
  compile. `Slots.quote` is now this, under its old name.
  - **The first dependency this module resolves onto a plugin's classpath**: `com.palantir.javapoet:javapoet`
    (the maintained fork; the original has had no release since 2021), one 106 KB jar with none of its own.
    Taken deliberately against the standing rule that a dependency here becomes every plugin's — the choice
    was never "a dependency or nothing", it was "one implementation or one hand-rolled escaper per plugin".
  - **No JavaPoet type appears in a signature**, so the library is replaceable without breaking a plugin.
  - **`Source.string` is the one member JavaPoet does not implement, and a test pins why**: `$S` splits a
    string containing a newline into a concatenation *across source lines*, which is right for a generated
    file and wrong for a slot — the host writes the result into the middle of an existing line, and one slot
    holds one expression.
- **`AbstractStudioPlugin`** — a `StudioPlugin` that builds each of its four contributions once, on first
  use, so nothing expensive runs in the constructor `ServiceLoader` calls while a project is opening.
- **`testing.TestContexts`** — a recording `SlotContext`/`ValueContext` for unit-testing an editor and its
  predicate with no host, no project and no JavaFX thread.
- **`Editors`** — eleven whole editors, each built from a `ValueContext` and needing nothing else:
  `region` (drag a rectangle on screen), `numbers` (a labelled tuple), `tuplePill` (the same tuple as a
  constructor in a bot's source, with a screen picker), `bounded` (a slider and read-out), `text`,
  `textSlot` (the same, writing Java when the value is a slot), `program` (browse for an executable, or type
  a command), `choice`, `gallery` (a grid of pictures), `boundedPill` (a number with a range, edited in a
  dialog and committed on OK) and `flag`. Extracted from the host's own thirteen pickers rather than
  designed, which is why there are eleven: these are the shapes that recurred.
- **`Editors.tuplePill` + `TupleSpec` + `Pick`** — one editor where the SDK had three. A `TupleSpec` says
  which class the slot constructs, what its numbers are called, how a person reads them back, and whether
  they can come off the screen (`REGION` drags, `POINT` clicks under a magnifier, `MEASURE` drags and throws
  the origin away). The formatter stays with whoever owns the type: *a small tuple of pixels* is a shape,
  *a `Rect` is an origin plus a size* is not. `Editors.tupleLabel` is public alongside it because the label
  is the one half assertable with no JavaFX toolkit, and the half worth asserting — it is read back out of
  what the last pick wrote.
- **`Slots.holdsNumbers`** — whether a value is coordinates at all, rather than a variable or a call that
  happens to read as zeroes. Without it a slot holding `bounds` labels itself `0, 0  0×0`, which claims a
  value the user never set.
- **`Pills`** — the summary-plus-menu control nine of those thirteen are. Its menu is rebuilt on opening,
  because an editor's choices depend on state that moves.
- **`Fields`** — a text field committing on Enter **and** on focus loss (either alone loses edits), a
  spinner clamped to its range, a slider with a read-out for the numbers whose scale nobody knows, and
  `duration`: a length of time as one box per unit. The control that replaced was one amount plus a unit
  dropdown, which could only ever say a multiple of a single unit — four and a half minutes had to be entered
  as 270 seconds. It hands back a millisecond total and draws **no preview label**, because spelling a total
  back out is the wire format owner's job and a preview that disagreed with what was stored would be worse
  than none.
- **`Pills.button`** — a pill with no menu, for a value with genuinely one thing to do to it. A menu of one
  entry makes the user click twice to reach the only destination there was.
- **`Modals`** — themed through `Theme` and owned through `Dialogs.owner()`. A plugin must not build its own
  `Stage`: with no theme it is unstyled JavaFX in the middle of a themed application, and with no owner it
  falls behind the editor. Cancelling calls nothing. Five entry points: `form` (any body, with OK/Cancel —
  the general case, so an editor whose modal is a slider or a grid with a search box has somewhere to go),
  `numbers`, `chooser`, `gallery` and `program`.
- **`Modals.gallery`** — a searchable grid whose contents are **listed off the JavaFX thread**, with an
  optional row for typing an answer the grid does not contain. All three are traps a library browser hits
  exactly once each: scanning on the FX thread freezes the editor mid-draw for a time proportional to how
  much the user owns; a grid is a bad way to choose among two hundred things; and the right answer is often
  not installed on the machine the bot is being written on.
- **`Modals.program`** — the native "choose a program" dialog **run off the calling thread**, falling back to
  JavaFX's own chooser where there is no native one. A native dialog blocks its thread until answered, so
  opening one on the FX thread freezes the application behind a window the user cannot see is theirs — and it
  happens to work on the platform with no native tool, which is the worst way for that bug to be distributed.
- **`Values`** — reading the `List<String>` a value arrives as. Every method degrades and never throws,
  because an editor that throws while building leaves a row of the Parameters window empty.
- **`Styles`** — the host's style-class names as constants. The smallest class here and the one a plugin
  cannot do without: the stylesheet is inside the host's jar, so there is no other way to learn the strings.
- **`Thumbnail`** — one cell of a picture chooser; a missing image is an ordinary state and renders as the
  label alone.
- **`ScreenPicks`** — the host's screen overlay (`selectRegion`, `pickPoint`, `sampleColor`) reduced to the
  shape an editor actually calls it in: hand back a value or leave the slot alone. Cancelling is the common
  case, so it is the default rather than something every caller writes a branch for.
- **`ZoomPan`** — pan-and-zoom over a `Node`, for the two modals that show a picture bigger than their own
  window. Scroll to zoom about the pointer, drag to pan, and a fit-to-window reset — small, but wrong in a
  different way in each of the three places it had been written.

### Deliberately absent

- **Any third-party UI dependency.** ControlsFX's `PropertySheet` is a whole-form abstraction and these are
  bespoke single-value nodes; it would sit unused while every plugin resolved it. To be revisited only with
  a concrete need.
- **A form or validation layer.** A seventh widget is worth adding the day a second editor wants it.
