package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.PluginType;

import java.util.List;

/**
 * A {@link PluginType} that holds its own {@link Class} and reads its components back without a cast at
 * every line.
 *
 * <pre>{@code
 * final class PointType extends AbstractPluginType<Point> implements ComponentType<Point> {
 *     PointType() { super(Point.class); }
 *
 *     @Override public Point fresh()                  { return new Point(0, 0); }
 *     @Override public Node  editor(ValueContext ctx) { return Editors.tuplePill(ctx, this, SPEC, picks); }
 *
 *     @Override public List<Class<?>> componentTypes()  { return List.of(int.class, int.class); }
 *     @Override public List<Object> components(Point p) { return List.of(p.x(), p.y()); }
 *     @Override public Point build(List<Object> parts)  { return new Point(whole(parts, 0), whole(parts, 1)); }
 * }
 * }</pre>
 *
 * <p><b>Optional, like the rest of this module.</b> It saves one field and the four reads below; a plugin
 * that would rather implement the two interfaces directly loses nothing, and the SDK's enum types do
 * exactly that.
 *
 * <p><b>It deliberately does not implement {@link ComponentType}.</b> The two questions are independent —
 * a type may be picked without being taken apart, and taken apart without ever being picked — and welding
 * them here would owe every enum constant a {@code components} nothing calls. Extend this, and add
 * {@code implements ComponentType<T>} when the type's Java is a call.
 *
 * <h2>Why the readers are here and not on the contract</h2>
 *
 * <p>{@code build(List<Object>)} is the one place a declaration has to trust what it is handed: the host
 * passes back the components this same type produced, so the casts are sound, and a helper that says
 * {@code whole(parts, 0)} rather than {@code (int) parts.get(0)} makes a wrong index a clearer failure
 * than a {@code ClassCastException} on an unrelated line. Putting them on {@link ComponentType} would make
 * them {@code default} methods every implementor inherits whether or not it has components at all.
 *
 * <p>Each one <b>degrades rather than throwing</b> — a missing component reads as zero, empty or
 * {@code false}. A value read out of a user's file may be shorter than this version of the type expects,
 * and the rule everywhere in this module is that no unreadable input may be the reason a project will not
 * open.
 *
 * @param <T> the plugin's own type
 */
public abstract class AbstractPluginType<T> implements PluginType<T> {

    private final Class<T> type;

    protected AbstractPluginType(Class<T> type) {
        this.type = type;
    }

    @Override
    public Class<T> type() {
        return type;
    }

    /** Component {@code index} as a whole number, or {@code 0}. */
    protected static int whole(List<Object> components, int index) {
        return (int) Math.round(number(components, index));
    }

    /** Component {@code index} as a long, or {@code 0}. */
    protected static long count(List<Object> components, int index) {
        return Math.round(number(components, index));
    }

    /** Component {@code index} as a fractional number, or {@code 0}. */
    protected static double number(List<Object> components, int index) {
        Object part = at(components, index);
        return part instanceof Number n ? n.doubleValue() : 0;
    }

    /** Component {@code index} as text, or {@code ""}. */
    protected static String text(List<Object> components, int index) {
        Object part = at(components, index);
        return part instanceof String s ? s : "";
    }

    /** Component {@code index} as a yes/no, or {@code false}. */
    protected static boolean flag(List<Object> components, int index) {
        Object part = at(components, index);
        return part instanceof Boolean b && b;
    }

    /**
     * Component {@code index} as {@code as}, or {@code null} — for a component that is itself a value some
     * type describes.
     */
    protected static <C> C part(List<Object> components, int index, Class<C> as) {
        Object part = at(components, index);
        return as.isInstance(part) ? as.cast(part) : null;
    }

    private static Object at(List<Object> components, int index) {
        return components == null || index < 0 || index >= components.size() ? null : components.get(index);
    }
}
