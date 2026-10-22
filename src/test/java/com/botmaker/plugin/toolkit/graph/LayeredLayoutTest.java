package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Loops;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes.Route;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayeredLayoutTest {

    private static Card card(String id) {
        return Card.of(id, 120, 60);
    }

    static void assertNoOverlap(List<Card> cards, Placement placement) {
        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                Card a = cards.get(i);
                Card b = cards.get(j);
                if (!placement.at().containsKey(a.id()) || !placement.at().containsKey(b.id())) continue;
                Box boxA = a.at(placement.at().get(a.id()));
                Box boxB = b.at(placement.at().get(b.id()));
                assertFalse(boxA.overlaps(boxB), a.id() + " overlaps " + b.id());
            }
        }
    }

    @Test
    void aWayBackThreadsBackThroughTheRanksClearOfEveryCard() {
        List<Card> cards = List.of(card("home"), card("a"), card("c"), card("d"), card("b"));
        List<Link> links = List.of(Link.of("home", "a"), Link.of("home", "c"), Link.of("home", "d"),
                Link.of("a", "b"), Link.of("b", "home"));
        Placement placement = LayeredLayout.arrange(cards, links, List.of("home"), Direction.RIGHT, Gaps.DEFAULT,
                LayeredLayout.SWEEPS, Loops.THROUGH);
        Route back = Routes.of(cards, placement.at(), List.of(Link.of("b", "home")), Direction.RIGHT,
                Routes.Rules.DEFAULT.with(Loops.THROUGH)).getFirst();

        assertNoOverlap(cards, placement);
        assertEquals(placement.at().get("b").x(), back.start().x(), 0.01);
        assertEquals(placement.at().get("home").x() + 120, back.end().x(), 0.01);
        for (Card card : cards) {
            Box box = card.at(placement.at().get(card.id()));
            for (Pt p : back.sample(3)) assertFalse(box.holds(p, 1), "through " + card.id() + " at " + p);
        }
        Box block = placement.extent(cards);
        assertTrue(back.bounds().minY() >= block.minY() && back.bounds().maxY() <= block.maxY(),
                "around the block: " + back.bounds());
    }

    @Test
    void aChainRunsLeftToRightWithALoopBackLeftOutOfTheRanks() {
        List<Card> cards = List.of(card("a"), card("b"), card("c"));
        List<Link> links = List.of(Link.of("a", "b"), Link.of("b", "c"), Link.of("c", "a"));

        Placement placement = LayeredLayout.arrange(cards, links, List.of("a"));

        double a = placement.at().get("a").x();
        double b = placement.at().get("b").x();
        double c = placement.at().get("c").x();
        assertTrue(a < b && b < c, "ranks in order: " + placement.at());
        assertNoOverlap(cards, placement);
    }

    @Test
    void aLinkThatSkipsARankMakesRoomForItself() {
        List<Card> cards = List.of(card("a"), card("b"), card("c"), card("d"));
        List<Link> links = List.of(Link.of("a", "b"), Link.of("b", "c"), Link.of("a", "c"), Link.of("a", "d"),
                Link.of("d", "c"));

        Placement placement = LayeredLayout.arrange(cards, links, List.of("a"));

        assertEquals(placement.at().get("b").x(), placement.at().get("d").x());
        assertNoOverlap(cards, placement);
    }

    @Test
    void portsKeepTheirOrderDownTheNextRank() {
        Card root = new Card("r", 120, 80, Map.of("first", 15.0, "second", 65.0));
        List<Card> cards = List.of(root, card("y"), card("x"));
        List<Link> links = List.of(new Link("r", "second", "y"), new Link("r", "first", "x"));

        Placement placement = LayeredLayout.arrange(cards, links, List.of("r"));

        assertTrue(placement.at().get("x").y() < placement.at().get("y").y(), placement.at().toString());
    }

    @Test
    void noSweepKeepsTheOrderTheCardsAreListedIn() {
        Card root = new Card("r", 120, 80, Map.of("first", 15.0, "second", 65.0));
        List<Card> cards = List.of(root, card("y"), card("x"));
        List<Link> links = List.of(new Link("r", "second", "y"), new Link("r", "first", "x"));

        Placement placement = LayeredLayout.arrange(cards, links, List.of("r"), Direction.RIGHT, Gaps.DEFAULT, 0);

        assertTrue(placement.at().get("y").y() < placement.at().get("x").y(), placement.at().toString());
    }

    @Test
    void cardsNoRootReachesGoInAGridBelow() {
        List<Card> cards = List.of(card("a"), card("b"), card("lone"));
        Placement placement = LayeredLayout.arrange(cards, List.of(Link.of("a", "b")), List.of("a"));

        double blockBottom = placement.at().get("a").y() + 60;
        assertTrue(placement.at().get("lone").y() > blockBottom, placement.at().toString());
        assertNoOverlap(cards, placement);
    }

    @Test
    void withNoRootTheRootsAreTheCardsNothingEnters() {
        List<Card> cards = List.of(card("b"), card("a"));
        Placement placement = LayeredLayout.arrange(cards, List.of(Link.of("a", "b")), List.of());

        assertTrue(placement.at().get("a").x() < placement.at().get("b").x(), placement.at().toString());
    }

    @Test
    void downwardsTheRanksRunDown() {
        List<Card> cards = List.of(card("a"), card("b"), card("c"));
        List<Link> links = List.of(Link.of("a", "b"), Link.of("a", "c"));

        Placement placement = LayeredLayout.arrange(cards, links, List.of("a"), Direction.DOWN, Gaps.DEFAULT,
                LayeredLayout.SWEEPS);

        assertTrue(placement.at().get("a").y() < placement.at().get("b").y());
        assertEquals(placement.at().get("b").y(), placement.at().get("c").y());
        assertNoOverlap(cards, placement);
    }
}
