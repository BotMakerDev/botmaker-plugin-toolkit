package com.botmaker.plugin.toolkit.testing;

import com.botmaker.plugin.api.DeclaredPlugin;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.ToolbarHolder;
import com.botmaker.plugin.api.TypeHolder;
import com.botmaker.plugin.api.toolbar.ToolbarGroup;
import com.botmaker.plugin.api.toolbar.ToolbarItem;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.DeclaredCallType;
import com.botmaker.plugin.api.value.PluginType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginTestsTest {

    @TempDir
    Path dir;

    public static final class Good extends DeclaredPlugin {
        public Good() {
            super(StudioPlugin.declare());
        }
    }

    public static final class BrokenToolbar extends DeclaredPlugin {
        public BrokenToolbar() {
            super(StudioPlugin.declare().toolbar(Throws.class));
        }
    }

    static final class Throws implements ToolbarHolder {
        Throws() {
            throw new IllegalStateException("no toolbar today");
        }

        @Override
        public List<ToolbarItem> toolbar() {
            return List.of();
        }
    }

    public static class NotFinal extends DeclaredPlugin {
        public NotFinal() {
            super(StudioPlugin.declare());
        }
    }

    public static final class NeedsArgument extends DeclaredPlugin {
        public NeedsArgument(String name) {
            super(StudioPlugin.declare());
        }
    }

    @Test
    void a_plugin_its_pom_names_passes() throws IOException {
        PluginTests.assertDeclared(Good.class, pom(Good.class.getName(), "Good"));
    }

    @Test
    void a_pom_naming_another_class_or_no_name_fails_with_both() throws IOException {
        AssertionError error = assertThrows(AssertionError.class,
                () -> PluginTests.assertDeclared(Good.class, pom("com.example.Other", "")));
        assertTrue(error.getMessage().contains("names com.example.Other"), error.getMessage());
        assertTrue(error.getMessage().contains("must set <name>"), error.getMessage());
    }

    @Test
    void a_pom_without_the_property_says_what_to_write() throws IOException {
        Path pom = Files.writeString(dir.resolve("pom.xml"), "<project><name>X</name></project>");
        AssertionError error = assertThrows(AssertionError.class, () -> PluginTests.assertDeclared(Good.class, pom));
        assertTrue(error.getMessage().contains("<botmaker.plugin>" + Good.class.getName()), error.getMessage());
    }

    @Test
    void a_holder_that_throws_when_built_fails_the_check() throws IOException {
        AssertionError error = assertThrows(AssertionError.class, () -> PluginTests.assertDeclared(
                BrokenToolbar.class, pom(BrokenToolbar.class.getName(), "Broken")));
        assertTrue(error.getMessage().contains(".toolbar:"), error.getMessage());
        assertTrue(error.getMessage().contains("no toolbar today"), error.getMessage());
    }

    @Test
    void a_class_the_host_cannot_construct_fails_the_check() throws IOException {
        AssertionError notFinal = assertThrows(AssertionError.class, () -> PluginTests.assertDeclared(
                NotFinal.class, pom(NotFinal.class.getName(), "N")));
        assertTrue(notFinal.getMessage().contains("public final"), notFinal.getMessage());
        AssertionError argument = assertThrows(AssertionError.class, () -> PluginTests.assertDeclared(
                NeedsArgument.class, pom(NeedsArgument.class.getName(), "N")));
        assertTrue(argument.getMessage().contains("no arguments"), argument.getMessage());
    }

    static final class Listed implements ToolbarHolder {
        public static final ToolbarItem SHOWN = item("shown");
        public static final ToolbarItem FORGOTTEN = item("forgotten");
        public static final String NOT_AN_ITEM = "ignored";

        @Override
        public List<ToolbarItem> toolbar() {
            return List.of(SHOWN);
        }
    }

    static final class AllListed implements ToolbarHolder {
        public static final ToolbarItem SHOWN = Listed.SHOWN;

        @Override
        public List<ToolbarItem> toolbar() {
            return List.of(SHOWN);
        }
    }

    public record Pair(int a, int b) {}

    static final class TypeAsComponent implements TypeHolder {
        public static final DeclaredCallType<Pair> TEXT = PluginType.value(Pair.class).fresh(() -> new Pair(0, 0))
                .editor(() -> context -> null).writtenAsRecord();

        @Override
        public List<PluginType<?>> types() {
            return List.of();
        }

        @Override
        public List<ComponentType<?>> components() {
            return List.of(TEXT);
        }
    }

    @Test
    void a_type_listed_as_a_component_is_not_listed() {
        AssertionError error = assertThrows(AssertionError.class,
                () -> PluginTests.assertListsEveryField(TypeAsComponent.class));
        assertTrue(error.getMessage().contains("declares TEXT but"), error.getMessage());
    }

    @Test
    void a_field_its_holder_method_does_not_list_fails_by_name() {
        AssertionError error = assertThrows(AssertionError.class, () -> PluginTests.assertListsEveryField(Listed.class));
        assertTrue(error.getMessage().contains("declares FORGOTTEN but"), error.getMessage());
        PluginTests.assertListsEveryField(AllListed.class);
    }

    private static ToolbarItem item(String id) {
        return ToolbarItem.id(id).label(id).tooltip(id).in(ToolbarGroup.TOOLS, 0).onPress(() -> context -> {});
    }

    private Path pom(String plugin, String name) throws IOException {
        return Files.writeString(dir.resolve("pom.xml"), """
                <project>
                    <name>%s</name>
                    <properties>
                        <botmaker.plugin>%s</botmaker.plugin>
                    </properties>
                </project>
                """.formatted(name, plugin));
    }
}
