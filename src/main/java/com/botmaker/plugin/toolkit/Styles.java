package com.botmaker.plugin.toolkit;

import javafx.scene.Node;

/**
 * The host's own style-class names, as constants.
 *
 * <p>This is the smallest class here and the one a plugin cannot do without. A plugin's editor is attached
 * to a scene the host has already themed, so it inherits the host's stylesheet for free — but only for the
 * classes that stylesheet actually names. A node with no class, or with a class of the plugin's own
 * invention, renders as unstyled JavaFX in the middle of a themed application, and the plugin author has no
 * way to discover the right string: the stylesheet is inside the host's jar.
 *
 * <p><b>These are names, not a look.</b> What each one renders as is the host's to change, and changes with
 * the theme the user picked — which is the point. A plugin that hard-codes a colour matches the application
 * once; a plugin that names a class matches it after the next release too.
 *
 * <p>Every widget in this toolkit applies the right ones already. Reach for these directly only when
 * building a node the toolkit does not cover.
 */
public final class Styles {

    private Styles() {}

    /** The control standing in for a value — the pill shape every argument editor wears. */
    public static final String PILL = "argument-pill";

    /** A field sunk into the surface behind it, for a control sitting inside a block or a card. */
    public static final String INSET_FIELD = "block-inset-field";

    /** {@link #INSET_FIELD} without its border — for a field that is already inside a bordered container. */
    public static final String INSET_FIELD_FLAT = "block-inset-field--flat";

    /** A small rounded token: a tag, a unit, a mode. */
    public static final String CHIP = "block-chip";

    /** {@link #CHIP} with no fill, for a chip that must not compete with the value beside it. */
    public static final String CHIP_PLAIN = "block-chip--plain";

    /** Secondary text: a unit, a hint, the word between two fields. */
    public static final String CAPTION = "block-caption";

    /** {@link #CAPTION} at full weight, for the one word in a row that carries the meaning. */
    public static final String CAPTION_STRONG = "block-caption--strong";

    /** A value shown but not editable here. */
    public static final String VALUE_LABEL = "static-value-label";

    /** The greyed stand-in for a value that has not been chosen yet. */
    public static final String PLACEHOLDER = "block-placeholder";

    /** A square glyph-only button, sized to the same footprint as every other one in a row. */
    public static final String ICON_BUTTON = "icon-button";

    /** The title line of a dialog built by hand rather than through {@code Dialog}. */
    public static final String DIALOG_HEADING = "dialog-heading";

    /** A section title inside a dialog. */
    public static final String DIALOG_SUBHEADING = "dialog-subheading";

    /** An explanatory line under a dialog's controls. */
    public static final String DIALOG_HINT = "dialog-hint";

    /** A dialog root with the host's standard padding and spacing. */
    public static final String DIALOG_COMPACT = "dialog-compact";

    /** The confirming button of a dialog — one per dialog, never two. */
    public static final String PRIMARY_BUTTON = "primary-button";

    /** An on/off toggle whose state shows in its colour as well as its word — off one colour, on another. */
    public static final String SWITCH = "value-switch";

    /** One cell of a thumbnail grid. */
    public static final String TILE = "template-tile";

    /** The caption under a {@link #TILE}. */
    public static final String TILE_NAME = "template-tile-name";

    /**
     * On the root of a window that must <em>not</em> acquire the host's chrome — a translucent surface drawn
     * over a live game, where the shell's background, border and radius are the one thing that would ruin it.
     *
     * <p>An opt-out rather than an omission: the host themes a plugin's windows for it, so a surface that
     * wants no theme has to say so. It is a marker class, so it carries no properties of its own and a host
     * that does not recognise it simply themes the window as usual.
     */
    public static final String UNTHEMED = "unthemed-window";

    /** Adds {@code classes} to {@code node} and hands it back, so a builder reads as one expression. */
    public static <T extends Node> T on(T node, String... classes) {
        if (node != null && classes != null) node.getStyleClass().addAll(classes);
        return node;
    }
}
