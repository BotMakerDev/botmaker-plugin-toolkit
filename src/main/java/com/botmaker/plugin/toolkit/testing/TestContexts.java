package com.botmaker.plugin.toolkit.testing;

import com.botmaker.plugin.api.slot.Bounds;
import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.SlotRun;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.TypeRef;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.value.Ref;

import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Contexts a plugin's editors can be exercised against without a running Studio.
 *
 * <p>A {@code SlotEditor} is a predicate and a factory over a {@link ValueContext}, and both halves are worth
 * a test: <em>does this predicate claim the calls I meant and decline the ones I did not</em>, and <em>does
 * this factory build a node and write back what I expect</em>. Neither question needs a host, a project or a
 * JavaFX thread — but until this class existed every plugin author had to write the two stubs first, which is
 * enough friction that the predicate half generally went untested.
 *
 * <pre>{@code
 * var ctx = TestContexts.slot(TestContexts.voidCall(Game::launchSteam), 0, "\"440\"");
 * assertTrue(STEAM_APP_ID.matches(ctx));
 * assertFalse(STEAM_APP_ID.matches(TestContexts.row(String.class, "\"440\"")));
 * }</pre>
 *
 * <h2>What is real and what is not</h2>
 *
 * <p>The value, the type, the call site and the writes are <b>real</b>: {@link Recording#value()} returns
 * exactly the value the editor handed back, which is what the host would have spelled into the file.
 * {@link ValueContext#services()} is <b>not</b>: it answers {@code null}, because every one of those
 * services is the host doing something a test has no way to fake — dragging a region on a real screen, owning
 * a real window. An editor that reaches for one in a test fails with an NPE naming the line, which is the
 * honest outcome; test that editor's <em>predicate</em> here and its dialog by hand.
 *
 * <p>Building a JavaFX control still needs the FX toolkit started. That is JavaFX's rule, not this class's:
 * run those tests on an initialised toolkit, or keep to the predicates, which touch no control at all.
 */
public final class TestContexts {

    private TestContexts() {}

    /**
     * A value with a type and no call behind it — a Parameters row, or a managed method's value.
     *
     * <p>{@link ValueContext#slot()} is empty here, which is the case every call-site predicate must decline
     * — and the one most easily forgotten, because it is the case that cannot arise while an editor is being
     * developed against a bot's source.
     *
     * @param type   the declared type, or {@code null} for one the host could not resolve
     * @param source the Java expression the value is written as — {@code "\"gold.png\""}, {@code "3"}
     */
    public static Recording row(Class<?> type, String source) {
        return new Recording(type, source, false, null, -1);
    }

    /**
     * A slot in a bot's source: argument {@code argIndex} of {@code call}, typed as that parameter — the
     * component type for an argument of a varargs tail.
     *
     * @param call          the method or constructor called, as the host resolved it; {@code null} for a
     *                      call it could not resolve — see {@link #call}
     * @param argIndex      which argument this slot is, counting from 0
     * @param currentSource the Java expression currently in the slot
     */
    public static Recording slot(Executable call, int argIndex, String currentSource) {
        return new Recording(parameterType(call, argIndex), currentSource, true, call, argIndex);
    }

    /** A slot of a known type with no call around it — a field initialiser, a local declaration. */
    public static Recording typedSlot(Class<?> type, String currentSource) {
        return new Recording(type, currentSource, true, null, -1);
    }

    /**
     * The method or constructor {@code ref} names, for {@link #slot}: {@code TestContexts.call(Duration::ofMillis)}.
     * javac checks the name and picks the overload from the reference, so a rename breaks the test where it is
     * written. A method returning nothing is {@link #voidCall}, which javac cannot tell apart from this. An
     * overloaded name needs its overload said with a cast: {@code call((Ref.Of2<Long, TemporalUnit, Duration>)
     * Duration::of)}.
     */
    public static <R> Executable call(Ref.Of0<R> ref) {
        return Ref.resolve(ref);
    }

    /** {@link #call(Ref.Of0)} for one argument, or a receiver: {@code Duration::ofMillis}. */
    public static <A, R> Executable call(Ref.Of1<A, R> ref) {
        return Ref.resolve(ref);
    }

    /** {@link #call(Ref.Of0)} for two. */
    public static <A, B, R> Executable call(Ref.Of2<A, B, R> ref) {
        return Ref.resolve(ref);
    }

    /** {@link #call(Ref.Of0)} for three. */
    public static <A, B, C, R> Executable call(Ref.Of3<A, B, C, R> ref) {
        return Ref.resolve(ref);
    }

    /** {@link #call(Ref.Of0)} for four. */
    public static <A, B, C, D, R> Executable call(Ref.Of4<A, B, C, D, R> ref) {
        return Ref.resolve(ref);
    }

    /** The {@code void} method {@code ref} names, for {@link #slot}: {@code TestContexts.voidCall(Game::launchSteam)}. */
    public static <A> Executable voidCall(Ref.Void1<A> ref) {
        return Ref.resolve(ref);
    }

    /** {@link #voidCall(Ref.Void1)} for two arguments. */
    public static <A, B> Executable voidCall(Ref.Void2<A, B> ref) {
        return Ref.resolve(ref);
    }

    /** {@link #voidCall(Ref.Void1)} for three. */
    public static <A, B, C> Executable voidCall(Ref.Void3<A, B, C> ref) {
        return Ref.resolve(ref);
    }

    /** {@link #voidCall(Ref.Void1)} for four. */
    public static <A, B, C, D> Executable voidCall(Ref.Void4<A, B, C, D> ref) {
        return Ref.resolve(ref);
    }

    private static Class<?> parameterType(Executable call, int argIndex) {
        if (call == null || argIndex < 0) return null;
        Class<?>[] parameters = call.getParameterTypes();
        if (call.isVarArgs() && argIndex >= parameters.length - 1) {
            return parameters[parameters.length - 1].getComponentType();
        }
        return argIndex < parameters.length ? parameters[argIndex] : null;
    }

    /**
     * A context that records what an editor writes instead of writing it anywhere.
     *
     * <p>It implements {@link SlotContext} in every case and lies about one thing only: {@link #slot()}
     * is empty for a row, exactly as the host's own is. That is the single behaviour a call-site predicate
     * turns on, so getting it right here is most of what this class is for.
     */
    public static final class Recording implements SlotContext {

        private Class<?> type;
        private final boolean isSlot;
        private final Executable call;
        private final int argIndex;

        private final String source;
        private Object value;
        private int writes;

        private List<SlotRun.Element> runElements;
        private int runMinimum;
        private List<Object> runAllowed;
        private List<Object> runReplacement;
        private final Map<Integer, Object> arguments = new HashMap<>();
        /** The constant of each open set, by its id, that the value is written as. */
        private final Map<String, PluginValues.Member> members = new HashMap<>();
        private Bounds bounds = Bounds.NONE;

        private Recording(Class<?> type, String source, boolean isSlot, Executable call, int argIndex) {
            this.type = type;
            this.source = source == null ? "" : source;
            this.isSlot = isSlot;
            this.call = call;
            this.argIndex = argIndex;
        }

        /**
         * The declared type of the value, in place of the one {@link #slot} read off the call — a slot typed
         * as an interface holding one implementation, say. {@code null} is a type the host could not resolve.
         */
        public Recording withType(Class<?> type) {
            this.type = type;
            return this;
        }

        /**
         * The value {@link ValueContext#value(Class)} answers — the decoded {@code Duration}, {@code Point}
         * or {@code String} the host would have handed the editor.
         *
         * <p><b>It is set, never derived.</b> This module owns no grammar and reads no Java: turning
         * {@code "Duration.ofSeconds(3)"} into a {@code Duration} is the host's job, and a stub that
         * guessed at it would be a second reader of the one thing the 2026-09-22 change exists to have
         * exactly one of. Leaving it unset is the case an editor must handle first — an expression nothing
         * can decode, which is shown read-only.
         */
        public Recording withValue(Object value) {
            this.value = value;
            return this;
        }

        /**
         * Makes this slot part of a {@link SlotRun} of {@code elements}, as a varargs argument is.
         *
         * <p>Without it {@link #siblingRun()} is empty, which is what nearly every real slot answers and so
         * the case an editor must handle first. {@code minimum} and {@code allowed} are the host's two
         * narrowings — how few elements the surrounding code still compiles with, and the only values it
         * will accept ({@code null} for no limit).
         */
        public Recording withRun(List<SlotRun.Element> elements, int minimum, List<Object> allowed) {
            this.runElements = elements == null ? List.of() : List.copyOf(elements);
            this.runMinimum = Math.max(0, minimum);
            this.runAllowed = allowed == null ? null : List.copyOf(allowed);
            return this;
        }

        /** A run of readable values, with no minimum and no narrowing. */
        public Recording withRun(Object... values) {
            List<SlotRun.Element> elements = new ArrayList<>();
            for (Object each : values) elements.add(element(each));
            return withRun(elements, 0, null);
        }

        /**
         * What {@link SlotContext#argumentValue} answers for argument {@code index} of the call — the host's
         * reading of a neighbour, such as the {@code Color} beside a {@code Precision}. Set, never derived, for
         * the reason {@link #withValue} gives.
         */
        public Recording withArgument(int index, Object value) {
            arguments.put(index, value);
            return this;
        }

        /**
         * What {@link ValueContext#bounds()} answers — a field's {@code @Param(min, max)}.
         *
         * <p>{@link #set(Object)} does <b>not</b> clamp here, though the host does: what a test asserts is what
         * the editor itself decided, so an editor that ignores the range is caught rather than rescued.
         */
        public Recording withBounds(double min, double max) {
            this.bounds = new Bounds(min, max);
            return this;
        }

        /**
         * The constant of the open set {@code set} the value is written as — what {@link ValueContext#member}
         * answers, {@code Flows.DAILY} for a slot holding that reference. Set, never derived, for the reason
         * {@link #withValue} gives; a {@link ValueContext#writeMember} replaces it and counts a write.
         */
        public Recording withMember(ManagedValue<?> set, PluginValues.Member member) {
            if (member == null) members.remove(set.id());
            else members.put(set.id(), member);
            return this;
        }

        /** What {@link SlotRun#replace} was last given, or {@code null} if the editor never rewrote the run. */
        public List<Object> runReplacement() {
            return runReplacement;
        }

        /** The value the editor last wrote through {@link #set(Object)}, or what {@link #withValue} seeded. */
        public Object value() {
            return value;
        }

        /**
         * How many times the editor wrote anything.
         *
         * <p>Worth asserting is {@code 0} straight after building the editor: the toolkit's rule is that
         * building never writes — not even to normalise what is already there — because a project merely
         * opened and closed must come back byte-identical.
         */
        public int writes() {
            return writes;
        }

        @Override
        public TypeRef type() {
            return type == null ? TypeRef.unresolved("") : TypeRef.of(type);
        }

        /** What {@link #withValue} seeded, when it is of the type asked for. Empty otherwise. */
        @Override
        public <T> Optional<T> value(Class<T> type) {
            return type != null && type.isInstance(value) ? Optional.of(type.cast(value)) : Optional.empty();
        }

        /**
         * Records the value instead of writing it, and leaves {@link #written()} alone.
         *
         * <p>The host would spell it here, through the owning plugin's {@code ComponentType}. This stub
         * cannot and does not pretend to — assert on {@link #value()}, which is what the editor actually
         * decided, rather than on a spelling this module would have had to invent.
         */
        @Override
        public void set(Object newValue) {
            this.value = newValue;
            writes++;
        }

        /** What {@link #withMember} seeded for {@code set}. */
        @Override
        public Optional<PluginValues.Member> member(ManagedValue<?> set) {
            return set == null ? Optional.empty() : Optional.ofNullable(members.get(set.id()));
        }

        /** Records the constant as {@link #withMember} would, and counts a write. */
        @Override
        public Optional<String> writeMember(ManagedValue<?> set, PluginValues.Member member) {
            if (set == null || member == null) return Optional.of("No constant to write.");
            members.put(set.id(), member);
            writes++;
            return Optional.empty();
        }

        @Override
        public String source() {
            return source;
        }

        /** Always {@code null}: every service is the host doing something a test cannot fake. */
        @Override
        public StudioServices services() {
            return null;
        }

        @Override
        public Optional<SlotContext> slot() {
            return isSlot ? Optional.of(this) : Optional.empty();
        }

        @Override
        public Bounds bounds() {
            return bounds;
        }

        @Override
        public Optional<Executable> enclosingExecutable() {
            return Optional.ofNullable(call);
        }

        @Override
        public int argIndex() {
            return argIndex;
        }

        /** What {@link #withArgument} seeded for {@code index}, when it is of the type asked for. */
        @Override
        public <T> Optional<T> argumentValue(int index, Class<T> type) {
            Object held = arguments.get(index);
            return type != null && type.isInstance(held) ? Optional.of(type.cast(held)) : Optional.empty();
        }

        /**
         * The run set up by {@link #withRun}, or empty.
         *
         * <p>{@link SlotRun#replace} records rather than writes, and it enforces {@link SlotRun#minimum()}
         * exactly as the host does — a shorter list leaves the elements alone and counts no write, so a test
         * can assert that an editor's floor is honoured rather than trusting it. An {@link SlotRun.Element}
         * handed back is kept as it was; any other item becomes a readable element.
         */
        @Override
        public Optional<SlotRun> siblingRun() {
            if (runElements == null) return Optional.empty();
            return Optional.of(new SlotRun() {
                @Override
                public List<Element> elements() {
                    return runElements;
                }

                @Override
                public int minimum() {
                    return runMinimum;
                }

                @Override
                public Optional<List<Object>> allowed() {
                    return Optional.ofNullable(runAllowed);
                }

                @Override
                public void replace(List<?> items) {
                    List<Object> next = items == null ? List.of() : List.copyOf(items);
                    if (next.size() < runMinimum) return;
                    List<Element> elements = new ArrayList<>();
                    for (Object item : next) elements.add(item instanceof Element kept ? kept : element(item));
                    runReplacement = next;
                    runElements = List.copyOf(elements);
                    writes++;
                }
            });
        }
    }

    /** A readable element, shown as its own {@code toString} — this module writes no Java. */
    private static SlotRun.Element element(Object value) {
        return SlotRun.Element.of(value, String.valueOf(value));
    }
}
