package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.TypeRef;
import com.botmaker.plugin.api.slot.ValueContext;

/**
 * Reading and writing the value a {@link ValueContext} carries, for the handful of JDK types a widget deals
 * in directly.
 *
 * <p>{@link ValueContext#value(Class)} already answers the typed value, so nothing here parses anything —
 * which is the whole difference from what this class was. Until 2026-09-22 it read a {@code List<String>}
 * off the context and turned strings into numbers, and its javadoc pointed at a method the contract had
 * deleted two days earlier.
 *
 * <p>What is left is the part a widget genuinely cannot do for itself: <b>a number's Java type is not the
 * number</b>. A slider works in {@code double}, and writing one back into a field declared {@code int} has
 * to write an {@code Integer} or the host writes {@code 3.0} where {@code 3} was asked for. That is
 * {@link #setNumber}, and it is the one place the declared type is consulted.
 *
 * <p><b>Nothing here throws.</b> A value may have been typed by hand, or written by a newer version of the
 * plugin now reading it; every read degrades to the fallback the caller named. An editor that throws while
 * building its node leaves a row of the Parameters window with no widget in it.
 */
public final class Values {

    private Values() {}

    /** The value as text, or {@code fallback} — for a slot whose expression is not a plain string. */
    public static String text(ValueContext ctx, String fallback) {
        return ctx == null ? fallback : ctx.value(String.class).orElse(fallback);
    }

    /** The value as a yes/no, or {@code fallback}. */
    public static boolean flag(ValueContext ctx, boolean fallback) {
        return ctx == null ? fallback : ctx.value(Boolean.class).orElse(fallback);
    }

    /**
     * The value as a number whatever numeric type it is declared as, or {@code fallback}.
     *
     * <p>Falling back to the caller's number rather than to zero matters: opening an editor on a slot
     * holding a variable and pressing OK would otherwise write {@code 0}, which for a confidence means
     * "match anything".
     */
    public static double number(ValueContext ctx, double fallback) {
        if (ctx == null) return fallback;
        // The boxes, not Number.class: a value is asked for by the exact type it was written as, so asking
        // for a supertype would need the host to decide what "assignable" means across two classloaders —
        // the comparison rule 2 exists to keep out. Six empty answers cost nothing.
        for (Class<? extends Number> box : BOXES) {
            java.util.Optional<? extends Number> held = ctx.value(box);
            if (held.isPresent()) return held.get().doubleValue();
        }
        return fallback;
    }

    private static final java.util.List<Class<? extends Number>> BOXES = java.util.List.of(
            Integer.class, Long.class, Double.class, Float.class, Short.class, Byte.class);

    /**
     * Writes {@code value} as the numeric type this slot is declared as.
     *
     * <p>A widget works in {@code double} and a field is declared {@code int}, {@code long} or
     * {@code double}, and only the declaration says which. Writing the wrong one puts {@code 3.0} into a
     * pixel count, or truncates a confidence to {@code 0}.
     *
     * <p>An unresolved or non-numeric type is written as a {@code double}, which is the reading that keeps
     * the most information. Nothing here rounds silently in the other direction: a whole type rounds to
     * nearest, so a slider at 2.5 on an {@code int} field writes 3 rather than 2.
     */
    public static void setNumber(ValueContext ctx, double value) {
        if (ctx == null) return;
        TypeRef type = ctx.type();
        if (type.is(int.class) || type.is(Integer.class)) ctx.set((int) Math.round(value));
        else if (type.is(long.class) || type.is(Long.class)) ctx.set(Math.round(value));
        else if (type.is(float.class) || type.is(Float.class)) ctx.set((float) value);
        else if (type.is(short.class) || type.is(Short.class)) ctx.set((short) Math.round(value));
        else if (type.is(byte.class) || type.is(Byte.class)) ctx.set((byte) Math.round(value));
        else ctx.set(value);
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
