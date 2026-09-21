package com.botmaker.plugin.toolkit;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.LongConsumer;

/**
 * The input controls an editor cannot get right by reaching for the JavaFX one directly.
 *
 * <p>A plain {@link TextField} fires nothing useful: {@code setOnAction} catches Enter and misses the far
 * more common case of the user typing and clicking away, which is how a value gets silently lost. A plain
 * {@link Spinner} accepts anything typed into it, including text, and hands back the last valid value with
 * the invalid text still on screen. Both bugs were in this repository before the widgets that fixed them,
 * and both are the kind a plugin author reproduces exactly once each.
 */
public final class Fields {

    private Fields() {}

    /**
     * A text field that commits on Enter <b>and</b> on losing focus.
     *
     * <p>Both, always. Committing only on Enter loses every edit the user ends by clicking elsewhere, which
     * is most of them; committing only on focus loss makes Enter do nothing in a control that plainly looks
     * like it should accept it.
     */
    public static TextField committing(String initial, String prompt, Consumer<String> onCommit) {
        TextField field = Styles.on(new TextField(initial == null ? "" : initial), Styles.INSET_FIELD);
        field.setPromptText(prompt == null ? "" : prompt);
        field.setOnAction(e -> {
            if (onCommit != null) onCommit.accept(field.getText());
        });
        field.focusedProperty().addListener((obs, had, has) -> {
            if (!has && onCommit != null) onCommit.accept(field.getText());
        });
        return field;
    }

    /**
     * A whole-number spinner clamped to {@code [min, max]}, which refuses text rather than accepting it.
     *
     * <p>Editable, because a bounded range is often wide and dragging to 1920 is not a thing anybody wants
     * to do — but with the value factory's own converter, so what is typed is either a number in range or
     * is reverted on commit.
     */
    public static Spinner<Integer> integer(int value, int min, int max) {
        int lo = Math.min(min, max);
        int hi = Math.max(min, max);
        Spinner<Integer> spinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(
                lo, hi, Math.clamp(value, lo, hi)));
        spinner.setEditable(true);
        return Styles.on(spinner, Styles.INSET_FIELD_FLAT);
    }

    /**
     * A slider and a read-out for a bounded fractional number — a confidence, a tolerance, a delay.
     *
     * <p>A slider and not a spinner because these are the numbers whose <em>scale</em> is the thing nobody
     * knows: a tolerance of 12 means nothing until you can see where 12 sits between the ends. The read-out
     * is beside it because the exact value still matters once the shape of the range is understood.
     *
     * <p>{@code onChange} fires continuously while dragging. That is deliberate and matches the contract's
     * note that {@link com.botmaker.plugin.api.slot.ValueContext#set(java.util.List)} may be called repeatedly —
     * it is what lets an editor that previews its value track the drag.
     */
    public static HBox bounded(double value, double min, double max, double step, DoubleConsumer onChange) {
        double lo = Math.min(min, max);
        double hi = Math.max(min, max);
        Slider slider = new Slider(lo, hi, Math.clamp(value, lo, hi));
        if (step > 0) {
            slider.setBlockIncrement(step);
            slider.setMajorTickUnit(step);
            slider.setSnapToTicks(true);
        }
        Label readout = Styles.on(new Label(format(slider.getValue(), step)), Styles.CAPTION_STRONG);
        slider.valueProperty().addListener((obs, was, now) -> {
            readout.setText(format(now.doubleValue(), step));
            if (onChange != null) onChange.accept(now.doubleValue());
        });

        HBox row = new HBox(6, slider, readout);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /**
     * A length of time as one box per unit — hours, minutes, seconds, milliseconds.
     *
     * <p>The control this replaced everywhere it appeared was one amount plus a unit dropdown, and it could
     * only ever say a multiple of a single unit: four and a half minutes had to be entered as 270 seconds, and
     * anything that did not divide evenly came back as a raw millisecond count. Four boxes cost three more
     * widgets and remove the arithmetic.
     *
     * <p>{@code onChange} fires on every keystroke with the running total. Anything unreadable in a box counts
     * as zero and never as a throw, which is {@link Values}' rule — a half-typed number is a normal state of a
     * box someone is still typing in.
     *
     * <p><b>No preview label.</b> Spelling a total back out — {@code 4h30m}, {@code 1m30s} — is the caller's,
     * because the caller is the one that has to store it, and a preview that disagreed with what was written
     * would be worse than no preview at all.
     */
    public static HBox duration(long millis, LongConsumer onChange) {
        long total = Math.max(0L, millis);
        TextField hours = unit("h", total / 3_600_000L);
        TextField minutes = unit("m", total / 60_000L % 60);
        TextField seconds = unit("s", total / 1000L % 60);
        TextField ms = unit("ms", total % 1000L);

        HBox row = new HBox(6);
        row.setAlignment(Pos.CENTER_LEFT);
        Runnable report = () -> {
            if (onChange != null) {
                onChange.accept(whole(hours) * 3_600_000L + whole(minutes) * 60_000L
                                + whole(seconds) * 1000L + whole(ms));
            }
        };
        for (TextField field : List.of(hours, minutes, seconds, ms)) {
            field.textProperty().addListener((obs, was, now) -> report.run());
            row.getChildren().addAll(field, Styles.on(new Label(field.getPromptText()), Styles.CAPTION));
        }
        return row;
    }

    private static TextField unit(String suffix, long value) {
        TextField field = Styles.on(new TextField(Long.toString(value)), Styles.INSET_FIELD);
        field.setPromptText(suffix);
        field.setPrefColumnCount(suffix.length() > 1 ? 4 : 3);
        return field;
    }

    /** What a box says as a whole number, floored at zero — see the note on {@link #duration}. */
    private static long whole(TextField field) {
        try {
            return Math.max(0L, Long.parseLong(field.getText() == null ? "" : field.getText().trim()));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /** A whole number reads as one; anything else keeps two places, which is enough for every real knob. */
    private static String format(double value, double step) {
        boolean whole = step >= 1 && step == Math.rint(step);
        return whole || value == Math.rint(value)
                ? Long.toString(Math.round(value))
                : String.format("%.2f", value);
    }
}
