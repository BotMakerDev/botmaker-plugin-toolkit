package com.botmaker.plugin.toolkit.testing;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.SlotRun;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.TypeRef;
import com.botmaker.plugin.api.slot.ValueContext;

import java.util.ArrayList;
import java.util.List;
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
 * var ctx = TestContexts.slot("Game", "launchSteam", 0, "\"440\"");
 * assertTrue(STEAM_APP_ID.test(ctx));
 * assertFalse(STEAM_APP_ID.test(TestContexts.row("java.lang.String", "\"440\"")));
 * }</pre>
 *
 * <h2>What is real and what is not</h2>
 *
 * <p>The value, the type, the call site and the writes are <b>real</b>: {@link Recording#written()} returns
 * exactly the expression the editor asked for, so a test asserts on the string that would have reached the
 * file. {@link ValueContext#services()} is <b>not</b>: it answers {@code null}, because every one of those
 * services is the host doing something a test has no way to fake — dragging a region on a real screen, owning
 * a real window. An editor that reaches for one in a test fails with an NPE naming the line, which is the
 * honest outcome; test that editor's <em>predicate</em> here and its dialog by hand.
 *
 * <p>Building a JavaFX control still needs the FX toolkit started. That is JavaFX's rule, not this class's:
 * run those tests on an initialised toolkit, or keep to the predicates, which touch no control at all.
 */
public final class TestContexts {

    private TestContexts() {}

    /** The eight primitive spellings, taken off the class literals so nothing here is typed out. */
    private static final java.util.Set<String> PRIMITIVES = java.util.Set.of(
            boolean.class.getName(), byte.class.getName(), char.class.getName(), short.class.getName(),
            int.class.getName(), long.class.getName(), float.class.getName(), double.class.getName());

    /**
     * A value with a type and no call behind it — a Parameters row, or a {@code @Managed} method's value.
     *
     * <p>{@link ValueContext#slot()} is empty here, which is the case every call-site predicate must decline
     * — and the one most easily forgotten, because it is the case that cannot arise while an editor is being
     * developed against a bot's source.
     *
     * @param typeName the declared type, simple or qualified
     * @param source   the Java expression the value is written as — {@code "\"gold.png\""}, {@code "3"}
     */
    public static Recording row(String typeName, String source) {
        return new Recording(typeName, source, false, null, null, -1);
    }

    /**
     * A slot in a bot's source, with the call around it.
     *
     * @param enclosingClass  the class the call is on, as the host resolved it — a simple or a qualified name
     * @param enclosingMethod the method being called
     * @param argIndex        which argument this slot is, counting from 0
     * @param currentSource   the Java expression currently in the slot
     */
    public static Recording slot(String enclosingClass, String enclosingMethod, int argIndex,
                                 String currentSource) {
        return new Recording("", currentSource, true, enclosingClass, enclosingMethod, argIndex);
    }

    /** A slot of a known type with no call around it — a field initialiser, a local declaration. */
    public static Recording typedSlot(String typeName, String currentSource) {
        return new Recording(typeName, currentSource, true, null, null, -1);
    }

    /**
     * A context that records what an editor writes instead of writing it anywhere.
     *
     * <p>It implements {@link SlotContext} in every case and lies about one thing only: {@link #slot()}
     * is empty for a row, exactly as the host's own is. That is the single behaviour a call-site predicate
     * turns on, so getting it right here is most of what this class is for.
     */
    public static final class Recording implements SlotContext {

        private String typeName;
        private final boolean isSlot;
        private final String enclosingClass;
        private final String enclosingMethod;
        private final int argIndex;

        private String source;
        private Object value;
        private final List<String> imports = new ArrayList<>();
        private String enclosingReplacement;
        private String enclosingSource;
        private int writes;

        private List<String> runElements;
        private int runMinimum;
        private List<String> runAllowed;
        private List<String> runReplacement;

        private Recording(String typeName, String source, boolean isSlot,
                          String enclosingClass, String enclosingMethod, int argIndex) {
            this.typeName = typeName == null ? "" : typeName;
            this.source = source == null ? "" : source;
            this.isSlot = isSlot;
            this.enclosingClass = enclosingClass;
            this.enclosingMethod = enclosingMethod;
            this.argIndex = argIndex;
        }

        /**
         * The declared type of the value, for a context built by {@link #slot} — which knows the call but not
         * the type. A name with no dot in it answers only {@link TypeRef#simpleName()}, exactly as the host's
         * own {@code TypeRef} does for a type it could not resolve.
         */
        public Recording withType(String typeName) {
            this.typeName = typeName == null ? "" : typeName;
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

        /** The source of the call this slot sits in, for an editor that reads it. Fluent, for setup. */
        public Recording withEnclosingSource(String source) {
            this.enclosingSource = source;
            return this;
        }

        /**
         * Makes this slot part of a {@link SlotRun} of {@code elements}, as a varargs argument is.
         *
         * <p>Without it {@link #siblingRun()} is empty, which is what nearly every real slot answers and so
         * the case an editor must handle first. {@code minimum} and {@code allowed} are the host's two
         * narrowings — how few elements the surrounding code still compiles with, and the only element
         * sources it will accept ({@code null} for no limit).
         */
        public Recording withRun(List<String> elements, int minimum, List<String> allowed) {
            this.runElements = elements == null ? List.of() : List.copyOf(elements);
            this.runMinimum = Math.max(0, minimum);
            this.runAllowed = allowed == null ? null : List.copyOf(allowed);
            return this;
        }

        /** As {@link #withRun(List, int, List)}, with no minimum and no narrowing. */
        public Recording withRun(String... elements) {
            return withRun(List.of(elements), 0, null);
        }

        /** What {@link SlotRun#replace} was last given, or {@code null} if the editor never rewrote the run. */
        public List<String> runReplacement() {
            return runReplacement;
        }

        /**
         * The Java expression the value now holds — what {@link #set(String, Class...)} was last given, or
         * the initial one.
         */
        public String written() {
            return source;
        }

        /** The value the editor last wrote through {@link #set(Object)}, or what {@link #withValue} seeded. */
        public Object value() {
            return value;
        }

        /** The imports the last {@link #set} asked for. */
        public List<String> imports() {
            return List.copyOf(imports);
        }

        /** What {@link #replaceEnclosingCall} was last given, or {@code null}. */
        public String enclosingReplacement() {
            return enclosingReplacement;
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
            return new TypeRef() {
                @Override
                public String simpleName() {
                    int dot = typeName.lastIndexOf('.');
                    return dot < 0 ? typeName : typeName.substring(dot + 1);
                }

                @Override
                public String qualifiedName() {
                    // A primitive IS its own qualified name, which "has it got a dot in it" reads as
                    // unresolved — and a widget asking TypeRef.is(int.class) then never matches an `int`
                    // field. The names come off the class literals rather than being typed, so a fourth
                    // hand-rolled keyword list is not created here.
                    return typeName.indexOf('.') >= 0 || PRIMITIVES.contains(typeName) ? typeName : "";
                }
            };
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

        @Override
        public String source() {
            return source;
        }

        @Override
        public void set(String javaExpression, Class<?>... importsNeeded) {
            this.source = javaExpression == null ? "" : javaExpression;
            this.imports.clear();
            if (importsNeeded != null) {
                for (Class<?> needed : importsNeeded) {
                    if (needed != null) this.imports.add(com.botmaker.plugin.toolkit.Source.type(needed));
                }
            }
            writes++;
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
        public Optional<String> enclosingClassName() {
            return Optional.ofNullable(enclosingClass);
        }

        @Override
        public Optional<String> enclosingMethodName() {
            return Optional.ofNullable(enclosingMethod);
        }

        @Override
        public int argIndex() {
            return argIndex;
        }

        @Override
        public Optional<String> enclosingCall() {
            return Optional.ofNullable(enclosingSource);
        }

        /**
         * The run set up by {@link #withRun}, or empty.
         *
         * <p>{@link SlotRun#replace} records rather than writes, and it enforces {@link SlotRun#minimum()}
         * exactly as the host does — a shorter list leaves the elements alone and counts no write, so a test
         * can assert that an editor's floor is honoured rather than trusting it.
         */
        @Override
        public Optional<SlotRun> siblingRun() {
            if (runElements == null) return Optional.empty();
            return Optional.of(new SlotRun() {
                @Override
                public List<String> elements() {
                    return runElements;
                }

                @Override
                public int minimum() {
                    return runMinimum;
                }

                @Override
                public Optional<List<String>> allowedSources() {
                    return Optional.ofNullable(runAllowed);
                }

                @Override
                public void replace(List<String> javaExpressions, String... importsNeeded) {
                    List<String> next = javaExpressions == null ? List.of() : List.copyOf(javaExpressions);
                    if (next.size() < runMinimum) return;
                    runReplacement = next;
                    runElements = next;
                    imports.clear();
                    if (importsNeeded != null) imports.addAll(List.of(importsNeeded));
                    writes++;
                }
            });
        }

        @Override
        public void replaceEnclosingCall(String javaExpression, String... importsNeeded) {
            this.enclosingReplacement = javaExpression;
            this.enclosingSource = javaExpression;
            this.imports.clear();
            if (importsNeeded != null) this.imports.addAll(List.of(importsNeeded));
            writes++;
        }
    }
}
