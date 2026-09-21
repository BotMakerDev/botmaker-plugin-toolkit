package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a call-site predicate claims, and — more importantly — what it declines.
 *
 * <p>The declines are the half that goes untested when a plugin author is developing against a bot's source,
 * because every context in front of them is a slot with a call in it. A predicate that forgets to check
 * {@link ValueContext#asSlot()} then reaches the Parameters window, where {@code enclosingClass()} is null and
 * {@code argIndex()} is -1, and claims a row it can draw nothing for.
 *
 * <p>These cases stand for the SDK's five constants too. Those name {@code Game} and {@code BotSettings} and
 * so cannot be tested here — the toolkit may not know what a game is — but every one of them is one of the
 * four shapes below, so the matching itself is covered once rather than per plugin.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CallSitesTest {

    /** Stands in for a plugin's facade; only its simple name is ever read. */
    private static final class Launcher {}

    private static final Predicate<ValueContext> FIRST =
            CallSites.firstArgumentOf(Launcher.class, "start", "startIfStopped");

    // --- the ordinary shape ---

    @Test
    void argument_zero_of_a_named_method_is_claimed() {
        assertTrue(FIRST.test(TestContexts.slot("Launcher", "start", 0, "\"440\"")));
        assertTrue(FIRST.test(TestContexts.slot("Launcher", "startIfStopped", 0, "\"440\"")));
    }

    @Test
    void a_later_argument_of_the_same_method_is_not() {
        assertFalse(FIRST.test(TestContexts.slot("Launcher", "start", 1, "source")),
                "argument 1 is some other parameter, with an editor of its own");
    }

    @Test
    void a_method_that_was_not_named_is_not_claimed() {
        assertFalse(FIRST.test(TestContexts.slot("Launcher", "stop", 0, "\"440\"")));
    }

    @Test
    void a_call_on_a_different_class_is_not_claimed() {
        assertFalse(FIRST.test(TestContexts.slot("Other", "start", 0, "\"440\"")));
    }

    /**
     * The reason the tail is matched rather than the whole name: the host resolves the call out of the bot's
     * own classpath and may hand back either spelling, and an older version of the plugin's library may have
     * had the class in another package.
     */
    @Test
    void a_qualified_name_ending_in_the_simple_name_matches() {
        assertTrue(FIRST.test(TestContexts.slot("com.example.game.Launcher", "start", 0, "\"440\"")));
        assertFalse(FIRST.test(TestContexts.slot("MyLauncher", "start", 0, "\"440\"")),
                "the tail must be a whole segment — a name merely ending in the letters is not a match");
    }

    // --- the decline that matters ---

    @Test
    void a_parameters_row_is_always_declined_because_it_has_no_call() {
        assertFalse(FIRST.test(TestContexts.row("java.lang.String", "\"440\"")));
    }

    @Test
    void a_slot_with_no_call_around_it_is_declined_too() {
        assertFalse(FIRST.test(TestContexts.typedSlot("java.lang.String", "\"440\"")),
                "a field initialiser is a slot, but there is no call to identify the value by");
    }

    // --- the other three shapes ---

    @Test
    void an_argument_at_a_chosen_index_is_claimed() {
        Predicate<ValueContext> second = CallSites.argumentOf(Launcher.class, 2, "start");
        assertTrue(second.test(TestContexts.slot("Launcher", "start", 2, "5")));
        assertFalse(second.test(TestContexts.slot("Launcher", "start", 1, "5")));
    }

    @Test
    void a_trailing_argument_is_claimed_from_the_index_its_own_overload_says() {
        Predicate<ValueContext> option = CallSites.trailingArgumentOf(Launcher.class,
                Map.of("start", 1, "startIfStopped", 2));
        assertFalse(option.test(TestContexts.slot("Launcher", "start", 0, "\"path\"")));
        assertTrue(option.test(TestContexts.slot("Launcher", "start", 1, "\"-windowed\"")));
        assertFalse(option.test(TestContexts.slot("Launcher", "startIfStopped", 1, "source")),
                "argument 1 is still a fixed parameter on this overload");
        assertTrue(option.test(TestContexts.slot("Launcher", "startIfStopped", 2, "\"-windowed\"")));
        assertFalse(option.test(TestContexts.slot("Launcher", "stop", 9, "\"-windowed\"")),
                "a method the table has no entry for is declined rather than defaulted");
    }

    /**
     * Asking a table rather than repeating its keys, which is what keeps a predicate from claiming a call the
     * editor behind it has no configuration for.
     */
    @Test
    void a_method_predicate_can_be_answered_by_the_editors_own_table() {
        Map<String, Integer> table = Map.of("setDelay", 500);
        Predicate<ValueContext> bounded =
                CallSites.firstArgumentWhere(Launcher.class, m -> table.containsKey(m));
        assertTrue(bounded.test(TestContexts.slot("Launcher", "setDelay", 0, "500")));
        assertFalse(bounded.test(TestContexts.slot("Launcher", "setSomethingElse", 0, "500")));
    }

    // --- the recording half of the stub ---

    @Test
    void a_recording_context_reports_what_an_editor_wrote_and_where() {
        TestContexts.Recording slot = TestContexts.slot("Launcher", "start", 0, "\"440\"");
        assertEquals(0, slot.writes(), "building nothing writes nothing");

        Slots.write(slot, "\"550\"");
        assertEquals("\"550\"", slot.written());
        assertEquals(1, slot.writes());

        TestContexts.Recording row = TestContexts.row("java.lang.String", "\"440\"");
        Slots.write(row, "\"550\"");
        assertEquals("\"550\"", row.written(),
                "one spelling everywhere: a row holds the same Java a slot does");
    }
}
