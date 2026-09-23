package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.ValueContext;

/**
 * The value as it is written, for the one case a typed value cannot answer: an expression nothing can
 * decode.
 *
 * <p>Two methods, and they are the whole of what a plugin still needs from source text: showing it. A
 * hand-written initializer, a variable, {@code target.center()}, a constant from elsewhere — {@link ValueContext#value}
 * answers empty for each, and the honest thing an editor does then is <b>show what the author wrote</b>
 * rather than overwrite it with a default. That needs the text.
 *
 * <h2>What was here until 2026-09-22, and why none of it is</h2>
 *
 * <p>Ten methods, eight of them deleted: {@code ints}, {@code literal} and {@code isNumber} (two of the
 * project's three numeric-literal strippers), {@code holdsNumbers}, {@code stringLiteral} (a weaker
 * {@code Source.stringValue}), {@code quote}, {@code writeConstructor}, {@code write} and
 * {@code writeText}. {@code arguments} survived, narrowed to the enclosing call, until 2026-09-23.
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

    // arguments(String) stood here until 2026-09-23, "for SlotContext.enclosingCall() and nothing else". The
    // enclosing call left the contract that day, and with it the last Java this module split.
}
