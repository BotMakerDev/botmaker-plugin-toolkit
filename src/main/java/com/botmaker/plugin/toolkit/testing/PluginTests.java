package com.botmaker.plugin.toolkit.testing;

import com.botmaker.plugin.api.AssistantHolder;
import com.botmaker.plugin.api.CatalogHolder;
import com.botmaker.plugin.api.DeclaredPlugin;
import com.botmaker.plugin.api.EditorHolder;
import com.botmaker.plugin.api.OverlayHolder;
import com.botmaker.plugin.api.RunOverlayHolder;
import com.botmaker.plugin.api.ToolbarHolder;
import com.botmaker.plugin.api.TypeHolder;
import com.botmaker.plugin.api.ValueHolder;
import com.botmaker.plugin.api.assist.AssistantTool;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.overlay.OverlayPart;
import com.botmaker.plugin.api.run.RunOverlayPart;
import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.plugin.api.source.ManagedValue;
import com.botmaker.plugin.api.toolbar.ToolbarItem;
import com.botmaker.plugin.api.value.ComponentType;
import com.botmaker.plugin.api.value.PluginType;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The checks a host makes when it loads a plugin, run by the plugin's own {@code mvn verify}, so a plugin a host
 * would refuse or load empty fails its build instead.
 *
 * <pre>{@code
 * @Test
 * void the_plugin_is_declared() {
 *     PluginTests.assertDeclared(DiscordPlugin.class);
 * }
 * }</pre>
 *
 * <p>A host finds a plugin only through its pom's {@code botmaker.plugin} property, constructs it on a classpath
 * that may have no JavaFX, and builds each holder the first time it asks for that surface. Each of those fails
 * quietly in a host — no plugin, an empty palette, one line in a log — and nothing in the plugin's own build
 * reaches them. This does: every finding is listed in one {@link AssertionError}, which any test framework reports
 * as a failure. Nothing here needs JUnit.
 */
public final class PluginTests {

    private PluginTests() {}

    /** {@link #assertDeclared(Class, Path)} against {@code pom.xml} in the working directory, the module's own. */
    public static void assertDeclared(Class<? extends DeclaredPlugin> plugin) {
        assertDeclared(plugin, Path.of("pom.xml"));
    }

    /**
     * Fails unless a host would load {@code plugin} from a jar built by {@code pom}:
     * <ul>
     *   <li>the pom's {@code <properties>} set {@code botmaker.plugin} to the class's binary name, and its
     *       {@code <name>} is set (the name Studio shows);</li>
     *   <li>the class is {@code public final}, extends {@link DeclaredPlugin} directly, and has a public
     *       constructor with no arguments, which runs;</li>
     *   <li>every holder the declaration names can be built and answers without throwing.</li>
     * </ul>
     */
    public static void assertDeclared(Class<? extends DeclaredPlugin> plugin, Path pom) {
        List<String> problems = new ArrayList<>(pomProblems(plugin, pom));
        DeclaredPlugin instance = constructed(plugin, problems);
        if (instance != null) {
            ask(problems, "types", instance::types);
            ask(problems, "types (components)", instance::componentTypes);
            ask(problems, "values", instance::managedValues);
            ask(problems, "editors", instance::slotEditors);
            ask(problems, "toolbar", instance::toolbarItems);
            ask(problems, "runOverlay", instance::runOverlayParts);
            ask(problems, "overlay", instance::overlayPart);
            ask(problems, "assistant", instance::assistantTools);
            ask(problems, "catalog", instance::catalog);
        }
        if (!problems.isEmpty()) {
            throw new AssertionError(plugin.getName() + " would not load as declared:\n  - "
                    + String.join("\n  - ", problems));
        }
    }

    /**
     * Fails when a {@code public static final} field of {@code holder} holds what one of its holder methods gives
     * — a type, a value, an editor, a toolbar item, an overlay part, an assistant tool, a catalog, or a list of
     * them — and that method does not list it. A host takes only what the method answers, so a field written and
     * never listed is declared nowhere, and nothing else says so.
     */
    public static void assertListsEveryField(Class<?> holder) {
        Object built;
        try {
            Constructor<?> constructor = holder.getDeclaredConstructor();
            constructor.setAccessible(true);
            built = constructor.newInstance();
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            throw new AssertionError(holder.getName() + " cannot be built: " + e, e);
        }
        // Each kind to what its own method listed: a type belongs in types(), a part in components(). PluginType
        // comes first because a type is a ComponentType too.
        Map<Class<?>, Set<Object>> listed = new LinkedHashMap<>();
        if (built instanceof TypeHolder types) {
            gives(listed, PluginType.class, types.types());
            gives(listed, ComponentType.class, types.components());
        }
        if (built instanceof ValueHolder values) gives(listed, ManagedValue.class, values.values());
        if (built instanceof EditorHolder editors) gives(listed, SlotEditor.class, editors.editors());
        if (built instanceof ToolbarHolder toolbar) gives(listed, ToolbarItem.class, toolbar.toolbar());
        if (built instanceof RunOverlayHolder run) gives(listed, RunOverlayPart.class, run.runOverlay());
        if (built instanceof AssistantHolder tools) gives(listed, AssistantTool.class, tools.tools());
        if (built instanceof OverlayHolder overlay) {
            gives(listed, OverlayPart.class, Collections.singletonList(overlay.overlay()));
        }
        if (built instanceof CatalogHolder catalog) {
            gives(listed, PaletteCatalog.class, Collections.singletonList(catalog.catalog()));
        }
        List<String> missing = new ArrayList<>();
        for (Field field : holder.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                continue;
            }
            Object value;
            try {
                value = field.get(null);
            } catch (IllegalAccessException e) {
                continue;
            }
            Collection<?> each = value instanceof Collection<?> many ? many : Collections.singletonList(value);
            for (Object one : each) {
                Set<Object> where = one == null ? null : listed.entrySet().stream()
                        .filter(kind -> kind.getKey().isInstance(one))
                        .map(Map.Entry::getValue)
                        .findFirst().orElse(null);
                if (where != null && !where.contains(one)) {
                    missing.add(field.getName());
                    break;
                }
            }
        }
        if (!missing.isEmpty()) {
            throw new AssertionError(holder.getName() + " declares " + String.join(", ", missing)
                    + " but its holder method does not list " + (missing.size() == 1 ? "it" : "them"));
        }
    }

    private static void gives(Map<Class<?>, Set<Object>> listed, Class<?> kind, List<?> answer) {
        Set<Object> given = Collections.newSetFromMap(new IdentityHashMap<>());
        if (answer != null) given.addAll(answer);
        listed.put(kind, given);
    }

    private static List<String> pomProblems(Class<?> plugin, Path pom) {
        if (!Files.isRegularFile(pom)) return List.of(pom.toAbsolutePath() + " is not a file");
        Element project;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document document = factory.newDocumentBuilder().parse(pom.toFile());
            project = document.getDocumentElement();
        } catch (Exception e) {
            return List.of(pom + " does not read as XML: " + e.getMessage());
        }
        List<String> problems = new ArrayList<>();
        String declared = text(child(child(project, "properties"), "botmaker.plugin"));
        if (!plugin.getName().equals(declared)) {
            problems.add(declared == null
                    ? pom + " must set <properties><botmaker.plugin>" + plugin.getName() + "</botmaker.plugin>"
                    : pom + " names " + declared + " in <botmaker.plugin>, not " + plugin.getName());
        }
        String name = text(child(project, "name"));
        if (name == null || name.isBlank()) problems.add(pom + " must set <name>, the name Studio shows");
        return problems;
    }

    private static DeclaredPlugin constructed(Class<? extends DeclaredPlugin> plugin, List<String> problems) {
        int modifiers = plugin.getModifiers();
        if (!Modifier.isPublic(modifiers) || !Modifier.isFinal(modifiers)) {
            problems.add("the class must be public final");
        }
        if (plugin.getSuperclass() != DeclaredPlugin.class) {
            problems.add("the class must extend DeclaredPlugin directly, not " + plugin.getSuperclass().getName());
        }
        Constructor<? extends DeclaredPlugin> constructor;
        try {
            constructor = plugin.getConstructor();
        } catch (NoSuchMethodException e) {
            problems.add("the class needs a public constructor with no arguments");
            return null;
        }
        try {
            return constructor.newInstance();
        } catch (InvocationTargetException e) {
            problems.add("its constructor throws " + e.getCause());
        } catch (ReflectiveOperationException | LinkageError e) {
            problems.add("it cannot be constructed: " + e);
        }
        return null;
    }

    private static void ask(List<String> problems, String surface, Supplier<?> answer) {
        try {
            answer.get();
        } catch (RuntimeException | LinkageError e) {
            problems.add("." + surface + ": " + e.getMessage()
                    + (e.getCause() == null ? "" : " (" + e.getCause() + ")"));
        }
    }

    private static Element child(Element parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getTagName())) return element;
        }
        return null;
    }

    private static String text(Element element) {
        return element == null ? null : element.getTextContent().trim();
    }
}
