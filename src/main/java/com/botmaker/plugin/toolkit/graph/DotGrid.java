package com.botmaker.plugin.toolkit.graph;

import javafx.scene.image.WritableImage;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;

/**
 * {@link GraphCanvas}'s dot grid. It is painted, not styled, so it is painted again once CSS has something to say:
 * the dot's colour is read off an unseen region the stylesheet styles ({@code graph-grid-ink}).
 */
final class DotGrid {

    private static final double STEP = 24;
    private static final Color FALLBACK = Color.web("#d8d8d8");

    private final Region dots = new Region();
    private final Region ink = new Region();

    DotGrid() {
        dots.setMouseTransparent(true);
        dots.setManaged(false);
        ink.getStyleClass().add("graph-grid-ink");
        ink.setManaged(false);
        ink.setMouseTransparent(true);
        ink.resize(0, 0);
    }

    /** The grid itself. */
    Region dots() {
        return dots;
    }

    /** The unseen region whose style says a dot's colour: in the content beside {@link #dots()}. */
    Region ink() {
        return ink;
    }

    void resize(double width, double height) {
        dots.resize(width, height);
    }

    void paint() {
        int step = (int) STEP;
        WritableImage tile = new WritableImage(step, step);
        tile.getPixelWriter().setColor(0, 0, inkColour());
        dots.setBackground(new Background(new BackgroundFill(
                new ImagePattern(tile, 0, 0, step, step, false), null, null)));
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
