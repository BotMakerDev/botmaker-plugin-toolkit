package com.botmaker.plugin.toolkit;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;

import java.util.List;
import java.util.Optional;

/**
 * Java source, spelled correctly — the one place a plugin writes an expression a bot will compile.
 *
 * <h2>Why this is the toolkit's problem and not each plugin's</h2>
 *
 * <p>A plugin that registers a value type is emitting Java whether it thinks so or not:
 * {@link com.botmaker.plugin.api.value.ValueCodec#literal} returns Java source, and
 * {@link Slots#write} writes Java into a slot. Before this class there were three hand-rolled copies of
 * string escaping in this project alone — one here, one in the SDK, one in the generated plugin skeleton —
 * which is what a concern with no owner looks like. A wrong escape is not a compile error in <em>this</em>
 * build; it is a compile error in somebody's <em>bot</em>, reported against a line they did not write.
 *
 * <h2>An argument is source, not a value — and since 2026-09-01 the compiler says so</h2>
 *
 * <p>{@link #newInstance} takes arguments that are already Java. The rule used to be documented and
 * unenforced: the parameter was {@code Object...}, so passing {@code "ding"} emitted {@code ding} — a
 * reference to a variable nobody declared. Both spellings are valid Java and only the caller knows which was
 * meant, so the distinction has to be in the type. It is {@link Expr}: {@link #string}, {@link #number} and
 * their friends return one, a bare {@link String} is not accepted, and the one way to pass unchecked text is
 * {@link #code}, which says at the call site that the caller took responsibility for it.
 *
 * <h2>There is no {@code call}, and a method reference is not what replaced it</h2>
 *
 * <p>A {@code call(Class<?>, String method, Expr...)} lived here from 2026-08-28 to 2026-09-04 and was
 * deleted with <b>no production caller</b>: the one place that wanted it — the SDK's macro translator —
 * declined it, because a recorded macro is pasted into a user's file where the type is imported and
 * {@code call} qualifies it in full. What is left of it is {@link #requireMethod}, which is the half worth
 * keeping: anybody composing a call by hand can still ask whether the name resolves.
 *
 * <p><b>A compile-checked method reference was considered and is not possible here.</b> {@code call(Mouse::click)}
 * needs a functional interface whose <em>shape matches the method</em>, so arbitrary arity means one
 * interface per parameter count — which is exactly the {@code MemberRef} plus {@code M0}–{@code M5}
 * apparatus this project built for {@code PaletteCatalog} and deleted on 2026-08-27, and a method reference
 * still cannot name a specific overload. Do not re-propose it without an arity-free form, and there is none.
 *
 * <h2>JavaPoet is the implementation and never the interface</h2>
 *
 * <p>Every member here takes and returns {@link String} or this class's own {@link Expr}, so no JavaPoet
 * type reaches a plugin's signature and the library can be replaced without a toolkit release that breaks
 * anybody. A plugin that wants JavaPoet directly is free to declare it.
 */
public final class Source {

    private Source() {}

    /**
     * A Java expression, as source text — the type that separates {@code "ding"} from {@code ding}.
     *
     * <p>A record with one component rather than a bare {@link String} for exactly one reason, and it is the
     * whole point: a {@code String} in an argument list is ambiguous and an {@code Expr} is not. It carries
     * no behaviour, costs one allocation per argument, and {@link #toString()} is the source, so it
     * concatenates and prints as the text it holds.
     *
     * <p>The boundary back to {@link String} is deliberate and stays: {@code ValueCodec.literal} and
     * {@code SlotContext.replaceWith} take text because text is the wire. Call {@code source()} there.
     */
    public record Expr(String source) {

        public Expr {
            source = source == null || source.isBlank() ? "null" : source;
        }

        /** The source text, so an {@code Expr} concatenates and prints as what it holds. */
        @Override
        public String toString() {
            return source;
        }
    }

    /**
     * Java source the caller vouches for — the escape hatch, and the only way a raw {@link String} becomes
     * an argument.
     *
     * <p>Named so that it reads as a claim at the call site: {@code call(Wait.class, "time", code(userText))}
     * says out loud that {@code userText} is an expression rather than something to quote. Everything that
     * can be built safely has a factory above; reach for this when composing an expression this class does
     * not model, never to pass a value.
     */
    public static Expr code(String javaExpression) {
        return new Expr(javaExpression);
    }

    /**
     * The fully-qualified names of {@code types}, for the {@code importsNeeded} of
     * {@code SlotContext.replaceWith} and {@code replaceEnclosingCall}.
     *
     * <p>The contract keeps imports as text — text is the wire and must stay a {@link String} — so this is
     * the toolkit's half of the same trade {@link #type} makes: a plugin names real classes and never types
     * a package path that a rename would silently invalidate. A nested type comes out {@code Outer.Inner},
     * which is what an import needs and what {@code getName()} does not give.
     */
    public static String[] imports(Class<?>... types) {
        if (types == null) return new String[0];
        String[] names = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            names[i] = type(types[i]);
        }
        return names;
    }

    /**
     * {@code text} as a Java string literal, quotes included, always as a <b>single</b> expression.
     *
     * <p><b>This is the one member JavaPoet does not implement, and the reason is worth knowing before
     * anybody "fixes" it.</b> JavaPoet's {@code $S} splits a string containing a newline into a
     * concatenation across source lines ({@code "line\n" + "break"}), which is right for a generated file
     * and wrong here: a slot holds one expression, and the host writes it into the middle of somebody
     * else's line. So the escaping is done here, and it is total — every character that cannot appear
     * literally inside a Java string is escaped, including the control characters a user can paste in
     * without ever seeing them.
     */
    public static Expr string(String text) {
        String s = text == null ? "" : text;
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            out.append(escape(s.charAt(i), '"'));
        }
        return new Expr(out.append('"').toString());
    }

    /**
     * A Java char literal, quotes included.
     *
     * <p>Separate from {@link #string} rather than a parameter of it because what is legal differs by
     * position: a {@code '} must be escaped here and must not be there, and the reverse holds for {@code "}.
     */
    public static Expr character(char c) {
        return new Expr("'" + escape(c, '\'') + "'");
    }

    /**
     * {@link #string} read backwards: the text a Java string literal holds, or empty when {@code javaSource}
     * is not one literal.
     *
     * <p><b>Here because the escaping is here.</b> A plugin's {@code ValueCodec.valueOfLiteral} has to undo
     * exactly what its {@code literal} wrote, and an inverse kept in a different file from the thing it
     * inverts is one that drifts — which is how a pasted tab came to be written correctly and read back as
     * nothing.
     *
     * <p>Deliberately strict, and strict is the safe direction: a concatenation, an unescaped quote in the
     * middle and an octal escape all answer empty, because each means the source was written by a person
     * rather than by {@link #string}, and the host shows what it cannot read rather than rewriting it.
     */
    public static Optional<String> stringValue(String javaSource) {
        String source = javaSource == null ? "" : javaSource.strip();
        if (source.length() < 2 || source.charAt(0) != '"' || !source.endsWith("\"")) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder();
        return unescape(source.substring(1, source.length() - 1), '"', out)
                ? Optional.of(out.toString())
                : Optional.empty();
    }

    /** {@link #character} read backwards: the character a Java char literal holds, or empty. */
    public static Optional<Character> characterValue(String javaSource) {
        String source = javaSource == null ? "" : javaSource.strip();
        if (source.length() < 3 || source.charAt(0) != '\'' || !source.endsWith("'")) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder();
        return unescape(source.substring(1, source.length() - 1), '\'', out) && out.length() == 1
                ? Optional.of(out.charAt(0))
                : Optional.empty();
    }

    /**
     * The body of a literal, unescaped into {@code out}; false when it is not one this class wrote.
     *
     * <p>{@code quote} is the delimiter, which may not appear unescaped inside — that is what tells one
     * literal from a concatenation of two.
     */
    private static boolean unescape(String body, char quote, StringBuilder out) {
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\') {
                if (c == quote) return false;
                out.append(c);
                continue;
            }
            if (++i >= body.length()) return false;
            switch (body.charAt(i)) {
                case 'n' -> out.append('\n');
                case 't' -> out.append('\t');
                case 'r' -> out.append('\r');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 's' -> out.append(' ');
                case '0' -> out.append('\0');
                case '\\', '"', '\'' -> out.append(body.charAt(i));
                // The one escape nobody types and this class emits anyway: a control character, which a user
                // can paste without ever seeing it. Refusing it here would make exactly those values
                // write-only.
                case 'u' -> {
                    if (i + 4 >= body.length()) return false;
                    try {
                        out.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
                    } catch (NumberFormatException notAnEscape) {
                        return false;
                    }
                    i += 4;
                }
                default -> {
                    return false;                            // an octal escape: not ours to read
                }
            }
        }
        return true;
    }

    /**
     * A number as a person would have typed it: {@code 3} for a whole value, {@code 0.75} otherwise.
     *
     * <p>Never {@code 3.0} for a count. The value goes into a bot's source where somebody reads it, and a
     * trailing {@code .0} on a pixel count reads as a unit that was never meant.
     */
    public static Expr number(double value) {
        return new Expr(value == Math.rint(value) && !Double.isInfinite(value)
                ? Long.toString(Math.round(value))
                : Double.toString(value));
    }

    /** A whole number. */
    public static Expr number(long value) {
        return new Expr(Long.toString(value));
    }

    /**
     * An enum constant, fully qualified — {@code com.example.Direction.LEFT}.
     *
     * <p>Fully qualified is the safe half of the pair the contract documents: pass the type's name as the
     * import alongside it and the host shortens what it can, while an expression the host chose not to
     * shorten is still correct on its own.
     */
    public static Expr enumConstant(Enum<?> constant) {
        if (constant == null) return new Expr("null");
        return new Expr(type(constant.getDeclaringClass()) + "." + constant.name());
    }

    /**
     * {@code new Type(argument, …)}, with the type fully qualified.
     *
     * <p>Each argument is an {@link Expr} — Java source, not a value. Build one with {@link #string},
     * {@link #number} and their friends, or with {@link #code} for text the caller vouches for. A
     * {@code null} argument emits the literal {@code null}, which is a real thing to write and not an
     * accident.
     */
    public static String newInstance(Class<?> type, Expr... arguments) {
        return CodeBlock.of("new $T($L)", className(type), joined(arguments)).toString();
    }

    /**
     * Refuses {@code method} if {@code type} declares no such name, naming the nearest alternatives.
     *
     * <p>Call it before writing a static call by hand. It is what survives of the {@code call} member
     * described in this class's javadoc: the emitting half had no caller, and the checking half is the part
     * that catches source failing to compile in somebody's <em>bot</em>, reported against a line they did
     * not write.
     *
     * <p><b>Declared, not inherited</b>, and public only — the same rule {@code PaletteCatalog} applies, and
     * for the same reason: a member a facade merely inherits belongs to the supertype that declared it, and
     * emitting {@code Mouse.wait(…)} because {@link Object} has one is precisely the mistake this check
     * exists to catch.
     *
     * <p><b>It degrades rather than throwing when the class cannot be read at all.</b> A {@link LinkageError}
     * from {@code getDeclaredMethods()} means a member's signature names something this classloader cannot
     * see — an optional dependency the host did not resolve — and that is not evidence the caller's method
     * name is wrong. The rule behind it is the one this project applies everywhere: no unreadable input may
     * be the reason a project will not open. A genuinely wrong name still fails, later, exactly as it did
     * before this check existed.
     */
    public static void requireMethod(Class<?> type, String method) {
        if (type == null || method == null || method.isBlank()) {
            throw new IllegalArgumentException("A call needs a type and a method name.");
        }
        java.util.Set<String> declared = new java.util.TreeSet<>();
        try {
            for (java.lang.reflect.Method m : type.getDeclaredMethods()) {
                if (java.lang.reflect.Modifier.isPublic(m.getModifiers())) declared.add(m.getName());
            }
        } catch (LinkageError e) {
            return;
        }
        if (declared.contains(method)) return;

        // The nearest names, so the message is actionable: a typo and a rename look identical at the call
        // site, and "did you mean" is the difference between fixing it now and reading the class.
        String lower = method.toLowerCase(java.util.Locale.ROOT);
        List<String> near = declared.stream()
                .filter(n -> n.toLowerCase(java.util.Locale.ROOT).contains(lower)
                        || lower.contains(n.toLowerCase(java.util.Locale.ROOT)))
                .limit(4)
                .toList();
        throw new IllegalArgumentException(type.getName() + " declares no public method '" + method + "'"
                + (near.isEmpty() ? "." : "; did you mean " + String.join(", ", near) + "?"));
    }

    /** A type's name as it may be written in an expression, fully qualified. */
    public static String type(Class<?> type) {
        return type == null ? "" : className(type).toString();
    }

    // ---- internals ------------------------------------------------------------------------------------

    private static CodeBlock joined(Expr... arguments) {
        if (arguments == null || arguments.length == 0) return CodeBlock.of("");
        CodeBlock.Builder out = CodeBlock.builder();
        for (int i = 0; i < arguments.length; i++) {
            out.add(i == 0 ? "" : ", ");
            // Every argument is already the source the caller chose — which is the point of Expr. The
            // instanceof ladder this replaced existed only because the parameter was Object...: a Double had
            // to be routed through number() so a count did not read as `3.0`, and a String was passed
            // through verbatim, which is how `play(ding)` got written for `play("ding")`.
            out.add("$L", arguments[i] == null ? "null" : arguments[i].source());
        }
        return out.build();
    }

    /**
     * JavaPoet's name for a class, including a nested one.
     *
     * <p>{@code ClassName.get(Class)} spells a nested type {@code Outer.Inner} rather than
     * {@code Outer$Inner}, which is the difference between source and a class file name — and the reason
     * this goes through JavaPoet rather than {@code getName()}.
     */
    private static ClassName className(Class<?> type) {
        return ClassName.get(type);
    }

    /** One character as it may appear inside a literal delimited by {@code quote}. */
    private static String escape(char c, char quote) {
        if (c == quote) return "\\" + quote;
        return switch (c) {
            case '\\' -> "\\\\";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            default -> c < 0x20 || c == 0x7f ? String.format("\\u%04x", (int) c) : String.valueOf(c);
        };
    }
}
