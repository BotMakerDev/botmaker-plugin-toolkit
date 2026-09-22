package com.botmaker.plugin.toolkit;

import java.util.List;

/**
 * Java source, spelled correctly — for the one place a plugin still authors text.
 *
 * <h2>Three members, because the host writes everything else now</h2>
 *
 * <p>A plugin does not emit Java any more. A value is written by
 * {@link com.botmaker.plugin.api.slot.ValueContext#set(Object)} and spelled by the host through the
 * plugin's own {@link com.botmaker.plugin.api.value.ComponentType}, so {@code newInstance},
 * {@code enumConstant}, {@code number}, {@code character}, {@code imports}, {@code code} and the
 * {@code Expr} type they existed for are all deleted, along with the readers {@code stringValue} and
 * {@code characterValue} — reading is the host's half of the same job.
 *
 * <p>What survives is what a plugin writes <b>into a user's file as text a user pasted</b>: the SDK's macro
 * translator turns a recording into statements, which is not a value and has no type. That needs
 * {@link #string} to escape what was typed, {@link #type} to name a class without typing a package path,
 * and {@link #requireMethod} to catch a renamed method here rather than in somebody's bot.
 *
 * <h2>There is no {@code call}, and a method reference is not what replaced it</h2>
 *
 * <p>A {@code call(Class<?>, String method, Expr...)} lived here from 2026-08-28 to 2026-09-04 and was
 * deleted with <b>no production caller</b>: the one place that wanted it — the SDK's macro translator —
 * declined it, because a recorded macro is pasted into a user's file where the type is imported and
 * {@code call} qualifies it in full.
 *
 * <p><b>A compile-checked method reference was considered and is not possible here.</b> {@code call(Mouse::click)}
 * needs a functional interface whose <em>shape matches the method</em>, so arbitrary arity means one
 * interface per parameter count — which is exactly the {@code MemberRef} plus {@code M0}–{@code M5}
 * apparatus this project built for {@code PaletteCatalog} and deleted on 2026-08-27, and a method reference
 * still cannot name a specific overload. Do not re-propose it without an arity-free form, and there is none.
 *
 * <h2>JavaPoet is gone, and the toolkit now resolves nothing</h2>
 *
 * <p>It was here for two calls: {@code CodeBlock} joined a constructor's arguments, and
 * {@code ClassName.get(Class)} spelled a nested type {@code Outer.Inner} rather than {@code Outer$Inner}.
 * The first has no caller left, and the second is {@link Class#getCanonicalName()} — which is what
 * {@code ClassName.get} reads too. A module whose only dependency is {@code provided} is a module a plugin
 * author cannot get a version conflict out of.
 */
public final class Source {

    private Source() {}

    /**
     * {@code text} as a Java string literal, quotes included, always as a <b>single</b> expression.
     *
     * <p>Never a concatenation across source lines the way a code generator writes one ({@code "line\n" +
     * "break"}): what this produces goes into the middle of somebody else's line. The escaping is total —
     * every character that cannot appear literally inside a Java string is escaped, including the control
     * characters a user can paste in without ever seeing them.
     */
    public static String string(String text) {
        String s = text == null ? "" : text;
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            out.append(escape(s.charAt(i), '"'));
        }
        return out.append('"').toString();
    }

    /**
     * A type's name as it may be written in an expression, fully qualified.
     *
     * <p>A nested type comes out {@code Outer.Inner}, which is what source and an import both need and what
     * {@link Class#getName()} does not give. An array is written with brackets, and a class with no
     * canonical name at all — anonymous, local — falls back to its binary name with the dollars replaced,
     * which is the closest thing to a spelling it has.
     */
    public static String type(Class<?> type) {
        if (type == null) return "";
        String canonical = type.getCanonicalName();
        return canonical != null ? canonical : type.getName().replace('$', '.');
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
