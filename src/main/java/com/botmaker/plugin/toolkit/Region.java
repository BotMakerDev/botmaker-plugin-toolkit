package com.botmaker.plugin.toolkit;

/**
 * A rectangle in screen pixels, as {@link ScreenPicks} reports one.
 *
 * <p>A type of this module's own rather than the SDK's {@code Rect}: this module must not depend on any
 * plugin, and a second plugin picking a region would otherwise be made to speak the first one's geometry.
 * Converting it is one constructor call at the one place an editor needs it.
 *
 * <p>Integers because every producer is a pixel and every consumer is an input event delivered at a whole
 * pixel — the same reason the SDK's geometry types are.
 *
 * <p><b>It was {@code com.botmaker.plugin.api.Region} until 2026-09-07, and the move is what the contract's
 * own rule asks for.</b> It arrived there for {@code Capture}, a host capability deleted on 2026-08-31; with
 * that producer gone, no contract signature takes or returns it and the only producer left is
 * {@link ScreenPicks}, which is this module's. A record that only ever travels between a plugin and a widget
 * kit does not belong in the artifact both of them must agree on for ever.
 */
public record Region(int x, int y, int width, int height) {

    public int right() {
        return x + width;
    }

    public int bottom() {
        return y + height;
    }

    public boolean isEmpty() {
        return width <= 0 || height <= 0;
    }
}
