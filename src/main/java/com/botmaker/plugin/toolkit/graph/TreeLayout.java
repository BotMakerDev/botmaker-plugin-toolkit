package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A tidy tree from one root, for a graph that is mostly a tree: each card's depth is its rank, every rank's cards
 * line up, and a parent sits level with the middle of its children.
 *
 * <p>The tree is the first link into each card a walk from the root meets, in the order the links are listed; the
 * other links are drawn between the placed cards but place nothing. A collapsed card keeps its place and hides
 * everything below it in the tree, even a card another link also reaches. Cards the root does not reach go in a grid
 * below.
 *
 * <p>Cards are stacked leaf by leaf, each as close to the one before it in its rank as {@link Gaps#across()}
 * allows; a parent pushed down by its own rank moves its whole subtree with it, so it stays in the middle.
 */
public final class TreeLayout {

    private TreeLayout() {}

    /** Lays the tree under {@code root} out left to right, {@link Gaps#DEFAULT} apart, nothing collapsed. */
    public static Placement arrange(List<Card> cards, List<Link> links, String root) {
        return arrange(cards, links, root, Direction.RIGHT, Gaps.DEFAULT, Set.of());
    }

    /** Lays the tree under {@code root} out, hiding what every card in {@code collapsed} has below it. */
    public static Placement arrange(List<Card> cards, List<Link> links, String root, Direction direction, Gaps gaps,
                                    Set<String> collapsed) {
        Map<String, Card> byId = new LinkedHashMap<>();
        for (Card card : GraphLayout.flip(cards, direction)) byId.put(card.id(), card);
        Map<String, List<String>> children = byId.containsKey(root) ? tree(byId, links, root) : Map.of();

        Set<String> hidden = new LinkedHashSet<>();
        for (String id : collapsed) {
            if (children.containsKey(id)) hideBelow(id, children, hidden);
        }

        Map<String, Pt> at = new LinkedHashMap<>();
        double top = 0;
        if (byId.containsKey(root)) {
            Placer placer = new Placer(byId, children, collapsed, gaps);
            placer.place(root, 0);
            placer.columns(at);
            top = placer.bottom() + gaps.across() * 2;
        }
        List<String> leftovers = new ArrayList<>();
        for (String id : byId.keySet()) {
            if (!at.containsKey(id) && !hidden.contains(id)) leftovers.add(id);
        }
        GraphLayout.grid(leftovers, byId, top, gaps, at);
        return new Placement(at, hidden, 0).flip(direction);
    }

    /** Each card's children: the cards it is the first to reach on a breadth-first walk from {@code root}. */
    private static Map<String, List<String>> tree(Map<String, Card> cards, List<Link> links, String root) {
        Map<String, List<String>> out = new HashMap<>();
        for (Link link : links) {
            if (cards.containsKey(link.from()) && cards.containsKey(link.to())) {
                out.computeIfAbsent(link.from(), k -> new ArrayList<>()).add(link.to());
            }
        }
        Map<String, List<String>> children = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>(List.of(root));
        Deque<String> queue = new ArrayDeque<>(List.of(root));
        while (!queue.isEmpty()) {
            String parent = queue.poll();
            List<String> own = new ArrayList<>();
            for (String next : out.getOrDefault(parent, List.of())) {
                if (seen.add(next)) {
                    own.add(next);
                    queue.add(next);
                }
            }
            children.put(parent, own);
        }
        return children;
    }

    private static void hideBelow(String id, Map<String, List<String>> children, Set<String> hidden) {
        for (String child : children.getOrDefault(id, List.of())) {
            if (hidden.add(child)) hideBelow(child, children, hidden);
        }
    }

    /** Places a tree depth-first: x by depth, y leaf by leaf, each rank's next free y kept as it fills. */
    private static final class Placer {

        private final Map<String, Card> cards;
        private final Map<String, List<String>> children;
        private final Set<String> collapsed;
        private final Gaps gaps;
        private final Map<String, Integer> depthOf = new LinkedHashMap<>();
        private final Map<String, Double> yOf = new HashMap<>();
        private final List<Double> free = new ArrayList<>();

        Placer(Map<String, Card> cards, Map<String, List<String>> children, Set<String> collapsed, Gaps gaps) {
            this.cards = cards;
            this.children = children;
            this.collapsed = collapsed;
            this.gaps = gaps;
        }

        /** Places {@code id} and what it shows below it; returns the subtree's cards. */
        List<String> place(String id, int depth) {
            depthOf.put(id, depth);
            while (free.size() <= depth) free.add(0.0);
            Card card = cards.get(id);
            List<String> subtree = new ArrayList<>(List.of(id));
            List<String> kids = collapsed.contains(id) ? List.of() : children.getOrDefault(id, List.of());
            double y;
            if (kids.isEmpty()) {
                y = free.get(depth);
            } else {
                for (String kid : kids) subtree.addAll(place(kid, depth + 1));
                double first = yOf.get(kids.getFirst());
                String last = kids.getLast();
                double middle = (first + yOf.get(last) + cards.get(last).height()) / 2;
                y = middle - card.height() / 2;
                double push = free.get(depth) - y;
                if (push > 0) {
                    for (String below : subtree.subList(1, subtree.size())) yOf.merge(below, push, Double::sum);
                    y += push;
                    for (String below : subtree.subList(1, subtree.size())) claim(below);
                }
            }
            yOf.put(id, y);
            claim(id);
            return subtree;
        }

        /** Marks the rank of {@code id} as taken down to under its card. */
        private void claim(String id) {
            int depth = depthOf.get(id);
            free.set(depth, Math.max(free.get(depth), yOf.get(id) + cards.get(id).height() + gaps.across()));
        }

        /** Every placed card's corner: each rank as wide as its widest card, {@link Gaps#between()} apart. */
        void columns(Map<String, Pt> at) {
            List<Double> widest = new ArrayList<>();
            depthOf.forEach((id, depth) -> {
                while (widest.size() <= depth) widest.add(0.0);
                widest.set(depth, Math.max(widest.get(depth), cards.get(id).width()));
            });
            double top = Double.MAX_VALUE;
            for (double y : yOf.values()) top = Math.min(top, y);
            List<Double> x = new ArrayList<>();
            double next = 0;
            for (double w : widest) {
                x.add(next);
                next += w + gaps.between();
            }
            for (Map.Entry<String, Integer> entry : depthOf.entrySet()) {
                at.put(entry.getKey(), new Pt(x.get(entry.getValue()), yOf.get(entry.getKey()) - top));
            }
        }

        /** The bottom of the placed block, measured from its top. */
        double bottom() {
            double top = Double.MAX_VALUE;
            double bottom = 0;
            for (Map.Entry<String, Double> entry : yOf.entrySet()) {
                top = Math.min(top, entry.getValue());
                bottom = Math.max(bottom, entry.getValue() + cards.get(entry.getKey()).height());
            }
            return bottom - top;
        }
    }
}
