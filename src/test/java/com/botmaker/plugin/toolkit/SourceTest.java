package com.botmaker.plugin.toolkit;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
 * <p>Since 2026-09-01 one of those wrong answers is refused by javac instead: an argument is a
 * {@link Source.Expr} rather than an {@code Object}, so text and an expression cannot be confused. The
 * cases for it, and for {@link Source#requireMethod}, are at the bottom.
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
        expected.forEach((text, literal) -> assertEquals(literal, Source.string(text).source()));
    }

    /**
     * The one behaviour JavaPoet gets wrong for a slot, pinned so nobody "simplifies" this onto {@code $S}.
     *
     * <p>{@code CodeBlock.of("$S", "a\nb")} emits {@code "a\n" + "b"} across two source lines, which is
     * right for a generated file and wrong for a slot: the host writes the result into the middle of an
     * existing line, and one slot holds one expression.
     */
    @Test
    void a_multi_line_string_stays_one_expression() {
        String literal = Source.string("first\nsecond\nthird").source();
        assertEquals("\"first\\nsecond\\nthird\"", literal);
        assertFalse(literal.contains("\n"), "a slot expression may not span source lines");
        assertFalse(literal.contains("+"), "a slot expression is not a concatenation");
    }

    @Test
    void a_control_character_becomes_a_unicode_escape() {
        assertEquals("\"bell\\u0007\"", Source.string("bell\u0007").source());
    }

    @Test
    void a_null_string_is_the_empty_literal_rather_than_null() {
        assertEquals("\"\"", Source.string(null).source());
    }

    @Test
    void a_char_literal_escapes_its_own_quote_and_not_the_other_one() {
        assertEquals("'a'", Source.character('a').source());
        assertEquals("'\\''", Source.character('\'').source());
        assertEquals("'\"'", Source.character('"').source());
        assertEquals("'\\n'", Source.character('\n').source());
        assertEquals("'\\\\'", Source.character('\\').source());
    }

    @Test
    void a_whole_number_reads_as_a_count_and_a_fraction_as_a_decimal() {
        assertEquals("3", Source.number(3.0).source());
        assertEquals("0.75", Source.number(0.75).source());
        assertEquals("-2", Source.number(-2.0).source());
        assertEquals("500", Source.number(500L).source());
    }

    @Test
    void a_constructor_names_its_type_in_full_and_keeps_argument_order() {
        assertEquals("new java.awt.Point(12, 34)",
                Source.newInstance(java.awt.Point.class, Source.number(12), Source.number(34)));
        assertEquals("new java.lang.String()", Source.newInstance(String.class));
    }

    @Test
    void a_nested_type_is_spelled_the_way_source_spells_it() {
        assertEquals("java.util.Map.Entry", Source.type(Map.Entry.class));
    }

    @Test
    void an_enum_constant_is_qualified_by_its_own_declaring_type() {
        assertEquals("java.time.DayOfWeek.MONDAY", Source.enumConstant(java.time.DayOfWeek.MONDAY).source());
        assertEquals("null", Source.enumConstant(null).source());
    }

    @Test
    void slots_quote_is_the_same_answer_because_it_is_the_same_code() {
        assertEquals(Source.string("a\tb").source(), Slots.quote("a\tb"));
    }

    // ---- an argument is source, and the type now says so -----------------------------------------------

    /**
     * The distinction {@link Source.Expr} exists for, in the two spellings that used to be one type.
     *
     * <p>Passing a bare {@code "name"} no longer compiles — that is the whole point, and the reason there is
     * no test asserting what it used to emit. Text is {@link Source#string}; an expression the caller
     * vouches for is {@link Source#code}.
     */
    @Test
    void text_and_an_expression_are_different_types_rather_than_different_readings() {
        assertEquals("new java.awt.Point(\"ding\")",
                Source.newInstance(java.awt.Point.class, Source.string("ding")));
        assertEquals("new java.awt.Point(name)",
                Source.newInstance(java.awt.Point.class, Source.code("name")));
    }

    /** An {@code Expr} prints and concatenates as the source it holds, so it drops into a message unchanged. */
    @Test
    void an_expr_reads_as_its_own_source() {
        assertEquals("\"ding\"", Source.string("ding").toString());
        assertEquals("was \"ding\"", "was " + Source.string("ding"));
    }

    /** Blank and null are the same accident, and both are the literal {@code null} rather than empty source. */
    @Test
    void an_empty_expression_is_the_null_literal_and_not_a_hole() {
        assertEquals("null", Source.code(null).source());
        assertEquals("null", Source.code("   ").source());
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

    // ---- reading a literal back --------------------------------------------------------------------

    /**
     * The property that matters: whatever a user typed or pasted, writing it and reading it back is the
     * identity.
     *
     * <p>It is here rather than in a plugin because the escaping is here — an inverse kept in a different
     * file from the thing it inverts is one that drifts, and the drift is silent in exactly the direction
     * that hurts: the value is written correctly and then read as nothing, so the editor shows the user a
     * cell it refuses to edit.
     */
    @Test
    void every_string_survives_being_written_and_read_back() {
        for (String text : new String[] {"", "hello", "a \"quoted\" one", "back\\slash", "tab\there",
                "line\nbreak", "comma, inside", "'single'", "form\ffeed", "bell\u0007", "del\u007f"}) {
            String literal = Source.string(text).source();
            assertEquals(Optional.of(text), Source.stringValue(literal), literal);
        }
    }

    @Test
    void every_character_survives_being_written_and_read_back() {
        for (char c : new char[] {'x', '\'', '"', '\\', '\n', '\t', ' ', '\u0000', '\u007f'}) {
            String literal = Source.character(c).source();
            assertEquals(Optional.of(c), Source.characterValue(literal), literal);
        }
    }

    /**
     * Anything this class did not write answers empty, so the host shows it rather than rewriting it.
     *
     * <p>The concatenation is the one worth naming: {@code "a" + "b"} ends in a quote and begins with one,
     * so a reader checking only the ends would read it as the single string {@code a" + "b}.
     */
    @Test
    void a_source_this_class_did_not_write_is_not_a_literal() {
        assertEquals(Optional.empty(), Source.stringValue("\"a\" + \"b\""));
        assertEquals(Optional.empty(), Source.stringValue("name()"));
        assertEquals(Optional.empty(), Source.stringValue("\"unterminated"));
        assertEquals(Optional.empty(), Source.stringValue("\"\\q\""));      // not an escape we emit
        assertEquals(Optional.empty(), Source.stringValue("\"\\101\""));    // octal: a person wrote this
        assertEquals(Optional.empty(), Source.stringValue(null));
        assertEquals(Optional.empty(), Source.characterValue("'ab'"));
        assertEquals(Optional.empty(), Source.characterValue("c"));
    }

    /** Whitespace a formatter left is the caller's, not the value's. */
    @Test
    void surrounding_whitespace_is_tolerated() {
        assertEquals(Optional.of("hi"), Source.stringValue("  \"hi\"  "));
        assertEquals(Optional.of(' '), Source.characterValue("  ' '  "));
    }

    // ---- imports -----------------------------------------------------------------------------------

    /**
     * {@code Source.imports} is what stops a plugin typing a package path by hand, and the nested case is
     * why it is not {@code getName()}: an import needs {@code Outer.Inner}, never {@code Outer$Inner}.
     */
    @Test
    void imports_are_the_names_an_import_statement_accepts() {
        assertArrayEquals(new String[] {"java.awt.Point", "java.util.Map.Entry"},
                Source.imports(java.awt.Point.class, Map.Entry.class));
        assertArrayEquals(new String[0], Source.imports());
    }
}
