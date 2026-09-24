package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Values}, which is what a widget reads a value through.
 *
 * <p>Every case here is a real state a bot's source reaches, and in every one the answer is the caller's
 * fallback rather than an exception — the toolkit's rule 2, restated for typed values. An editor that
 * throws while building its node leaves a row of the Parameters window with no widget in it.
 *
 * <p>The half worth the most is {@link Values#setNumber}: a widget works in {@code double} and a field is
 * declared {@code int}, and only the declaration says which. Getting it wrong writes {@code 3.0} into a
 * pixel count or truncates a confidence to {@code 0}.
 */
class ValuesTest {

    // ---- reading ---------------------------------------------------------------------------------------

    @Test
    void aValueOfTheRightTypeComesBack() {
        assertEquals("gold.png", Values.text(TestContexts.row(String.class,"\"gold.png\"")
                .withValue("gold.png"), ""));
        assertTrue(Values.flag(TestContexts.row(boolean.class,"true").withValue(true), false));
        assertEquals(500, Values.number(TestContexts.row(int.class,"500").withValue(500), -1));
    }

    /**
     * The case an editor most often forgets: an expression the grammar cannot decode. A variable, a
     * computed initializer, {@code target.center()} — the honest answer is the caller's fallback, and the
     * honest thing to draw is what {@link Slots#raw} holds.
     */
    @Test
    void anUndecodableValueAnswersTheFallbackRatherThanZero() {
        var computed = TestContexts.row(double.class,"config.confidence()");

        assertEquals(0.8, Values.number(computed, 0.8), "never 0, which for a confidence means match anything");
        assertEquals("(none)", Values.text(computed, "(none)"));
        assertFalse(Values.flag(computed, false));
        assertEquals("config.confidence()", Slots.raw(computed));
    }

    @Test
    void aValueOfAnotherTypeIsNotThisOne() {
        var flagged = TestContexts.row(boolean.class,"true").withValue(true);

        assertEquals("", Values.text(flagged, ""));
        assertEquals(-1, Values.number(flagged, -1));
    }

    /** Every numeric type answers, which is why the read asks for each box rather than for {@code Number}. */
    @Test
    void everyNumericTypeReadsAsANumber() {
        assertEquals(3, Values.number(TestContexts.row(int.class,"3").withValue(3), -1));
        assertEquals(3, Values.number(TestContexts.row(long.class,"3L").withValue(3L), -1));
        assertEquals(0.5, Values.number(TestContexts.row(double.class,"0.5").withValue(0.5), -1));
        assertEquals(0.5, Values.number(TestContexts.row(float.class,"0.5f").withValue(0.5f), -1));
    }

    // ---- writing ---------------------------------------------------------------------------------------

    /** The declared type decides the box, so a slider at 2.6 on an {@code int} field writes {@code 3}. */
    @Test
    void aNumberIsWrittenAsTheTypeTheFieldIsDeclaredAs() {
        var whole = TestContexts.row(int.class,"0");
        Values.setNumber(whole, 2.6);
        assertEquals(3, whole.value());

        var counted = TestContexts.row(long.class,"0");
        Values.setNumber(counted, 1500.0);
        assertEquals(1500L, counted.value());

        var fraction = TestContexts.row(double.class,"0");
        Values.setNumber(fraction, 0.8);
        assertEquals(0.8, fraction.value());
    }

    /** A box is the same answer as its primitive: which one a field declares is not the widget's business. */
    @Test
    void aBoxedTypeIsTheSameAnswerAsItsPrimitive() {
        var boxed = TestContexts.row(Integer.class,"0");
        Values.setNumber(boxed, 7.0);
        assertEquals(7, boxed.value());
    }

    /** An unresolved or non-numeric type keeps the most information rather than guessing. */
    @Test
    void anUnknownTypeIsWrittenAsADouble() {
        var unknown = TestContexts.row(null, "");
        Values.setNumber(unknown, 1.5);
        assertEquals(1.5, unknown.value());
    }

    // ---- labels ----------------------------------------------------------------------------------------

    @Test
    void anUnsetLabelReadsAsItsPlaceholder() {
        assertEquals("Choose region…", Values.labelOr("", "Choose region…"));
        assertEquals("Choose region…", Values.labelOr("   ", "Choose region…"));
        assertEquals("Choose region…", Values.labelOr(null, "Choose region…"));
        assertEquals("10, 20", Values.labelOr("10, 20", "Choose region…"));
    }
}
