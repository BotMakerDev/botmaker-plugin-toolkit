package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.SlotContext;
import com.botmaker.plugin.api.slot.ValueContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Reading and writing a value that may be either a Java expression or a row of stored strings — the one
 * thing every editor needs and the contract deliberately does not do for it.
 *
 * <p>It lived in the SDK until 2026-08-28, which was an accident of who wrote the first editor rather than a
 * decision: not a line of it names an SDK type, and any plugin drawing an editor for a slot in a bot's source
 * needs exactly this. {@link Editors} is the layer above it; this is the layer that makes the layer above
 * work in <em>both</em> of the places the host edits a value.
 *
 * <p>The host edits values in more than one place and the same editor serves them all (see
 * {@link com.botmaker.plugin.api.slot.SlotEditor}) — a slot in a bot's source, a row of the Parameters window,
 * and the expression a {@code @Managed} method returns. <b>All three spell a value the same way since
 * 2026-09-20</b>: as the Java that writes it. Until then a Parameters row held a {@code List<String>} of its
 * own, so every method here had to ask {@link ValueContext#slot()} and take a second branch — two encodings
 * of one value, kept in step by hand. The branch is gone and so is the second encoding.
 *
 * <p><b>Nothing here throws.</b> A value may have been typed by hand, written by a newer version of this
 * plugin, or left blank; every read degrades to a default, which is the toolkit's rule 2 restated for source
 * text — the same rule {@link Editors} and {@link Values} keep, for the same reason.
 */
public final class Slots {

    private Slots() {}

    /** The Java expression the value is written as, trimmed. */
    public static String raw(ValueContext ctx) {
        String source = ctx.source();
        return source == null ? "" : source.trim();
    }

    /** Whether there is nothing there yet — a slot never filled in, or a value never set. */
    public static boolean isEmpty(ValueContext ctx) {
        return raw(ctx).isBlank();
    }

    /**
     * The {@code n} numeric arguments of the value, however it is spelled.
     *
     * <p>The arguments of a constructor or factory call — {@code new Rect(12, 40, 300, 80)} — read
     * positionally and without caring which type is being constructed, since the editor already decided that
     * by matching on the value's type. A missing or unparseable argument reads as {@code 0}, which is the
     * value a numeric field would show anyway.
     */
    public static int[] ints(ValueContext ctx, int n) {
        List<String> args = arguments(raw(ctx));
        List<String> normalised = new ArrayList<>(args.size());
        for (String arg : args) normalised.add(literal(arg));
        return Values.ints(normalised, n);
    }

    /**
     * A Java integer literal as the plain digits {@code Integer.parseInt} accepts — {@code 100L} is 100 and
     * {@code 1_000} is 1000.
     *
     * <p>Needed here and not in {@link Values} because {@code Values} reads a project file's stored strings,
     * where a value is already plain, and this class reads Java source, where it is a literal a person wrote.
     * The leniency is deliberate: it is fed whatever is in the slot, and a suffix must not cost the user their
     * label in the middle of rendering a block.
     */
    private static String literal(String source) {
        String s = source == null ? "" : source.trim().replace("_", "");
        return s.endsWith("L") || s.endsWith("l") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * The argument list of a call or constructor in {@code source}, as written.
     *
     * <p>A brace-and-quote-aware split rather than a parser: the contract hands over source text and this
     * module depends on no parsing library, deliberately (see the contract's rule 3). It handles the shapes
     * that actually occur in a slot — nested calls, string literals containing commas — and returns nothing
     * for text it cannot make sense of, which the callers all treat as "no current value".
     */
    public static List<String> arguments(String source) {
        String s = source == null ? "" : source.trim();
        int open = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (open < 0 || close <= open) return List.of();

        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = open + 1; i < close; i++) {
            char c = s.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (c == '\\' && inString) {
                escaped = true;
            } else if (c == '"') {
                inString = !inString;
            } else if (!inString) {
                if (c == '(' || c == '[' || c == '{') depth++;
                else if (c == ')' || c == ']' || c == '}') depth--;
                else if (c == ',' && depth == 0) {
                    out.add(current.toString().trim());
                    current.setLength(0);
                    continue;
                }
            }
            current.append(c);
        }
        String last = current.toString().trim();
        if (!last.isEmpty() || !out.isEmpty()) out.add(last);
        return out;
    }

    /**
     * Whether the value is a row of whole numbers at all, rather than something else that happens to read as
     * zeroes — a variable, a call, a computed expression.
     *
     * <p>The check every tuple editor needs before it labels itself. Without it a slot holding {@code bounds}
     * would show {@code 0, 0  0×0}, which claims a value the user never set; showing the raw text instead is
     * honest, and rewriting somebody's {@code target.center()} into "0, 0" is a lie about what the bot does.
     *
     * <p>Two things it deliberately does <em>not</em> check. A <b>missing</b> argument is fine and reads as
     * zero — {@code new Point(10)} is what a freshly inserted block looks like before the user picks, and
     * labelling it {@code 10, 0} is the right answer. And the <b>type being constructed</b> is nobody's
     * business: which editor a slot gets was decided by its declared type one layer up, so this reads
     * positionally and a second type check would be dead code.
     *
     * <p>Here rather than in {@link Editors} because it is source-text inspection, which is this class's
     * whole subject: it moved out of the SDK with {@code tuplePill} on 2026-08-28 and names no SDK type.
     */
    public static boolean holdsNumbers(ValueContext ctx, int n) {
        String raw = raw(ctx);
        if (!raw.startsWith("new ")) return false;
        List<String> args = arguments(raw);
        for (int i = 0; i < Math.min(n, args.size()); i++) {
            String arg = args.get(i).trim();
            if (!arg.isEmpty() && !isNumber(arg)) return false;
        }
        return true;
    }

    /** A whole number as a person writes one, with a Java {@code long} suffix or digit separators allowed. */
    private static boolean isNumber(String text) {
        String s = text == null ? "" : text.trim().replace("_", "");
        if (s.endsWith("L") || s.endsWith("l")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isDigit(c) && !(i == 0 && (c == '-' || c == '+'))) return false;
        }
        return !(s.length() == 1 && (s.charAt(0) == '-' || s.charAt(0) == '+'));
    }

    /** The one string literal in {@code source}, unescaped, or {@code null} when there is none. */
    public static String stringLiteral(String source) {
        String s = source == null ? "" : source;
        int open = s.indexOf('"');
        if (open < 0) return null;
        StringBuilder out = new StringBuilder();
        for (int i = open + 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                out.append(switch (next) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    default -> next;
                });
            } else if (c == '"') {
                return out.toString();
            } else {
                out.append(c);
            }
        }
        return null;
    }

    /**
     * {@code text} as a Java string literal.
     *
     * <p>Kept under this name because it is where every editor already reaches for it; the escaping itself
     * is {@link Source#string}, which is total — this used to escape the backslash and the quote and
     * nothing else, so a pasted tab or newline produced a slot that would not compile.
     */
    public static String quote(String text) {
        return Source.string(text).source();
    }

    /**
     * Writes {@code numbers} as a constructor call on {@code type}.
     *
     * <p>The type is named fully-qualified in the expression and passed again as the import, which is the
     * combination the contract documents as always safe: the host adds the import if it is missing, and then
     * the fully-qualified name it shortens is already correct if it is not.
     */
    public static void writeConstructor(ValueContext ctx, Class<?> type, int... numbers) {
        Source.Expr[] arguments = new Source.Expr[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            arguments[i] = Source.number(numbers[i]);
        }
        // Source.imports rather than getName(): a nested type is Outer.Inner in both an expression and an
        // import, and Outer$Inner in neither.
        ctx.set(Source.newInstance(type, arguments), Source.imports(type));
    }

    /**
     * Writes a Java expression, adding any imports it needs.
     *
     * <p>It took a second {@code storedForm} argument until 2026-09-20, for the Parameters row that held a
     * value as plain text rather than as Java — {@code Diablo IV} beside
     * {@code CaptureSource.window("Diablo IV")}. A row holds the expression now, so there is one answer.
     */
    public static void write(ValueContext ctx, String javaExpression, String... imports) {
        ctx.set(javaExpression, imports);
    }

    /** Writes plain text as a Java string literal. */
    public static void writeText(ValueContext ctx, String text) {
        ctx.set(quote(text));
    }
}
