package com.botmaker.plugin.toolkit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The rules behind {@link Modals#tuple}, kept apart from its widgets so they are tested with no JavaFX: what a
 * paste means, how far a step goes, and what a rectangle's numbers imply.
 */
final class TupleFields {

    private static final Pattern WHOLE = Pattern.compile("-?\\d+");

    private TupleFields() {
    }

    /**
     * {@code text} as every number of a tuple at once — {@code "120, 340"}, {@code "new Rect(10, 20, 30, 40)"}
     * — when it holds exactly {@code arity} whole numbers and more than one. A single number is somebody
     * typing into one field, not a paste of the whole value.
     */
    static Optional<int[]> paste(String text, int arity) {
        if (text == null || arity < 2) return Optional.empty();
        List<Integer> found = new ArrayList<>();
        Matcher m = WHOLE.matcher(text);
        while (m.find()) {
            try {
                found.add(Integer.parseInt(m.group()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        if (found.size() != arity) return Optional.empty();
        return Optional.of(found.stream().mapToInt(Integer::intValue).toArray());
    }

    /** One step of an arrow, a wheel notch or a ▲/▼: one pixel, or ten with Shift. */
    static int step(int value, int direction, boolean shift) {
        return value + Integer.signum(direction) * (shift ? 10 : 1);
    }

    /** What a rectangle's four numbers imply — where it ends — or nothing for a point or a size. */
    static String readout(int[] values) {
        if (values == null || values.length != 4) return "";
        return "right " + (values[0] + values[2]) + " · bottom " + (values[1] + values[3]);
    }
}
