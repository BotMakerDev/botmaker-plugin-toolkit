package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.parameters.ParameterGroup;
import com.botmaker.plugin.api.slot.SlotEditor;
import com.botmaker.plugin.api.StudioPlugin;
import com.botmaker.plugin.api.catalog.PaletteCatalog;
import com.botmaker.plugin.api.value.ValueCatalog;

import java.util.List;

/**
 * A {@link StudioPlugin} that builds each of its four contributions once, on first use.
 *
 * <pre>{@code
 * public final class DiscordPlugin extends AbstractStudioPlugin {
 *     public DiscordPlugin() { super("com.example.discord", "Discord"); }
 *
 *     @Override protected PaletteCatalog buildCatalog()      { return PaletteCatalog.of(Discord.class); }
 *     @Override protected ValueCatalog   buildValueTypes()   { return DiscordTypes.CATALOG; }
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
 * twice under a race, which is accepted: all four answers are immutable values, and the alternative is
 * holding a lock across arbitrary plugin code that the host calls while rendering.
 *
 * <h2>What it deliberately does not do</h2>
 *
 * <p><b>Nothing here is {@code final}.</b> In particular {@link #catalog(String)} is memoised ignoring its
 * argument, which is right for a plugin whose palette does not vary by pinned version — most of them — and
 * wrong for one that ships per-version curation. That plugin overrides {@code catalog(String)} directly and
 * this class gets out of the way. A base class that narrowed the contract to fit the common case would be
 * doing to plugins exactly what the platform exists to stop the host doing to them.
 *
 * <p>It also holds no state beyond the four cached answers and takes no services: a plugin is constructed
 * before the host has a project open, so there is nothing to hand it yet. Everything context-dependent
 * arrives later, per call, in a {@code ValueContext}.
 */
public abstract class AbstractStudioPlugin implements StudioPlugin {

    private final String id;
    private final String displayName;

    private volatile PaletteCatalog catalog;
    private volatile ValueCatalog valueTypes;
    private volatile List<SlotEditor> slotEditors;
    private volatile List<ParameterGroup> parameters;

    /** A plugin whose display name is its id. */
    protected AbstractStudioPlugin(String id) {
        this(id, id);
    }

    protected AbstractStudioPlugin(String id, String displayName) {
        this.id = id;
        this.displayName = displayName == null || displayName.isBlank() ? id : displayName;
    }

    /** The palette this plugin offers. Called at most once; see the class note about per-version curation. */
    protected PaletteCatalog buildCatalog() {
        return PaletteCatalog.empty();
    }

    /** The value types this plugin registers. Called at most once. */
    protected ValueCatalog buildValueTypes() {
        return ValueCatalog.empty();
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

    /** The parameter sections this plugin owns. Called at most once. */
    protected List<ParameterGroup> buildParameters() {
        return List.of();
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public PaletteCatalog catalog(String pinnedVersion) {
        PaletteCatalog local = catalog;
        if (local == null) {
            local = buildCatalog();
            if (local == null) local = PaletteCatalog.empty();
            catalog = local;
        }
        return local;
    }

    @Override
    public ValueCatalog valueTypes() {
        ValueCatalog local = valueTypes;
        if (local == null) {
            local = buildValueTypes();
            if (local == null) local = ValueCatalog.empty();
            valueTypes = local;
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

    @Override
    public List<ParameterGroup> parameters(String pinnedVersion) {
        List<ParameterGroup> local = parameters;
        if (local == null) {
            List<ParameterGroup> built = buildParameters();
            local = built == null ? List.of() : List.copyOf(built);
            parameters = local;
        }
        return local;
    }
}
