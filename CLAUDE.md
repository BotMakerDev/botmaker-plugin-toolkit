# CLAUDE.md

Guidance for working in **botmaker-plugin-toolkit**, the widget kit a BotMaker Studio plugin compiles
against.

Read the umbrella `../CLAUDE.md` first, `../botmaker-studio-api/CLAUDE.md` for the contract this sits on,
and `../docs/refactor/24-plugin-platform.md` for why either exists. This file states what is true now. What
was here and went — the `config` package and its grammar, `CallSites`, `Codecs`, `Source`, `Types`,
`AbstractStudioPlugin`, `AbstractPluginType`, JavaPoet — and why, is in
`../docs/refactor/31-umbrella-history.md` (*toolkit*); the text this file carried until 2026-09-28 is
`git show 45a5ea9:CLAUDE.md` in this repository.

## What this module is, and what it is not

Implementation — which is exactly what `botmaker-studio-api` may not contain. That split is the whole
reason there are two modules:

- **The contract must version slowly.** A plugin's compiled `.class` files cannot be rewritten by anybody,
  so a change there that an already-built plugin cannot survive is a Studio-major decision.
- **A widget kit will not.** It moves every time an editor moves.

A plugin takes a new toolkit without taking a new contract. That is the property to protect; if a change
here would force the contract to move with it, the change is in the wrong module.

**A class here must be a *widget or a shape*, useful to a plugin that has no vocabulary of its own.** If it
needs types to mean anything, it belongs to the plugin that owns them. That is the rule that sent the
parameter grammar to `botmaker-plugin-basics` and plugin declaration to the contract's steps, and it is the
rule for the next thing proposed.

**Only the SDK's plugin half (`plugin/`, held by the SDK's `PluginLayersTest`) may name a toolkit class.** A
library half that reached for a plugin's widget kit would be unusable in every host that does not bundle
one — which, since Studio bundles no plugin, is every host.

**It is the PLUGIN's dependency and never the host's — `botmaker-studio` must not list it.** `PluginLoader`
resolves the toolkit child-first, so a host copy would not deny a plugin its own version; the reason is that
**there is no plugin a host copy helps and one it silently mis-serves**. `botmaker-cli`'s `pom-scopes`
refuses a `provided` toolkit and passes a plugin that declares none, so a plugin either brings its own at
`compile` — what `botmaker-plugin-archetype` generates — or uses no widget of ours. A host fallback would only
rescue the plugin the registry rejects, and would bind it to the host's toolkit version, converting an
honest failure to load into a `NoSuchMethodError` at whichever method moved. **No Studio source may name a
`com.botmaker.plugin.toolkit` type** (`StudioSourcesTest`, which scans source rather than the classpath).

**What both sides need goes in the contract, not here.** The one thing a plugin's widgets and Studio's own
genuinely shared was the style-class names; they are the contract's `StyleClasses`, `Styles` implements it,
and Studio's `StyleClassesTest` holds the stylesheet to it. Ask the next such thing *is the host the only
possible source?* before anyone proposes Studio take this module.

## What is in here

| class | what it is |
|---|---|
| `Editors` | whole editors: `tuplePill`/`tupleLabel` (a few whole numbers, picked on screen or typed — a `TupleSpec` says the labels and reading order, `Pick` which numbers a drag or click yields), `boundedPill` (a number with a range, in a dialog, committed on OK — `NumberRange`), `flag`, `text`, `program` (browse-or-type for an executable), `choiceSlot` (a dropdown over a set that moves: a `Supplier` read when the list opens, and editable, because a name that does not exist yet is a real and often deliberate state) |
| `Pills` | the pill: `bare` + `onOpen` (a menu rebuilt on each open), `button`, `icon`, `item`, `separator`, `value` (placeholder styling when unset) |
| `Fields` | `committing` (a text field that commits on Enter *and* on focus loss) and `stepped` (▲/▼, the arrow keys and the wheel over a field, Shift reported; what a step means is the caller's) |
| `Modals` | a themed, owned window: `form` (any body, OK/Cancel, or Close for a body that has written already), `window` (a whole window with its own buttons, returned unshown — `Frame` says title, size, minimum and modality; reopens at the size it last closed at, in memory, `WindowSizes`), `gallery` (a searchable grid scanned off the FX thread, with an optional typed row — `Gallery`, `Thumbnail`), `owner` |
| `Async` | slow work off the FX thread: `load` (a supplier's answer to a callback on the FX thread, a throw as one sentence to another) and `run` (work, then an optional FX follow-up); daemon threads, returned started so a closing window can interrupt them |
| `Values` | reading a JDK value off a `ValueContext` with a fallback, `setNumber` (writes as the declared type), `labelOr`. Degrades, never throws |
| `Slots` | the value as **written**, for an expression nothing can decode: `raw`, `isEmpty`, `sourceOr` (the second half of every pill label) |
| `Styles` | the contract's `StyleClasses`, applied (`on`), plus `UNTHEMED`, an opt-out for a translucent surface over a live game |
| `ScreenPicks`, `Region` | the screen pick a tuple pill asks its plugin for: **the shape is the toolkit's and the pixels are the plugin's**. Passed to the widget, never registered in a static |
| `ZoomPan` | Ctrl+scroll zoom about the cursor and middle-drag pan, as event **filters** over a pane and a content group — a gesture, which is the definition of a shape |
| `ManagedHandle` | one `@Managed` value opened, read as its type, created when missing and written, from a plugin's own window; the type comes from the plugin's own `ManagedValue<T>` |
| `ManagedSet` | one `@Managed` open set: `PluginValues`' member operations addressed by the plugin's own `ManagedValue<E>`, with `add` taking an `E`. Links only contract members a released contract has |
| `testing.TestContexts` | a recording `SlotContext`/`ValueContext`, so an editor **and its predicate** can be unit-tested. `withRun` enforces `minimum()` exactly as the host does, so a test can assert an editor honours the floor |

**The split each time is *shape versus table*.** `tuplePill` is the SDK's three geometry editors written once;
the SDK keeps three `TupleSpec` constants, and what is in them is exactly what could not move: that a `Rect`
is an origin plus a size and reads `10, 20  640×480`. `boundedPill` is a shape; the table saying a match's
confidence runs 0 to 1 is the SDK's and stayed.

## The audit of 2026-09-28: every member has a caller

Every public member was counted against its callers in the SDK's `plugin/`, basics and the archetype. The
rule applied: **no caller, deleted; called only from inside the toolkit, package-private; one outside
caller, kept when it is a shape** (the `Editors` pills, `Modals.gallery`, `ZoomPan` — each was lifted out of
a plugin under rule 4 below, and a second plugin is who they are for).

- **Deleted, no caller anywhere**: `Editors.bounded` and `Editors.choice` (`choiceSlot` is the dropdown
  every caller wanted), `Fields.duration` (basics owns `Duration` and draws its own picker — a length of time
  is a vocabulary), `Modals.numbers` (replaced by `tuple` on 2026-09-27), `Modals.chooser` (`gallery` is the
  grid every caller wanted), `Modals.Gallery.pictures`, both `Pills.menu` overloads (every editor needs the
  pill before its menu, so it is `bare` + `onOpen`), `Region.right/bottom/isEmpty`, and
  `ScreenPicks.color` — implemented twice by the SDK and never called: a colour editor samples through its
  own plugin.
- **Package-private, the toolkit's own parts**: `Fields.integer` and `Fields.bounded` (`boundedPill`'s),
  `Modals.tuple` (`tuplePill`'s), `Modals.program` (`Editors.program`'s), `Thumbnail.of`, and `ZoomPan`'s
  zoom limits.
- **Kept with no plugin caller**: `ManagedHandle.value()` (the accessor of what the handle holds) and
  `TestContexts.Recording.withType`/`withBounds` — a test kit sets every part of the context an editor can
  read, whether or not a test has asked yet.
- **Lifted, because both plugins wrote them**: `Fields.stepped` (`Modals.tuple` and basics' number field,
  whose copies had drifted: one read Shift on the buttons and ignored the arrow keys, the other the reverse)
  and `Slots.sourceOr` (ten hand-written copies across both plugins).
- **Considered and not lifted**: the "button pill that opens `Modals.form`, writes, relabels" pattern (six
  sites) — its body, its commit rule and its label differ at every site, and a helper would take three
  lambdas to save one array; the `java.awt.Color` ↔ JavaFX conversions (three lines, and naming
  `java.awt` here for them is not a shape); the two plugins' `trim(double)` (they round differently, on
  purpose); the SDK's ⚙ Bot Settings spinners (a form of five typed settings, not a single-value shape).

## The rules

**1. A widget takes a `ValueContext` and gets everything else from it.** Not a `Stage` the caller found
somewhere. `ctx.services()` is theming, dialogs and the project's paths, and it is the only door. A widget
that needs something not on `StudioServices` is telling you the *contract* is missing something — say so, do
not route around it. A window with no value behind it (a checklist row that sets a run property) takes the
`StudioServices` itself: `Modals.form` and `Modals.gallery` have that overload (2026-09-29), never a `Stage`.

**2. Nothing throws while building a node.** `ValueContext.value` answers **empty** for anything the host's
grammar could not decode — a variable, a computed initializer, `target.center()` — and that is a normal
state, not a failure path. `Values` degrades to the caller's fallback in every case, on purpose. An editor
that throws in its constructor leaves a row of the Parameters window with no widget in it and no
explanation — which reads as the host being broken.

**2b. No plugin parses or writes Java.** Read the value with `ValueContext.value(Class)` and write one with
`set(Object)`. `Slots` exists only to **show** an expression `value()` could not decode, so the user sees what
is in their file. *"Is this value numbers at all, or is it `target.center()`"* is exactly "did `value()`
answer", asked by the thing that knows — never reimplement it here.

**3. Building an editor never writes.** Not even to normalise what is already there. A project opened and
closed must come back byte-identical, and a widget that "tidies" a value on render rewrites every bot the
user merely looked at.

**4. Nothing here may name a plugin's vocabulary.** Not in a signature, not in a name, not in a javadoc
sentence. If a member has to say "Steam", "duration", "capture source" or "confidence", it belongs to the
plugin that owns that word. **A widget that is generic only because its one caller happens to be generic is
not generic.** This is the acceptance test for every lift out of a plugin, and it is what stops this module
becoming the SDK's second home.

## No dependency at all

`botmaker-studio-api` and `javafx-controls` are `provided` — a plugin has both already — and there is nothing
else. `mvn dependency:tree` shows no `compile` entry: a plugin author cannot get a version conflict out of
this module. The bar for adding one is that *it becomes every plugin's dependency, and its compatibility
becomes ours*. ControlsFX was weighed and declined on that bar (`PropertySheet` is a whole-form abstraction
and these are bespoke single-value nodes); if a later need wants `PopOver` specifically, take the dependency
**then**, with the need in hand.

## Why it flattens

This module pins a BotMaker upstream — the contract, whose `ValueContext` every widget takes — so like
`botmaker-session` and `botmaker-sdk` it runs `flatten-maven-plugin` and carries `.deps.env`: Maven publishes
the *committed* pom, not the effective one, so without flatten a `-D` changes what this build resolves and
nothing about what a plugin resolving this toolkit from JitPack sees. `flattenMode=oss` with
`<repositories>keep</repositories>`: `oss` strips the jitpack repository declaration otherwise, and a consumer
resolving a `com.github.BotMakerDev` artifact needs it.

## Style

`../docs/refactor/00-conventions.md` applies, and so does the contract's own rule that **Javadoc is the
deliverable**: a plugin author has this module's Javadoc and nothing else. Every widget's doc says which
mistake it exists to prevent — that a bare `TextField` loses edits made by clicking away, that an unowned
`Stage` falls behind the editor, that a native file dialog blocks its thread — because those are the things
the author cannot find out any other way.

## Building

```bash
mvn test        # ValuesTest, SlotRunTest, TupleLabelTest, TupleFieldsTest, ManagedHandleTest,
                # TestContextsArgumentTest (29) — what is assertable with no JavaFX toolkit
mvn dependency:tree   # nothing at `compile`: the property to keep
mvn install     # com.github.BotMakerDev:botmaker-plugin-toolkit:0.0.0-SNAPSHOT
```

Do not add a test that asserts a builder returned non-null; a compile proves that. What is worth holding is
in `ValuesTest`: every case there is a real state a project file reaches, and in every one the answer is a
default rather than an exception.

Published through JitPack, which serves each git tag under `com.github.BotMakerDev` regardless of this pom's
`groupId`/`version`. **The maintainer owns the publish** — releases are cut from the umbrella with
`../release.sh --plugin-toolkit <version>`.
