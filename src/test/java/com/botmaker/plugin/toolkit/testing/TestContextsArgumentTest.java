package com.botmaker.plugin.toolkit.testing;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A test slot can say what its neighbours hold, as the host does through {@code argumentValue}. */
class TestContextsArgumentTest {

    @Test
    void aSiblingArgumentIsReadAsItsOwnType() {
        var ctx = TestContexts.typedSlot(Object.class, "").withArgument(0, Color.RED);

        assertEquals(Color.RED, ctx.argumentValue(0, Color.class).orElseThrow());
        assertTrue(ctx.argumentValue(0, String.class).isEmpty(), "another type answers empty");
        assertTrue(ctx.argumentValue(1, Color.class).isEmpty(), "an argument never given answers empty");
    }

    @Test
    void aRowHasNoNeighbours() {
        var row = TestContexts.row(String.class, "\"x\"").withArgument(0, Color.RED);

        assertTrue(row.slot().isEmpty());
    }
}
