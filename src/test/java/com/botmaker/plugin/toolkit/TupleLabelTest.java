package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.Editors.Pick;
import com.botmaker.plugin.toolkit.Editors.TupleSpec;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What {@link Editors#tupleLabel} puts on a collapsed pill, and the {@link Slots#holdsNumbers} check under
 * it.
 *
 * <p>Both arrived here on 2026-08-28 out of the SDK's {@code GeometryEditors}, whose own
 * {@code GeometryLabelTest} keeps asserting the same round trip through the three specs it still owns. This
 * is the half that belongs to the <em>shape</em>: a spec's formatter is reached exactly when the value is
 * numbers, and never otherwise.
 *
 * <p>No JavaFX toolkit is needed to ask any of it, which is the reason the label is a separate method rather
 * than something only reachable by building the pill.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TupleLabelTest {

    /** A stand-in for the SDK's {@code Rect}: four numbers, an origin and a size. */
    private record Box(int x, int y, int width, int height) {}

    private static final TupleSpec SPEC = new TupleSpec(Box.class, "Box",
            new String[]{"x", "y", "width", "height"}, "Choose box…", Pick.REGION,
            v -> v[0] + ", " + v[1] + "  " + v[2] + "×" + v[3]);

    private static final TupleSpec PAIR = new TupleSpec(Box.class, "Pair",
            new String[]{"a", "b"}, "Choose pair…", Pick.NONE, v -> v[0] + " × " + v[1]);

    private static String label(String source) {
        return Editors.tupleLabel(TestContexts.typedSlot("", source), SPEC);
    }

    @Test
    void a_constructor_is_read_back_through_the_spec_formatter() {
        assertEquals("10, 20  640×480", label("new Box(10, 20, 640, 480)"));
    }

    @Test
    void an_empty_slot_shows_the_placeholder_rather_than_zeroes() {
        assertEquals("Choose box…", label("   "));
    }

    /** A half-written constructor is what a freshly inserted block looks like before the user picks. */
    @Test
    void a_missing_argument_reads_as_zero() {
        assertEquals("10, 20  0×0", label("new Box(10, 20)"));
    }

    /**
     * The formatter is not reached at all for an expression that is not a construction. Rewriting somebody's
     * {@code target.bounds()} into "0, 0  0×0" would claim a value they never set.
     */
    @Test
    void a_non_constructor_expression_is_shown_verbatim() {
        assertEquals("bounds", label("bounds"));
        assertEquals("target.bounds()", label("target.bounds()"));
        assertEquals("new Box(abc, 2, 3, 4)", label("new Box(abc, 2, 3, 4)"));
    }

    @Test
    void a_spec_with_fewer_numbers_reads_only_those() {
        assertEquals("1 × 2", Editors.tupleLabel(TestContexts.typedSlot("", "new Box(1, 2, 3)"), PAIR));
    }

    /** A value with no call site — a Parameters row, a {@code @Managed} method — reads the same way. */
    @Test
    void a_value_with_no_call_site_is_labelled_the_same_way() {
        ValueContext row = TestContexts.row("", "new Box(10, 20, 640, 480)");
        assertEquals("10, 20  640×480", Editors.tupleLabel(row, SPEC));
        assertEquals("Choose box…", Editors.tupleLabel(TestContexts.row("", ""), SPEC));
    }

    @Test
    void a_value_holding_something_that_is_not_a_number_is_shown_verbatim() {
        assertEquals("target.center()",
                Editors.tupleLabel(TestContexts.row("", "target.center()"), SPEC));
    }

    // --- The check itself ---

    @Test
    void java_integer_literals_count_as_numbers() {
        assertTrue(Slots.holdsNumbers(TestContexts.typedSlot("", "new Box(100L, 1_000, -3, +4)"), 4));
    }

    @Test
    void a_value_that_is_not_a_constructor_call_does_not_hold_numbers() {
        assertFalse(Slots.holdsNumbers(TestContexts.row("", "target.bounds()"), 4));
    }

    /** Building a label must never write — the toolkit's rule that opening a project changes nothing. */
    @Test
    void labelling_writes_nothing() {
        TestContexts.Recording ctx = TestContexts.typedSlot("", "new Box(1, 2, 3, 4)");
        Editors.tupleLabel(ctx, SPEC);
        assertEquals(0, ctx.writes());
    }
}
