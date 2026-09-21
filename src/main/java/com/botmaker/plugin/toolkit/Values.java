package com.botmaker.plugin.toolkit;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading and writing the {@code List<String>} a {@link com.botmaker.plugin.api.slot.ValueContext} carries.
 *
 * <p>The contract is deliberate that a value is a list of strings — that is what a project file holds — and
 * equally deliberate that <em>parsing it is the editor's own job, and failing to parse it is normal</em>: a
 * user may have typed anything, and a value may have been written by a newer version of the plugin than the
 * one now reading it. Every method here therefore <b>degrades and never throws</b>. That is not politeness;
 * an editor that throws while building its node leaves a row of the Parameters window with no widget in it.
 *
 * <p>The one thing to keep in mind: {@code ""} and "absent" are the same here. An editor that needs to tell
 * <em>never set</em> from <em>deliberately cleared</em> has to look at {@link
 * com.botmaker.plugin.api.slot.ValueContext#value()} itself, because a one-element list holding {@code ""} and an
 * empty list both read as blank through these.
 */
public final class Values {

    private Values() {}

    /** The item at {@code index}, or {@code ""} — never null, whatever the list holds. */
    public static String at(List<String> value, int index) {
        if (value == null || index < 0 || index >= value.size()) return "";
        String item = value.get(index);
        return item == null ? "" : item;
    }

    /** The item at {@code index} as an {@code int}, or {@code fallback} if it is missing or not a number. */
    public static int intAt(List<String> value, int index, int fallback) {
        try {
            return Integer.parseInt(at(value, index).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** The item at {@code index} as a {@code double}, or {@code fallback} if it is missing or not a number. */
    public static double doubleAt(List<String> value, int index, double fallback) {
        try {
            return Double.parseDouble(at(value, index).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * The first {@code count} items as {@code int}s, missing or unparsable ones as {@code 0}.
     *
     * <p>The shape every geometry editor wants: a {@code Rect} is four of these and a {@code Point} is two,
     * and a half-written value must still open an editor rather than refuse to render.
     */
    public static int[] ints(List<String> value, int count) {
        int[] out = new int[Math.max(count, 0)];
        for (int i = 0; i < out.length; i++) out[i] = intAt(value, i, 0);
        return out;
    }

    /** {@code numbers} as the wire form, ready for {@link com.botmaker.plugin.api.slot.ValueContext#set(List)}. */
    public static List<String> of(int... numbers) {
        if (numbers == null) return List.of();
        List<String> out = new ArrayList<>(numbers.length);
        for (int n : numbers) out.add(Integer.toString(n));
        return List.copyOf(out);
    }

    /** Whether every item is missing or whitespace — the state a placeholder is shown for. */
    public static boolean isBlank(List<String> value) {
        if (value == null || value.isEmpty()) return true;
        for (String item : value) {
            if (item != null && !item.isBlank()) return false;
        }
        return true;
    }

    /**
     * {@code text} if it says anything, otherwise {@code placeholder}.
     *
     * <p>Every pill in this toolkit reads its label through here, so an unset value says <i>Choose region…</i>
     * rather than showing an empty control the user cannot tell from a broken one.
     */
    public static String labelOr(String text, String placeholder) {
        return text == null || text.isBlank() ? placeholder : text;
    }
}
