package com.botmaker.plugin.toolkit;

import javafx.scene.image.Image;

/**
 * One cell of a picture chooser: what the user sees, and what gets written if they pick it.
 *
 * <p>Four of the thirteen editors this toolkit was extracted from are the same chooser with different
 * pictures in it — an image template, a template group, a game's cover art, an emulator's screen — so the
 * grid is the widget and this is its item.
 *
 * @param value what {@link com.botmaker.plugin.api.slot.ValueContext#setSource(String, Class...)} is called with; never shown
 * @param label the caption under the picture, and what is shown when there is no picture
 * @param image the picture, or {@code null} — a missing image is an ordinary state (a template whose file
 *              was deleted, cover art that never downloaded) and renders as the label alone
 */
public record Thumbnail(String value, String label, Image image) {

    public Thumbnail {
        value = value == null ? "" : value;
        label = label == null || label.isBlank() ? value : label;
    }

    /** A cell with no picture — the label alone. */
    public static Thumbnail of(String value, String label) {
        return new Thumbnail(value, label, null);
    }
}
