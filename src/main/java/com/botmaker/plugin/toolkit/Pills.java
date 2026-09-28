package com.botmaker.plugin.toolkit;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;

import java.util.List;
import java.util.function.Supplier;

/**
 * The pill: a control that shows a value and opens a short menu of ways to change it.
 *
 * <p>Nine of the thirteen editors this toolkit was extracted from are this shape and nothing else — a
 * summary of the current value, and two or three actions behind it (<i>Select on screen…</i>, <i>Edit
 * values…</i>, <i>Clear</i>). It is a shape rather than a widget because what goes in the menu is entirely
 * the editor's business; what a plugin should not have to rediscover is the class name, the label-or-
 * placeholder rule, and that the menu must be rebuilt when it opens.
 *
 * <p><b>The menu is built on showing, not on construction, and that is load-bearing.</b> An editor's
 * choices routinely depend on state that moves — which windows are open, which templates exist, whether the
 * value is set yet. A menu built once shows the answer as it was when the block was first rendered.
 */
public final class Pills {

    private Pills() {}

    /**
     * A pill with no menu yet.
     *
     * <p>Pair it with {@link #onOpen}: the menu's entries need the pill itself, to relabel it after a write —
     * the pill cannot know when a write has landed, so every editor in {@link Editors} does it in the same
     * callback that writes. (A one-step {@code menu(label, items)} stood beside it until 2026-09-28; every
     * caller needed the pill first, so none called it.)
     */
    public static MenuButton bare(String label) {
        return Styles.on(new MenuButton(label), Styles.PILL);
    }

    /**
     * A pill with no menu at all — one label, one action, taken on the click.
     *
     * <p>The right shape when there is genuinely only one thing to do with the value: open the picture
     * chooser, open the library browser. A menu of one entry makes the user click twice to reach the only
     * destination there was, and reads as though something has been hidden from them.
     *
     * <p>It is a {@link Button} and not a {@link MenuButton} so it carries no disclosure arrow, and it takes
     * the same {@link Styles#PILL} class, so a slot holding one of these sits in the block at the same size
     * and weight as a slot holding a menu pill beside it.
     */
    public static Button button(String label, Runnable onPressed) {
        Button button = Styles.on(new Button(label), Styles.PILL);
        button.setOnAction(e -> {
            if (onPressed != null) onPressed.run();
        });
        return button;
    }

    /** Rebuilds {@code button}'s entries from {@code items} each time it is opened, and hands it back. */
    public static MenuButton onOpen(MenuButton button, Supplier<List<MenuItem>> items) {
        button.setOnShowing(e -> {
            button.getItems().clear();
            List<MenuItem> built = items == null ? null : items.get();
            if (built != null) button.getItems().addAll(built);
        });
        return button;
    }

    /** One menu entry. */
    public static MenuItem item(String label, Runnable onChosen) {
        MenuItem item = new MenuItem(label);
        item.setOnAction(e -> {
            if (onChosen != null) onChosen.run();
        });
        return item;
    }

    /** A rule between two groups of entries. */
    public static MenuItem separator() {
        return new SeparatorMenuItem();
    }

    /**
     * A value shown but not editable here — with the host's placeholder styling when it says nothing.
     *
     * <p>The styling is the whole reason this exists rather than a {@code new Label}: an unset value that
     * renders in the same ink as a set one reads as a value of {@code ""}, which is a different fact.
     */
    public static Label value(String text, String placeholder) {
        boolean unset = text == null || text.isBlank();
        Label label = new Label(unset ? placeholder : text);
        return Styles.on(label, unset ? Styles.PLACEHOLDER : Styles.VALUE_LABEL);
    }

    /** A small glyph-only button on the host's standard footprint — a clear, a browse, a refresh. */
    public static Button icon(String glyph, Runnable onPressed) {
        Button button = Styles.on(new Button(glyph), Styles.ICON_BUTTON);
        button.setOnAction(e -> {
            if (onPressed != null) onPressed.run();
        });
        return button;
    }
}
