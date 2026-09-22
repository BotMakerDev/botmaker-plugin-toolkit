package com.botmaker.plugin.toolkit;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Source}, whose output is compiled by somebody else's build.
 *
 * <p>Every case here is a character a user can put in a value without seeing it — a pasted tab, a newline
 * out of a text area, a Windows path full of backslashes — and in each one a wrong answer is a compile
 * error in a <em>bot</em>, reported against a line its author did not write. That is why this is worth
 * asserting where "the builder returned non-null" is not.
 *
 * <p><b>It was three times this size until 2026-09-22.</b> A plugin does not emit Java any more — a value
 * crosses as a value and the host spells it — so {@code newInstance}, {@code enumConstant}, {@code number},
 * {@code character}, {@code imports}, {@code code} and the {@code Expr} type they took are deleted, with
 * the readers {@code stringValue} and {@code characterValue}. Reading is the host's half of the same job,
 * and it now has exactly one implementation.
 *
 * <p>What is left is what the SDK's macro translator still needs: a recording pasted into a user's file is
 * statements, not a value, so it has no type for the host to spell it from.
 */
class SourceTest {

    @Test
    void a_string_literal_escapes_everything_that_cannot_appear_literally() {
        Map<String, String> expected = Map.of(
                "plain", "\"plain\"",
                "with \"quotes\"", "\"with \\\"quotes\\\"\"",
                "C:\\Games\\bot.exe", "\"C:\\\\Games\\\\bot.exe\"",
                "line\nbreak", "\"line\\nbreak\"",
                "tab\there", "\"tab\\there\"",
                "cr\rhere", "\"cr\\rhere\"",
                "", "\"\"");
        expected.forEach((text, literal) -> assertEquals(literal, Source.string(text)));
    }

    /**
     * The one behaviour a code generator gets wrong for a pasted statement, pinned so nobody "simplifies"
     * this back onto JavaPoet's {@code $S}.
     *
     * <p>{@code CodeBlock.of("$S", "a\nb")} emits {@code "a\n" + "b"} across two source lines, which is
     * right for a generated file and wrong here: the result goes into the middle of an existing line.
     */
    @Test
    void a_multi_line_string_stays_one_expression() {
        String literal = Source.string("first\nsecond\nthird");
        assertEquals("\"first\\nsecond\\nthird\"", literal);
        assertFalse(literal.contains("\n"), "a pasted expression may not span source lines");
        assertFalse(literal.contains("+"), "a pasted expression is not a concatenation");
    }

    @Test
    void a_control_character_becomes_a_unicode_escape() {
        assertEquals("\"bell\\u0007\"", Source.string("bell\u0007"));
    }

    @Test
    void a_null_string_is_the_empty_literal_rather_than_null() {
        assertEquals("\"\"", Source.string(null));
    }

    // ---- naming a type ---------------------------------------------------------------------------------

    /**
     * The one thing JavaPoet was kept for, and the reason it was not needed: an import and an expression
     * both want {@code Outer.Inner}, and {@link Class#getName()} gives {@code Outer$Inner}.
     * {@link Class#getCanonicalName()} is what {@code ClassName.get} reads too.
     */
    @Test
    void a_nested_type_is_spelled_the_way_source_spells_it() {
        assertEquals("java.util.Map.Entry", Source.type(Map.Entry.class));
        assertEquals("java.awt.Point", Source.type(java.awt.Point.class));
        assertEquals("int", Source.type(int.class));
        assertEquals("java.lang.String[]", Source.type(String[].class));
        assertEquals("", Source.type(null));
    }

    /** A class with no canonical name at all still gets the closest thing to a spelling rather than null. */
    @Test
    void a_class_with_no_canonical_name_falls_back_rather_than_answering_null() {
        Runnable anonymous = new Runnable() {
            @Override public void run() {}
        };
        assertFalse(Source.type(anonymous.getClass()).isEmpty());
        assertFalse(Source.type(anonymous.getClass()).contains("$"));
    }

    // ---- a method name is checked against its class ----------------------------------------------------

    /** The name that resolves is simply accepted, which is the case every other one here is measured from. */
    @Test
    void a_method_the_class_declares_is_accepted() {
        assertDoesNotThrow(() -> Source.requireMethod(String.class, "valueOf"));
    }

    @Test
    void a_method_the_class_does_not_declare_is_refused() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Source.requireMethod(String.class, "valueOff"));
        assertTrue(e.getMessage().contains("valueOff"), e.getMessage());
        assertTrue(e.getMessage().contains("valueOf"), "the near miss is worth naming: " + e.getMessage());
    }

    /**
     * Declared, not inherited — the rule {@code PaletteCatalog} applies for the same reason.
     *
     * <p>{@code Object} declares {@code wait}, so without this rule a caller checking {@code "wait"} against
     * {@code Point} would be told to go ahead and write a static call to an instance method of a supertype:
     * source that compiles nowhere and looks deliberate.
     */
    @Test
    void an_inherited_method_does_not_count_as_declared() {
        assertThrows(IllegalArgumentException.class,
                () -> Source.requireMethod(java.awt.Point.class, "wait"));
    }

    @Test
    void a_missing_type_or_name_is_refused_before_anything_is_emitted() {
        assertThrows(IllegalArgumentException.class, () -> Source.requireMethod(null, "valueOf"));
        assertThrows(IllegalArgumentException.class, () -> Source.requireMethod(String.class, " "));
    }
}
