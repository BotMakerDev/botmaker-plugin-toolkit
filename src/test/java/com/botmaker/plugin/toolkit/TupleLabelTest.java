package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a tuple pill says — the one piece of a geometry editor assertable with no JavaFX toolkit, and the
 * piece worth asserting: the number a user reads off the pill is what the last pick wrote, and getting it
 * wrong shows one coordinate while the bot runs another.
 *
 * <p>The three answers are exactly the three states of {@code ValueContext.value}: nothing written, a
 * value this type describes, and an expression it does not.
 *
 * <p><b>The third is the one this replaced a worse test for.</b> The old label asked
 * {@code Slots.holdsNumbers}, which required the source to start with {@code "new "} — so
 * {@code Point.of(1, 2)} read as not-numbers and showed raw source for a value it could have labelled,
 * while {@code new Point(a, b)} read as numbers and labelled a pair of variables {@code 0, 0}. The
 * question is "did the grammar decode this", and the grammar is the one that answers it now.
 */
class TupleLabelTest {

    record Rect(int x, int y, int width, int height) {}

    static final ComponentType<Rect> RECT = new ComponentType<>() {
        @Override public Class<Rect> type() { return Rect.class; }
        @Override public List<Class<?>> componentTypes() {
            return List.of(int.class, int.class, int.class, int.class);
        }
        @Override public List<Object> components(Rect r) {
            return List.of(r.x(), r.y(), r.width(), r.height());
        }
        @Override public Rect build(List<Object> parts) {
            return new Rect((int) parts.get(0), (int) parts.get(1), (int) parts.get(2), (int) parts.get(3));
        }
    };

    static final Editors.TupleSpec SPEC = new Editors.TupleSpec("Region",
            new String[] {"X", "Y", "Width", "Height"}, "Choose region…", Editors.Pick.REGION,
            n -> n[0] + ", " + n[1] + "  " + n[2] + "×" + n[3]);

    @Test
    void anEmptySlotReadsAsItsPlaceholder() {
        assertEquals("Choose region…", Editors.tupleLabel(TestContexts.typedSlot("Rect", ""), RECT, SPEC));
        assertEquals("Choose region…", Editors.tupleLabel(TestContexts.typedSlot("Rect", "  "), RECT, SPEC));
    }

    @Test
    void aValueTheTypeDescribesIsLabelledTheWayThePluginSpellsIt() {
        var slot = TestContexts.typedSlot("Rect", "new Rect(10, 20, 640, 480)")
                .withValue(new Rect(10, 20, 640, 480));

        assertEquals("10, 20  640×480", Editors.tupleLabel(slot, RECT, SPEC));
    }

    /**
     * An expression the grammar could not read shows as written, because rewriting {@code target.bounds()}
     * into {@code 0, 0  0×0} is a lie about what the bot does.
     */
    @Test
    void anExpressionTheGrammarCannotReadIsShownAsWritten() {
        assertEquals("target.bounds()",
                Editors.tupleLabel(TestContexts.typedSlot("Rect", "target.bounds()"), RECT, SPEC));
        assertEquals("BOUNDS",
                Editors.tupleLabel(TestContexts.typedSlot("Rect", "BOUNDS"), RECT, SPEC));
    }

    /** A spelling that is not a constructor is no longer a reason to refuse a label. */
    @Test
    void aFactoryCallLabelsJustAsAConstructorDoes() {
        var factory = TestContexts.typedSlot("Rect", "Rect.of(1, 2, 3, 4)")
                .withValue(new Rect(1, 2, 3, 4));

        assertEquals("1, 2  3×4", Editors.tupleLabel(factory, RECT, SPEC));
    }
}
