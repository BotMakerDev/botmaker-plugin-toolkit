package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.ValueContext;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Whole editors: hand one a {@link ValueContext} and it is done.
 *
 * <p>This is the layer the toolkit exists for. Everything under it — {@link Pills}, {@link Fields},
 * {@link Modals} — is JavaFX with the host's conventions baked in; these read the value, write it back
 * and reach {@link com.botmaker.plugin.api.StudioServices} for capture and dialogs themselves, so a
 * plugin's editor for a rectangle is a predicate and a method reference:
 *
 * <pre>{@code
 * SlotEditor.of(c -> c.type().is(Rect.class), Editors::region)
 * }</pre>
 *
 * <p>They were extracted from the host's own thirteen pickers rather than designed, which is why there are
 * eleven and not sixteen: these are the shapes that recurred. An editor that needs something else builds it
 * from the layer below, and the day a second one wants the same thing it is worth adding here — which is how
 * {@link #boundedPill} and {@link #flag}, and then {@link #tuplePill}, {@link #program} and
 * {@link #textSlot}, arrived out of the SDK on 2026-08-28.
 *
 * <p><b>Some read through {@link Slots} and the rest through {@link ValueContext} directly</b>, and the
 * difference is which places the editor can serve. {@code Slots} spells a value as Java when the context is
 * a slot in a bot's source and as stored text when it is a Parameters row; the plain ones write stored text
 * either way. Reach for {@code Slots} whenever the editor might sit in source — which is why {@link #text}
 * and {@link #textSlot}, and {@link #numbers} and {@link #tuplePill}, are pairs rather than one method each.
 *
 * <p><b>Every one of them writes only when the user acts.</b> Building an editor never sets a value — not
 * even to normalise what is already there — because a project that is merely opened and closed must come
 * back byte-identical.
 */
public final class Editors {

    private Editors() {}

    /**
     * Where the widgets in this class go to ask the user to point at something. See {@link ScreenPicks} for
     * why it is supplied by the plugin rather than by the host, and why a static field is the right shape.
     */
    private static ScreenPicks picks = ScreenPicks.NONE;

    /**
     * Registers the screen picker every editor here uses. Call it once, from the plugin's constructor.
     *
     * <p>Passing {@code null} restores {@link ScreenPicks#NONE} rather than throwing, so a plugin that
     * conditionally has no capture path can say so in one line.
     */
    public static void pickWith(ScreenPicks screenPicks) {
        picks = screenPicks == null ? ScreenPicks.NONE : screenPicks;
    }

    /**
     * A pill over {@code labels.length} whole numbers, edited in a dialog.
     *
     * <p>The shape of {@code Point}, {@code Size} and any other small tuple. The pill shows the numbers
     * comma-separated; the dialog gives each one its own labelled field.
     */
    public static Node numbers(ValueContext ctx, String title, String... labels) {
        int count = labels == null ? 0 : labels.length;
        MenuButton pill = Pills.bare(numbersLabel(ctx, count, title));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Edit values…", () ->
                        Modals.numbers(ctx, title, labels, Values.ints(ctx.value(), count), picked -> {
                            ctx.set(Values.of(picked));
                            pill.setText(numbersLabel(ctx, count, title));
                        }))));
        return pill;
    }

    /**
     * A pill over a screen rectangle: <i>Select on screen…</i>, or the four numbers by hand.
     *
     * <p>The one editor that is worth having even if a plugin ships nothing else: dragging out a region is
     * the pick every other one is a variation of. The overlay behind it is the plugin's own — see
     * {@link ScreenPicks}.
     */
    public static Node region(ValueContext ctx) {
        MenuButton pill = Pills.bare(regionLabel(ctx));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Select on screen…", () -> picks.region(r -> {
                    ctx.set(Values.of(r.x(), r.y(), r.width(), r.height()));
                    pill.setText(regionLabel(ctx));
                })),
                Pills.separator(),
                Pills.item("Edit values…", () -> Modals.numbers(ctx, "Region",
                        new String[]{"x", "y", "width", "height"}, Values.ints(ctx.value(), 4), picked -> {
                            ctx.set(Values.of(picked));
                            pill.setText(regionLabel(ctx));
                        }))));
        return pill;
    }

    /**
     * A slider and read-out for a bounded fractional number.
     *
     * <p>Writes continuously as the slider moves — see {@link Fields#bounded}, and the contract's note that
     * calling {@code set} repeatedly is expected.
     */
    public static Node bounded(ValueContext ctx, double min, double max, double step) {
        double current = Values.doubleAt(ctx.value(), 0, min);
        return Fields.bounded(current, min, max, step, v -> ctx.set(trim(v)));
    }

    /** A text field that commits on Enter and on losing focus. */
    public static Node text(ValueContext ctx, String prompt) {
        return Fields.committing(ctx.single(), prompt, ctx::set);
    }

    /**
     * A dropdown over a fixed set.
     *
     * <p>A value the list does not contain is <b>kept and shown</b> rather than corrected: it is what the
     * project file holds, and a plugin whose option set shrank between releases must not silently rewrite
     * every bot that used the option it dropped.
     */
    public static Node choice(ValueContext ctx, List<String> options) {
        List<String> items = new ArrayList<>(options == null ? List.of() : options);
        String current = ctx.single();
        if (!current.isBlank() && !items.contains(current)) items.add(current);

        ComboBox<String> box = Styles.on(new ComboBox<>(), Styles.INSET_FIELD_FLAT);
        box.getItems().setAll(items);
        if (!current.isBlank()) box.setValue(current);
        box.valueProperty().addListener((obs, was, now) -> {
            if (now != null && !now.equals(was)) ctx.set(now);
        });
        return box;
    }

    /**
     * A dropdown over a set that moves, over a value that may be a slot — {@link #choice}'s counterpart on
     * the {@link Slots} side, and {@link #textSlot}'s with a list of suggestions attached.
     *
     * <p>Three differences from {@link #choice}, each of them the reason this exists:
     *
     * <ul>
     *   <li><b>It writes through {@link Slots}</b>, so the value is a Java string literal in a bot's source
     *       and the characters themselves in a Parameters row.</li>
     *   <li><b>{@code options} is a {@link Supplier} read when the list is opened</b>, never when the node is
     *       built. What there is to choose from changes while a block is on screen — a name added in another
     *       window a moment ago — and a list read at render time is the list as it was when the block first
     *       appeared. Same rule as {@link #gallery}.</li>
     *   <li><b>The box is editable</b>, so a name that is not on the list yet can still be typed. A value
     *       naming something that does not exist is a real state and frequently a deliberate one — the code
     *       written before the thing it names — so the editor must be able to say it. Typing commits on Enter
     *       and on losing focus, for the reason {@link Fields#committing} exists.</li>
     * </ul>
     *
     * <p>Whatever is already in the value is kept and shown even when the supplier does not offer it, which
     * is {@link #choice}'s rule and matters more here: the list is the project's current state and the value
     * is what somebody wrote, and correcting the second to match the first would silently edit a bot.
     *
     * @param prompt what the empty box says — the place to name what the list holds
     */
    public static Node choiceSlot(ValueContext ctx, Supplier<List<String>> options, String prompt) {
        ComboBox<String> box = Styles.on(new ComboBox<>(), Styles.INSET_FIELD_FLAT);
        box.setEditable(true);
        box.setPromptText(prompt);

        String literal = Slots.stringLiteral(Slots.raw(ctx));
        String current = literal != null ? literal : ctx.single();
        if (current != null && !current.isBlank()) box.setValue(current);

        box.setOnShowing(event -> {
            List<String> items = new ArrayList<>(options == null ? List.of() : options.get());
            String now = box.getValue();
            if (now != null && !now.isBlank() && !items.contains(now)) items.add(now);
            box.getItems().setAll(items);
        });
        box.valueProperty().addListener((obs, was, now) -> {
            if (now != null && !now.isBlank() && !now.equals(was)) Slots.writeText(ctx, now);
        });
        // An editable ComboBox commits its editor to valueProperty on Enter and on nothing else, so clicking
        // away from a typed name would lose it — the same edit a bare TextField loses, and the reason
        // Fields.committing exists.
        box.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) return;
            String typed = box.getEditor() == null ? null : box.getEditor().getText();
            if (typed != null && !typed.isBlank()) box.setValue(typed.trim());
        });
        return box;
    }

    /**
     * A pill opening a grid of pictures.
     *
     * <p>{@code items} is a {@link Supplier} and is called when the pill is opened, never when it is built:
     * what there is to choose from moves — a template captured a moment ago, an emulator that just started —
     * and a list read at render time is the list as it was when the block first appeared.
     */
    public static Node gallery(ValueContext ctx, String title, Supplier<List<Thumbnail>> items,
                               String emptyMessage) {
        MenuButton pill = Pills.bare(Values.labelOr(ctx.single(), "Choose…"));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Choose…", () -> Modals.chooser(ctx, title,
                        items == null ? List.of() : items.get(), emptyMessage, picked -> {
                            ctx.set(picked.value());
                            pill.setText(Values.labelOr(picked.label(), "Choose…"));
                        })),
                Pills.separator(),
                Pills.item("Clear", () -> {
                    ctx.set(List.of());
                    pill.setText("Choose…");
                })));
        return pill;
    }

    /**
     * A number that has a range, and the words that make the range mean something.
     *
     * <p>Every field here exists because a bare number does not say it. {@code 0.8} means something only once
     * you can see where it sits between 0 and 1; {@code 500} means something only next to the fact that it is
     * milliseconds. That is the whole argument for {@link #boundedPill}: a free-typed literal states neither,
     * and accepts {@code 80} for a fraction — a value the library will clamp and the author will never hear
     * about.
     *
     * @param label    the value's name as a person would say it, not as the method spells it
     * @param prompt   the sentence above the control, which is where the unit and the limit are stated
     * @param unit     appended to the pill's own text, so a delay reads {@code 500 ms} on the block itself
     * @param whole    a count (spinner, typed) rather than a fraction (slider, found)
     * @param fallback what an unreadable or absent value opens on — never 0 unless 0 is genuinely the default
     */
    public record NumberRange(String label, String prompt, String unit, boolean whole,
                              double min, double max, double step, double fallback) {}

    /**
     * A pill over a bounded number, edited in a dialog and committed on <i>OK</i>.
     *
     * <p>Whole counts get a spinner and fractions get a slider, which is {@link Fields}' own division and the
     * reason it draws them differently: 500 milliseconds is a quantity a person types, while 0.8 confidence is
     * a position a person finds. Unlike {@link #bounded}, the value is <b>not</b> written while dragging —
     * this shape is for a slot in a bot's source, and a slider that rewrote the file on every pixel of the
     * drag would fill the undo stack with values nobody chose.
     *
     * <p>Reads and writes through {@link Slots}, so it serves a slot and a Parameters row alike; whether the
     * editor is <em>offered</em> in both is the predicate's business, not this one's.
     */
    public static Node boundedPill(ValueContext ctx, NumberRange range) {
        MenuButton pill = Pills.bare(rangeLabel(ctx, range));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Set " + range.label().toLowerCase() + "…", () -> {
                    double current = rangeCurrent(ctx, range);
                    if (range.whole()) {
                        Spinner<Integer> spinner = Fields.integer((int) Math.round(current),
                                (int) range.min(), (int) range.max());
                        Modals.form(ctx, range.label(), rangeBody(range, spinner), () -> {
                            commitRange(ctx, range, spinner.getValue());
                            pill.setText(rangeLabel(ctx, range));
                        });
                    } else {
                        double[] picked = {current};
                        HBox slider = Fields.bounded(current, range.min(), range.max(), range.step(),
                                value -> picked[0] = value);
                        Modals.form(ctx, range.label(), rangeBody(range, slider), () -> {
                            commitRange(ctx, range, picked[0]);
                            pill.setText(rangeLabel(ctx, range));
                        });
                    }
                })));
        return pill;
    }

    /**
     * A yes/no value, written the moment it is ticked.
     *
     * <p>No modal and no OK: there is nothing to get wrong about a checkbox, and a window asking a person to
     * confirm the tick they just made is a window. It carries {@code label} because {@code enableDebug(true)}
     * beside a bare box reads as though the box is the argument to something else — which, without the label,
     * is exactly what it looks like.
     */
    public static Node flag(ValueContext ctx, String label) {
        CheckBox box = new CheckBox(label);
        box.setSelected(Boolean.parseBoolean(Slots.raw(ctx)));
        box.setOnAction(e -> Slots.write(ctx, Boolean.toString(box.isSelected()),
                Boolean.toString(box.isSelected())));
        return box;
    }

    /**
     * How the numbers of a {@link TupleSpec} are taken off the screen.
     *
     * <p>Every arm is a {@link ScreenPicks} call and not a plugin's vocabulary, which is why the whole shape
     * could move here. (It was a host capability until 2026-08-31, when the overlay went to the plugins; the
     * arms did not change, only who draws them.) What differs between them is only
     * which of the four numbers a drag or a click yields, and the word for the action: you <i>select</i> a
     * region, <i>pick</i> a pixel and <i>measure</i> a thing whose position does not matter.
     */
    public enum Pick {

        /** Drag a rectangle; writes {@code x, y, width, height}. */
        REGION("Select on screen…"),

        /** Click one pixel under a magnifier; writes {@code x, y}. */
        POINT("Pick on screen…"),

        /** Drag a rectangle and throw the origin away; writes {@code width, height}. */
        MEASURE("Measure on screen…"),

        /** No on-screen arm at all — the numbers are only ever typed. */
        NONE(null);

        private final String item;

        Pick(String item) {
            this.item = item;
        }

        /** The menu entry's wording, or {@code null} for {@link #NONE}. */
        public String item() {
            return item;
        }
    }

    /**
     * A small tuple of whole numbers that is also a thing on the screen.
     *
     * <p>The shape behind every coordinate editor: a pill showing the numbers, a way to take them off the
     * screen, and a way to type them. Taking them off the screen is what it exists for — nobody knows that a
     * health bar is 240 pixels wide, they know where its ends are.
     *
     * @param type        the class the slot's expression constructs; the same class is passed as the import
     * @param title       the value's name, used as the dialog's title and in the empty pill's placeholder
     * @param labels      one per number, in constructor order — this is also how many numbers there are
     * @param placeholder what the pill says with nothing chosen yet
     * @param pick        how the numbers come off the screen, if they can
     * @param label       the numbers as the pill spells them, which is the part every tuple words differently
     */
    public record TupleSpec(Class<?> type, String title, String[] labels, String placeholder, Pick pick,
                            Function<int[], String> label) {}

    /**
     * A pill over a {@link TupleSpec}: the numbers, a screen picker and a typed dialog.
     *
     * <p>Reads and writes through {@link Slots}, so one editor serves a slot in a bot's source and a row of
     * the Parameters window; the slot gets {@code new Rect(12, 40, 300, 80)} and the row gets four strings.
     *
     * <p>Distinct from {@link #numbers}, which is the same idea without a screen and without a constructor:
     * that one writes stored values through {@code ctx.set} and cannot appear in source.
     */
    public static Node tuplePill(ValueContext ctx, TupleSpec spec) {
        MenuButton pill = Pills.bare(tupleLabel(ctx, spec));
        Pills.onOpen(pill, () -> {
            List<javafx.scene.control.MenuItem> items = new ArrayList<>();
            if (spec.pick() != Pick.NONE) {
                items.add(Pills.item(spec.pick().item(), () -> pickTuple(ctx, spec, pill)));
                items.add(Pills.separator());
            }
            items.add(Pills.item("Edit values…", () -> Modals.numbers(ctx, spec.title(), spec.labels(),
                    Slots.ints(ctx, spec.labels().length), picked -> {
                        Slots.writeConstructor(ctx, spec.type(), picked);
                        pill.setText(tupleLabel(ctx, spec));
                    })));
            return items;
        });
        return pill;
    }

    /**
     * What the collapsed pill says — the placeholder, the formatted numbers, or the source text verbatim.
     *
     * <p>Public because it is the one piece of a tuple editor that can be asserted with no JavaFX toolkit,
     * and it is the piece worth asserting: the number a user reads off the pill is read back out of what the
     * last pick wrote, and getting it wrong shows one coordinate while the bot runs another.
     */
    public static String tupleLabel(ValueContext ctx, TupleSpec spec) {
        if (Slots.isEmpty(ctx)) return spec.placeholder();
        int count = spec.labels().length;
        return Slots.holdsNumbers(ctx, count)
                ? spec.label().apply(Slots.ints(ctx, count))
                : Slots.raw(ctx);
    }

    private static void pickTuple(ValueContext ctx, TupleSpec spec, MenuButton pill) {
        switch (spec.pick()) {
            case REGION -> picks.region(r -> {
                Slots.writeConstructor(ctx, spec.type(), r.x(), r.y(), r.width(), r.height());
                pill.setText(tupleLabel(ctx, spec));
            });
            case MEASURE -> picks.region(r -> {
                Slots.writeConstructor(ctx, spec.type(), r.width(), r.height());
                pill.setText(tupleLabel(ctx, spec));
            });
            case POINT -> picks.point(p -> {
                Slots.writeConstructor(ctx, spec.type(), p.x(), p.y());
                pill.setText(tupleLabel(ctx, spec));
            });
            case NONE -> { }
        }
    }

    /**
     * A pill over a path to a program: the OS file chooser, or a typed path.
     *
     * <p>Typed matters as much as browsed: what a call launches is frequently a command that is not a file on
     * this machine at all, and a chooser alone would make those unsayable. Browsing goes through
     * {@link Modals#program}, which is where the "a native dialog blocks its thread" trap is answered once
     * for every plugin rather than once per editor.
     *
     * <p>The label is the file's own name and not the whole path: a slot on a block is a few centimetres
     * wide, and {@code C:\Program Files (x86)\…\game.exe} elided in the middle says less than
     * {@code game.exe}.
     *
     * @param prompt the typed field's prompt — the place to say what a command is allowed to look like
     */
    public static Node program(ValueContext ctx, String prompt) {
        MenuButton pill = Pills.bare(fileLabel(Slots.stringLiteral(Slots.raw(ctx))));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Browse for program…", () -> Modals.program(ctx,
                        parentOf(Slots.stringLiteral(Slots.raw(ctx))), path -> {
                            Slots.writeText(ctx, path.toString());
                            pill.setText(fileLabel(path.toString()));
                        })),
                Pills.separator(),
                Pills.item("Enter path…", () -> {
                    String now = Slots.stringLiteral(Slots.raw(ctx));
                    TextField field = Fields.committing(now == null ? "" : now, prompt, null);
                    field.setPrefColumnCount(40);
                    Modals.form(ctx, "Program path", field, () -> {
                        String typed = field.getText() == null ? "" : field.getText().trim();
                        if (typed.isEmpty()) return;
                        Slots.writeText(ctx, typed);
                        pill.setText(fileLabel(typed));
                    });
                })));
        return pill;
    }

    /**
     * A text field over a value that may be a slot — {@link #text}'s counterpart on the {@link Slots} side.
     *
     * <p>{@code text} writes through {@code ctx.set}, which stores the characters themselves; this writes a
     * Java string literal when the value is a slot in a bot's source, and the same characters when it is a
     * Parameters row. An editor that might sit in source wants this one.
     */
    public static Node textSlot(ValueContext ctx, String prompt, int columns) {
        String current = Slots.stringLiteral(Slots.raw(ctx));
        TextField field = Fields.committing(current == null ? "" : current, prompt,
                typed -> Slots.writeText(ctx, typed));
        if (columns > 0) field.setPrefColumnCount(columns);
        return field;
    }

    /** The folder a path sits in, for the chooser to open on; null when there is no usable path yet. */
    private static Path parentOf(String path) {
        try {
            return path == null || path.isBlank() ? null : Path.of(path).getParent();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String fileLabel(String path) {
        if (path == null || path.isBlank()) return "Choose program…";
        int slash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
    }

    private static VBox rangeBody(NumberRange range, Node control) {
        return new VBox(6, Styles.on(new Label(range.prompt()), Styles.CAPTION), control);
    }

    /** Writes the number the way a person would have typed it: a count as a count, a fraction as a decimal. */
    private static void commitRange(ValueContext ctx, NumberRange range, double value) {
        double clamped = Math.clamp(value, range.min(), range.max());
        String literal = range.whole()
                ? Long.toString(Math.round(clamped))
                : BigDecimal.valueOf(clamped).setScale(3, RoundingMode.HALF_UP)
                        .stripTrailingZeros().toPlainString();
        Slots.write(ctx, literal, literal);
    }

    /**
     * The number in the value, or the range's own default.
     *
     * <p>Falling back to the default rather than to zero matters: opening the editor on a slot holding a
     * variable and pressing OK would otherwise write {@code 0}, which for a confidence means "match anything".
     */
    private static double rangeCurrent(ValueContext ctx, NumberRange range) {
        try {
            String raw = Slots.raw(ctx).replace("_", "").replaceAll("[lLdDfF]$", "");
            return raw.isBlank() ? range.fallback() : Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return range.fallback();
        }
    }

    /** What the pill says: the value as written, plus the unit — or the source text when it is not a number. */
    private static String rangeLabel(ValueContext ctx, NumberRange range) {
        String raw = Slots.raw(ctx);
        if (raw.isBlank()) return range.label() + "…";
        try {
            Double.parseDouble(raw.replace("_", "").replaceAll("[lLdDfF]$", ""));
            return raw + range.unit();
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /** {@code 12, 34} — or the placeholder, when nothing has been chosen. */
    private static String numbersLabel(ValueContext ctx, int count, String title) {
        if (Values.isBlank(ctx.value())) return "Set " + title.toLowerCase() + "…";
        int[] v = Values.ints(ctx.value(), count);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < v.length; i++) out.append(i == 0 ? "" : ", ").append(v[i]);
        return out.toString();
    }

    /** {@code 12, 34  800×600} — the reading order of a {@link Region}, position then size. */
    private static String regionLabel(ValueContext ctx) {
        if (Values.isBlank(ctx.value())) return "Choose region…";
        int[] v = Values.ints(ctx.value(), 4);
        return v[0] + ", " + v[1] + "  " + v[2] + "×" + v[3];
    }

    /** A whole number reads as one — {@code "3"}, not {@code "3.0"} — because that is what a bot's source wants. */
    private static String trim(double value) {
        return value == Math.rint(value)
                ? Long.toString(Math.round(value))
                : Double.toString(value);
    }
}
