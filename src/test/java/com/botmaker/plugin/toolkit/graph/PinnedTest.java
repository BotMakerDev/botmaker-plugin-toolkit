package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

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
    void noPinsIsTheLayoutItself() {
        assertSame(LAID, LAID.pinned(Map.of()));
    }
}
