package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Loops;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes.Route;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutesTest {

    private static final Card A = Card.of("a", 100, 60);
    private static final Card WALL = Card.of("wall", 100, 200);
    private static final Card B = Card.of("b", 100, 60);
    private static final List<Card> CARDS = List.of(A, WALL, B);
    /** a, then a tall card straight in a's way, then b level with a. */
    private static final Map<String, Pt> AT = Map.of("a", new Pt(0, 100), "wall", new Pt(250, 30),
            "b", new Pt(500, 100));

    private static void assertClear(Route route, String... others) {
        for (String other : others) {
            Card card = CARDS.stream().filter(c -> c.id().equals(other)).findFirst().orElseThrow();
            Box box = card.at(AT.get(other));
            for (Pt p : route.sample(3)) {
                assertFalse(box.holds(p, 1), route.link() + " goes through " + other + " at " + p);
            }
        }
    }

    @Test
    void aForwardLinkGoesAroundTheCardInItsWay() {
        Route route = Routes.of(CARDS, AT, List.of(Link.of("a", "b")), Direction.RIGHT, Routes.Rules.DEFAULT)
                .getFirst();

        assertEquals(new Pt(100, 130), route.start());
        assertEquals(new Pt(500, 130), route.end());
        assertClear(route, "wall");
    }

    @Test
    void aLinkBackGoesAroundEveryCardBetween() {
        Route route = Routes.of(CARDS, AT, List.of(Link.of("b", "a")), Direction.RIGHT, Routes.Rules.DEFAULT)
                .getFirst();

        assertEquals(new Pt(600, 130), route.start());
        assertEquals(new Pt(0, 130), route.end());
        assertClear(route, "wall", "a", "b");
    }

    @Test
    void aLinkBackThroughTheRanksLeavesItsNearSideAndEntersTheFarSide() {
        Route route = Routes.of(CARDS, AT, List.of(Link.of("b", "a")), Direction.RIGHT,
                Routes.Rules.DEFAULT.with(Loops.THROUGH)).getFirst();

        assertEquals(new Pt(500, 130), route.start());
        assertEquals(new Pt(100, 130), route.end());
        assertClear(route, "wall", "a", "b");
        // Past the wall and no further: not around the whole block.
        assertTrue(route.bounds().minX() >= 100 && route.bounds().maxX() <= 500, route.bounds().toString());
    }

    @Test
    void aLinkOntoItsOwnCardHooksBesideItWhenThrough() {
        Route route = Routes.of(CARDS, AT, List.of(Link.of("a", "a")), Direction.RIGHT,
                Routes.Rules.DEFAULT.with(Loops.THROUGH)).getFirst();

        Box bounds = route.bounds();
        assertTrue(bounds.minY() >= 100 && bounds.maxY() <= 160, bounds.toString());
        assertTrue(bounds.minX() >= 100 && bounds.maxX() < 250, bounds.toString());
        assertTrue(route.start().y() < route.end().y());
    }

    @Test
    void aLinkBackStaysUnderTheTopItIsGiven() {
        Route route = Routes.of(CARDS, AT, List.of(Link.of("b", "a")), Direction.RIGHT, new Routes.Rules(0, 0))
                .getFirst();

        assertTrue(route.bounds().minY() >= 0, route.bounds().toString());
    }

    @Test
    void aPortSetsWhereTheLinkLeaves() {
        Card ported = new Card("a", 100, 60, Map.of("low", 50.0));
        Route route = Routes.of(List.of(ported, B), AT, List.of(new Link("a", "low", "b")), Direction.RIGHT,
                new Routes.Rules(6, Double.NEGATIVE_INFINITY)).getFirst();

        assertEquals(new Pt(94, 150), route.start());
        assertEquals(new Pt(506, 130), route.end());
    }

    @Test
    void downwardsALinkLeavesTheBottomAndEntersTheTop() {
        Map<String, Pt> at = Map.of("a", new Pt(100, 0), "b", new Pt(100, 300));
        Route route = Routes.of(List.of(A, B), at, List.of(Link.of("a", "b")), Direction.DOWN, Routes.Rules.DEFAULT)
                .getFirst();

        assertEquals(new Pt(150, 60), route.start());
        assertEquals(new Pt(150, 300), route.end());
    }

    @Test
    void theExtentHoldsCardsAndRoutes() {
        List<Route> routes = Routes.of(CARDS, AT, List.of(Link.of("b", "a")), Direction.RIGHT, Routes.Rules.DEFAULT);
        Box extent = Routes.extent(CARDS, AT, routes);

        for (Route route : routes) {
            Box bounds = route.bounds();
            assertTrue(extent.minY() <= bounds.minY() && extent.maxY() >= bounds.maxY());
        }
        assertTrue(extent.minX() <= 0 && extent.maxX() >= 600);
    }
}
