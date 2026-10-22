package com.botmaker.plugin.toolkit.graph;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The shapes a graph layout works on, without JavaFX: cards with sizes and ports, links between them, and the
 * places a layout gives the cards. {@link LayeredLayout} turns cards and links into a {@link Placement};
 * {@link Routes} draws each link between placed cards; {@link GraphCanvas} shows the result.
 *
 * <p>Our own layout and no layout library, the user's call (2026-10-14, held again 2026-10-22): the toolkit reaches
 * every bot through the SDK, and ELK would have brought EMF and Guava, 8.4 MB, into each one. The layered layout is
 * the one the SDK's flow editor proved; the screen map threads its ways back through it ({@link Loops#THROUGH}).
 *
 * <p>Every layout works left to right; {@link Direction#DOWN} is the same layout with the axes swapped, so a
 * port's offset runs along a card's right side going {@link Direction#RIGHT}, and along its bottom going down.
 */
public final class GraphLayout {

    private GraphLayout() {}

    /** Which way links run: the way a layout puts each rank after the last. */
    public enum Direction { RIGHT, DOWN }

    /** How a link that runs against the direction, or within one rank, is laid out and drawn. */
    public enum Loops {
        /**
         * Out of its card's side, around the whole block, and in from behind; the ranks keep no room for it. A
         * graph with a few loops reads best so: each stands apart from the work.
         */
        AROUND,
        /**
         * Back through the ranks between its ends, out of its card's near side and into the far side of the card it
         * returns to, each rank it crosses keeping room for it; within one rank, a hook beside it. A graph with many
         * ways back reads best so: each stays by the cards it joins instead of all of them wrapping the block. A
         * link from a named port still goes {@link #AROUND}, as the port is on the far side.
         */
        THROUGH
    }

    /** A point, unscaled. */
    public record Pt(double x, double y) {

        Pt flip(Direction direction) {
            return direction == Direction.DOWN ? new Pt(y, x) : this;
        }
    }

    /** An axis-aligned box. */
    public record Box(double minX, double minY, double maxX, double maxY) {

        public double width() { return maxX - minX; }

        public double height() { return maxY - minY; }

        public Pt centre() { return new Pt((minX + maxX) / 2, (minY + maxY) / 2); }

        public Box union(Box other) {
            if (other == null) return this;
            return new Box(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.max(maxX, other.maxX), Math.max(maxY, other.maxY));
        }

        /** Whether the two boxes share more than an edge. */
        public boolean overlaps(Box other) {
            return minX < other.maxX && other.minX < maxX && minY < other.maxY && other.minY < maxY;
        }

        /** Whether {@code p} lies strictly inside, more than {@code inset} from every edge. */
        public boolean holds(Pt p, double inset) {
            return p.x() > minX + inset && p.x() < maxX - inset && p.y() > minY + inset && p.y() < maxY - inset;
        }
    }

    /**
     * One card: what a layout places.
     *
     * @param ports each named port's centre along the side links leave from, as a distance from that side's
     *              start; a link from a port not named here leaves from the side's middle
     */
    public record Card(String id, double width, double height, Map<String, Double> ports) {

        public Card {
            ports = Map.copyOf(ports);
        }

        public static Card of(String id, double width, double height) {
            return new Card(id, width, height, Map.of());
        }

        public Box at(Pt p) {
            return new Box(p.x(), p.y(), p.x() + width, p.y() + height);
        }

        /** This card with its axes swapped, so a layout working left to right lays it out downwards. */
        Card flip(Direction direction) {
            return direction == Direction.DOWN ? new Card(id, height, width, ports) : this;
        }

        /** Where along its side {@code port} sits, from 0 (start) to under 1 (end). */
        double portFraction(String port) {
            Double at = port == null ? null : ports.get(port);
            return at == null || height <= 0 ? 0.5 : Math.clamp(at / height, 0, 0.999);
        }
    }

    /** A link from {@code from}'s {@code port} (null: the side's middle) to {@code to}. */
    public record Link(String from, String port, String to) {

        public static Link of(String from, String to) {
            return new Link(from, null, to);
        }
    }

    /**
     * How far apart a layout keeps cards.
     *
     * @param between the space between two ranks: room for links to turn
     * @param across  the space between two cards of one rank
     */
    public record Gaps(double between, double across) {

        public static final Gaps DEFAULT = new Gaps(110, 40);
    }

    /**
     * What a layout decided.
     *
     * @param at        each shown card's top-left corner; the block starts at 0, 0
     * @param hidden    the cards a collapsed card hides, which have no place
     * @param crossings the link crossings between neighbouring ranks, on the chosen order
     */
    public record Placement(Map<String, Pt> at, Set<String> hidden, int crossings) {

        public Placement {
            at = Collections.unmodifiableMap(new LinkedHashMap<>(at));
            hidden = Set.copyOf(hidden);
        }

        /** The box the placed {@code cards} take; null when none is placed. */
        public Box extent(List<Card> cards) {
            Box box = null;
            for (Card card : cards) {
                Pt p = at.get(card.id());
                if (p != null) box = card.at(p).union(box);
            }
            return box;
        }

        /**
         * This placement with each card {@code pins} names at the corner it gives instead: cards moved by hand. A pin
         * for a card that is hidden or not placed is ignored.
         */
        public Placement pinned(Map<String, Pt> pins) {
            if (pins.isEmpty()) return this;
            Map<String, Pt> moved = new LinkedHashMap<>(at);
            pins.forEach((id, p) -> {
                if (p != null && moved.containsKey(id)) moved.put(id, p);
            });
            return new Placement(moved, hidden, crossings);
        }

        /** This placement, worked out left to right, put back the way {@code direction} runs. */
        Placement flip(Direction direction) {
            if (direction == Direction.RIGHT) return this;
            Map<String, Pt> flipped = new LinkedHashMap<>();
            at.forEach((id, p) -> flipped.put(id, p.flip(direction)));
            return new Placement(flipped, hidden, crossings);
        }
    }

    /** {@code cards} with their axes swapped for {@code direction}. */
    static List<Card> flip(List<Card> cards, Direction direction) {
        return cards.stream().map(c -> c.flip(direction)).toList();
    }

    /**
     * Lays {@code ids} out as a squarish grid whose top edge sits at {@code top}, in order: the cards no link
     * reaches, under the block that holds the rest.
     */
    static void grid(List<String> ids, Map<String, Card> cards, double top, Gaps gaps, Map<String, Pt> at) {
        if (ids.isEmpty()) return;
        int columns = (int) Math.ceil(Math.sqrt(ids.size()));
        double pitch = 0;
        for (String id : ids) pitch = Math.max(pitch, cards.get(id).width());
        pitch += gaps.between();
        double y = top;
        double rowHeight = 0;
        for (int i = 0; i < ids.size(); i++) {
            int column = i % columns;
            if (column == 0 && i > 0) {
                y += rowHeight + gaps.across();
                rowHeight = 0;
            }
            at.put(ids.get(i), new Pt(column * pitch, y));
            rowHeight = Math.max(rowHeight, cards.get(ids.get(i)).height());
        }
    }
}
