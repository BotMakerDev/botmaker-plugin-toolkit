package com.botmaker.plugin.toolkit.testing;

import com.botmaker.plugin.api.value.Ref;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.PrintStream;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A test slot can say what its neighbours hold, as the host does through {@code argumentValue}, and names its call
 * by reference.
 */
class TestContextsArgumentTest {

    @Test
    void aSiblingArgumentIsReadAsItsOwnType() {
        var ctx = TestContexts.typedSlot(Object.class, "").withArgument(0, Color.RED);

        assertEquals(Color.RED, ctx.argumentValue(0, Color.class).orElseThrow());
        assertTrue(ctx.argumentValue(0, String.class).isEmpty(), "another type answers empty");
        assertTrue(ctx.argumentValue(1, Color.class).isEmpty(), "an argument never given answers empty");
    }

    @Test
    void aCallIsNamedByReference() throws NoSuchMethodException {
        assertEquals(Duration.class.getMethod("ofMillis", long.class), TestContexts.call(Duration::ofMillis));
        assertEquals(PrintStream.class.getMethod("println", String.class),
                TestContexts.voidCall((Ref.Void2<PrintStream, String>) PrintStream::println));
        assertTrue(TestContexts.slot(TestContexts.call(Duration::ofMillis), 0, "3L").slot().isPresent());
    }

    @Test
    void aRowHasNoNeighbours() {
        var row = TestContexts.row(String.class, "\"x\"").withArgument(0, Color.RED);

        assertTrue(row.slot().isEmpty());
    }
}
