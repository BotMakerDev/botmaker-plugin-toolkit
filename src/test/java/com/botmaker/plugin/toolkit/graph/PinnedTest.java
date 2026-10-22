package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
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
}
