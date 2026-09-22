package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.ValueContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The value as it is written, for the one case a typed value cannot answer: an expression nothing can
 * decode.
 *
 * <p>Three methods, and they are the whole of what a plugin still needs from source text. A hand-written
 * initializer, a variable, {@code target.center()}, a constant from elsewhere — {@link ValueContext#value}
 * answers empty for each, and the honest thing an editor does then is <b>show what the author wrote</b>
 * rather than overwrite it with a default. That needs the text.
 *
 * <h2>What was here until 2026-09-22, and why none of it is</h2>
 *
 * <p>Ten methods, eight of them deleted: {@code ints}, {@code literal} and {@code isNumber} (two of the
 * project's three numeric-literal strippers), {@code holdsNumbers}, {@code stringLiteral} (a weaker
 * {@code Source.stringValue}), {@code quote}, {@code writeConstructor}, {@code write} and
 * {@code writeText}. {@link #arguments} survives, narrowed to the one thing the host cannot type for a
 * plugin — see its own note.
 *
 * <p>They existed because {@link ValueContext} handed over a {@code String} of Java, so every plugin that
 * wanted a typed value parsed it — and parsed it slightly differently from the host and from each other.
 * A value crosses as a value now, so there is one reader and it is the host's.
 *
 * <p><b>{@code holdsNumbers} is deliberately not reimplemented.</b> "Is this value numbers at all, or is it
 * {@code target.center()}" is exactly "did {@link ValueContext#value} answer", asked by the thing that
 * actually knows. The old one required {@code startsWith("new ")}, so {@code Point.of(1, 2)} read as
 * not-numbers and a pill showed raw source for a value it could have labelled.
 */
public final class Slots {

    private Slots() {}

    /** The Java expression the value is written as, trimmed. Never {@code null}. */
    public static String raw(ValueContext ctx) {
        String source = ctx == null ? null : ctx.source();
        return source == null ? "" : source.trim();
    }

    /** Whether there is nothing there yet — a slot never filled in, or a value never set. */
    public static boolean isEmpty(ValueContext ctx) {
        return raw(ctx).isBlank();
    }

    /**
     * The arguments of a call, as written — <b>for
     * {@link com.botmaker.plugin.api.slot.SlotContext#enclosingCall()} and nothing else</b>.
     *
     * <p>The one place the contract still hands over source text with no typed form behind it, and it is
     * not an oversight: the enclosing call is not a value. An editor that offers to turn
     * {@code Wait.time(x)} into {@code Wait.between(min, max)} is rewriting the <em>call</em>, and the host
     * has no type for it to decode the old arguments through — {@code Wait.time} is the plugin's own API
     * and only the plugin knows what its arguments mean.
     *
     * <p><b>Do not reach for this for a value.</b> {@link com.botmaker.plugin.api.slot.ValueContext#value}
     * is the value, decoded once by the host through the owning plugin's
     * {@link com.botmaker.plugin.api.value.ComponentType}. Nine methods here parsed Java until 2026-09-22
     * and every one of them was a second, slightly different copy of something the host already did.
     *
     * <p>A brace-and-quote-aware split rather than a parser: it handles the shapes that actually occur —
     * nested calls, string literals containing commas — and returns nothing for text it cannot make sense
     * of, which every caller treats as "no current value".
     */
    public static List<String> arguments(String source) {
        String s = source == null ? "" : source.trim();
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close <= open) return List.of();

        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = open + 1; i < close; i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\' && inString) {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth--;
                else if (c == ',' && depth == 0) {
                    out.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        String last = current.toString().trim();
        if (!last.isEmpty() || !out.isEmpty()) out.add(last);
        return List.copyOf(out);
    }
}
