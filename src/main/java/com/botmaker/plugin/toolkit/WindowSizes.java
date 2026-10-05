package com.botmaker.plugin.toolkit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The size each {@link Modals#window} was last closed at, by title, for as long as the plugin is loaded.
 *
 * <p>Kept in memory only: the toolkit has no storage of its own, and a window reopened in the same session
 * is the case that matters — the manager resized to see its pictures, closed, and opened again a minute later.
 */
final class WindowSizes {

    private static final Map<String, double[]> LAST = new ConcurrentHashMap<>();

    private WindowSizes() {}

    /** The size to open at: the last one remembered under this title, or the window's own. */
    static double[] opening(String title, double width, double height) {
        double[] last = title == null ? null : LAST.get(title);
        return last == null ? new double[] {width, height} : last.clone();
    }

    /** Remembers a closed window's size; a window that never laid out (no positive size) is not one. */
    static void remember(String title, double width, double height) {
        if (title == null || !(width > 0) || !(height > 0)) return;
        LAST.put(title, new double[] {width, height});
    }
}
