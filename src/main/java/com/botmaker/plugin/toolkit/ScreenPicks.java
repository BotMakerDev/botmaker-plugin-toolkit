package com.botmaker.plugin.toolkit;

import java.util.List;
import java.util.function.Consumer;

/**
 * How a widget in this kit asks the user to point at something on the screen.
 *
 * <p><b>This used to be the host's job and is not any more.</b> The plugin contract carried a {@code Capture}
 * service with these methods until 2026-08-31, defended on the grounds that only a host can put
 * a surface over its own windows. The maintainer's ruling is that the overlay and the desktop grab do not
 * belong to the editor at all — a full-screen surface over a running game, asking the user to point at
 * something in it, is about what a <em>bot</em> sees from end to end — so the service is deleted and a plugin
 * draws its own, over pixels it grabs itself through {@code botmaker-shared}, which is published.
 *
 * <p>That leaves this kit with a gap it cannot fill: {@link Editors#tuplePill} is a widget whose whole point
 * is a screen pick, and this module has one dependency and none of it is capture.
 * So the pick is supplied, not implemented — <b>the shape is the toolkit's and the pixels are the plugin's</b>,
 * which is the same split that keeps a bounded-number widget here and the table saying what the bound is in
 * the SDK.
 *
 * <p><b>Passed to the widget that needs it</b> ({@code tuplePill}'s last argument). It was registered once
 * through a static {@code Editors.pickWith} until that static was deleted — one field shared by every plugin
 * holding one toolkit copy, last writer winning. A widget given none uses {@link #NONE}, which does nothing
 * and says so.
 *
 * <p>Every method is asynchronous and the callback runs on the JavaFX application thread. A cancelled pick
 * must <em>not</em> call back at all: an editor then leaves its slot as it found it rather than writing a
 * default over what the user had.
 */
public interface ScreenPicks {

    /** Drag out a rectangle; reported in the capture source's own pixel space. */
    void region(Consumer<Region> onSelected);

    /**
     * Point at one pixel; reported as a {@link Region} of zero size.
     *
     * <p>Not {@link #region} with the size thrown away: choosing a single pixel needs a magnifier that
     * follows the cursor, because at 1:1 the pointer covers the thing it is choosing.
     */
    void point(Consumer<Region> onPicked);

    // color(Consumer<Color>) stood here until 2026-09-28. No widget here asked for it — a colour editor samples
    // through its own plugin, which knows what a colour of its is — so every implementation was dead code.

    /** One place a pick can be made, named as the plugin names it, and the picker bound to it. */
    record Choice(String label, ScreenPicks picks) {}

    /**
     * Where a pick can be made, when the plugin offers a choice of surfaces — the bot's window, another
     * window, the whole desktop — so a widget lists them in its own menu rather than opening a second menu
     * after the first (feedback 2, 2026-09-27). Empty, the default, means this picker asks for itself.
     */
    default List<Choice> choices() {
        return List.of();
    }

    /**
     * The fallback for a plugin that registered none — every pick does nothing.
     *
     * <p>It prints one line rather than throwing, because a missing registration is a plugin's own mistake
     * and it must not take a slot editor's whole popup down with it. Doing nothing is also exactly what a
     * cancelled pick does, so the slot survives either way.
     */
    ScreenPicks NONE = new ScreenPicks() {

        @Override public void region(Consumer<Region> onSelected) { unavailable(); }

        @Override public void point(Consumer<Region> onPicked) { unavailable(); }

        private void unavailable() {
            System.err.println("No screen picker given: pass the plugin's ScreenPicks to Editors.tuplePill(…) "
                    + "if its editors offer picking on screen.");
        }
    };
}
