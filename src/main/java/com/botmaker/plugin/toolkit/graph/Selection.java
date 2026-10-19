package com.botmaker.plugin.toolkit.graph;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Which of {@link GraphCanvas}'s cards are selected: the item clicked last, and the ids of every selected card, that
 * item's among them while it is set. Every change is told to the one listener, which marks the cards.
 */
final class Selection<N> {

    private final Function<N, String> id;
    /** The canvas's shown items by id, read live. */
    private final Map<String, N> byId;
    private final ObjectProperty<N> selected;
    private final Set<String> ids = new LinkedHashSet<>();
    private Runnable changed = () -> {};

    Selection(Object bean, Function<N, String> id, Map<String, N> byId) {
        this.id = id;
        this.byId = byId;
        selected = new SimpleObjectProperty<>(bean, "selected");
        selected.addListener((o, was, is) -> {
            // Picked by a plain click, a search or the plugin: that card alone, unless it is one of several already.
            String key = is == null ? null : id.apply(is);
            if (key == null) ids.clear();
            else if (!ids.contains(key)) {
                ids.clear();
                ids.add(key);
            }
            changed.run();
        });
    }

    void onChanged(Runnable changed) {
        this.changed = changed;
    }

    ObjectProperty<N> property() {
        return selected;
    }

    void set(N node) {
        selected.set(node);
    }

    boolean contains(String key) {
        return ids.contains(key);
    }

    boolean isEmpty() {
        return ids.isEmpty();
    }

    /** The selected ids, in the order they were picked: a copy. */
    Set<String> ids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(ids));
    }

    /** A plain click on {@code key}'s card: that card alone. */
    void only(String key) {
        ids.clear();
        ids.add(key);
        selected.set(byId.get(key));
        changed.run();
    }

    /** Ctrl+click: {@code key} into the selection or out of it. The card clicked last stays the selected item. */
    void toggle(String key) {
        if (ids.remove(key)) {
            N now = selected.get();
            if (now != null && id.apply(now).equals(key)) {
                selected.set(ids.isEmpty() ? null : byId.get(ids.iterator().next()));
            }
        } else {
            ids.add(key);
            selected.set(byId.get(key));
        }
        changed.run();
    }

    /** A box drag let go over {@code keys}: those, added to what was selected when {@code adds}. */
    void boxed(Collection<String> keys, boolean adds) {
        if (!adds) ids.clear();
        ids.addAll(keys);
        N now = selected.get();
        if (ids.isEmpty()) selected.set(null);
        else if (now == null || !ids.contains(id.apply(now))) selected.set(byId.get(ids.iterator().next()));
        changed.run();
    }

    /** After a new layout: only the cards still shown stay selected, and {@code keep} stays the item if it is. */
    void keepShown(N keep) {
        ids.retainAll(byId.keySet());
        selected.set(keep != null && byId.containsKey(id.apply(keep)) ? byId.get(id.apply(keep)) : null);
        changed.run();
    }
}
