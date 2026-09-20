# CLAUDE.md

Guidance for working in **botmaker-plugin-toolkit**, the widget kit a BotMaker Studio plugin compiles
against.

Read the umbrella `../CLAUDE.md` first, `../botmaker-studio-api/CLAUDE.md` for the contract this sits on,
and `../docs/refactor/24-plugin-platform.md` for why either exists.

## What this module is, and what it is not

Implementation — which is exactly what `botmaker-studio-api` may not contain. That split is the whole
reason there are two modules:

- **The contract must version slowly.** A plugin's compiled `.class` files cannot be rewritten by anybody,
  so a change there that an already-built plugin cannot survive is a Studio-major decision.
- **A widget kit will not.** It moves every time an editor moves.

A plugin takes a new toolkit without taking a new contract. That is the property to protect; if a change
here would force the contract to move with it, the change is in the wrong module.

## It was two halves for one day, and it is a widget kit again

On 2026-09-09 this module grew `com.botmaker.plugin.toolkit.config` — `Settings`, `ProjectValues`,
`ValueGrammar`, how a *running bot* reads its own parameters — with `jackson-databind` beside it, and lost
both the same evening to **`botmaker-plugin-basics`**. Nothing of it is here now. The lineage is worth
carrying, because every step of it was right about the step before:

- They were `botmaker-shared`'s from 2026-09-07, which made a plugin shipping nothing but a value type and
  its grammar depend on JNA, `jna-platform`, OpenCV and dadb to reach a four-method interface. **There is no
  link between reading a parameter and matching an image.**
- They came here because **this is an artifact that reaches a bot**: a plugin declares the toolkit at
  `compile` scope, so it travels with that plugin onto the classpath of every bot that uses it, while the
  contract is `provided` and so absent from a bot, and the SDK holding them made reading a value a privilege
  of plugin #1.
- **They left because a widget kit owns no value types.** The mechanism belongs with somebody who has a
  vocabulary to read; here it could only be held under a standing promise never to use it (*this module
  ships no grammar, or it becomes a vocabulary*), and it made a plugin that wanted to read one parameter
  resolve a widget kit and a JSON parser to do it. `botmaker-plugin-basics` is a plugin, owns the nine JDK
  value types, ships `BasicsGrammar` for them, and reaches a bot through the SDK's own `compile`-scope
  dependency on it.

**The rule to apply to the next thing proposed for this module is the one that removed those:** a class here
must be a *widget or a shape*, useful to a plugin that has no vocabulary of its own. If it needs types to
mean anything, it belongs to the plugin that owns them.

**And the rule on the SDK's side is unchanged, with its exception moved house**: *only the SDK's plugin half
(`plugin/`, `internal/plugin/`) may name a toolkit **widget***. `internal/config/SdkGrammar` and
`api/config/Settings` are library-half classes, and what they name is another plugin's API
(`com.botmaker.plugin.basics.store`) rather than anything here.

**It is the PLUGIN's dependency and never the host's — `botmaker-studio` must not list it.** That rule
stood, was struck on 2026-08-28, and was restored on 2026-09-02. The round trip is worth carrying, because
the argument that struck it is correct and will be made again.

The original wording was *"Two plugins are entitled to two toolkit versions on two classloaders, and the
moment Studio resolves one, they are not."* **The second sentence is false**, and nobody should restore it:
`PluginLoader` is parent-first only for `com.botmaker.plugin.api.**` and the platform namespaces, so the
toolkit is resolved **child-first** — a plugin carrying its own copy still gets its own.

It was struck for a real bug. Studio's plugin #1 was the SDK, whose `SdkPlugin` extends
`AbstractStudioPlugin` through a dependency the SDK declares `optional` — so not transitive, so Studio's
classpath had no toolkit at all, so `ServiceLoader` threw `NoClassDefFoundError` while constructing the only
plugin Studio shipped and **Studio ran with an empty palette**. Studio took a `runtime`-scoped toolkit under
the replacement rule *whoever puts a plugin on a classpath supplies what that plugin needs*.

**On 2026-09-02 Studio stopped bundling a plugin, and that rule stopped applying**: Studio puts no plugin on
any classpath, so it supplies nothing. The dependency survived one further day on *"the fallback copy for a
plugin that brings none"*, and **that is the claim to refuse.** Read `botmaker-cli`'s `pom-scopes` check: it
**refuses** a `provided` toolkit and **passes** a plugin declaring no toolkit at all. So a plugin either
brings its own at `compile` scope — which is what `botmaker-plugin-archetype` generates — or uses no widget
of ours and needs nothing. There is no third plugin for a fallback to serve; the only one it would rescue is
the one the plugin registry rejects, and rescuing it would bind that plugin to **the host's** toolkit version
rather than the one it compiled against, converting an honest failure to load into a `NoSuchMethodError`
deferred to whichever method moved.

So the rule is the original one with the false clause replaced by the true one: **not because a host copy
denies a plugin its own version (it does not), but because there is no plugin it helps and one it silently
mis-serves.**

Enforced regardless, and unchanged throughout: **no Studio source may name a `com.botmaker.plugin.toolkit`
type.** `StudioSourcesTest` scans Studio's *source* rather than its classpath, which is what keeps it a real
test now that the way to break the rule is to add the dependency back rather than widen a scope.

## What is in here, after the 2026-08-28 lift

Seven widget classes (`Editors`, `Pills`, `Fields`, `Modals`, `Values`, `Styles`, `Thumbnail`) and, since
the lift out of the SDK, five more that are not widgets at all:

| class | what it is | why it is here and not in a plugin |
|---|---|---|
| `Slots` | reads and writes a value that is Java source in a slot and stored text in a Parameters row | not a line of it named an SDK type; **every** editor that can sit in source needs it |
| `CallSites` | the four matcher shapes for an editor chosen by the call around a value, not by its type | the matching is generic; the class and method **names** are the plugin's and stay there |
| `Codecs` | `ValueCodec`s from lambdas, plus `or` (total) and `seeded` | four one-line answers should not cost fourteen lines of anonymous class per type; there is no three-argument `of`, because the reader it defaulted away is how eight of seventeen types became write-only |
| `AbstractStudioPlugin` | the four contributions, each built once on first use | the build hooks **cannot be fields**: `ServiceLoader` constructs a plugin while a project is opening |
| `testing.TestContexts` | a recording `SlotContext`/`ValueContext` | a plugin author could not unit-test an editor without writing this first, so the predicate half went untested |

**`TestContexts` gained `withRun` on 2026-08-31**, for the contract's new `SlotRun`. It records what an
editor writes to a run *and enforces `minimum()` exactly as the host does* — a `replace` with too few
elements leaves the elements alone and counts no write — so a test can assert that an editor honours the
floor rather than trusting it. `run()` answers `null` without it, which is what nearly every real slot
answers and therefore the case an editor must handle first. `SlotRunTest` holds those cases.
| `Source` (2026-08-28) | Java source: a string literal, a char, a number, an enum constant, a constructor | a plugin emits Java whether it means to or not — `ValueCodec.literal` and every slot write — and this project already had three hand-rolled escapers, each of which stopped at the backslash and the quote |
| `ZoomPan` (2026-08-30) | Ctrl+scroll zoom about the cursor and middle-drag pan, as event **filters** over a `Pane` and a content `Group` | it names no capture target, no colour and nothing of any plugin's API — it is a gesture, which is the definition of a shape. Written in Studio, held in the SDK for two slices because Studio source may not name a toolkit type, and moved the moment both its callers were the SDK's |

**`Styles.UNTHEMED` arrived with `ZoomPan` and is the first style class here that is an *opt-out*.** The host
themes a plugin's windows for it, so a translucent surface drawn over a live game — where the shell's
background, border and radius are the one thing that would ruin it — has to be able to say no. It is a
marker with no properties of its own, so a host that does not recognise it simply themes the window as usual.

`Editors` gained `boundedPill` (a number with a range, in a dialog, committed on OK) and `flag` out of the
same lift. **What did not move is the `Bound` table** naming `setDefaultConfidence` and what its range is —
that is the SDK's knowledge about its own API, and it is the worked example of rule 4 below.

**Later the same day it gained three more, and the split each time is *shape versus table*.**
`tuplePill(ctx, TupleSpec)` is the SDK's three geometry editors written once — a pill over a few whole
numbers, a way to take them off the screen (`Pick.REGION`/`POINT`/`MEASURE`/`NONE`, all of them host
capabilities), a dialog to type them. The SDK keeps three `TupleSpec` constants, and what is in them is
exactly what could not move: that a `Rect` is an origin plus a size and reads `10, 20  640×480`.
`Slots.holdsNumbers` moved with it, because *is this value coordinates at all, or is it `target.center()`*
is a question about source text. **`choiceSlot(ctx, options, prompt)` arrived on 2026-08-30 and is the pair to `choice` that `textSlot` is to
`text`** — it writes through `Slots`, so the value is a Java string literal in a bot's source and the
characters themselves in a Parameters row. Two things in it are the shape rather than a preference: the
options are a `Supplier` read when the list opens (a set that moves — `gallery`'s rule), and the box is
**editable**, because a value naming something that does not exist yet is a real and frequently deliberate
state, and an editor that could only pick from what exists would make it unsayable. Its one caller today is
the SDK's activity/outcome pair, and the vocabulary — *which* names, and the prompts — stayed there.

`program(ctx, prompt)` is browse-or-type for an executable and
`textSlot(ctx, prompt, columns)` is `text` on the `Slots` side — the SDK's `LaunchEditors` keeps `game()`,
its cover art, and the two prompts, which are the only sentences in it that know what a launch call is.

**One thing that looks liftable and is not: the SDK's own `SdkValueTypes` still uses its private
`codec(…)`/`seeded(…)` helpers rather than `Codecs`, and `LiteralWriter` keeps its own escaping rather than
`Source`.** Not an oversight. The crash argument for it came and went — Studio carried no toolkit, then
briefly did (2026-08-28 to 2026-09-02), and does not again — but the conclusion never depended on it:
**the SDK is a library *and* a plugin, and only its plugin half (`plugin/`, `internal/plugin/`) may name
us.** A library half that reached for a plugin's widget kit would be unusable in every host that does not
happen to bundle one — which, since Studio stopped bundling any plugin at all, is **every host without
exception**.

So the ~15 lines `Source` and `LiteralWriter` have in common are **deliberate duplication**, and the SDK's
copy carries a comment saying so.

## The three rules

**1. A widget takes a `ValueContext` and gets everything else from it.** Not a `CodeEditorService`, not a
`ProjectConfig`, not a `Stage` the caller found somewhere. `ctx.services()` is theming, capture, dialogs and
the project's paths, and it is the only door. A widget that needs something not on `StudioServices` is
telling you the *contract* is missing something — say so, do not route around it.

**2. Nothing throws while building a node.** A value is a `List<String>` that a user may have typed anything
into, or that a newer version of the plugin wrote. `Values` degrades in every case, on purpose. An editor
that throws in its constructor leaves a row of the Parameters window with no widget in it and no explanation
— which reads as the host being broken.

**3. Building an editor never writes.** Not even to normalise what is already there. A project opened and
closed must come back byte-identical, and a widget that "tidies" a value on render rewrites every bot the
user merely looked at.

**4. Nothing here may name a plugin's vocabulary.** Not in a signature, not in a name, not in a javadoc
sentence. If a member has to say "Steam", "duration", "capture source" or "confidence", it belongs to the
plugin that owns that word. `Fields.duration` is the worked precedent — it returns a `long` and draws no
preview label, because spelling a total back out is the wire format owner's job, and `WireText.spellDuration`
stayed in the SDK. `Editors.NumberRange` passes because a *bounded number* is a shape; the table saying
`setDefaultConfidence` runs 0 to 1 does not, and stayed.

This is the acceptance test for every lift out of a plugin, and it is what stops this module becoming the
SDK's second home. A widget that is generic only because its one caller happens to be generic is not generic.

## The one dependency, and why the bar was met

`botmaker-studio-api` and `javafx-controls` are `provided` — a plugin has both already. **The only
dependency a plugin actually resolves through this module is JavaPoet**
(`com.palantir.javapoet:javapoet`, one 106 KB jar with none of its own), added 2026-08-28 for `Source`.

The bar it had to clear is the one this section has always stated: *it becomes every plugin's dependency,
and its compatibility becomes ours.* What cleared it is that **a plugin writes Java whether it means to or
not** — `ValueCodec.literal` returns Java source and `Slots.write` writes an expression into a bot's file —
so the choice was never "a dependency or nothing", it was "one implementation or a hand-rolled escaper per
plugin". This project had already written three of those (here, in the SDK, and in the generated skeleton),
and each of them escaped the backslash and the quote and stopped, so a pasted tab produced a slot that would
not compile.

**`Source.call` was deleted on 2026-09-04, and the reason is the rule to apply to the next member proposed
here.** It composed `Type.method(a, b)` and checked the method name reflectively, and it had **no production
caller in its whole life**: the one place that wanted it — the SDK's `MacroTranslator` — declined it, because
a recorded macro is text somebody reads before pasting into a file where the type is already imported, and
`call` qualifies the type in full. A member with one candidate caller that refuses it is a member whose shape
was guessed. What survives is `Source.requireMethod`, which is the half that was actually used, called
directly by that translator.

**A compile-checked method reference was raised as the replacement and is not possible.** `call(Mouse::click)`
binds to a functional interface whose *shape matches the method*, so arbitrary arity needs one interface per
parameter count — precisely the `MemberRef` + `M0`–`M5` apparatus built for `PaletteCatalog` and deleted on
2026-08-27, and a method reference still cannot name a specific overload. There is no arity-free form. Both
`Source`'s javadoc and this paragraph say so, because the idea is a good one and will be had again.

**`Source.string` is the one member JavaPoet does not implement**, and that is pinned by a test rather than
left to be rediscovered: `$S` splits a string containing a newline into a concatenation *across source
lines*, which is right for a generated file and wrong for a slot, where the host writes the result into the
middle of an existing line. The structural members (`newInstance`, `call`, `type`) are JavaPoet's, and
**no JavaPoet type appears in a signature here** — so a plugin's own compile is unaffected by which library
is behind it, and the library can be replaced without breaking anybody.

ControlsFX was considered and declined on the same bar and did not clear it: `PropertySheet` is a whole-form
abstraction and these are bespoke single-value nodes, so it would sit unused beside them while every plugin
that wanted a slider resolved it. If the toolbar or panel work later wants `PopOver` specifically, take the
dependency **then**, with the need in hand.

## Why it flattens and the other two plugin-facing modules do not

`botmaker-studio-api` and `botmaker-plugin-processor` run no `flatten-maven-plugin` and carry no
`.deps.env`, because both exist to bake a `-D`-injected `${botmaker.*.version}` into a published pom and
neither module pins a BotMaker upstream. **This one does** — the contract, whose `ValueContext` every widget
takes. So it gets both, exactly like `botmaker-session` and `botmaker-sdk`, and for the reason their headers
give: Maven publishes the *committed* pom, not the effective one, so without flatten a `-D` changes what
this build resolves and nothing about what a plugin resolving this toolkit from JitPack sees. That is the
shape of the `0.0.0-SNAPSHOT` bug that shipped in every SDK up to v1.0.24.

`flattenMode=oss` with `<repositories>keep</repositories>`: `oss` strips the jitpack repository declaration
otherwise, and a consumer resolving a `com.github.LiQiyeDev` artifact needs it.

## Style

`../docs/refactor/00-conventions.md` applies, and so does the contract's own rule that **Javadoc is the
deliverable**: a plugin author has this module's Javadoc and nothing else. Every widget's doc says which
mistake it exists to prevent — that a bare `TextField` loses edits made by clicking away, that an unowned
`Stage` falls behind the editor — because those are the things the author cannot find out any other way.

## Building

```bash
mvn test        # ValuesTest, CallSitesTest, SourceTest, SlotRunTest, TupleLabelTest (52) — what is
                # assertable with no JavaFX toolkit
mvn install     # com.github.LiQiyeDev:botmaker-plugin-toolkit:0.0.0-SNAPSHOT
```

Do not add a test that asserts a builder returned non-null; a compile proves that. `SourceTest` is the
other kind worth having: its output is compiled by **somebody else's** build, so a wrong escape is a
compile error in a bot, reported against a line its author never wrote. What is worth holding is
in `ValuesTest`: every case there is a real state a project file reaches, and in every one the answer is a
default rather than an exception.

Published through JitPack, which serves each git tag under `com.github.LiQiyeDev` regardless of this pom's
`groupId`/`version`. **The maintainer owns the publish** — releases are cut from the umbrella with
`../release.sh --plugin-toolkit <version>`.
