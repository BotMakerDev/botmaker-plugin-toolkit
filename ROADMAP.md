# ROADMAP — botmaker-plugin-toolkit

The running engineering log. `CHANGELOG.md` is the short, per-release answer; this is the detail and the
reasoning.

## Done

### 2026-09-23 — no Java in the toolkit: `Source`, `Slots.arguments` and `Editors.gallery` go

The contract stopped carrying Java for a plugin to write (`setSource`, `replaceEnclosingCall`) or split
(`enclosingCall`), and `SlotRun` crosses values. What was here to serve them had no caller left: `Source`
(the macro recorder was its last user), `Slots.arguments` (documented as for the enclosing call and nothing
else), and `Editors.gallery`, which wrote a picked cell as text and was never called. `TestContexts` records
values only; `withRun` takes `SlotRun.Element`s. `Slots.raw`/`isEmpty` stay, to show what the host could not
read. 19 tests (`SourceTest` went with `Source`).

### 2026-09-23 — `buildCatalog()` stays empty; the host discovers the palette

The example and the javadoc drop `PaletteCatalog.of(…)`: the host (`botmaker-plugin-host`'s `Palettes`)
catalogues every `@Palette` class in a plugin's jar when `catalog()` is empty. A scan here was tried the
same day and withdrawn, because a discovery each plugin runs on itself is the host's job done N times.

### 2026-09-04 — `Source.call` is deleted, and the method-reference idea is written down as refused

`Source.call(Class, String method, Expr...)` composed `Type.method(a, b)` with the type fully qualified, and
checked the method name against the class first. It is gone, with **no caller in its whole life**.

**Why, and it is the rule to apply to the next member proposed here.** It had exactly one candidate caller —
the SDK's `MacroTranslator` — and that caller **declined it**, in a comment, for a reason the member could
not have accommodated: a recorded macro is text somebody reads before pasting into a file where the type is
already imported, so `com.botmaker.sdk.api.interaction.Keyboard.type(…)` is the wrong output. What that
translator *did* take is `Source.requireMethod`, called directly. **A member whose only candidate caller
refuses it is a member whose shape was guessed**, and the toolkit's own acceptance test — is this a shape or
a vocabulary — does not catch that case, because `call` is plainly a shape. It was simply the wrong one.

**The maintainer asked whether a real method reference could replace the `String` method name, and it
cannot.** `call(Mouse::click)` binds to a functional interface whose *shape matches the method*, so
arbitrary arity means one interface per parameter count. That is exactly `MemberRef` plus `M0`–`M5`, built
for `PaletteCatalog` in `botmaker-studio-api` and deleted on 2026-08-27 — and a method reference still
cannot name a specific overload, which is the other half of what a call-site emitter would need. There is no
arity-free form. Both `Source`'s class javadoc and `CLAUDE.md` record this, because it is a good idea and
will be had again.

`SourceTest` 17 → 18: the `call` cases become `requireMethod` cases (declared-not-inherited, the near-miss
message, the missing type or name), plus one asserting that a name which *does* resolve is accepted — the
case the old suite never had, because `call` returning source implied it. The `Expr` distinction is now
asserted through `newInstance`, which is where it still lives. Module total 52.

### 2026-09-02 — JDK 25 LTS and JavaFX 25.0.4

`jitpack.yml` → `openjdk25`, the pom to `maven.compiler.release` 25 (rather than `source`/`target`, which do
not check against the platform API), `javafx.version` → 25.0.4, CI to `java-version: '25'`. The full account
of the constellation-wide move — including the JitPack probe that proved `openjdk25` exists before anything
was touched — is in `../botmaker-studio-api/ROADMAP.md`, dated the same day.

### 2026-08-28 — the two editor shapes that were written three times

Part F, phase C. Both lifts pass rule 4 the same way: what moved is the **shape**, what stayed is the
**table**.

- **`Editors.tuplePill(ctx, TupleSpec)`, with `Pick` and `Editors.tupleLabel`.** The SDK's `GeometryEditors`
  was one editor written three times — a pill showing the numbers, a way to take them off the screen, a way
  to type them — and it is now three `TupleSpec` constants over one implementation: 145 lines down to 72,
  with no `Pills`, `Modals` or `Slots` call left in it. `Pick` is `REGION`, `POINT`, `MEASURE` or `NONE`;
  every arm is a host capability (`StudioServices.capture()`), never a vocabulary, which is what let the
  whole shape move. What the spec keeps is the part that could not: that a `Rect` is an origin plus a size
  and reads `10, 20  640×480`.
- **`Slots.holdsNumbers` moved with it**, because *is this value coordinates at all, or is it
  `target.center()`* is a question about source text and this class's whole subject is source text. It is
  the check that stops a slot holding `bounds` relabelling itself `0, 0  0×0` — claiming a value the user
  never set.
- **`Editors.program(ctx, prompt)` and `Editors.textSlot(ctx, prompt, columns)`.** Neither named Steam,
  Epic or a game; `LaunchEditors.game()` does, and stays with its cover-art resolution. `textSlot` is the
  gap `text` left: `text` writes through `ctx.set` and so cannot appear in a bot's source at all.
- **The SDK still owns both prompts** — *"Path or command"* and *"launch option (e.g. `--fullscreen`)"* — so
  `program()` and `option()` survive as two-line methods rather than being deleted. The example in a prompt
  is knowledge of what a launch call is passed.
- 39 tests (was 29), the ten new ones over the label and the check, on both the slot and the row side.

### 2026-08-28 — `Source`, and the first dependency a plugin resolves through this module

Part F, phase B. A plugin emits Java whether it means to or not: a `ValueCodec`'s third function returns the
literal a bot will compile, and every slot write is an expression. Nothing owned that concern, so this
project had written **three** escapers for it — `Slots.quote` here, `LiteralWriter.quote` in the SDK, and a
private `quote` in the archetype's generated skeleton — and all three escaped the backslash and the quote
and stopped, so a pasted tab or newline produced source that would not compile.

- **`Source`** — `string`, `character`, `number` (a count reads as `3`, not `3.0`), `enumConstant`,
  `newInstance`, `call`, `type`. `Slots.quote` is now `Source.string` under its old name, and
  `Slots.writeConstructor` composes through `Source.newInstance` — which also fixed a nested type being
  written `Outer$Inner`, legal in neither an expression nor an import.
- **An argument is source, not a value.** `call(Sound.class, "play", string("ding"))`. The alternative — a
  `String` argument meaning text — makes `call(X.class, "m", name)` ambiguous between a variable and a
  literal, and only the caller knows which was meant.
- **JavaPoet (`com.palantir.javapoet`, 106 KB, no transitive dependencies) is the implementation and never
  the interface.** No JavaPoet type appears in a signature here, so it can be replaced without breaking a
  plugin. Taken against this module's standing rule that a dependency becomes every plugin's: the choice was
  never "a dependency or nothing", it was "one implementation or one hand-rolled escaper per plugin".
- **`Source.string` is deliberately *not* JavaPoet's `$S`, and `SourceTest` pins why.** `$S` splits a string
  containing a newline into a concatenation across source lines (`"a\n" + "b"`) — right for a generated
  file, wrong for a slot, where the host writes the result into the middle of an existing line and one slot
  holds one expression. Checked against the real library before the class was written, not assumed.
- The SDK's `LiteralWriter` **keeps its own copy**, and it is now total the same way. The reason is the rule
  rather than an oversight: only the SDK's plugin half may name the toolkit, and `internal/authoring` is
  library code any host reaches. Written in both files so neither reads as a missed cleanup.
- 29 tests (was 18).

### 2026-08-28 — the lift out of the SDK: five classes that were never SDK-specific

Plugin-ecosystem plan, phase 4. The SDK carried ~1300 lines of plugin code, and a good deal of it was
host-plumbing any second plugin would have had to rewrite. Five classes moved and two editors were added; the
acceptance test for each was the module's rule 4, *nothing here may name a plugin's vocabulary*.

- **`Slots`** (189 lines, from `sdk/internal/plugin/editors`). Reads and writes a value that is a Java
  expression in a slot and a row of stored strings in the Parameters window — the question
  `ValueContext.asSlot()` exists to answer, asked here and as far as possible nowhere else. It was in the SDK
  by accident of who wrote the first editor: not a line of it names an SDK type, and every editor that can
  appear in source needs it.
- **`CallSites`** — the four matcher shapes (`firstArgumentOf`, `argumentOf`, `firstArgumentWhere`,
  `trailingArgumentOf`, plus a public `isOn`). The SDK keeps its five *constants*, which name `Game` and
  `BotSettings` and are the part that is genuinely its own; its `CallSites` went from 99 lines to a table of
  five. Every predicate declines a Parameters row, which is the half a plugin author never tests by hand
  because every context in front of them while developing has a call in it.
- **`Editors.boundedPill` + `Editors.flag` + `Editors.NumberRange`** — the pill, dialog, spinner-or-slider
  division, clamping and label out of `SettingsEditors`, which is now the table and nothing else (172 → 84
  lines). The split is exactly rule 4: a *bounded number* is a shape, and "`setDefaultConfidence` runs 0 to
  1" is the SDK's knowledge about its own API.
- **`AbstractStudioPlugin`** — the four contributions, each built once on first use. Honestly stated in its
  own javadoc: this buys `SdkPlugin` almost nothing, since a `static final` catalog was already right there.
  What it buys everyone else is that the build hooks **cannot be fields** — `ServiceLoader` constructs a
  plugin while a project is opening, so reflecting over forty classes in a constructor happens on that path
  whether the answer is ever wanted or not. `SdkPlugin`'s 52-facade `PaletteCatalog.of(…)` became
  `buildCatalog()` for that reason and is genuinely lazier than it was.
- **`Codecs`** — `ValueCodec`s from three lambdas, plus `or` (make a partial parser total) and `seeded`.
  **The SDK deliberately does not use it**, and the reason is worth carrying: `Authoring` reaches
  `SdkValueTypes.CATALOG` on *Studio's own* classpath, and Studio must not depend on this module, so naming a
  toolkit class from there would be a `NoClassDefFoundError` the first time anybody generated a project. The
  SDK is a library *and* a plugin; only its plugin half may name us.
- **`testing.TestContexts`** — a recording `SlotContext`/`ValueContext`, fluent, with `written()`,
  `replacement()`, `enclosingReplacement()` and `writes()`. `writes() == 0` straight after building an editor
  is the assertion for rule 3. It replaced 28 hand-rolled lines in `GeometryLabelTest` and 50 in
  `DurationSourceTest`, which is the argument for it: a plugin author had to write those before they could
  assert anything, and so generally did not.
- **`CallSitesTest`**, 11 cases, covering all four shapes and — the point — both declines: a Parameters row,
  and a slot with no call around it.

Not moved, and worth knowing why: the SDK's five call-site constants, the `Bound` table, `WireText`, and
anything else whose signature or name would have had to say "Steam" or "confidence".

### 2026-08-28 — a CI workflow, and a GitHub Release published by JReleaser

- **`.github/workflows/ci.yml` — this repository's first workflow of any kind.** Three jobs: `build` on
  every push and PR (checking out `botmaker-studio-api` from source, since the pom pins it at
  `0.0.0-SNAPSHOT` and no such artifact is published), `release` and `jitpack` on a `v*` tag. Tests run
  here rather than being skipped: `ValuesTest` is a fraction of a second and holds the rule a plugin author
  depends on most — every reader of a value degrades and never throws.
- **`jreleaser.yml` + `tools/changelog-section.sh`.** The tag publishes the GitHub Release with the
  `## [x.y.z]` section of `CHANGELOG.md` as its body. The extractor is a script in this repository, not in
  the umbrella, because two readers must not be able to disagree about it: the umbrella's `check_changelog`
  gate calls it before anything is tagged, and the workflow calls it for the notes.
- **Why CI and not the umbrella's `release.sh`:** JReleaser cannot open a submodule — `.git` is a `gitdir:`
  FILE there and its JGit reports *repository not found*, while `--git-root-search` resolves the
  **umbrella** repo instead. `jreleaser-maven-plugin` is not a way round it either: it ignores
  `jreleaser.yml` and reads the cosmetic `0.0.0-SNAPSHOT` from the pom. The version comes from the tag as
  `JRELEASER_PROJECT_VERSION`. The build itself is untouched.
- Also found, and now refused in `release.sh`'s decide pass: **this repository has no `origin` remote.**
  Create `LiQiyeDev/botmaker-plugin-toolkit` before any `--plugin-toolkit` release.

### 2026-08-27 — the module exists (plugin platform, phase 12a)

Created as the eighth BotMaker repository, third in the umbrella reactor — after `botmaker-studio-api`,
which it compiles against, and before `botmaker-sdk`, which phase 12 makes its first consumer.

**It is additive.** Nothing was re-homed out of the contract; `botmaker-studio-api` keeps every type it had.
That was the maintainer's constraint when the phase was added mid-plan, and it is also the right shape: the
contract is *interfaces and records, no implementation*, and a widget kit is nothing but implementation.
Keeping them apart is what lets a plugin take a new toolkit without taking a new contract — the contract
must be allowed to version slowly, because a plugin's compiled classes cannot be rewritten by anybody, and a
widget kit will not, because it moves every time an editor does.

**What landed:** `Editors` (six whole editors over a `ValueContext`), `Pills`, `Fields`, `Modals`, `Values`,
`Styles`, `Thumbnail`, and `ValuesTest` (7).

**The four decisions, each taken against a named alternative:**

- **No third-party UI dependency.** ControlsFX was the alternative and was declined: `PropertySheet` is a
  whole-form abstraction and these are bespoke single-value nodes, so it would sit unused beside them while
  every plugin that wanted a slider resolved it. Revisit at phase 13 only with a concrete need — and knowing
  it becomes every plugin's dependency and its JavaFX compatibility becomes ours.
- **A widget takes `ValueContext`.** The alternative was pure JavaFX builders plus callbacks, which keeps
  the toolkit usable outside a plugin and versions independently — and makes every plugin write the same
  wiring. Taking the context is what reduces an SDK `Rect` editor to `Editors::region`.
- **v1 is exactly what the host's own thirteen pickers need**, extracted from them as they read today rather
  than designed. They are the spec even before they move, which is why there are six editors and not
  sixteen. A seventh is worth adding the day a second editor wants it.
- **The plugin's own dependency; Studio never has it.** `compile` scope, resolved onto the plugin's
  classloader. The alternative — Studio ships it, plugins scope it `provided` — makes the toolkit's version
  Studio's to bump, which is exactly the slow-versioning property the contract was split out to get. Two
  plugins are entitled to two toolkit versions.

**Two smaller ones worth not re-litigating:**

- **`Pills.bare(String)` is a separate name rather than a one-argument `menu`.** `menu(label)` would also
  match the `MenuItem...` overload, and which javac picks is not something a reader should have to know.
- **`Editors.choice` keeps a value its option list does not contain**, showing it rather than correcting it.
  A plugin whose option set shrank between releases must not silently rewrite every bot that used the option
  it dropped.

**The one thing it is not:** `botmaker-plugin-processor`. Merging the two was proposed and declined the same
day. The classpath objection turned out to be weaker than it first looked — javac scans the compile
classpath for `META-INF/services/javax.annotation.processing.Processor` only when `-processorpath` is unset,
and `<annotationProcessorPaths>` sets it — but the cadence argument stands: the processor pins nothing, can
be tagged once and left alone for months, and is what the SDK's `.deps.env` names. Merged, every widget fix
would force the SDK to re-pin its processor.

## Deferred / next

- **Phase 12 is the proof.** Thirteen editors move out of Studio and into the SDK on top of this. Until one
  of them is written against it, nothing here has a consumer — and one consumer proves little anyway; the
  real validation is a second plugin.
- **A widget for a bounded *pair*** — the `Precision` editor is two knobs whose meanings differ, and it is
  the one of the thirteen that plainly wants something this kit does not have yet. Deliberately not guessed
  at in advance.
- **`PopOver`.** Several of the host's pickers would read better as a popover than a modal. That is the
  concrete need that would justify the ControlsFX dependency, and it belongs to phase 13, not here.
