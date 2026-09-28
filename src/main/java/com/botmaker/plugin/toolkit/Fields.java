package com.botmaker.plugin.toolkit;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * The input controls an editor cannot get right by reaching for the JavaFX one directly.
 *
 * <p>A plain {@link TextField} fires nothing useful: {@code setOnAction} catches Enter and misses the far
 * more common case of the user typing and clicking away, which is how a value gets silently lost. A plain
 * {@link Spinner} accepts anything typed into it, including text, and hands back the last valid value with
 * the invalid text still on screen. Both bugs were in this repository before the widgets that fixed them,
 * and both are the kind a plugin author reproduces exactly once each.
 *
 * <p>{@code duration} (one box per unit) stood here until 2026-09-28 with no caller: the plugin that owns
 * {@code Duration} draws its own picker, and a length of time is that plugin's word, not a shape.
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

    /** One step of a {@link #stepped} field: which way ({@code 1} up, {@code -1} down), and whether Shift was held. */
    @FunctionalInterface
    public interface Step {
        void by(int direction, boolean shift);
    }

    /**
     * {@code field} with ▲/▼ beside it, and the arrow keys and the wheel over it, each calling {@code step}.
     *
     * <p>What a step <em>means</em> — one pixel, one unit, the last decimal place shown, ten of them with Shift
     * — is the caller's, and so is writing the result into the field: this is only the four ways a person asks
     * for one. They were written twice before this (2026-09-28), in the toolkit's own tuple dialog and in a
     * plugin's number field, and the copies had drifted: one read Shift on the buttons and ignored the arrow
     * keys, the other the reverse.
     *
     * <p>Shift is read as a button is <em>pressed</em>, because an action event does not carry the modifier
     * keys. The buttons take no focus, so tabbing moves between fields rather than through every arrow.
     */
    public static HBox stepped(TextField field, Step step) {
        boolean[] shift = {false};
        Button up = Pills.icon("▲", () -> step.by(1, shift[0]));
        Button down = Pills.icon("▼", () -> step.by(-1, shift[0]));
        for (Button button : new Button[] {up, down}) {
            button.setOnMousePressed(e -> shift[0] = e.isShiftDown());
            button.setFocusTraversable(false);
        }
        up.setTooltip(new Tooltip("Up (Shift: ten steps) — or the ↑ key, or scroll over the field"));
        down.setTooltip(new Tooltip("Down (Shift: ten steps) — or the ↓ key, or scroll over the field"));
        field.setOnKeyPressed(e -> {
            int direction = switch (e.getCode()) {
                case UP -> 1;
                case DOWN -> -1;
                default -> 0;
            };
            if (direction == 0) return;
            step.by(direction, e.isShiftDown());
            e.consume();
        });
        field.setOnScroll(e -> {
            if (e.getDeltaY() == 0) return;
            step.by(e.getDeltaY() > 0 ? 1 : -1, e.isShiftDown());
            e.consume();
        });
        HBox row = new HBox(2, field, new VBox(0, up, down));
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    /**
     * A whole-number spinner clamped to {@code [min, max]}, which refuses text rather than accepting it.
     *
     * <p>Editable, because a bounded range is often wide and dragging to 1920 is not a thing anybody wants
     * to do — but with the value factory's own converter, so what is typed is either a number in range or
     * is reverted on commit. {@link Editors#boundedPill}'s; package-private since 2026-09-28, when it had no
     * other caller.
     */
    static Spinner<Integer> integer(int value, int min, int max) {
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
     * <p>{@code onChange} fires continuously while dragging. {@link Editors#boundedPill}'s; package-private
     * since 2026-09-28, when it had no other caller.
     */
    static HBox bounded(double value, double min, double max, double step, DoubleConsumer onChange) {
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

    /** A whole number reads as one; anything else keeps two places, which is enough for every real knob. */
    private static String format(double value, double step) {
        boolean whole = step >= 1 && step == Math.rint(step);
        return whole || value == Math.rint(value)
                ? Long.toString(Math.round(value))
                : String.format("%.2f", value);
    }
}
