package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphCanvas.Arrow;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes.Line;
import com.botmaker.plugin.toolkit.graph.Routes.Route;
import javafx.scene.Node;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A drag's frame moves the links already drawn, touching only those whose route changed. */
class LinkLayerTest {

    private static Route route(String from, String to, double x1, double y1, double x2, double y2) {
        return new Route(new Link(from, null, to), List.of(new Line(new Pt(x1, y1), new Pt(x2, y2))));
    }

    @Test
    void aFrameMovesOnlyTheLinksThatChanged() {
        LinkLayer<String> layer = new LinkLayer<>();
        List<Arrow> arrows = List.of(Arrow.solid("A", "B"), Arrow.solid("B", "C"));
        List<String> edges = List.of("ab", "bc");
        layer.draw(List.of(route("A", "B", 0, 0, 100, 0), route("B", "C", 100, 0, 200, 0)), arrows, edges, 10, 10);
        List<Node> nodes = List.copyOf(layer.links().getChildren());
        Path bc = (Path) nodes.get(4);
        List<PathElement> untouched = List.copyOf(bc.getElements());

        layer.move(List.of(route("A", "B", 0, 50, 100, 0), route("B", "C", 100, 0, 200, 0)), arrows, edges, 10, 10);
        assertEquals(nodes, layer.links().getChildren(), "the same nodes, none built again");
        Path ab = (Path) nodes.get(1);
        LineTo end = (LineTo) ab.getElements().get(1);
        assertEquals(110, end.getX());
        assertEquals(60, ((MoveTo) ab.getElements().getFirst()).getY(), "the moved start");
        assertSame(untouched.getFirst(), bc.getElements().getFirst(), "a link whose route held is left alone");

        layer.move(List.of(route("A", "B", 0, 0, 100, 0)), arrows.subList(0, 1), edges.subList(0, 1), 10, 10);
        assertEquals(3, layer.links().getChildren().size(), "other links than were drawn: drawn afresh");
    }
}
