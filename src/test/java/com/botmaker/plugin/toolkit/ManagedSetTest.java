package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.TypeRef;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedOpenSet;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import com.botmaker.plugin.api.source.PluginValues.Member;
import com.botmaker.plugin.toolkit.testing.TestContexts;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedSetTest {

    private static final ManagedOpenSet<String> WORDS =
            ManagedValue.openSet(TestValue.Id.WORDS).of(String.class).in("Words").because("Mine.");

    /** A project holding the open set {@code @TestValue(WORDS)}, each constant a String; records every call. */
    private static final class Project implements PluginValues {
        final Map<Member, Object> members = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();

        @Override public List<String> ids() { return List.of(); }
        @Override public Optional<ValueContext> open(ManagedValue<?> value) { return Optional.empty(); }

        @Override public List<Member> members(ManagedValue<?> set) {
            return WORDS.id().equals(set.id()) ? List.copyOf(members.keySet()) : List.of();
        }

        @Override public Optional<ValueContext> open(ManagedValue<?> set, Member member) {
            if (!members.containsKey(member)) return Optional.empty();
            return Optional.of(new ValueContext() {
                @Override public TypeRef type() { return TypeRef.of(String.class); }
                @Override public <T> Optional<T> value(Class<T> type) {
                    Object held = members.get(member);
                    return type.isInstance(held) ? Optional.of(type.cast(held)) : Optional.empty();
                }
                @Override public void set(Object value) { members.put(member, value); }
                @Override public String source() { return String.valueOf(members.get(member)); }
                @Override public StudioServices services() { return null; }
            });
        }

        @Override public <E> Optional<String> add(ManagedValue<E> set, Member member, E value) {
            calls.add("add " + set.id() + "." + member);
            members.put(member, value);
            return Optional.empty();
        }

        @Override public Optional<String> rename(ManagedValue<?> set, Member member, String newName) {
            calls.add("rename " + set.id() + "." + member + " " + newName);
            members.put(member.renamed(newName), members.remove(member));
            return Optional.empty();
        }

        @Override public Optional<String> remove(ManagedValue<?> set, Member member) {
            calls.add("remove " + set.id() + "." + member);
            members.remove(member);
            return Optional.empty();
        }
    }

    private static final Member HELLO = Member.of("HELLO");
    private static final Member HI = Member.of("HI");

    @Test
    void everyOperationIsAddressedByTheDeclaration() {
        Project project = new Project();
        ManagedSet<String> words = ManagedSet.of(WORDS);

        assertEquals(Optional.empty(), words.add(project, HELLO, "hello"));
        assertEquals(Optional.empty(), words.rename(project, HELLO, "HI"));
        assertEquals(Optional.of("hello"), words.read(project, HI));
        assertTrue(words.contains(project, HI));
        assertEquals(List.of(HI), words.members(project));
        assertEquals(Optional.empty(), words.remove(project, HI));

        String id = WORDS.id();
        assertEquals(List.of("add " + id + ".HELLO", "rename " + id + ".HELLO HI", "remove " + id + ".HI"),
                project.calls);
        assertTrue(words.members(project).isEmpty());
    }

    @Test
    void aConstantTheGrammarCannotReadReadsEmpty() {
        Project project = new Project();
        project.members.put(Member.of("COUNT"), 3);

        assertTrue(ManagedSet.of(WORDS).read(project, Member.of("COUNT")).isEmpty());
    }

    @Test
    void noHostIsAHostWithNoSourceTree() {
        ManagedSet<String> words = ManagedSet.of(WORDS);

        assertTrue(words.members(null).isEmpty());
        assertTrue(words.groups(null).isEmpty());
        assertTrue(words.read(null, HELLO).isEmpty());
        assertEquals(Optional.of(PluginValues.NO_SOURCE_TREE), words.add(null, HELLO, "hello"));
        assertEquals(Optional.of(PluginValues.NO_SOURCE_TREE), words.moveBefore(null, HELLO, HI));
    }

    @Test
    void aSlotNamesItsConstantAndAPickWritesOneByReference() {
        ManagedSet<String> words = ManagedSet.of(WORDS);
        TestContexts.Recording ctx = TestContexts.row(String.class, "Words.HELLO").withValue("hello")
                .withMember(WORDS, HELLO);

        assertEquals(Optional.of(HELLO), words.picked(ctx));
        assertEquals(Optional.empty(), words.pick(ctx, HI));
        assertEquals(Optional.of(HI), words.picked(ctx));
        assertEquals(1, ctx.writes());
        assertTrue(words.pick(null, HI).isPresent(), "no context is refused, not thrown");
    }

    @Test
    void aMemberChoiceSaysTheConstantTheNoneLabelOrWhatIsWritten() {
        ManagedSet<String> words = ManagedSet.of(WORDS);

        assertEquals("HELLO", Editors.memberLabel(TestContexts.row(String.class, "Words.HELLO")
                .withMember(WORDS, HELLO), words, "Pick a word", "(none)", ""));
        assertEquals("(none)", Editors.memberLabel(TestContexts.row(String.class, "\"\"").withValue(""),
                words, "Pick a word", "(none)", ""));
        assertEquals("\"hey\"", Editors.memberLabel(TestContexts.row(String.class, "\"hey\"").withValue("hey"),
                words, "Pick a word", "(none)", ""));
        assertEquals("Pick a word", Editors.memberLabel(TestContexts.row(String.class, ""), words,
                "Pick a word", null, null));
    }

    /** A method's value is no set: {@code of} takes an open set, so passing one does not compile. */
    @Test
    void noSetIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> ManagedSet.of(null));
    }
}
