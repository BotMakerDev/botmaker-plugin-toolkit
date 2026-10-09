package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.Theme;
import com.botmaker.plugin.api.slot.TypeRef;
import com.botmaker.plugin.api.slot.ValueContext;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.source.PluginValues;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedHandleTest {

    private static final ManagedValue<String> GREETING =
            ManagedValue.method(TestValue.Id.GREETING).in("Values").holds(String.class, "hello").because("Mine.");

    /** A project holding one {@code @TestValue(GREETING)} String, created only when asked. */
    private static final class Project implements StudioServices, PluginValues {
        Object held;
        boolean exists;

        @Override public Path projectDir() { return Path.of("."); }
        @Override public Path resourcesDir() { return Path.of("."); }
        @Override public Theme theme() { return null; }
        @Override public Dialogs dialogs() { return null; }
        @Override public PluginValues pluginValues() { return this; }

        @Override public List<String> ids() { return exists ? List.of(GREETING.id()) : List.of(); }

        @Override public Optional<ValueContext> open(String id) {
            if (!exists || !GREETING.id().equals(id)) return Optional.empty();
            return Optional.of(new ValueContext() {
                @Override public TypeRef type() { return TypeRef.of(String.class); }
                @Override public <T> Optional<T> value(Class<T> type) {
                    return type.isInstance(held) ? Optional.of(type.cast(held)) : Optional.empty();
                }
                @Override public void set(Object value) { held = value; }
                @Override public String source() { return String.valueOf(held); }
                @Override public StudioServices services() { return Project.this; }
            });
        }

        @Override public Optional<String> create(String id) {
            exists = true;
            held = "hello";
            return Optional.empty();
        }
    }

    @Test
    void writeCreatesTheHolderWhenTheProjectHasNone() {
        Project project = new Project();
        ManagedHandle<String> handle = ManagedHandle.of(GREETING);

        assertTrue(handle.read(project).isEmpty(), "nothing to read before the holder exists");
        assertNull(handle.write(project, "hi"));
        assertEquals(Optional.of("hi"), handle.read(project));
    }

    @Test
    void aValueTheGrammarCannotReadIsNotReadable() {
        Project project = new Project();
        project.exists = true;
        project.held = 42;
        ManagedHandle<String> handle = ManagedHandle.of(GREETING);

        assertTrue(handle.read(project).isEmpty());
        assertTrue(handle.open(project).filter(ctx -> !handle.readable(ctx)).isPresent());
    }

    @Test
    void noServicesIsEmptyAndWriteSaysWhy() {
        ManagedHandle<String> handle = ManagedHandle.of(GREETING);

        assertTrue(handle.read((StudioServices) null).isEmpty());
        assertEquals("This project has no greeting value BotMaker can write in Values.java.",
                handle.write(null, "hi"));
    }

    @Test
    void anOpenSetHasNoValueToHandle() {
        assertThrows(IllegalArgumentException.class,
                () -> ManagedHandle.of(ManagedValue.openSet(TestValue.Id.PICTURES).of(String.class).in("Pictures")
                        .because("Mine.")));
    }
}
