package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedOpenSet;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;

import java.util.List;
import java.util.Optional;

/**
 * One open set — a class of constants the plugin grows — changed from a plugin's own window with no id
 * spelled and no cast: {@link ManagedHandle}'s counterpart for {@code ManagedValue.openSet}.
 *
 * <pre>{@code
 * static final ManagedSet<Item> ITEMS = ManagedSet.of(MyValues.ITEMS);
 *
 * ITEMS.add(values, Member.of("FIRST"), Item.named("First"));   // a value of another class does not compile
 * Optional<Item> first = ITEMS.read(values, Member.of("FIRST"));
 * Optional<Member> picked = ITEMS.picked(ctx);                  // the constant a slot names
 * }</pre>
 *
 * <p>Each operation is {@link PluginValues}' own, addressed by the declaration; what this adds is the element
 * type, so a value of another class is a compile error here rather than a refusal from the host. A constant is a
 * {@link Member}, which owns the {@code Group.NAME} spelling of a set of nested enums. Everything is total: no
 * {@code values} is a host with no source tree.
 *
 * @param <E> the class of each constant, the declaration's {@link ManagedValue#type()}
 */
public final class ManagedSet<E> {

    private final ManagedOpenSet<E> set;

    private ManagedSet(ManagedOpenSet<E> set) {
        this.set = set;
    }

    /** The open set {@code set} declares. */
    public static <E> ManagedSet<E> of(ManagedOpenSet<E> set) {
        if (set == null) throw new IllegalArgumentException("a managed set changes an open set; none given");
        return new ManagedSet<>(set);
    }

    /** The declaration this changes. */
    public ManagedOpenSet<E> value() {
        return set;
    }

    // ---- the constants ---------------------------------------------------------------------------------

    /** The constants, in the order they are written; empty when the project has no such class. */
    public List<Member> members(PluginValues values) {
        return host(values).members(set);
    }

    /** Whether the set declares {@code member}. */
    public boolean contains(PluginValues values, Member member) {
        return member != null && members(values).contains(member);
    }

    /** {@code member}'s initialiser as a value to edit, or empty when there is no such constant to read. */
    public Optional<ValueContext> open(PluginValues values, Member member) {
        return host(values).open(set, member);
    }

    /** What {@code member} holds, or empty when there is none or the grammar cannot read it. */
    public Optional<E> read(PluginValues values, Member member) {
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
    public Optional<String> add(PluginValues values, Member member, E value) {
        return host(values).add(set, member, value);
    }

    /** Every use of {@code member} outside its own declaration. */
    public List<PluginValues.Use> uses(PluginValues values, Member member) {
        return host(values).uses(set, member);
    }

    /** Renames {@code member} and every use of it; empty when done, else why not. */
    public Optional<String> rename(PluginValues values, Member member, String newName) {
        return host(values).rename(set, member, newName);
    }

    /** Points every use of {@code member} at {@code replacement}; see {@link PluginValues#repoint}. */
    public Optional<String> repoint(PluginValues values, Member member, Member replacement, String note) {
        return host(values).repoint(set, member, replacement, note);
    }

    /** Deletes {@code member}'s declaration; refused while anything uses it. */
    public Optional<String> remove(PluginValues values, Member member) {
        return host(values).remove(set, member);
    }

    /**
     * Declares {@code member} just above {@code before} when it is written below it, so {@code before}'s
     * initialiser may read it; see {@link PluginValues#moveBefore}. Empty when done or already so, else why not.
     */
    public Optional<String> moveBefore(PluginValues values, Member member, Member before) {
        return host(values).moveBefore(set, member, before);
    }

    // ---- a slot naming one -----------------------------------------------------------------------------

    /**
     * The constant {@code ctx} is written as — {@code Flows.DAILY} — or empty when it names none of this set's.
     * What a picker shows as picked: two constants may hold equal values, so {@link #read(ValueContext)} cannot
     * say which one the bot names.
     */
    public Optional<Member> picked(ValueContext ctx) {
        return ctx == null ? Optional.empty() : ctx.member(set);
    }

    /** Writes {@code ctx} as a reference to {@code member}; empty when written, else the sentence to show. */
    public Optional<String> pick(ValueContext ctx, Member member) {
        return ctx == null ? Optional.of("Nothing to write.") : ctx.writeMember(set, member);
    }

    // ---- a set of nested enums -------------------------------------------------------------------------

    /** The nested enums' names, in the order they are written; empty for a flat set. */
    public List<String> groups(PluginValues values) {
        return host(values).groups(set);
    }

    /**
     * Declares a nested enum {@code group} holding {@code constants}; see {@link PluginValues#addGroup}. Empty when
     * done, else why not.
     */
    public Optional<String> addGroup(PluginValues values, String group, List<String> constants) {
        return host(values).addGroup(set, group, constants);
    }

    /** Renames the nested enum {@code group} and every use of it; empty when done, else why not. */
    public Optional<String> renameGroup(PluginValues values, String group, String newName) {
        return host(values).renameGroup(set, group, newName);
    }

    /** Deletes the nested enum {@code group}; refused while anything uses it. */
    public Optional<String> removeGroup(PluginValues values, String group) {
        return host(values).removeGroup(set, group);
    }

    /** The methods that return {@code group}, as {@link PluginValues#methods(ManagedValue, String)} names them. */
    public List<String> methods(PluginValues values, String group) {
        return host(values).methods(set, group);
    }

    /** Writes a method returning {@code group}; see {@link PluginValues#createMethod}. */
    public PluginValues.MethodWrite createMethod(PluginValues values, String group, String className, String method) {
        return host(values).createMethod(set, group, className, method);
    }

    /**
     * The plugin's value of the nested enum {@code group}, as a {@code G}; see
     * {@link ManagedOpenSet.EnumGroups#byGroup}. Empty for any other kind of set.
     */
    public <G> Optional<G> byGroup(Class<G> as, String group) {
        return set instanceof ManagedOpenSet.EnumGroups<E> groups ? groups.byGroup(as, group) : Optional.empty();
    }

    private static PluginValues host(PluginValues values) {
        return values == null ? PluginValues.NONE : values;
    }
}
