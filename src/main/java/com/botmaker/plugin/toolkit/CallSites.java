package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;

import java.util.Map;
import java.util.function.Predicate;

/**
 * Predicates that choose an editor by <em>where</em> a value is used rather than by what type it is.
 *
 * <p>Most editors match on the type, and that is the better answer wherever it works: an editor chosen by
 * type is drawn in a bot's source <b>and</b> in the Parameters window. Some values cannot be told apart that
 * way. A Steam app id, a program path, a command-line flag and a window title are all {@code String}, and the
 * type says nothing about which of them a given slot holds — only the call around it does.
 *
 * <pre>{@code
 * static final Predicate<ValueContext> STEAM_APP_ID =
 *         CallSites.firstArgumentOf(Game.class, "launchSteam", "launchSteamIfNotRunning");
 * }</pre>
 *
 * <h2>Every predicate here declines when there is no call</h2>
 *
 * <p>{@link ValueContext#slot()} is empty for a row of the Parameters window and for a {@code @Managed}
 * value, and neither has a call site by construction — there is no {@code Game.launchSteam(…)} behind a
 * variable named {@code appId}.
 * Declining is the honest answer, and it is why an editor chosen this way is <b>absent</b> from that window
 * rather than misfiring in it. Plan for that: a value that must be editable in both places needs a
 * type-matched editor too, or it needs to be a type of its own.
 *
 * <h2>The class is matched by simple name, or by a qualified name ending in it</h2>
 *
 * <p>The host resolves the call out of the bot's own classpath and may hand back either spelling, and a
 * version of the plugin's library older than the one the editor is running against may have had the class in
 * a different package. Matching the tail is what keeps an old project's blocks drawing their real editors.
 * The cost is stated rather than hidden: a slot on somebody else's {@code Game} matches too, and a plugin for
 * which that is a real risk should give the value a type instead.
 *
 * <p>This is the generic half of a call-site editor. The <em>constants</em> — which class, which method names
 * — are the plugin's own vocabulary and stay with the plugin; nothing in this file names one.
 */
public final class CallSites {

    private CallSites() {}

    /**
     * Argument 0 of any of {@code methods} called on {@code owner}.
     *
     * <p>The overwhelmingly common shape: a call whose first argument is the thing worth an editor, and whose
     * later arguments are a capture source or a timeout with editors of their own.
     */
    public static Predicate<ValueContext> firstArgumentOf(Class<?> owner, String... methods) {
        return argumentOf(owner, 0, methods);
    }

    /** Argument {@code index} of any of {@code methods} called on {@code owner}. */
    public static Predicate<ValueContext> argumentOf(Class<?> owner, int index, String... methods) {
        String[] names = methods == null ? new String[0] : methods.clone();
        return ctx -> {
            SlotContext slot = ctx.slot().orElse(null);
            if (slot == null || slot.argIndex() != index || !isOn(slot, owner)) return false;
            String called = slot.enclosingMethodName().orElse(null);
            for (String method : names) {
                if (method != null && method.equals(called)) return true;
            }
            return false;
        };
    }

    /**
     * Argument 0 of any method on {@code owner} whose name {@code methods} accepts.
     *
     * <p>For the case where the set of method names is already written down somewhere — a table the editor
     * reads to know what to draw. Asking that table rather than repeating its keys is what stops a predicate
     * from claiming a call the table has no entry for, which is an editor that does not know what to do and
     * therefore draws nothing.
     */
    public static Predicate<ValueContext> firstArgumentWhere(Class<?> owner, Predicate<String> methods) {
        return ctx -> {
            SlotContext slot = ctx.slot().orElse(null);
            return slot != null && slot.argIndex() == 0 && isOn(slot, owner)
                   && methods.test(slot.enclosingMethodName().orElse(""));
        };
    }

    /**
     * A trailing (varargs) argument of a call on {@code owner}, where the first varargs position differs per
     * method.
     *
     * <p>{@code firstVarargIndex} maps a method name to the index its varargs begin at, because the fixed
     * parameters differ per overload: {@code run(path, …)} varies from 1, {@code run(path, source, …)} from 2.
     * Below that index the argument is one of the fixed ones, and each of those has an editor of its own. A
     * method not in the map is declined.
     */
    public static Predicate<ValueContext> trailingArgumentOf(Class<?> owner, Map<String, Integer> firstVarargIndex) {
        Map<String, Integer> table = Map.copyOf(firstVarargIndex);
        return ctx -> {
            SlotContext slot = ctx.slot().orElse(null);
            if (slot == null || !isOn(slot, owner)) return false;
            Integer from = table.get(slot.enclosingMethodName().orElse(""));
            return from != null && slot.argIndex() >= from;
        };
    }

    /**
     * Whether the enclosing call is on {@code owner}, by simple name or by a qualified name ending in it.
     *
     * <p>Public because a plugin whose predicate needs a shape none of the above covers should ask this
     * question the same way the rest of them do, rather than re-deriving the two spellings.
     */
    public static boolean isOn(SlotContext slot, Class<?> owner) {
        String name = slot == null ? null : slot.enclosingClassName().orElse(null);
        String simple = owner.getSimpleName();
        return name != null && (name.equals(simple) || name.endsWith("." + simple));
    }
}
