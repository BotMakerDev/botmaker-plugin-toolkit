package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.StyleClasses;
import javafx.scene.Node;

/**
 * The host's own style-class names, as constants, and a way to put them on a node.
 *
 * <p>This is the smallest class here and the one a plugin cannot do without. A plugin's editor is attached
 * to a scene the host has already themed, so it inherits the host's stylesheet for free — but only for the
 * classes that stylesheet actually names. A node with no class, or with a class of the plugin's own
 * invention, renders as unstyled JavaFX in the middle of a themed application.
 *
 * <p><b>The names are the contract's</b> ({@link StyleClasses}, since 2026-09-28): the host spells them with
 * the same constants and its tests hold its stylesheet to them, so a rename breaks a build instead of a
 * plugin's look. This class implements that interface so {@code Styles.PILL} keeps compiling; it declares no
 * name of its own.
 *
 * <p>Every widget in this toolkit applies the right ones already. Reach for these directly only when
 * building a node the toolkit does not cover.
 */
public final class Styles implements StyleClasses {

    private Styles() {}

    /** Adds {@code classes} to {@code node} and hands it back, so a builder reads as one expression. */
    public static <T extends Node> T on(T node, String... classes) {
        if (node != null && classes != null) node.getStyleClass().addAll(classes);
        return node;
    }

    /**
     * Gives {@code node} {@code chosen} and none of the rest of {@code among} — for a label whose state
     * changes its look, such as one that turns from {@link #WARNING_TEXT} to {@link #OK_TEXT}. A null
     * {@code chosen} clears them all. Adding without removing would leave both, and the stylesheet's later
     * rule would win whatever the state.
     */
    public static <T extends Node> T pick(T node, String chosen, String... among) {
        if (node == null) return null;
        if (among != null) node.getStyleClass().removeAll(among);
        node.getStyleClass().remove(chosen);
        if (chosen != null) node.getStyleClass().add(chosen);
        return node;
    }
}
