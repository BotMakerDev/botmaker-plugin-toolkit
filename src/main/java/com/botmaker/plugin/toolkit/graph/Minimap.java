package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** {@link GraphCanvas}'s minimap, in the bottom-right corner: a dot per card, the view's box, and a click moves the view. */
final class Minimap {

    private static final double MOST = 170;

    private final Pane content;
    private final Viewport viewport;
    private final Pane pane = new Pane();
    private final Rectangle view = new Rectangle();

    Minimap(Pane content, Viewport viewport) {
        this.content = content;
        this.viewport = viewport;
        pane.getStyleClass().add("graph-minimap");
        pane.setManaged(true);
        pane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(pane, Pos.BOTTOM_RIGHT);
        view.getStyleClass().add("graph-minimap-view");
        view.setMouseTransparent(true);
        pane.setOnMousePressed(this::moveView);
        pane.setOnMouseDragged(this::moveView);
        viewport.onZoomed(this::drawView);
        viewport.scroller().hvalueProperty().addListener((o, was, is) -> drawView());
        viewport.scroller().vvalueProperty().addListener((o, was, is) -> drawView());
        viewport.scroller().viewportBoundsProperty().addListener((o, was, is) -> drawView());
    }

    Node node() {
        return pane;
    }

    private double scale() {
        double longest = Math.max(content.getPrefWidth(), content.getPrefHeight());
        return longest <= 0 ? 1 : MOST / longest;
    }

    /** A dot for each card {@code placed} puts, marked when {@code selected} holds its id. */
    void draw(Map<String, Box> placed, Predicate<String> selected) {
        double m = scale();
        pane.setPrefSize(content.getPrefWidth() * m, content.getPrefHeight() * m);
        List<Node> dots = new ArrayList<>();
        placed.forEach((k, box) -> {
            Rectangle dot = new Rectangle(box.minX() * m, box.minY() * m,
                    Math.max(2, box.width() * m), Math.max(2, box.height() * m));
            dot.getStyleClass().add("graph-minimap-card");
            dot.pseudoClassStateChanged(GraphCanvas.SELECTED, selected.test(k));
            dot.setMouseTransparent(true);
            dots.add(dot);
        });
        dots.add(view);
        pane.getChildren().setAll(dots);
        pane.setVisible(!placed.isEmpty());
        drawView();
    }

    private void drawView() {
        Bounds shown = viewport.scroller().getViewportBounds();
        double m = scale();
        double z = viewport.zoom();
        Pt corner = viewport.viewToContent(0, 0);
        view.setX(Math.max(0, corner.x()) * m);
        view.setY(Math.max(0, corner.y()) * m);
        view.setWidth(Math.min(content.getPrefWidth(), shown.getWidth() / z) * m);
        view.setHeight(Math.min(content.getPrefHeight(), shown.getHeight() / z) * m);
    }

    private void moveView(MouseEvent e) {
        double m = scale();
        viewport.centreOn(new Pt(e.getX() / m, e.getY() / m));
        e.consume();
    }
}
