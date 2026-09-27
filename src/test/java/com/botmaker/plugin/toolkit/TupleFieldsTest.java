package com.botmaker.plugin.toolkit;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TupleFieldsTest {

    @Test
    void aPasteOfAllTheNumbersFillsEveryField() {
        assertArrayEquals(new int[] {120, 340}, TupleFields.paste("120, 340", 2).orElseThrow());
        assertArrayEquals(new int[] {10, 20, 30, 40}, TupleFields.paste("new Rect(10, 20, 30, 40)", 4).orElseThrow());
        assertArrayEquals(new int[] {-5, 7}, TupleFields.paste("x=-5 y=7", 2).orElseThrow());
    }

    @Test
    void oneNumberOrTheWrongCountIsNotAPaste() {
        assertEquals(Optional.empty(), TupleFields.paste("120", 2), "a single number is typing, not a paste");
        assertEquals(Optional.empty(), TupleFields.paste("1, 2, 3", 2));
        assertEquals(Optional.empty(), TupleFields.paste("", 2));
    }

    @Test
    void aStepIsOneOrTenWithShift() {
        assertEquals(6, TupleFields.step(5, 1, false));
        assertEquals(-5, TupleFields.step(5, -1, true));
    }

    @Test
    void aRectangleSaysWhereItEnds() {
        assertEquals("right 40 · bottom 60", TupleFields.readout(new int[] {10, 20, 30, 40}));
        assertTrue(TupleFields.readout(new int[] {10, 20}).isEmpty(), "a point or a size has nothing derived");
    }
}
