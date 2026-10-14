package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.botmaker.plugin.toolkit.graph.LayeredLayoutTest.assertNoOverlap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TreeLayoutTest {

    private static Card card(String id) {
        return Card.of(id, 140, 70);
    }

    private static double middle(Placement placement, Card card) {
        return placement.at().get(card.id()).y() + card.height() / 2;
    }

    @Test
    void aParentSitsLevelWithTheMiddleOfItsChildren() {
        Card home = card("home");
        List<Card> cards = List.of(home, card("a"), card("b"), card("c"));
        List<Link> links = List.of(Link.of("home", "a"), Link.of("home", "b"), Link.of("home", "c"));

        Placement placement = TreeLayout.arrange(cards, links, "home");

        assertEquals(placement.at().get("a").x(), placement.at().get("c").x());
        assertTrue(placement.at().get("home").x() < placement.at().get("a").x());
        assertEquals(middle(placement, cards.get(2)), middle(placement, home), 0.01);
        assertNoOverlap(cards, placement);
    }

    @Test
    void aLinkAcrossTheTreeShapesNothing() {
        List<Card> cards = List.of(card("home"), card("a"), card("b"));
        List<Link> links = List.of(Link.of("home", "a"), Link.of("a", "b"), Link.of("home", "b"));

        Placement placement = TreeLayout.arrange(cards, links, "home");

        assertEquals(placement.at().get("a").x(), placement.at().get("b").x(), "b is home's child, found first");
    }

    @Test
    void aTallParentPushesItsSubtreeClearOfTheOneAbove() {
        List<Card> cards = new ArrayList<>(List.of(card("home"), card("a"), Card.of("tall", 140, 400), card("a1"),
                card("a2"), card("t1")));
        List<Link> links = List.of(Link.of("home", "a"), Link.of("home", "tall"), Link.of("a", "a1"),
                Link.of("a", "a2"), Link.of("tall", "t1"));

        Placement placement = TreeLayout.arrange(cards, links, "home");

        assertNoOverlap(cards, placement);
        assertEquals(middle(placement, cards.get(2)), middle(placement, cards.get(5)), 0.01, "t1 stays level with tall");
    }

    @Test
    void aWideDeepTreeHasNoOverlap() {
        List<Card> cards = new ArrayList<>(List.of(card("root")));
        List<Link> links = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            cards.add(card("n" + i));
            links.add(Link.of("root", "n" + i));
            for (int j = 0; j < i; j++) {
                cards.add(Card.of("n" + i + "_" + j, 100 + 20 * j, 40 + 30 * j));
                links.add(Link.of("n" + i, "n" + i + "_" + j));
            }
        }

        assertNoOverlap(cards, TreeLayout.arrange(cards, links, "root"));
    }

    @Test
    void aCollapsedCardHidesWhatIsBelowIt() {
        List<Card> cards = List.of(card("home"), card("a"), card("a1"), card("a11"), card("b"));
        List<Link> links = List.of(Link.of("home", "a"), Link.of("a", "a1"), Link.of("a1", "a11"),
                Link.of("home", "b"));

        Placement placement = TreeLayout.arrange(cards, links, "home", Direction.RIGHT, Gaps.DEFAULT, Set.of("a"));

        assertEquals(Set.of("a1", "a11"), placement.hidden());
        assertFalse(placement.at().containsKey("a1"));
        assertTrue(placement.at().containsKey("a"));
    }

    @Test
    void cardsTheRootDoesNotReachGoBelow() {
        List<Card> cards = List.of(card("home"), card("a"), card("popup"));
        Placement placement = TreeLayout.arrange(cards, List.of(Link.of("home", "a")), "home");

        assertTrue(placement.at().get("popup").y() >= placement.at().get("a").y() + 70);
        assertNoOverlap(cards, placement);
    }

    @Test
    void downwardsChildrenAreBelow() {
        List<Card> cards = List.of(card("home"), card("a"), card("b"));
        List<Link> links = List.of(Link.of("home", "a"), Link.of("home", "b"));

        Placement placement = TreeLayout.arrange(cards, links, "home", Direction.DOWN, Gaps.DEFAULT, Set.of());

        assertTrue(placement.at().get("home").y() < placement.at().get("a").y());
        assertEquals(placement.at().get("a").y(), placement.at().get("b").y());
        assertNoOverlap(cards, placement);
    }
}
