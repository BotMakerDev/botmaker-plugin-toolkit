package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.SlotRun;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        assertEquals(List.of("a", "b", "c"), run.elements());
        assertEquals(0, run.minimum());
        assertTrue(run.allowedSources().isEmpty());
    }

    @Test
    void replacingTheRunWritesTheWholeList() {
        TestContexts.Recording ctx = TestContexts.slot("Matches", "hasAny", 0, "a").withRun("a", "b");
        ctx.siblingRun().orElseThrow().replace(List.of("a", "b", "c"), "com.example.Thing");

        assertEquals(List.of("a", "b", "c"), ctx.runReplacement());
        // The run's own view moves with it, so a second edit reads what the first one wrote.
        assertEquals(List.of("a", "b", "c"), ctx.siblingRun().orElseThrow().elements());
        assertEquals(List.of("com.example.Thing"), ctx.imports());
        assertEquals(1, ctx.writes());
    }

    @Test
    void aRunBelowItsMinimumIsRefusedRatherThanWritten() {
        // The floor is the host's knowledge, not the plugin's: a guarded branch stops compiling without it.
        // An editor that ignores it must not be able to produce source that will not build.
        TestContexts.Recording ctx = TestContexts.slot("Matches", "hasAny", 0, "a")
                .withRun(List.of("a", "b"), 2, null);

        ctx.siblingRun().orElseThrow().replace(List.of("a"));

        assertNull(ctx.runReplacement());
        assertEquals(List.of("a", "b"), ctx.siblingRun().orElseThrow().elements());
        assertEquals(0, ctx.writes());
    }

    @Test
    void narrowingIsElementSourcesRatherThanDecodedValues() {
        // Java source on both sides: the host computes the allowed set by looking at the code around the run,
        // which it can do without knowing what any of the strings mean.
        SlotRun run = TestContexts.slot("Matches", "hasAny", 0, "new ImageTemplate(\"gold.png\")")
                .withRun(List.of("new ImageTemplate(\"gold.png\")"), 1,
                        List.of("new ImageTemplate(\"gold.png\")", "new ImageTemplate(\"ore.png\")"))
                .siblingRun().orElseThrow();

        List<String> allowed = run.allowedSources().orElseThrow();
        assertEquals(2, allowed.size());
        assertTrue(allowed.getFirst().startsWith("new ImageTemplate("));
    }

    @Test
    void buildingTheContextWritesNothing() {
        TestContexts.Recording ctx = TestContexts.slot("Matches", "hasAny", 0, "a").withRun("a", "b");
        assertEquals(0, ctx.writes());
    }
}
