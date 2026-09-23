package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.value.PluginType;

import java.util.List;

/**
 * A {@link StudioPlugin} that builds each of its three contributions once, on first use.
 *
 * <pre>{@code
 * public final class DiscordPlugin extends AbstractStudioPlugin {
 *     public DiscordPlugin() { super("com.example.discord", "Discord"); }
 *
 *     // the palette is every @Palette class in this jar; buildCatalog() needs no override
 *     @Override protected List<PluginType<?>> buildTypes()    { return DiscordTypes.ALL; }
 *     @Override protected List<SlotEditor> buildSlotEditors() { return DiscordEditors.ALL; }
 * }
 * }</pre>
 *
 * <h2>What it is actually for: the build hooks cannot be fields</h2>
 *
 * <p>The obvious way to write a plugin is a {@code static final} catalog, and that is exactly what the SDK
 * does — so for the SDK this class buys almost nothing, and that is said plainly rather than dressed up. What
 * it buys everyone else is the <em>other</em> obvious way: computing the catalog in the constructor, or in an
 * instance field initialiser, which is wrong in a way that does not show up until the plugin is slow. The
 * host constructs a plugin through {@code ServiceLoader} while opening a project; anything expensive in a
 * constructor — reflecting over forty classes, reading a resource, scanning a directory — happens on that
 * path whether or not the answer is ever asked for. Each {@code build…} hook here runs at most once, and only
 * if the host asks the corresponding question.
 *
 * <p>The memoisation is a plain double-checked read on a {@code volatile} field. A hook may therefore run
 * twice under a race, which is accepted: all three answers are immutable values, and the alternative is
 * holding a lock across arbitrary plugin code that the host calls while rendering.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p><b>Nothing here is {@code final}.</b> A plugin whose palette or type list genuinely has to be rebuilt
 * per call overrides the contract method directly and this class gets out of the way. A base class that
 * narrowed the contract to fit the common case would be doing to plugins exactly what the platform exists
 * to stop the host doing to them.
 *
 * <p>{@code catalog} took the project's pinned version of this plugin until 2026-09-22 and this class
 * memoised ignoring it — which was the measurement that deleted the argument: no implementation anywhere
 * read it.
 *
 * <p>It also holds no state beyond the three cached answers and takes no services: a plugin is constructed
 * before the host has a project open, so there is nothing to hand it yet. Everything context-dependent
 * arrives later, per call, in a {@code ValueContext}.
 */
public abstract class AbstractStudioPlugin implements StudioPlugin {

    private final String id;
    private final String displayName;

    private volatile PaletteCatalog catalog;
    private volatile List<PluginType<?>> types;
    private volatile List<SlotEditor> slotEditors;

    /** A plugin whose display name is its id. */
    protected AbstractStudioPlugin(String id) {
        this(id, id);
    }

    protected AbstractStudioPlugin(String id, String displayName) {
        this.id = id;
        this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
    }

    /**
     * The palette this plugin offers: by default every {@code @Palette} class in this plugin's own jar
     * ({@link PaletteCatalog#scan(Class)}), so a plugin names its palette only by annotating it. A plugin
     * with no annotated class offers an empty catalog. Called at most once.
     */
    protected PaletteCatalog buildCatalog() {
        return PaletteCatalog.scan(getClass());
    }

    /** The types this plugin declares, in the order a picker should offer them. Called at most once. */
    protected List<PluginType<?>> buildTypes() {
        return List.of();
    }

    /**
     * The editors this plugin offers, narrowest match first.
     *
     * <p>Order matters only within this list — the host consults its own editors before any plugin's and its
     * JDK and enum fallbacks after them, whatever a plugin claims. Called at most once.
     */
    protected List<SlotEditor> buildSlotEditors() {
        return List.of();
    }

    // buildParameters() stood here from 2026-09-10 to 2026-09-22, memoising a list of ParameterGroup. The
    // contract surface it overrode is deleted: a parameter is a @Param field in the bot's own Java, and a
    // plugin that wants a row of its own puts one in the file it ships.

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public PaletteCatalog catalog() {
        PaletteCatalog local = catalog;
        if (local == null) {
            local = buildCatalog();
            if (local == null) local = PaletteCatalog.empty();
            catalog = local;
        }
        return local;
    }

    @Override
    public List<PluginType<?>> types() {
        List<PluginType<?>> local = types;
        if (local == null) {
            List<PluginType<?>> built = buildTypes();
            local = built == null ? List.of() : List.copyOf(built);
            types = local;
        }
        return local;
    }

    @Override
    public List<SlotEditor> slotEditors() {
        List<SlotEditor> local = slotEditors;
        if (local == null) {
            List<SlotEditor> built = buildSlotEditors();
            local = built == null ? List.of() : List.copyOf(built);
            slotEditors = local;
        }
        return local;
    }
}
