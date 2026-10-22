package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphCanvas.Arrow;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes.Cubic;
import com.botmaker.plugin.toolkit.graph.Routes.Route;
import com.botmaker.plugin.toolkit.graph.Routes.Segment;
import javafx.geometry.Bounds;
import javafx.scene.CacheHint;
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
import javafx.scene.shape.PathElement;
import javafx.scene.shape.Polygon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * {@link GraphCanvas}'s links: each routed edge's stroke, its arrowhead and its label, and the edge a click on one
 * names. Only each link's hit line takes the pointer; the strokes and heads let it through.
 */
final class LinkLayer<E> {

    /** How wide a link takes a click: its own stroke is thinner than a pointer aims. */
    private static final double HIT_WIDTH = 12;
    private static final double HEAD = 7;
    /** How much of a faint link shows: there to follow, not to read. */
    static final double FAINT = 0.15;

    /** One link drawn: its hit line, stroke and head, the label it carries (or null), and the route they follow. */
    private record Drawn(Path hit, Path path, Polygon head, Node label, Route route) {}

    private final Group links = new Group();
    private final Group labels = new Group();
    /** The edge each drawn link's hit line and label stands for: what a click on one names. */
    private final Map<Node, E> edgeOf = new HashMap<>();
    private Consumer<E> onClicked = e -> {};
    /** What {@link #draw} drew last, by index, with the arrows and the shift it drew them for. */
    private List<Drawn> drawn = List.of();
    private List<Arrow> drawnArrows = List.of();
    private double drawnX;
    private double drawnY;
    private CacheHint hint = CacheHint.QUALITY;
    /** Which links are drawn full with their labels; the rest are faint, without. */
    private Predicate<Arrow> lit = a -> true;

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

    /** How the labels, each drawn once and kept as a picture, are scaled: fast while the view moves, else sharp. */
    void setCacheHint(CacheHint hint) {
        this.hint = hint;
        for (Node label : labels.getChildren()) label.setCacheHint(hint);
    }

    /**
     * Draws the links {@code lit} names full, with their labels, over the rest, which are faint and without theirs.
     * Only opacity, visibility and order change: no link is routed or built again.
     */
    void light(Predicate<Arrow> lit) {
        this.lit = lit;
        for (int i = 0; i < drawn.size(); i++) light(drawn.get(i), drawnArrows.get(i));
    }

    private void light(Drawn link, Arrow a) {
        boolean full = lit.test(a);
        link.path().setOpacity(full ? 1 : FAINT);
        link.head().setOpacity(full ? 1 : FAINT);
        if (link.label() != null) link.label().setVisible(full);
        if (!full) return;
        // Over the faint ones, and first to take a click where they cross; in the order draw adds them.
        link.hit().toFront();
        link.path().toFront();
        link.head().toFront();
    }

    /** Draws {@code routes}, moved by {@code (dx, dy)}: the route at an index is that of {@code arrows}' there. */
    void draw(List<Route> routes, List<Arrow> arrows, List<E> drawnEdges, double dx, double dy) {
        links.getChildren().clear();
        labels.getChildren().clear();
        edgeOf.clear();
        List<Drawn> now = new ArrayList<>();
        // Routes come back in the order of the links they were asked for, skipping none: both cards are shown.
        for (int i = 0; i < routes.size() && i < arrows.size(); i++) {
            Route route = routes.get(i);
            Arrow a = arrows.get(i);
            E edge = drawnEdges.get(i);
            Path path = new Path(elements(route, dx, dy));
            path.getStyleClass().add("graph-link");
            if (a.dashed()) path.getStyleClass().add("graph-link-dashed");
            path.setMouseTransparent(true);
            Polygon head = new Polygon();
            head.getPoints().setAll(head(route, dx, dy));
            head.getStyleClass().add("graph-link-head");
            if (a.dashed()) head.getStyleClass().add("graph-link-dashed");
            head.setMouseTransparent(true);
            // An unseen wider line along the link takes its clicks: the drawn stroke is a pixel or two. Never
            // painted (opacity 0 is skipped, and still picked): a transparent stroke was drawn on every pan.
            Path hit = new Path(elements(route, dx, dy));
            hit.setFill(null);
            hit.setStroke(Color.TRANSPARENT);
            hit.setStrokeWidth(HIT_WIDTH);
            hit.setOpacity(0);
            hit.setCursor(Cursor.HAND);
            clickable(hit, edge);
            links.getChildren().addAll(hit, path, head);
            Node label = null;
            // A label node can have one parent: one shared by two edges rides on the first.
            if (a.label() != null && a.label().getParent() == null) {
                label = a.label();
                clickable(label, edge);
                labels.getChildren().add(label);
                label.applyCss();
                if (label instanceof Region region) region.autosize();
                // A chip of text on a fill: drawn once, moved as a picture while the view pans.
                label.setCache(true);
                label.setCacheHint(hint);
                place(label, route, dx, dy);
            }
            now.add(new Drawn(hit, path, head, label, route));
        }
        drawn = now;
        drawnArrows = List.copyOf(arrows.subList(0, now.size()));
        drawnX = dx;
        drawnY = dy;
        light(lit);
    }

    /**
     * Moves the links {@link #draw} drew to {@code routes}, changing only those whose route changed, in place: a
     * drag's frame, where the same links follow a card. Other links than were drawn are drawn afresh.
     */
    void move(List<Route> routes, List<Arrow> arrows, List<E> drawnEdges, double dx, double dy) {
        if (!arrows.equals(drawnArrows) || routes.size() != drawn.size()) {
            draw(routes, arrows, drawnEdges, dx, dy);
            return;
        }
        boolean shifted = dx != drawnX || dy != drawnY;
        for (int i = 0; i < drawn.size(); i++) {
            Drawn was = drawn.get(i);
            Route route = routes.get(i);
            if (!shifted && route.equals(was.route())) continue;
            was.path().getElements().setAll(elements(route, dx, dy));
            was.hit().getElements().setAll(elements(route, dx, dy));
            was.head().getPoints().setAll(head(route, dx, dy));
            if (was.label() != null) place(was.label(), route, dx, dy);
            drawn.set(i, new Drawn(was.hit(), was.path(), was.head(), was.label(), route));
        }
        drawnX = dx;
        drawnY = dy;
    }

    /** {@code route} as a path's elements, moved by {@code (dx, dy)}. */
    private static List<PathElement> elements(Route route, double dx, double dy) {
        List<PathElement> elements = new ArrayList<>();
        Pt start = route.start();
        elements.add(new MoveTo(start.x() + dx, start.y() + dy));
        for (Segment segment : route.segments()) {
            Pt to = segment.to();
            if (segment instanceof Cubic c) {
                elements.add(new CubicCurveTo(c.c1().x() + dx, c.c1().y() + dy,
                        c.c2().x() + dx, c.c2().y() + dy, to.x() + dx, to.y() + dy));
            } else {
                elements.add(new LineTo(to.x() + dx, to.y() + dy));
            }
        }
        return elements;
    }

    /** {@code label} centred on {@code route}'s middle, moved by {@code (dx, dy)}. */
    private static void place(Node label, Route route, double dx, double dy) {
        Bounds b = label.getLayoutBounds();
        Pt middle = route.middle();
        label.relocate(middle.x() + dx - b.getWidth() / 2, middle.y() + dy - b.getHeight() / 2);
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

    /** The arrowhead's three corners at {@code route}'s end, moved by {@code (dx, dy)}. */
    private static Double[] head(Route route, double dx, double dy) {
        Pt tip = route.end();
        Pt from = route.headFrom();
        double angle = Math.atan2(tip.y() - from.y(), tip.x() - from.x());
        double x = tip.x() + dx;
        double y = tip.y() + dy;
        return new Double[]{x, y,
                x - HEAD * Math.cos(angle - 0.45), y - HEAD * Math.sin(angle - 0.45),
                x - HEAD * Math.cos(angle + 0.45), y - HEAD * Math.sin(angle + 0.45)};
    }
}
