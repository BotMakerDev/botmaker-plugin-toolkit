package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;

import java.util.List;
import java.util.Optional;

/**
 * One open set — a class of constants the plugin grows — changed from a plugin's own window with no id
 * spelled and no cast: {@link ManagedHandle}'s counterpart for {@code ManagedValue.openSet}.
 *
 * <pre>{@code
 * static final ManagedSet<Item> ITEMS = ManagedSet.of(MyValues.ITEMS);
 *
 * ITEMS.add(values, "FIRST", Item.named("First"));   // a value of another class does not compile
 * Optional<Item> first = ITEMS.read(values, "FIRST");
 * }</pre>
 *
 * <p>Each operation is {@link PluginValues}' own, addressed by the declaration's id; what this adds is the
 * element type, so a value of another class is a compile error here rather than a refusal from the host.
 * Everything is total: no {@code values} is a host with no source tree.
 *
 * @param <E> the class of each constant, the declaration's {@link ManagedValue#type()}
 */
public final class ManagedSet<E> {

    private final ManagedValue<E> set;

    private ManagedSet(ManagedValue<E> set) {
        this.set = set;
    }

    /** The set {@code set} declares, which must be an open set rather than one method's value. */
    public static <E> ManagedSet<E> of(ManagedValue<E> set) {
        if (set == null || !set.isOpenSet()) {
            throw new IllegalArgumentException("a managed set changes an open set; a method's value has one value");
        }
        return new ManagedSet<>(set);
    }

    /** The declaration this changes. */
    public ManagedValue<E> value() {
        return set;
    }

    /** The constants' names, in the order they are written; empty when the project has no such class. */
    public List<String> members(PluginValues values) {
        return host(values).members(set.id());
    }

    /** Whether the set declares {@code member}. */
    public boolean contains(PluginValues values, String member) {
        return member != null && members(values).contains(member);
    }

    /** {@code member}'s initialiser as a value to edit, or empty when there is no such constant to read. */
    public Optional<ValueContext> open(PluginValues values, String member) {
        return host(values).open(set.id(), member);
    }

    /** What {@code member} holds, or empty when there is none or the grammar cannot read it. */
    public Optional<E> read(PluginValues values, String member) {
        return open(values, member).flatMap(this::read);
    }

    /** The constant {@code ctx} holds, as this set's element type. */
    public Optional<E> read(ValueContext ctx) {
        return ctx == null || set.type() == null ? Optional.empty() : ctx.value(set.type());
    }

    /**
     * Declares {@code member = value}.
     *
     * @return empty when declared, else the host's sentence
     */
    public Optional<String> add(PluginValues values, String member, E value) {
        return host(values).add(set.id(), member, value);
    }

    /** Every use of {@code member} outside its own declaration. */
    public List<PluginValues.Use> uses(PluginValues values, String member) {
        return host(values).uses(set.id(), member);
    }

    /** Renames {@code member} and every use of it; empty when done, else why not. */
    public Optional<String> rename(PluginValues values, String member, String newName) {
        return host(values).rename(set.id(), member, newName);
    }

    /** Points every use of {@code member} at {@code replacement}; see {@link PluginValues#repoint}. */
    public Optional<String> repoint(PluginValues values, String member, String replacement, String note) {
        return host(values).repoint(set.id(), member, replacement, note);
    }

    /** Deletes {@code member}'s declaration; refused while anything uses it. */
    public Optional<String> remove(PluginValues values, String member) {
        return host(values).remove(set.id(), member);
    }

    private static PluginValues host(PluginValues values) {
        return values == null ? PluginValues.NONE : values;
    }
}
