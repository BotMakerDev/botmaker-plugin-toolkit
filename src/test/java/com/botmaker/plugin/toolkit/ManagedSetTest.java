package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.TypeRef;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
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

    private static final ManagedValue<String> WORDS =
            ManagedValue.openSet(TestValue.Id.WORDS).of(String.class).in("Words").because("Mine.");

    /** A project holding the open set {@code @TestValue(WORDS)}, each constant a String; records every call. */
    private static final class Project implements PluginValues {
        final Map<String, Object> members = new LinkedHashMap<>();
        final List<String> calls = new ArrayList<>();

        @Override public List<String> ids() { return List.of(); }
        @Override public Optional<ValueContext> open(String id) { return Optional.empty(); }

        @Override public List<String> members(String id) {
            return WORDS.id().equals(id) ? List.copyOf(members.keySet()) : List.of();
        }

        @Override public Optional<ValueContext> open(String id, String member) {
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

        @Override public Optional<String> add(String id, String member, Object value) {
            calls.add("add " + id + "." + member);
            members.put(member, value);
            return Optional.empty();
        }

        @Override public Optional<String> rename(String id, String member, String newName) {
            calls.add("rename " + id + "." + member + " " + newName);
            members.put(newName, members.remove(member));
            return Optional.empty();
        }

        @Override public Optional<String> remove(String id, String member) {
            calls.add("remove " + id + "." + member);
            members.remove(member);
            return Optional.empty();
        }
    }

    @Test
    void everyOperationIsAddressedByTheDeclarationsId() {
        Project project = new Project();
        ManagedSet<String> words = ManagedSet.of(WORDS);

        assertEquals(Optional.empty(), words.add(project, "HELLO", "hello"));
        assertEquals(Optional.empty(), words.rename(project, "HELLO", "HI"));
        assertEquals(Optional.of("hello"), words.read(project, "HI"));
        assertTrue(words.contains(project, "HI"));
        assertEquals(Optional.empty(), words.remove(project, "HI"));

        String id = WORDS.id();
        assertEquals(List.of("add " + id + ".HELLO", "rename " + id + ".HELLO HI", "remove " + id + ".HI"),
                project.calls);
        assertTrue(words.members(project).isEmpty());
    }

    @Test
    void aConstantTheGrammarCannotReadReadsEmpty() {
        Project project = new Project();
        project.members.put("COUNT", 3);

        assertTrue(ManagedSet.of(WORDS).read(project, "COUNT").isEmpty());
    }

    @Test
    void noHostIsAHostWithNoSourceTree() {
        ManagedSet<String> words = ManagedSet.of(WORDS);

        assertTrue(words.members(null).isEmpty());
        assertTrue(words.read(null, "HELLO").isEmpty());
        assertEquals(Optional.of(PluginValues.NO_SOURCE_TREE), words.add(null, "HELLO", "hello"));
    }

    @Test
    void aMethodsValueIsNoSet() {
        assertThrows(IllegalArgumentException.class, () -> ManagedSet.of(
                ManagedValue.method(TestValue.Id.GREETING).in("Values").holds(String.class, "hi").because("Mine.")));
    }
}
