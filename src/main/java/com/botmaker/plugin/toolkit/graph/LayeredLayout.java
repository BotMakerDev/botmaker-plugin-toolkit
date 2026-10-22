package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Gaps;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Loops;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A layered (Sugiyama) layout, for a graph with loops in it: each card's rank is its longest path from the roots
 * with the loops taken out, so a link back is the only one that runs against the direction.
 *
 * <p>A link that skips ranks holds a lane slot in each rank it crosses, so the cards there make room for it. The
 * order within each rank comes from barycenter sweeps, there and back, keyed by the port a link leaves from, and
 * the order with the fewest crossings is kept. Each rank is centred on the longest, and ranks are as far apart as
 * their widest card needs. Cards no link from a root reaches go in a grid below. A link back keeps lane slots too
 * when it is to be drawn back through the ranks ({@link Loops#THROUGH}).
 *
 * <p>Lifted from the SDK's flow editor (2026-10-14), where it was proved on real flows.
 */
public final class LayeredLayout {

    /** The barycenter sweeps a layout runs: there, back, there, back. */
    public static final int SWEEPS = 4;

    /** The length a lane slot takes in a rank a link crosses. */
    private static final double LANE = 16;
    /** A lane slot's id: no card is named with a NUL. */
    private static final String LANE_PREFIX = "\0lane";

    private LayeredLayout() {}

    /** Lays {@code cards} out from {@code roots}, left to right, {@link Gaps#DEFAULT} apart. */
    public static Placement arrange(List<Card> cards, List<Link> links, List<String> roots) {
        return arrange(cards, links, roots, Direction.RIGHT, Gaps.DEFAULT, SWEEPS);
    }

    /**
     * Lays {@code cards} out from {@code roots}: those, and every card a link from them reaches. With no root the
     * roots are the cards no link enters; with no link every card goes in the grid.
     *
     * @param sweeps how many barycenter sweeps to run; 0 keeps each rank in the order {@code cards} lists it, so a
     *               caller that lists them in their current order rearranges without reshuffling
     */
    public static Placement arrange(List<Card> cards, List<Link> links, List<String> roots, Direction direction,
                                    Gaps gaps, int sweeps) {
        return arrange(cards, links, roots, direction, gaps, sweeps, Loops.AROUND);
    }

    /**
     * Lays {@code cards} out as {@link #arrange(List, List, List, Direction, Gaps, int)} does, with the links back
     * kept as {@code loops} says: {@link Loops#THROUGH} gives each a lane slot in every rank between its ends and
     * counts it in the order of each rank, so it can be drawn back through them. Which links run back is decided by
     * a walk from the roots that takes the links in the order listed: list the ones that make the work's shape first.
     */
    public static Placement arrange(List<Card> cards, List<Link> links, List<String> roots, Direction direction,
                                    Gaps gaps, int sweeps, Loops loops) {
        Map<String, Card> byId = new LinkedHashMap<>();
        for (Card card : GraphLayout.flip(cards, direction)) byId.put(card.id(), card);
        List<Link> known = links.stream()
                .filter(l -> byId.containsKey(l.from()) && byId.containsKey(l.to())).toList();
        List<String> reachable = known.isEmpty() ? List.of() : reachable(byId.keySet(), known, roots);

        Map<String, Pt> at = new LinkedHashMap<>();
        int crossings = 0;
        double top = 0;
        if (!reachable.isEmpty()) {
            Layered layered = Layered.of(reachable, known, byId, gaps, loops);
            crossings = layered.order(sweeps);
            top = layered.place(at) + gaps.across() * 2;
        }
        List<String> leftovers = new ArrayList<>();
        for (String id : byId.keySet()) {
            if (!at.containsKey(id)) leftovers.add(id);
        }
        GraphLayout.grid(leftovers, byId, top, gaps, at);
        return new Placement(at, Set.of(), crossings).flip(direction);
    }

    /** The cards reached from {@code roots}, or from the cards no link enters, in the order a walk finds them. */
    private static List<String> reachable(Set<String> ids, List<Link> links, List<String> roots) {
        List<String> starts = new ArrayList<>();
        for (String root : roots) {
            if (ids.contains(root)) starts.add(root);
        }
        if (starts.isEmpty()) {
            Set<String> entered = new HashSet<>();
            for (Link link : links) {
                if (!link.from().equals(link.to())) entered.add(link.to());
            }
            for (String id : ids) {
                if (!entered.contains(id)) starts.add(id);
            }
        }
        // Every card on a loop, or no root named: start at the first card, as the flow editor always did.
        if (starts.isEmpty() && !ids.isEmpty()) starts.add(ids.iterator().next());
        Map<String, List<String>> out = new HashMap<>();
        for (Link link : links) out.computeIfAbsent(link.from(), k -> new ArrayList<>()).add(link.to());
        Set<String> seen = new LinkedHashSet<>(starts);
        Deque<String> queue = new ArrayDeque<>(starts);
        while (!queue.isEmpty()) {
            for (String next : out.getOrDefault(queue.poll(), List.of())) {
                if (seen.add(next)) queue.add(next);
            }
        }
        return List.copyOf(seen);
    }

    /** A link's step from one rank to the next: from a card's port or a lane slot, into a card or a slot. */
    private record Hop(String from, double fromFraction, String to) {}

    /** The reachable cards in ranks, with a lane slot wherever a link crosses a rank. */
    private static final class Layered {

        private final Map<String, Card> cards;
        private final Gaps gaps;
        private final List<List<String>> layers = new ArrayList<>();
        private final Map<String, Integer> layerOf = new HashMap<>();
        private final List<Hop> hops = new ArrayList<>();
        private int lanes;

        private Layered(Map<String, Card> cards, Gaps gaps) {
            this.cards = cards;
            this.gaps = gaps;
        }

        static Layered of(List<String> reachable, List<Link> links, Map<String, Card> cards, Gaps gaps, Loops loops) {
            Layered layered = new Layered(cards, gaps);
            Map<String, Integer> layer = longestPathLayers(reachable, links);
            int max = 0;
            for (int l : layer.values()) max = Math.max(max, l);
            for (int l = 0; l <= max; l++) layered.layers.add(new ArrayList<>());
            for (String id : reachable) layered.add(id, layer.get(id));

            for (Link link : links) {
                Integer from = layer.get(link.from());
                Integer to = layer.get(link.to());
                if (from == null || to == null || to.equals(from)) continue;
                if (to > from) {
                    layered.chain(link.from(), cards.get(link.from()).portFraction(link.port()), from, link.to(), to);
                } else if (loops == Loops.THROUGH && link.port() == null) {
                    // Drawn from its own card's near side into the far side of the card it returns to: for the ranks
                    // between, the same as a link the other way. A link from a port goes around the block.
                    layered.chain(link.to(), 0.5, to, link.from(), from);
                }
            }
            return layered;
        }

        /** The hops from {@code upper}, in rank {@code from}, to {@code lower}: a lane slot in each rank between. */
        private void chain(String upper, double fraction, int from, String lower, int to) {
            for (int l = from + 1; l < to; l++) {
                String lane = LANE_PREFIX + lanes++;
                add(lane, l);
                hops.add(new Hop(upper, fraction, lane));
                upper = lane;
                fraction = 0.5;
            }
            hops.add(new Hop(upper, fraction, lower));
        }

        private void add(String node, int layer) {
            layers.get(layer).add(node);
            layerOf.put(node, layer);
        }

        /** Runs {@code sweeps} barycenter sweeps and keeps the order with the fewest crossings; returns them. */
        int order(int sweeps) {
            List<List<String>> best = copy(layers);
            int fewest = crossings();
            for (int pass = 0; pass < sweeps; pass++) {
                if (pass % 2 == 0) {
                    for (int l = 1; l < layers.size(); l++) sort(l, true);
                } else {
                    for (int l = layers.size() - 2; l >= 0; l--) sort(l, false);
                }
                int now = crossings();
                if (now < fewest) {
                    fewest = now;
                    best = copy(layers);
                }
            }
            for (int l = 0; l < layers.size(); l++) layers.set(l, best.get(l));
            return fewest;
        }

        /**
         * Orders rank {@code l} by where its links meet the rank before it ({@code byUpper}) or after it: the mean
         * of the neighbours' rows, each plus how far along its card the link's port sits. A node with no link on
         * that side keeps its row.
         */
        private void sort(int l, boolean byUpper) {
            List<String> column = layers.get(l);
            Map<String, Integer> row = rows(column);
            Map<String, Integer> neighbour = rows(layers.get(byUpper ? l - 1 : l + 1));
            Map<String, double[]> sums = new HashMap<>();
            for (Hop hop : hops) {
                String self = byUpper ? hop.to() : hop.from();
                String other = byUpper ? hop.from() : hop.to();
                if (!row.containsKey(self) || !neighbour.containsKey(other)) continue;
                double key = neighbour.get(other) + (byUpper ? hop.fromFraction() : 0.5);
                double[] sum = sums.computeIfAbsent(self, k -> new double[2]);
                sum[0] += key;
                sum[1]++;
            }
            Map<String, Double> key = new HashMap<>();
            for (String node : column) {
                double[] sum = sums.get(node);
                key.put(node, sum == null ? row.get(node) + 0.5 : sum[0] / sum[1]);
            }
            column.sort(Comparator.comparingDouble(key::get));
        }

        /** Pairs of hops between the same two ranks whose ends are in opposite orders. */
        int crossings() {
            Map<String, Integer> row = new HashMap<>();
            for (List<String> column : layers) row.putAll(rows(column));
            Map<Integer, List<Hop>> byLayer = new HashMap<>();
            for (Hop hop : hops) byLayer.computeIfAbsent(layerOf.get(hop.from()), k -> new ArrayList<>()).add(hop);
            int count = 0;
            for (List<Hop> between : byLayer.values()) {
                for (int i = 0; i < between.size(); i++) {
                    for (int j = i + 1; j < between.size(); j++) {
                        Hop a = between.get(i);
                        Hop b = between.get(j);
                        double upper = row.get(a.from()) + a.fromFraction() - row.get(b.from()) - b.fromFraction();
                        double lower = row.get(a.to()) - row.get(b.to());
                        if (upper * lower < 0) count++;
                    }
                }
            }
            return count;
        }

        /** Puts each card in its rank, the ranks centred on the longest; returns the longest's length. */
        double place(Map<String, Pt> at) {
            double tallest = 0;
            for (List<String> column : layers) tallest = Math.max(tallest, height(column));
            double x = 0;
            for (List<String> column : layers) {
                double y = (tallest - height(column)) / 2;
                double widest = 0;
                for (String node : column) {
                    Card card = cards.get(node);
                    if (card == null) {
                        y += LANE + gaps.across();
                        continue;
                    }
                    at.put(node, new Pt(x, y));
                    y += card.height() + gaps.across();
                    widest = Math.max(widest, card.width());
                }
                x += widest + gaps.between();
            }
            return tallest;
        }

        private double height(List<String> column) {
            double height = -gaps.across();
            for (String node : column) {
                Card card = cards.get(node);
                height += (card == null ? LANE : card.height()) + gaps.across();
            }
            return Math.max(0, height);
        }

        private static Map<String, Integer> rows(List<String> column) {
            Map<String, Integer> rows = new HashMap<>();
            for (int i = 0; i < column.size(); i++) rows.put(column.get(i), i);
            return rows;
        }

        private static List<List<String>> copy(List<List<String>> layers) {
            List<List<String>> copy = new ArrayList<>();
            for (List<String> column : layers) copy.add(new ArrayList<>(column));
            return copy;
        }
    }

    /**
     * Each reachable card's rank: the longest path to it from the roots, over the graph with its back-links
     * removed. Removing them is what keeps a cycle from making "longest path" unbounded, and what makes a loop
     * back the only link against the direction; with shortest-path ranks an ordinary link could point back.
     */
    private static Map<String, Integer> longestPathLayers(List<String> reachable, List<Link> links) {
        Map<String, List<String>> forward = forwardEdges(new LinkedHashSet<>(reachable), links);
        Map<String, Integer> layer = new LinkedHashMap<>();
        for (String id : reachable) layer.put(id, 0);
        // Relax |V| times: with the back-links gone this is a DAG, so that is enough for the longest path.
        for (int pass = 0; pass < reachable.size(); pass++) {
            boolean moved = false;
            for (String from : reachable) {
                for (String to : forward.getOrDefault(from, List.of())) {
                    if (layer.get(to) < layer.get(from) + 1) {
                        layer.put(to, layer.get(from) + 1);
                        moved = true;
                    }
                }
            }
            if (!moved) break;
        }
        return layer;
    }

    /** The links with cycles broken: every one except those a depth-first walk finds leading backwards. */
    private static Map<String, List<String>> forwardEdges(Set<String> known, List<Link> links) {
        Map<String, List<String>> all = new LinkedHashMap<>();
        for (Link e : links) {
            if (known.contains(e.from()) && known.contains(e.to()) && !e.from().equals(e.to())) {
                all.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e.to());
            }
        }
        Set<String> onStack = new LinkedHashSet<>();
        Set<String> done = new HashSet<>();
        Map<String, List<String>> forward = new LinkedHashMap<>();
        for (String root : known) {
            if (!done.contains(root)) dropBackEdges(root, all, forward, onStack, done);
        }
        return forward;
    }

    /** Depth-first from {@code node}, copying every link except those pointing at the current stack. */
    private static void dropBackEdges(String node, Map<String, List<String>> all,
                                      Map<String, List<String>> forward, Set<String> onStack, Set<String> done) {
        onStack.add(node);
        for (String next : all.getOrDefault(node, List.of())) {
            if (onStack.contains(next)) continue;   // a back-link: this is the loop, not a layout constraint
            forward.computeIfAbsent(node, k -> new ArrayList<>()).add(next);
            if (!done.contains(next)) dropBackEdges(next, all, forward, onStack, done);
        }
        onStack.remove(node);
        done.add(node);
    }
}
