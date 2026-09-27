package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;

import java.util.Optional;

/**
 * One {@link ManagedValue}, read and written from a plugin's own window with no id spelled and no cast.
 *
 * <pre>{@code
 * static final ManagedHandle<Flow> FLOW = ManagedHandle.of(MyValues.FLOW);
 *
 * Flow now = FLOW.read(services).orElse(Flow.NONE);
 * String refused = FLOW.write(services, edited);   // null when written
 * }</pre>
 *
 * <p>Every plugin with a value wrote these four steps by hand — open the id, read it as its type, ask the host
 * to create the holder when the project has none, then set — once per value (2026-09-28). Everything here is
 * total: no services, no method, an unreadable body and a host that throws all answer empty, so a window
 * degrades to its defaults rather than failing to open.
 *
 * @param <T> the value's class, the declaration's {@link ManagedValue#type()}
 */
public final class ManagedHandle<T> {

    private final ManagedValue<T> value;

    private ManagedHandle(ManagedValue<T> value) {
        this.value = value;
    }

    /** A handle on {@code value}, which must be a method-shaped value rather than an open set. */
    public static <T> ManagedHandle<T> of(ManagedValue<T> value) {
        if (value == null || value.isOpenSet()) {
            throw new IllegalArgumentException("a handle reads one method's value; an open set has none");
        }
        return new ManagedHandle<>(value);
    }

    /** The declaration this reads and writes. */
    public ManagedValue<T> value() {
        return value;
    }

    /** The value's context in the open project, or empty when there is none to edit. */
    public Optional<ValueContext> open(StudioServices services) {
        try {
            return services == null ? Optional.empty() : services.pluginValues().open(value.id());
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /**
     * {@link #open}, asking the host to write the holder first when the project has none — what a window that
     * is about to save wants. Empty when there is still nothing to edit.
     */
    public Optional<ValueContext> openOrCreate(StudioServices services) {
        Optional<ValueContext> found = open(services);
        if (found.isPresent() || services == null) return found;
        try {
            services.pluginValues().create(value.id());
        } catch (RuntimeException refused) {
            return Optional.empty();
        }
        return open(services);
    }

    /** The value the bot's Java holds, or empty when there is none or the grammar cannot read it. */
    public Optional<T> read(StudioServices services) {
        return open(services).flatMap(this::read);
    }

    /** The value {@code ctx} holds, as this value's type. */
    public Optional<T> read(ValueContext ctx) {
        try {
            return ctx == null ? Optional.empty() : ctx.value(value.type());
        } catch (RuntimeException unreadable) {
            return Optional.empty();
        }
    }

    /** Whether {@code ctx} holds a value the grammar can read — a window may rewrite only such a one. */
    public boolean readable(ValueContext ctx) {
        return read(ctx).isPresent();
    }

    /**
     * Writes {@code newValue}, creating the holder when the project has none.
     *
     * @return null when written, else the sentence to show
     */
    public String write(StudioServices services, T newValue) {
        Optional<ValueContext> ctx = openOrCreate(services);
        if (ctx.isEmpty()) {
            return "This project has no " + value.id() + " value BotMaker can write"
                    + (value.holder() == null ? "." : " in " + value.holder() + ".java.");
        }
        ctx.get().set(newValue);
        return null;
    }
}
