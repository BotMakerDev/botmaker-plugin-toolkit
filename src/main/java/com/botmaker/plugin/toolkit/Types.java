package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.EditableType;
import javafx.scene.Node;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Type declarations as expressions: one line per type a plugin owns, instead of one class per type.
 *
 * <pre>{@code
 * static final ComponentType<Point> POINT_CALL = Types.record(Point.class);
 *
 * static final List<PluginType<?>> ALL = List.of(
 *         Types.editable(Point.class, () -> new Point(0, 0), () -> ctx -> MyEditors.point(ctx, POINT_CALL))
 *                 .writtenAs(POINT_CALL),
 *         Types.enumType(Direction.class, () -> MyEditors::direction),
 *         Types.editable(Duration.class, () -> Duration.ZERO, () -> MyEditors::duration)
 *                 .writtenAs(Types.call(Duration.class, Types.method(Duration.class, "ofMillis", long.class),
 *                         d -> List.of(d.toMillis()), parts -> Duration.ofMillis(Types.count(parts, 0)))));
 * }</pre>
 *
 * <p><b>Why it is here.</b> Before it, every plugin wrote the same plumbing by hand: a {@code method(…)}
 * lookup (three copies), a base class holding a class and a factory with {@code componentTypes()} derived from
 * it (three copies), an enum declaration whose fresh value is the first constant (two), and for a record the
 * three methods its canonical constructor already states. What is left in a plugin is what only it knows —
 * the fresh value, the editor, and for a call that is not a record, how a value comes apart.
 *
 * <p><b>Nothing here names a plugin's word</b> (the module's rule 4): these are shapes. And like the rest of
 * the module it is optional — {@link AbstractPluginType} and the contract's interfaces still work for a type
 * whose declaration needs more than a lambda.
 *
 * <h2>Lookups fail when the plugin loads, never in a bot's file</h2>
 *
 * <p>{@link #method}, {@link #constructor} and {@link #constant} throw {@link IllegalStateException} naming
 * the member that is gone. They are meant for {@code static final} fields, so a rename breaks the plugin's
 * own class initialisation and its own tests, rather than writing a call into a bot's file that no longer
 * compiles.
 *
 * <h2>Reading parts back degrades, never throws</h2>
 *
 * <p>{@link #whole}, {@link #number}, {@link #text} and the rest answer zero, empty or {@code false} for a
 * part that is missing or of the wrong kind: a value read out of a user's file may be shorter than this
 * version of the type expects, and no unreadable input may be the reason a project will not open.
 */
public final class Types {

    private Types() {}

    // ---- members, looked up once ----------------------------------------------------------------------

    /** {@code owner.name(parameters)}, public and possibly inherited. Throws when it is gone. */
    public static Method method(Class<?> owner, String name, Class<?>... parameters) {
        try {
            return owner.getMethod(name, parameters);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(owner.getName() + "." + name + " is gone", e);
        }
    }

    /** {@code new type(parameters)}, public. Throws when it is gone. */
    public static <T> Constructor<T> constructor(Class<T> type, Class<?>... parameters) {
        try {
            return type.getConstructor(parameters);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("new " + type.getName() + "(…) is gone", e);
        }
    }

    /** The public field {@code owner.name}, for {@link ComponentType#constants()}. Throws when it is gone. */
    public static Field constant(Class<?> owner, String name) {
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(owner.getName() + "." + name + " is gone", e);
        }
    }

    /**
     * The parts a factory is written with: its parameters, after the receiver when it is an instance method.
     * A varargs factory's last part is the <em>element</em> type, which the host repeats for every further
     * argument. Deriving the parts is what keeps {@code componentTypes()} and the call from drifting apart.
     */
    public static List<Class<?>> parts(Executable factory) {
        List<Class<?>> out = new ArrayList<>();
        if (factory instanceof Method m && !Modifier.isStatic(m.getModifiers())) out.add(m.getDeclaringClass());
        Class<?>[] parameters = factory.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            boolean repeated = factory.isVarArgs() && i == parameters.length - 1;
            out.add(repeated ? parameters[i].getComponentType() : parameters[i]);
        }
        return List.copyOf(out);
    }

    // ---- reading parts back ----------------------------------------------------------------------------

    /** Part {@code index} as a whole number, rounded, or {@code 0}. */
    public static int whole(List<Object> parts, int index) {
        return (int) Math.round(number(parts, index));
    }

    /** Part {@code index} as a long, rounded, or {@code 0}. */
    public static long count(List<Object> parts, int index) {
        return Math.round(number(parts, index));
    }

    /** Part {@code index} as a fractional number, or {@code 0}. */
    public static double number(List<Object> parts, int index) {
        return at(parts, index) instanceof Number n ? n.doubleValue() : 0;
    }

    /** Part {@code index} as text, or {@code ""}. */
    public static String text(List<Object> parts, int index) {
        return at(parts, index) instanceof String s ? s : "";
    }

    /** Part {@code index} as a yes/no, or {@code false}. */
    public static boolean flag(List<Object> parts, int index) {
        return at(parts, index) instanceof Boolean b && b;
    }

    /** Part {@code index} as {@code as}, or {@code null} — for a part that is itself a value. */
    public static <C> C part(List<Object> parts, int index, Class<C> as) {
        Object part = at(parts, index);
        return as.isInstance(part) ? as.cast(part) : null;
    }

    /**
     * Every part as {@code as}, for a varargs factory's run of one element type — or {@code null} when there
     * is none, or when any part is something else, which is a call this declaration does not build.
     */
    public static <E> List<E> each(List<Object> parts, Class<E> as) {
        if (parts == null || parts.isEmpty()) return null;
        List<E> out = new ArrayList<>(parts.size());
        for (Object part : parts) {
            if (!as.isInstance(part)) return null;
            out.add(as.cast(part));
        }
        return List.copyOf(out);
    }

    private static Object at(List<Object> parts, int index) {
        return parts == null || index < 0 || index >= parts.size() ? null : parts.get(index);
    }

    // ---- declarations ----------------------------------------------------------------------------------

    /**
     * A value written as a call to {@code factory}: a constructor, a static method, or an instance method on
     * part 0 that the host reads and never writes. Its parts are {@link #parts(Executable) the factory's},
     * so what the host writes and what {@code build} is handed cannot disagree.
     *
     * @param components how a value comes apart, in the factory's parameter order
     * @param build      the value back from its parts, or {@code null} for parts this call does not build
     */
    public static <T> Call<T> call(Class<T> type, Executable factory, Function<T, List<Object>> components,
                                   Function<List<Object>, T> build) {
        return new Call<>(type, factory, components, build, List.of());
    }

    /**
     * A record written as its canonical constructor, {@code new Point(1, 2)}: the parts are its components, a
     * value comes apart through its accessors and is built back through the constructor, so
     * {@code build(components(v))} equals {@code v} by construction. A missing or mistyped part reads as
     * zero, {@code false} or {@code null}, as the part readers above do; a constructor that refuses its
     * arguments builds nothing.
     */
    public static <R extends Record> Call<R> record(Class<R> type) {
        RecordComponent[] fields = type.getRecordComponents();
        Class<?>[] parameters = new Class<?>[fields.length];
        for (int i = 0; i < fields.length; i++) parameters[i] = fields[i].getType();
        Constructor<R> canonical;
        try {
            canonical = type.getConstructor(parameters);
        } catch (NoSuchMethodException e) {
            // The canonical constructor always exists; it is only missing from getConstructor when the record
            // is not public, and then neither the host nor this class may call it.
            throw new IllegalStateException(type.getName() + " is not a public record", e);
        }
        return call(type, canonical, value -> {
            List<Object> out = new ArrayList<>(fields.length);
            for (RecordComponent field : fields) {
                try {
                    out.add(field.getAccessor().invoke(value));
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(type.getName() + "." + field.getName() + "() failed", e);
                }
            }
            return out;
        }, parts -> {
            Object[] arguments = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) arguments[i] = coerce(parts, i, parameters[i]);
            try {
                return canonical.newInstance(arguments);
            } catch (ReflectiveOperationException | IllegalArgumentException refused) {
                return null;
            }
        });
    }

    private static Object coerce(List<Object> parts, int index, Class<?> to) {
        if (to == int.class) return whole(parts, index);
        if (to == long.class) return count(parts, index);
        if (to == double.class) return number(parts, index);
        if (to == float.class) return (float) number(parts, index);
        if (to == boolean.class) return flag(parts, index);
        if (to.isPrimitive()) {
            throw new IllegalStateException(to + " parts are not read back; declare this record with call(…)");
        }
        return part(parts, index, to);
    }

    /**
     * A type its owner draws: what it is, what a fresh one is, and the editor. Add {@link Declared#preview},
     * {@link Declared#freshCall} or {@link Declared#writtenAs} for what else it says.
     *
     * <p><b>The editor is named inside a supplier</b> — {@code () -> MyEditors::point}, not
     * {@code MyEditors::point} — and that is load-bearing. A lambda or method reference that returns a
     * {@code Node} links JavaFX the moment it is evaluated, which for a {@code static final} declaration is
     * when the plugin's type list is built; a headless host ({@code botmaker plugin validate}, the registry's
     * CI) builds that list without JavaFX, and would report the plugin as not loading at all. The outer
     * lambda returns a {@code Function}, which links nothing, and the inner one is evaluated only when the
     * host draws. The type makes the unsafe spelling a compile error rather than a rule to remember.
     *
     * @param fresh  asked every time a value is seeded, so it may read live state
     * @param editor the editor, which never answers {@code null}; read only when the host draws
     */
    public static <T> Declared<T> editable(Class<T> type, Supplier<T> fresh, Drawn editor) {
        return new Declared<>(type, fresh, editor, null, null);
    }

    /**
     * An enum its owner draws. Its Java is the constant's own name, which the host reads and writes without
     * help, so it needs no call; the fresh value is the first constant, which is what an enum with no obvious
     * default means by "unset".
     */
    public static <E extends Enum<E>> Declared<E> enumType(Class<E> type, Drawn editor) {
        return editable(type, () -> type.getEnumConstants()[0], editor);
    }

    /**
     * How a node is drawn for a value, named without linking JavaFX: {@code () -> MyEditors::point}. See
     * {@link #editable} for why the extra arrow is there.
     *
     * <p><b>Name a method declared to return {@code Node}, in another class.</b> An inner lambda is still a
     * method of the declaring class, and one whose body answers a subclass — {@code ctx -> new Label(…)} —
     * makes the verifier load {@code Label} and {@code Node} the moment that class loads, which is exactly the
     * failure the arrow exists to avoid.
     */
    @FunctionalInterface
    public interface Drawn extends Supplier<Function<ValueContext, Node>> {

        /** The node for {@code ctx}. */
        default Node draw(ValueContext ctx) {
            return get().apply(ctx);
        }
    }

    /**
     * {@link #call}'s declaration: a {@link ComponentType} with nothing written by hand but how a value comes
     * apart and goes back together. Immutable; {@link #constants} answers a copy.
     */
    public static final class Call<T> implements ComponentType<T> {

        private final Class<T> type;
        private final Executable factory;
        private final List<Class<?>> parts;
        private final Function<T, List<Object>> components;
        private final Function<List<Object>, T> build;
        private final List<Field> constants;

        private Call(Class<T> type, Executable factory, Function<T, List<Object>> components,
                     Function<List<Object>, T> build, List<Field> constants) {
            this.type = type;
            this.factory = factory;
            this.parts = parts(factory);
            this.components = components;
            this.build = build;
            this.constants = constants;
        }

        /** This call, plus the {@code public static final} fields a value equal to one is written as. */
        public Call<T> constants(Field... fields) {
            return new Call<>(type, factory, components, build, List.of(fields));
        }

        @Override public Class<T> type() { return type; }
        @Override public Executable factory() { return factory; }
        @Override public List<Class<?>> componentTypes() { return parts; }
        @Override public List<Object> components(T value) { return components.apply(value); }
        @Override public T build(List<Object> parts) { return build.apply(parts); }
        @Override public List<Field> constants() { return constants; }
    }

    /**
     * {@link #editable}'s declaration. Immutable: each {@code with} answers a copy, so a declaration can be
     * a {@code static final} field and still be refined where it is listed.
     */
    public static sealed class Declared<T> implements EditableType<T> permits DeclaredCall {

        final Class<T> type;
        final Supplier<T> fresh;
        final Drawn editor;
        final Drawn preview;
        final Method freshCall;

        Declared(Class<T> type, Supplier<T> fresh, Drawn editor, Drawn preview, Method freshCall) {
            this.type = type;
            this.fresh = fresh;
            this.editor = editor;
            this.preview = preview;
            this.freshCall = freshCall;
        }

        /** This type, drawn read-only as {@code preview} where the host shows a value without editing it. */
        public Declared<T> preview(Drawn preview) {
            return new Declared<>(type, fresh, editor, preview, freshCall);
        }

        /**
         * This type, whose fresh form is a call to {@code method} the bot re-evaluates rather than a value —
         * {@code Vision.lastMatch()}; {@code fresh} then answers {@code null}.
         */
        public Declared<T> freshCall(Method method) {
            return new Declared<>(type, fresh, editor, preview, method);
        }

        /** This type, whose Java is {@code call}: the one object the host lists as both. */
        public DeclaredCall<T> writtenAs(ComponentType<T> call) {
            return new DeclaredCall<>(this, call);
        }

        @Override public Class<T> type() { return type; }
        @Override public T fresh() { return fresh.get(); }
        @Override public Method freshCall() { return freshCall; }
        @Override public Node editor(ValueContext ctx) { return editor.draw(ctx); }
        @Override public Node preview(ValueContext ctx) { return preview == null ? null : preview.draw(ctx); }
    }

    /**
     * A {@link Declared} type whose Java is a call: an {@link EditableType} and a {@link ComponentType} in one
     * object, which is how the host tells that the type it lists is also the call it reads.
     */
    public static final class DeclaredCall<T> extends Declared<T> implements ComponentType<T> {

        private final ComponentType<T> call;

        private DeclaredCall(Declared<T> declared, ComponentType<T> call) {
            super(declared.type, declared.fresh, declared.editor, declared.preview, declared.freshCall);
            this.call = call;
        }

        @Override public DeclaredCall<T> preview(Drawn preview) {
            return super.preview(preview).writtenAs(call);
        }

        @Override public DeclaredCall<T> freshCall(Method method) {
            return super.freshCall(method).writtenAs(call);
        }

        @Override public Executable factory() { return call.factory(); }
        @Override public List<Class<?>> componentTypes() { return call.componentTypes(); }
        @Override public List<Object> components(T value) { return call.components(value); }
        @Override public T build(List<Object> parts) { return call.build(parts); }
        @Override public List<Field> constants() { return call.constants(); }
    }
}
