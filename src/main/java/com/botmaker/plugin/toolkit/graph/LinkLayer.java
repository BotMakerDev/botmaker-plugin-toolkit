package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphCanvas.Arrow;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes.Cubic;
import com.botmaker.plugin.toolkit.graph.Routes.Route;
import com.botmaker.plugin.toolkit.graph.Routes.Segment;
import javafx.geometry.Bounds;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@link GraphCanvas}'s links: each routed edge's stroke, its arrowhead and its label, and the edge a click on one
 * names. Only each link's hit line takes the pointer; the strokes and heads let it through.
 */
final class LinkLayer<E> {

    /** How wide a link takes a click: its own stroke is thinner than a pointer aims. */
    private static final double HIT_WIDTH = 12;
    private static final double HEAD = 7;

    private final Group links = new Group();
    private final Group labels = new Group();
    /** The edge each drawn link's hit line and label stands for: what a click on one names. */
    private final Map<Node, E> edgeOf = new HashMap<>();
    private Consumer<E> onClicked = e -> {};

    /** The strokes, under the cards. */
    Group links() {
        return links;
    }

    /** The labels, over the cards. */
    Group labels() {
        return labels;
    }

    void setOnClicked(Consumer<E> onClicked) {
        this.onClicked = onClicked;
    }

    /** The edge whose link or label {@code node} is, or null. */
    E edgeOf(Node node) {
        return edgeOf.get(node);
    }

    /** Draws {@code routes}, moved by {@code (dx, dy)}: the route at an index is that of {@code drawn}'s arrow there. */
    void draw(List<Route> routes, List<Arrow> drawn, List<E> drawnEdges, double dx, double dy) {
        links.getChildren().clear();
        labels.getChildren().clear();
        edgeOf.clear();
        // Routes come back in the order of the links they were asked for, skipping none: both cards are shown.
        for (int i = 0; i < routes.size() && i < drawn.size(); i++) {
            Route route = routes.get(i);
            Arrow a = drawn.get(i);
            E edge = drawnEdges.get(i);
            Path path = route(route, dx, dy);
            path.getStyleClass().add("graph-link");
            if (a.dashed()) path.getStyleClass().add("graph-link-dashed");
            path.setMouseTransparent(true);
            Polygon head = head(route, a.dashed(), dx, dy);
            head.setMouseTransparent(true);
            // An unseen wider line along the link takes its clicks: the drawn stroke is a pixel or two.
            Path hit = route(route, dx, dy);
            hit.setFill(null);
            hit.setStroke(Color.TRANSPARENT);
            hit.setStrokeWidth(HIT_WIDTH);
            hit.setCursor(Cursor.HAND);
            clickable(hit, edge);
            links.getChildren().addAll(hit, path, head);
            // A label node can have one parent: one shared by two edges rides on the first.
            if (a.label() != null && a.label().getParent() == null) {
                Node label = a.label();
                clickable(label, edge);
                labels.getChildren().add(label);
                label.applyCss();
                if (label instanceof Region region) region.autosize();
                Bounds b = label.getLayoutBounds();
                Pt middle = route.middle();
                label.relocate(middle.x() + dx - b.getWidth() / 2, middle.y() + dy - b.getHeight() / 2);
            }
        }
    }

    /** {@code route} as a path, moved by {@code (dx, dy)}. */
    private static Path route(Route route, double dx, double dy) {
        Path path = new Path();
        Pt start = route.start();
        path.getElements().add(new MoveTo(start.x() + dx, start.y() + dy));
        for (Segment segment : route.segments()) {
            Pt to = segment.to();
            if (segment instanceof Cubic c) {
                path.getElements().add(new CubicCurveTo(c.c1().x() + dx, c.c1().y() + dy,
                        c.c2().x() + dx, c.c2().y() + dy, to.x() + dx, to.y() + dy));
            } else {
                path.getElements().add(new LineTo(to.x() + dx, to.y() + dy));
            }
        }
        return path;
    }

    /** {@code node} names {@code edge}: a click on it is {@link #setOnClicked}'s, a right click its menu. */
    private void clickable(Node node, E edge) {
        edgeOf.put(node, edge);
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || !e.isStillSincePress()) return;
            onClicked.accept(edge);
            e.consume();
        });
    }

    private static Polygon head(Route route, boolean dashed, double dx, double dy) {
        Pt tip = route.end();
        Pt from = route.headFrom();
        double angle = Math.atan2(tip.y() - from.y(), tip.x() - from.x());
        double x = tip.x() + dx;
        double y = tip.y() + dy;
        Polygon head = new Polygon(x, y,
                x - HEAD * Math.cos(angle - 0.45), y - HEAD * Math.sin(angle - 0.45),
                x - HEAD * Math.cos(angle + 0.45), y - HEAD * Math.sin(angle + 0.45));
        head.getStyleClass().add("graph-link-head");
        if (dashed) head.getStyleClass().add("graph-link-dashed");
        return head;
    }
}
