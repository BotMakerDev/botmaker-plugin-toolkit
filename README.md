# botmaker-plugin-toolkit

The widgets a **BotMaker Studio plugin** would otherwise hand-roll before it could write its first slot
editor.

`botmaker-studio-api` says *what* a plugin contributes. This says *what it looks like* — and, more usefully,
it already knows the things about the host that a plugin author has no way to discover: which style classes
the host's stylesheet actually names, that a dialog must be themed and owned or it appears behind the editor
as unstyled JavaFX, and that a text field which commits only on Enter silently loses most edits.

## Using it

```xml
<dependency>
    <groupId>com.github.LiQiyeDev</groupId>
    <artifactId>botmaker-plugin-toolkit</artifactId>
    <version>v0.1.0</version>
</dependency>
```

Ordinary `compile` scope — it is resolved onto your plugin's own classloader, so your toolkit version is
yours and no other plugin's. `botmaker-studio-api` and `javafx-controls` are `provided` here; you already
have both.

Then an editor is a predicate and a method reference:

```java
@Override
public List<SlotEditor> slotEditors() {
    return List.of(
            SlotEditor.of(c -> c.type().is(Rect.class), Editors::region),
            SlotEditor.of(c -> c.type().is(Point.class), c -> Editors.numbers(c, "Point", "x", "y")),
            SlotEditor.of(c -> c.type().is(Duration.class), c -> Editors.bounded(c, 0, 30_000, 100)));
}
```

The same editor serves a slot in a bot's source **and** a row of the Parameters window, because both arrive
as a `ValueContext`.

## What is in it

| | |
|---|---|
| `Editors` | whole editors — `tuplePill`, `bounded`, `boundedPill`, `flag`, `text`, `program`, `choice`, `choiceSlot`, `gallery`. Take a `ValueContext`, need nothing else. |
| `Pills` | the summary-plus-menu control nine of the host's own thirteen pickers are. |
| `Fields` | a text field that commits on Enter *and* on blur; a clamped spinner; a slider with a read-out. |
| `Modals` | a themed, correctly-owned numbers dialog and thumbnail chooser. |
| `Values` | reading a JDK value off a `ValueContext` with a fallback, and `labelOr`. Degrades, never throws. |
| `Styles` | the host's style-class names, as constants. |
| `Thumbnail` | one cell of a picture chooser. |
| `Slots` | the source of a value nothing can decode (`raw`, `isEmpty`), and `arguments` for an editor that rewrites the enclosing call. |
| `Source` | Java a **user pastes**, spelled correctly: a string literal and a type name. |
| `AbstractPluginType` | a `PluginType` that holds its class, plus `whole`/`text`/… helpers for reading `build`'s parts. |
| `AbstractStudioPlugin` | a `StudioPlugin` that builds each contribution once, on first use. |
| `testing.TestContexts` | a recording context, so an editor **and its predicate** can be unit-tested. |

**No editor here writes Java into a bot.** An editor is handed the value and hands one back; the host
spells it. `Source` is for the one place a plugin still produces text a person will read — Java it offers
to be pasted, as the SDK's macro recorder does.

`CallSites` and `Codecs` were here until 2026-09-22: `CallSites` is the contract's `SlotEditor.forCall`,
and `Codecs` went with `ValueCodec`.

## What is deliberately not in it

- **No UI dependency.** ControlsFX was weighed and declined — `PropertySheet` is a whole-form abstraction
  and these are bespoke single-value nodes.
- **No resolved dependency at all** since 2026-09-22. JavaPoet was the one, behind `Source`, and went when
  nothing here spelled a value any more.
- **No form or validation layer.** These were extracted from the host's own pickers because they recurred;
  the next one is worth adding the day a second editor wants it, not before.

## Building

```bash
mvn test        # ValuesTest, SourceTest, SlotRunTest, TupleLabelTest
mvn install     # com.github.LiQiyeDev:botmaker-plugin-toolkit:0.0.0-SNAPSHOT
```

Published through JitPack, which serves each git tag under `com.github.LiQiyeDev` regardless of this pom's
`groupId`/`version`. Releases are cut from the umbrella with `../release.sh --plugin-toolkit <version>`.
