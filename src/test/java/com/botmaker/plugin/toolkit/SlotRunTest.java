package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.SlotRun;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an editor may assume about {@link SlotRun}, asserted through the recording context a plugin author
 * would use.
 *
 * <p>Every case here is a state a real slot reaches, and in each of them the answer an editor gets is one it
 * can act on rather than an exception — the same rule {@code ValuesTest} holds for values.
 */
class SlotRunTest {

    @Test
    void aSlotStandingAloneHasNoRun() {
        // The ordinary case, and the one an editor must handle first: nearly every slot is a single argument.
        assertTrue(TestContexts.slot("Mouse", "click", 0, "new Point(1, 2)").siblingRun().isEmpty());
        assertTrue(TestContexts.row("java.awt.Color", "0xff0000").siblingRun().isEmpty());
    }

    @Test
    void aRunReportsItsElementsInOrder() {
        SlotRun run = TestContexts.slot("Matches", "hasAny", 0, "a")
                .withRun("a", "b", "c")
                .siblingRun().orElseThrow();
        assertEquals(List.of("a", "b", "c"), run.elements().stream().map(SlotRun.Element::value).toList());
        assertEquals(0, run.minimum());
        assertTrue(run.allowed().isEmpty());
    }

    @Test
    void replacingTheRunWritesTheWholeList() {
        TestContexts.Recording ctx = TestContexts.slot("Matches", "hasAny", 0, "a").withRun("a", "b");
        ctx.siblingRun().orElseThrow().replace(List.of("a", "b", "c"));

        assertEquals(List.of("a", "b", "c"), ctx.runReplacement());
        // The run's own view moves with it, so a second edit reads what the first one wrote.
        assertEquals(List.of("a", "b", "c"),
                ctx.siblingRun().orElseThrow().elements().stream().map(SlotRun.Element::value).toList());
        assertEquals(1, ctx.writes());
    }

    @Test
    void anElementHandedBackIsKeptAsWritten() {
        // A variable in the run has no value; rewriting the run around it must not lose it.
        SlotRun.Element unread = new SlotRun.Element(null, "someVariable");
        TestContexts.Recording ctx = TestContexts.slot("Matches", "hasAny", 0, "a")
                .withRun(List.of(unread), 0, null);

        ctx.siblingRun().orElseThrow().replace(List.of(unread, "b"));

        assertSame(unread, ctx.siblingRun().orElseThrow().elements().getFirst());
    }

    @Test
    void aRunBelowItsMinimumIsRefusedRatherThanWritten() {
        // The floor is the host's knowledge, not the plugin's: a guarded branch stops compiling without it.
        // An editor that ignores it must not be able to produce source that will not build.
        TestContexts.Recording ctx = TestContexts.slot("Matches", "hasAny", 0, "a")
                .withRun(List.of(new SlotRun.Element("a", "a"), new SlotRun.Element("b", "b")), 2, null);

        ctx.siblingRun().orElseThrow().replace(List.of("a"));

        assertNull(ctx.runReplacement());
        assertEquals(2, ctx.siblingRun().orElseThrow().elements().size());
        assertEquals(0, ctx.writes());
    }

    @Test
    void narrowingIsValues() {
        SlotRun run = TestContexts.slot("Matches", "hasAny", 0, "a")
                .withRun(List.of(new SlotRun.Element("gold", "gold")), 1, List.of("gold", "ore"))
                .siblingRun().orElseThrow();

        assertEquals(List.of("gold", "ore"), run.allowed().orElseThrow());
    }

    @Test
    void buildingTheContextWritesNothing() {
        TestContexts.Recording ctx = TestContexts.slot("Matches", "hasAny", 0, "a").withRun("a", "b");
        assertEquals(0, ctx.writes());
    }
}
