package com.botmaker.plugin.toolkit.graph;

import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;

/**
 * {@link GraphCanvas}'s dot grid: one region the size of the view, behind the scroller, its pattern moved with the
 * content rather than drawn over all of it. A grid as big as the graph was scaled with it, so every pan and every
 * zoom step drew it again over the whole graph (2026-10-22). It is painted, not styled, so it is painted again once
 * CSS has something to say: the dot's colour is read off an unseen region the stylesheet styles
 * ({@code graph-grid-ink}).
 */
final class DotGrid {

    /** The gap between dots at 1:1, in the content's pixels. */
    private static final double STEP = 24;
    /** The closest dots come on screen: zoomed far out, every other dot goes, then every other again. */
    private static final double MIN_GAP = 12;
    private static final Color FALLBACK = Color.web("#d8d8d8");

    private final Region dots = new Region();
    private final Region ink = new Region();
    private Color colour;
    /** One dot and the space after it, made again for a new gap or colour. */
    private WritableImage tile;
    /** The gap and where a dot sits, in the view's pixels: what the pattern is drawn with last. */
    private double gap;
    private double x;
    private double y;

    DotGrid() {
        dots.setMouseTransparent(true);
        ink.getStyleClass().add("graph-grid-ink");
        ink.setManaged(false);
        ink.setMouseTransparent(true);
        ink.resize(0, 0);
    }

    /** The grid itself: behind the scroller, sized by the canvas. */
    Region dots() {
        return dots;
    }

    /** The unseen region whose style says a dot's colour: in the content, where the stylesheet reaches it. */
    Region ink() {
        return ink;
    }

    /** Reads the dot's colour again, painting the grid in it when it changed: the theme did. */
    void paint() {
        Color now = inkColour();
        if (now.equals(colour)) return;
        colour = now;
        tile = null;
        draw();
    }

    /** Lines the dots up with the content, whose origin is at ({@code originX}, {@code originY}) in the view. */
    void follow(double originX, double originY, double zoom) {
        double next = STEP * zoom;
        while (next < MIN_GAP) next *= 2;
        double nextX = originX % next;
        double nextY = originY % next;
        if (next == gap && nextX == x && nextY == y) return;
        if (next != gap) tile = null;
        gap = next;
        x = nextX;
        y = nextY;
        draw();
    }

    private void draw() {
        if (gap <= 0 || colour == null) return;
        if (tile == null) {
            int size = (int) Math.ceil(gap);
            tile = new WritableImage(size, size);
            tile.getPixelWriter().setColor(0, 0, colour);
        }
        dots.setBackground(new Background(new BackgroundFill(
                new ImagePattern(tile, x, y, gap, gap, false), null, null)));
    }

    /** What the stylesheet says a grid dot is: a pattern tile takes a colour, never a style class. */
    private Color inkColour() {
        if (ink.getScene() == null) return FALLBACK;
        ink.applyCss();
        Background background = ink.getBackground();
        if (background == null || background.getFills().isEmpty()) return FALLBACK;
        return background.getFills().getFirst().getFill() instanceof Color c ? c : FALLBACK;
    }
}
