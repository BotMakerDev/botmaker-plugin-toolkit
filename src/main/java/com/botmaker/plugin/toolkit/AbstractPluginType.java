package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.PluginType;

import java.util.List;

/**
 * A {@link PluginType} that holds its own {@link Class} and reads its components back without a cast at
 * every line — for a declaration that needs more than {@link Types}' lambdas say.
 *
 * <pre>{@code
 * final class ColorType extends AbstractPluginType<Color> implements EditableType<Color>, ComponentType<Color> {
 *     ColorType() { super(Color.class); }
 *
 *     @Override public Color fresh()                  { return Color.WHITE; }
 *     @Override public Node  editor(ValueContext ctx) { return MyEditors.color(ctx); }
 *
 *     @Override public List<Class<?>> componentTypes()  { return List.of(int.class, int.class, int.class); }
 *     @Override public List<Object> components(Color c) { return List.of(c.getRed(), c.getGreen(), c.getBlue()); }
 *     @Override public Color build(List<Object> parts)  { return new Color(whole(parts, 0), whole(parts, 1), whole(parts, 2)); }
 * }
 * }</pre>
 *
 * <p><b>Reach for {@link Types} first</b> (2026-09-28): {@code Types.editable(…)}, {@code Types.enumType(…)},
 * {@code Types.record(…)} and {@code Types.call(…)} declare the same thing as one expression, and the SDK and
 * basics declare every type they own that way. This class stays for a declaration with state or behaviour of
 * its own.
 *
 * <p><b>It deliberately does not implement {@link ComponentType}.</b> The two questions are independent —
 * a type may be picked without being taken apart, and taken apart without ever being picked — and welding
 * them here would owe every enum constant a {@code components} nothing calls. Extend this, and add
 * {@code implements ComponentType<T>} when the type's Java is a call.
 *
 * <p><b>It deliberately does not implement {@code EditableType} either</b> (2026-09-27): a type is drawn by its
 * owner only when it says so, so a subclass that answered {@code null} from an inherited {@code editor} cannot
 * pass {@code botmaker plugin validate}'s picker check by accident. Add {@code implements EditableType<T>}.
 *
 * <p>The readers are {@link Types}' — {@code whole(parts, 0)} rather than {@code (int) parts.get(0)} — kept
 * here as inherited names so a subclass reads them unqualified. Each degrades rather than throwing.
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

    /** {@link Types#whole}. */
    protected static int whole(List<Object> components, int index) {
        return Types.whole(components, index);
    }

    /** {@link Types#count}. */
    protected static long count(List<Object> components, int index) {
        return Types.count(components, index);
    }

    /** {@link Types#number}. */
    protected static double number(List<Object> components, int index) {
        return Types.number(components, index);
    }

    /** {@link Types#text}. */
    protected static String text(List<Object> components, int index) {
        return Types.text(components, index);
    }

    /** {@link Types#flag}. */
    protected static boolean flag(List<Object> components, int index) {
        return Types.flag(components, index);
    }

    /** {@link Types#part}. */
    protected static <C> C part(List<Object> components, int index, Class<C> as) {
        return Types.part(components, index, as);
    }
}
