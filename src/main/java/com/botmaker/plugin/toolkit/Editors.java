package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.ComponentType;
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
 * and reach {@link com.botmaker.plugin.api.StudioServices} for dialogs themselves, so a plugin's editor for
 * a rectangle is one method reference on its own type declaration:
 *
 * <pre>{@code
 * public Node editor(ValueContext ctx) { return Editors.tuplePill(ctx, this, RECT, picks); }
 * }</pre>
 *
 * <p>They were extracted from the host's own thirteen pickers rather than designed, which is why there are
 * nine and not sixteen: these are the shapes that recurred.
 *
 * <h2>They read values, not source text (2026-09-22)</h2>
 *
 * <p>Every one of them used to go through {@code Slots}, which parsed the Java in the slot: a numeric
 * literal stripper here, an argument splitter there, a string unescaper in a third place, none of them
 * agreeing with the host's. A value crosses as a value now, so {@link ValueContext#value} is the read and
 * {@link ValueContext#set(Object)} is the write, and the pairs that existed only to span the two encodings
 * — {@code text}/{@code textSlot}, {@code numbers}/{@code tuplePill} — collapse into one method each.
 *
 * <p><b>An undecodable value is shown, never overwritten.</b> {@link ValueContext#value} answers empty for
 * a variable, a computed expression, a call from somewhere else; a pill then says what
 * {@link Slots#raw} holds. That is the honest label, and it is why {@code Slots.holdsNumbers} is not
 * missed: the question it asked is the question {@code value()} answers.
 *
 * <p><b>Every one of them writes only when the user acts.</b> Building an editor never sets a value — not
 * even to normalise what is already there — because a project that is merely opened and closed must come
 * back byte-identical.
 */
public final class Editors {

    private Editors() {}

    /**
     * A slider and read-out for a bounded fractional number.
     *
     * <p>Writes continuously as the slider moves — see {@link Fields#bounded}, and the contract's note that
     * calling {@code set} repeatedly is expected. The number is written back as the type the field is
     * declared as; see {@link Values#setNumber}.
     */
    public static Node bounded(ValueContext ctx, double min, double max, double step) {
        double current = Values.number(ctx, min);
        return Fields.bounded(current, min, max, step, value -> Values.setNumber(ctx, value));
    }

    /**
     * A text field that commits on Enter and on losing focus.
     *
     * <p>The value is {@code "gold.png"} in the file and {@code gold.png} in the box. There were two of
     * these until 2026-09-20 and again until 2026-09-22 — one writing characters into a Parameters row,
     * one writing a literal into a slot — and the host writes the literal now, so there is one.
     *
     * @param columns the field's width in characters, or {@code 0} for the default
     */
    public static Node text(ValueContext ctx, String prompt, int columns) {
        TextField field = Fields.committing(Values.text(ctx, ""), prompt, ctx::set);
        if (columns > 0) field.setPrefColumnCount(columns);
        return field;
    }

    /** {@link #text(ValueContext, String, int)} at the default width. */
    public static Node text(ValueContext ctx, String prompt) {
        return text(ctx, prompt, 0);
    }

    /**
     * A dropdown over a fixed set.
     *
     * <p>A value the list does not contain is <b>kept and shown</b> rather than corrected: it is what the
     * bot's source holds, and a plugin whose option set shrank between releases must not silently rewrite
     * every bot that used the option it dropped.
     */
    public static Node choice(ValueContext ctx, List<String> options) {
        List<String> items = new ArrayList<>(options == null ? List.of() : options);
        String current = Values.text(ctx, "");
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
     * A dropdown over a set that moves — {@link #choice} with a supplier and a typeable box.
     *
     * <p>Two differences from {@link #choice}, each of them the reason this exists:
     *
     * <ul>
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

        String current = Values.text(ctx, "");
        if (!current.isBlank()) box.setValue(current);

        box.setOnShowing(event -> {
            List<String> items = new ArrayList<>(options == null ? List.of() : options.get());
            String now = box.getValue();
            if (now != null && !now.isBlank() && !items.contains(now)) items.add(now);
            box.getItems().setAll(items);
        });
        box.valueProperty().addListener((obs, was, now) -> {
            if (now != null && !now.isBlank() && !now.equals(was)) ctx.set(now);
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
     *
     * <p>A {@link Thumbnail}'s {@code value} is the Java expression written into the bot's source, so an item
     * naming a picture carries {@code Pictures.ORE} rather than {@code ore}. It goes through
     * {@link ValueContext#setSource(String, Class...)} for that reason — it is a reference to something the bot
     * declares, not a value this editor holds.
     */
    public static Node gallery(ValueContext ctx, String title, Supplier<List<Thumbnail>> items,
                               String emptyMessage) {
        MenuButton pill = Pills.bare(Values.labelOr(Slots.raw(ctx), "Choose…"));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Choose…", () -> Modals.chooser(ctx, title,
                        items == null ? List.of() : items.get(), emptyMessage, picked -> {
                            ctx.setSource(picked.value());
                            pill.setText(Values.labelOr(picked.label(), "Choose…"));
                        })),
                Pills.separator(),
                Pills.item("Clear", () -> {
                    ctx.setSource("");
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
     */
    public static Node boundedPill(ValueContext ctx, NumberRange range) {
        MenuButton pill = Pills.bare(rangeLabel(ctx, range));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Set " + range.label().toLowerCase() + "…", () -> {
                    double current = Values.number(ctx, range.fallback());
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
     *
     * <p>It wrote {@code Slots.write(ctx, "true", "true")} until 2026-09-22, and the second {@code "true"}
     * landed in the varargs tail that meant <em>imports needed</em> — a leftover from the {@code storedForm}
     * parameter deleted two days earlier, asking the host for an import of a class called {@code true}.
     * {@link ValueContext#set(Object)} has no tail for it to fall into.
     */
    public static Node flag(ValueContext ctx, String label) {
        CheckBox box = new CheckBox(label);
        box.setSelected(Values.flag(ctx, false));
        box.setOnAction(e -> ctx.set(box.isSelected()));
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
     * The words around a tuple of whole numbers — everything about the editor that is not the type.
     *
     * <p><b>It carried the type and the number of numbers until 2026-09-22</b>, as a {@code Class<?>} and a
     * {@code labels.length}, and a third arity was implied by whichever {@code Slots.writeConstructor} call
     * the pick arm made. Three independent statements of one fact, with nothing checking them: a
     * {@code labels} array one short of the constructor wrote a {@code Rect} with three arguments.
     * {@link #tuplePill} takes the type as a {@link ComponentType} now, so the arity is
     * {@code componentTypes().size()} and there is one of it. Labels that do not match are padded or
     * ignored rather than deciding anything.
     *
     * @param title       the value's name, used as the dialog's title and in the empty pill's placeholder
     * @param labels      one per number, in component order — naming only
     * @param placeholder what the pill says with nothing chosen yet
     * @param pick        how the numbers come off the screen, if they can
     * @param label       the numbers as the pill spells them, which is the part every tuple words differently
     */
    public record TupleSpec(String title, String[] labels, String placeholder, Pick pick,
                            Function<int[], String> label) {}

    /**
     * A pill over a tuple of whole numbers that is also a thing on the screen: the numbers, a screen picker
     * and a typed dialog.
     *
     * <p>The shape behind every coordinate editor. Taking the numbers off the screen is what it exists for —
     * nobody knows that a health bar is 240 pixels wide, they know where its ends are.
     *
     * <p>One editor serves a slot in a bot's source, a row of the Parameters window and a {@code @Managed}
     * value: the value crosses as a value and the host spells it, so there is nothing left for the editor to
     * know about where it is being shown.
     *
     * @param type  the plugin's own declaration of the type — where the arity, the components and the way
     *              back from numbers to a value all come from
     * @param picks where a screen pick goes; {@link ScreenPicks#NONE} for a plugin with no capture path
     */
    public static <T> Node tuplePill(ValueContext ctx, ComponentType<T> type, TupleSpec spec,
                                     ScreenPicks picks) {
        MenuButton pill = Pills.bare(tupleLabel(ctx, type, spec));
        ScreenPicks picker = picks == null ? ScreenPicks.NONE : picks;
        Pills.onOpen(pill, () -> {
            List<javafx.scene.control.MenuItem> items = new ArrayList<>();
            if (spec.pick() != Pick.NONE) {
                items.add(Pills.item(spec.pick().item(), () -> pickTuple(ctx, type, spec, pill, picker)));
                items.add(Pills.separator());
            }
            items.add(Pills.item("Edit values…", () -> Modals.numbers(ctx, spec.title(),
                    labels(type, spec), numbers(ctx, type), picked -> {
                        write(ctx, type, picked);
                        pill.setText(tupleLabel(ctx, type, spec));
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
     *
     * <p>The three answers correspond exactly to the three states of {@link ValueContext#value}: a value
     * this type describes, an expression it does not — {@code target.center()}, shown as written because
     * rewriting it into {@code 0, 0} would be a lie about what the bot does — and nothing written at all.
     *
     * <p><b>The value is asked for first, and the order is the fix rather than a preference.</b> This
     * checked {@code Slots.isEmpty(ctx)} before asking, so a context that holds a decoded value but no
     * source text answered the placeholder — which reads to a user as "nothing chosen" over a value they
     * had just picked. A value is the authoritative answer wherever there is one; source is what is left
     * when there is not.
     */
    public static <T> String tupleLabel(ValueContext ctx, ComponentType<T> type, TupleSpec spec) {
        return ctx.value(type.type())
                .map(value -> spec.label().apply(ints(type.components(value))))
                .orElseGet(() -> Slots.isEmpty(ctx) ? spec.placeholder() : Slots.raw(ctx));
    }

    /** The numbers currently in the value, or zeroes — the shape every geometry dialog opens on. */
    private static <T> int[] numbers(ValueContext ctx, ComponentType<T> type) {
        return ctx.value(type.type())
                .map(value -> ints(type.components(value)))
                .orElseGet(() -> new int[type.componentTypes().size()]);
    }

    /** {@code components} as whole numbers, anything that is not one as {@code 0}. */
    private static int[] ints(List<Object> components) {
        int[] out = new int[components.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = components.get(i) instanceof Number n ? (int) Math.round(n.doubleValue()) : 0;
        }
        return out;
    }

    /** {@code labels} padded or truncated to the type's own arity, which is the only arity there is. */
    private static <T> String[] labels(ComponentType<T> type, TupleSpec spec) {
        int arity = type.componentTypes().size();
        String[] given = spec.labels() == null ? new String[0] : spec.labels();
        if (given.length == arity) return given;
        String[] out = new String[arity];
        for (int i = 0; i < arity; i++) out[i] = i < given.length ? given[i] : "#" + (i + 1);
        return out;
    }

    /** Builds a value out of {@code numbers} through the plugin's own type and writes it. */
    private static <T> void write(ValueContext ctx, ComponentType<T> type, int... numbers) {
        List<Object> components = new ArrayList<>(numbers.length);
        for (int number : numbers) components.add(number);
        ctx.set(type.build(components));
    }

    private static <T> void pickTuple(ValueContext ctx, ComponentType<T> type, TupleSpec spec,
                                      MenuButton pill, ScreenPicks picks) {
        switch (spec.pick()) {
            case REGION -> picks.region(r -> {
                write(ctx, type, r.x(), r.y(), r.width(), r.height());
                pill.setText(tupleLabel(ctx, type, spec));
            });
            case MEASURE -> picks.region(r -> {
                write(ctx, type, r.width(), r.height());
                pill.setText(tupleLabel(ctx, type, spec));
            });
            case POINT -> picks.point(p -> {
                write(ctx, type, p.x(), p.y());
                pill.setText(tupleLabel(ctx, type, spec));
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
        MenuButton pill = Pills.bare(fileLabel(Values.text(ctx, "")));
        Pills.onOpen(pill, () -> List.of(
                Pills.item("Browse for program…", () -> Modals.program(ctx,
                        parentOf(Values.text(ctx, "")), path -> {
                            ctx.set(path.toString());
                            pill.setText(fileLabel(path.toString()));
                        })),
                Pills.separator(),
                Pills.item("Enter path…", () -> {
                    TextField field = Fields.committing(Values.text(ctx, ""), prompt, null);
                    field.setPrefColumnCount(40);
                    Modals.form(ctx, "Program path", field, () -> {
                        String typed = field.getText() == null ? "" : field.getText().trim();
                        if (typed.isEmpty()) return;
                        ctx.set(typed);
                        pill.setText(fileLabel(typed));
                    });
                })));
        return pill;
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
        Values.setNumber(ctx, range.whole()
                ? Math.round(clamped)
                : BigDecimal.valueOf(clamped).setScale(3, RoundingMode.HALF_UP).doubleValue());
    }

    /** What the pill says: the value as written, plus the unit — or the source text when it is not a number. */
    private static String rangeLabel(ValueContext ctx, NumberRange range) {
        if (Slots.isEmpty(ctx)) return range.label() + "…";
        String raw = Slots.raw(ctx);
        double held = Values.number(ctx, Double.NaN);
        if (Double.isNaN(held)) return raw;
        return (range.whole() ? Long.toString(Math.round(held)) : trim(held)) + range.unit();
    }

    /** A whole number reads as one — {@code "3"}, not {@code "3.0"} — because that is what a person reads. */
    private static String trim(double value) {
        return value == Math.rint(value) ? Long.toString(Math.round(value)) : Double.toString(value);
    }
}
