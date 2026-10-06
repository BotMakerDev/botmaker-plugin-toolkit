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
    <groupId>com.github.BotMakerDev</groupId>
    <artifactId>botmaker-plugin-toolkit</artifactId>
    <version>v0.1.0</version>
</dependency>
```

Ordinary `compile` scope — it is resolved onto your plugin's own classloader, so your toolkit version is
yours and no other plugin's. `botmaker-studio-api` and `javafx-controls` are `provided` here; you already
have both.

Then an editor is a method that takes a `ValueContext` and answers a `Node`, claimed by the contract's steps:

```java
// in the plugin's declaration: .editors(() -> List.of(
//         SlotEditor.onParameter(Greetee.class).draw(() -> MyEditors::who)))

public static Node who(ValueContext ctx) {
    return Editors.text(ctx, "who to greet");
}
```

The same editor serves a slot in a bot's source **and** a row of the Parameters window, because both arrive
as a `ValueContext`.

## What is in it

| | |
|---|---|
| `Editors` | whole editors — `tuplePill`, `boundedPill`, `flag`, `text`, `program`, `choiceSlot`. Take a `ValueContext`, need nothing else. |
| `Pills` | the pill (`bare` + `onOpen`, `button`, `icon`) nine of the host's own thirteen pickers were. |
| `Fields` | a text field that commits on Enter *and* on blur; `stepped`, ▲/▼ and the arrow keys and wheel over a field. |
| `Modals` | a themed, correctly-owned window: `form` (any body, OK/Cancel), `gallery` (a searchable picture grid scanned off the FX thread), `owner`. |
| `Values` | reading a JDK value off a `ValueContext` with a fallback, and `labelOr`. Degrades, never throws. |
| `Styles` | the contract's style-class names, applied. |
| `Thumbnail` | one cell of a gallery. |
| `Slots` | the source of a value nothing can decode: `raw`, `isEmpty`, `sourceOr`. |
| `ScreenPicks`, `Region` | the screen pick a tuple pill asks its plugin for. |
| `ZoomPan` | Ctrl+scroll zoom and middle-drag pan over a pane. |
| `ManagedHandle` | one `@Managed` value, read and written from a plugin's own window. |
| `ManagedSet` | one `@Managed` open set: its constants listed, read, added, renamed, repointed and removed. |
| `testing.TestContexts` | a recording context, so an editor **and its predicate** can be unit-tested. |

**No editor here writes Java into a bot.** An editor is handed the value and hands one back; the host
spells it.

`CallSites` and `Codecs` went on 2026-09-22, `Source` on 2026-09-23, and every member no plugin called on
2026-09-28 (`CLAUDE.md`, *the audit*).

## What is deliberately not in it

- **No UI dependency.** ControlsFX was weighed and declined — `PropertySheet` is a whole-form abstraction
  and these are bespoke single-value nodes.
- **No resolved dependency at all** since 2026-09-22. JavaPoet was the one, and went when nothing here
  spelled a value any more.
- **No form or validation layer.** These were extracted from the host's own pickers because they recurred;
  the next one is worth adding the day a second editor wants it, not before.

## Building

```bash
mvn test        # ValuesTest, SlotRunTest, TupleLabelTest, TupleFieldsTest, ManagedHandleTest, …
mvn install     # com.github.BotMakerDev:botmaker-plugin-toolkit at the pom's main -SNAPSHOT
```

Published through JitPack, which serves each git tag under `com.github.BotMakerDev` regardless of this pom's
`groupId`/`version`. Releases are cut from the umbrella with `../release.sh --plugin-toolkit <version>`.
