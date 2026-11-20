// SPDX-FileCopyrightText: 2026 LiQiyeDev
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0

package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PinnedTest {

    private static final Placement LAID = new Placement(Map.of("A", new Pt(0, 0), "B", new Pt(200, 0)), Set.of("C"), 2);

    @Test
    void aPinMovesOnlyItsCard() {
        Placement pinned = LAID.pinned(Map.of("B", new Pt(40, 300)));
        assertEquals(new Pt(40, 300), pinned.at().get("B"));
        assertEquals(new Pt(0, 0), pinned.at().get("A"));
        assertEquals(Set.of("C"), pinned.hidden());
        assertEquals(2, pinned.crossings());
    }

    @Test
    void aHiddenOrUnknownCardStaysUnplaced() {
        Map<String, Pt> pins = new HashMap<>();
        pins.put("C", new Pt(5, 5));
        pins.put("GONE", new Pt(9, 9));
        pins.put("A", null);
        Placement pinned = LAID.pinned(pins);
        assertFalse(pinned.at().containsKey("C"), "a folded card has no place, pinned or not");
        assertFalse(pinned.at().containsKey("GONE"));
        assertEquals(new Pt(0, 0), pinned.at().get("A"), "a null pin is no pin");
    }

    @Test
    void aCardMovedLosesItsLinksPathsAndAPinOnItsPlaceKeepsThem() {
        Link ab = Link.of("A", "B");
        Link ba = Link.of("B", "A");
        List<Pt> second = List.of(new Pt(100, 30), new Pt(200, 30));
        Placement laid = new Placement(LAID.at(), LAID.hidden(), 2, Map.of(
                ab, List.of(List.of(new Pt(100, 20), new Pt(200, 20)), second),
                ba, List.of(List.of(new Pt(200, 40), new Pt(100, 40)))));

        assertEquals(second, laid.path(ab, 1), "a second edge the same way has its own path");
        assertTrue(laid.path(ab, 2).isEmpty());
        assertEquals(laid.paths(), laid.pinned(Map.of("A", new Pt(0.4, 0), "B", new Pt(200, 0.6))).paths(),
                "a pin rounded to whole pixels moves nothing");
        assertTrue(laid.pinned(Map.of("B", new Pt(40, 300))).paths().isEmpty());
    }

    @Test
    void noPinsIsTheLayoutItself() {
        assertSame(LAID, LAID.pinned(Map.of()));
    }

    /** Five cards pinned in a row, nine a layout laid over them: none left on another, the pinned ones unmoved. */
    @Test
    void pinnedThenSeparatedLeavesNoOverlap() {
        List<Card> cards = new ArrayList<>();
        Map<String, Pt> laid = new LinkedHashMap<>();
        Map<String, Pt> pins = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            cards.add(Card.of("P" + i, 120, 80));
            laid.put("P" + i, new Pt(0, 0));
            pins.put("P" + i, new Pt(i * 160, 0));
        }
        for (int i = 0; i < 9; i++) {
            cards.add(Card.of("F" + i, 100, 60));
            laid.put("F" + i, new Pt((i % 5) * 150 + 10, (i / 5) * 40 + 10));
        }

        Placement apart = new Placement(laid, Set.of(), 0).pinned(pins).apart(cards, Set.of(), pins.keySet(), 20);

        for (int i = 0; i < cards.size(); i++) {
            for (int j = i + 1; j < cards.size(); j++) {
                Card a = cards.get(i);
                Card b = cards.get(j);
                assertFalse(a.at(apart.at().get(a.id())).overlaps(b.at(apart.at().get(b.id()))), a.id() + " on " + b.id());
            }
        }
        pins.forEach((id, p) -> assertEquals(p, apart.at().get(id), id + " pinned, so it stays"));
    }

    /** A pinned card grown 100 px taller pushes the pinned card under it down, and stays where it was pinned. */
    @Test
    void aGrownPinnedCardPushesItsNeighbour() {
        List<Card> cards = List.of(Card.of("TOP", 120, 180), Card.of("UNDER", 120, 80));
        Map<String, Pt> pins = Map.of("TOP", new Pt(0, 0), "UNDER", new Pt(0, 100));

        Placement apart = new Placement(pins, Set.of(), 0).pinned(pins).apart(cards, Set.of(), pins.keySet(), 20);

        assertEquals(new Pt(0, 0), apart.at().get("TOP"));
        assertEquals(new Pt(0, 200), apart.at().get("UNDER"));
    }

    /** A card just dropped on a pinned one stays where it was dropped; the pinned one gives way. */
    @Test
    void aCardDroppedOnAnotherPushesIt() {
        List<Card> cards = List.of(Card.of("A", 100, 60), Card.of("B", 100, 60));
        Map<String, Pt> pins = Map.of("A", new Pt(0, 0), "B", new Pt(10, 10));

        Placement apart = new Placement(pins, Set.of(), 0).pinned(pins).apart(cards, Set.of("B"), pins.keySet(), 20);

        assertEquals(new Pt(10, 10), apart.at().get("B"));
        assertFalse(cards.get(0).at(apart.at().get("A")).overlaps(cards.get(1).at(apart.at().get("B"))));
    }

    @Test
    void cardsApartAlreadyAreTheLayoutItself() {
        assertSame(LAID, LAID.apart(List.of(Card.of("A", 100, 60), Card.of("B", 100, 60)), Set.of(), Set.of(), 20));
    }
}
