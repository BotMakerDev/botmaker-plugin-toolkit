package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The path each link takes between placed cards, clear of every other card — computed from where the cards are
 * now, not from the last layout, so a link stays clear after a card is dragged too.
 *
 * <p>A forward link crosses each rank of cards between its ends through a free gap, as flat S-curves. A link back,
 * or onto its own card, leaves forward, runs below or above the cards between its ends, and comes back into the
 * input side, with rounded corners. Lifted from the SDK's flow editor (2026-10-14).
 */
public final class Routes {

    /** How far a link keeps from a card it passes. */
    public static final double CLEAR = 18;

    /** The spacing between links that go around the block on the same side. */
    private static final double BACK_STEP = 10;
    /** The spacing between links arriving at one card. */
    private static final double FAN = 4;
    /** The radius of a routed link's corners. */
    private static final double RADIUS = 12;

    private Routes() {}

    /**
     * Where links meet cards, and where a link back may run.
     *
     * @param portInset how far inside a card's edge a link starts and ends: a port dot's radius, or 0
     * @param top       the least height a link back may run at above the cards, so it stays on a surface that
     *                  only grows down; {@code -Infinity} for anywhere
     */
    public record Rules(double portInset, double top) {

        public static final Rules DEFAULT = new Rules(0, Double.NEGATIVE_INFINITY);
    }

    /** One piece of a link's path. */
    public sealed interface Segment permits Line, Cubic {

        Pt from();

        Pt to();

        Pt at(double t);

        /** Every point the piece is drawn through or towards: its hull holds it. */
        List<Pt> hull();

        Segment flip(Direction direction);
    }

    public record Line(Pt from, Pt to) implements Segment {

        public Pt at(double t) {
            return new Pt(from.x() + (to.x() - from.x()) * t, from.y() + (to.y() - from.y()) * t);
        }

        public List<Pt> hull() { return List.of(from, to); }

        public Line flip(Direction direction) { return new Line(from.flip(direction), to.flip(direction)); }
    }

    public record Cubic(Pt from, Pt c1, Pt c2, Pt to) implements Segment {

        public Pt at(double t) {
            double u = 1 - t;
            double a = u * u * u;
            double b = 3 * u * u * t;
            double c = 3 * u * t * t;
            double d = t * t * t;
            return new Pt(a * from.x() + b * c1.x() + c * c2.x() + d * to.x(),
                    a * from.y() + b * c1.y() + c * c2.y() + d * to.y());
        }

        public List<Pt> hull() { return List.of(from, c1, c2, to); }

        public Cubic flip(Direction direction) {
            return new Cubic(from.flip(direction), c1.flip(direction), c2.flip(direction), to.flip(direction));
        }
    }

    /** One link's path, from its port to the card it enters. */
    public record Route(Link link, List<Segment> segments) {

        public Route {
            segments = List.copyOf(segments);
        }

        public Pt start() { return segments.getFirst().from(); }

        public Pt end() { return segments.getLast().to(); }

        /** The point the arrow head is aimed from: the path's last direction. */
        public Pt headFrom() {
            Segment last = segments.getLast();
            Pt from = last instanceof Cubic cubic && !cubic.c2().equals(cubic.to()) ? cubic.c2() : last.from();
            return from.equals(last.to()) ? new Pt(last.to().x() - 1, last.to().y()) : from;
        }

        /** Points along the path, about {@code step} apart. */
        public List<Pt> sample(double step) {
            List<Pt> points = new ArrayList<>();
            for (Segment segment : segments) {
                double length = 0;
                List<Pt> hull = segment.hull();
                for (int i = 1; i < hull.size(); i++) length += distance(hull.get(i - 1), hull.get(i));
                int n = Math.max(2, (int) Math.ceil(length / step));
                for (int i = 0; i <= n; i++) points.add(segment.at((double) i / n));
            }
            return points;
        }

        /** The point halfway along the path, where a label sits. */
        public Pt middle() {
            List<Pt> points = sample(4);
            return points.get(points.size() / 2);
        }

        public Box bounds() {
            Box box = null;
            for (Segment segment : segments) {
                for (Pt p : segment.hull()) box = new Box(p.x(), p.y(), p.x(), p.y()).union(box);
            }
            return box;
        }

        Route flip(Direction direction) {
            if (direction == Direction.RIGHT) return this;
            return new Route(link, segments.stream().map(s -> s.flip(direction)).toList());
        }
    }

    /** A card where it is now, in the frame the routes are worked out in. */
    private record Placed(Card card, double x, double y) {

        double height() { return card.height(); }

        double right() { return x + card.width(); }

        double bottom() { return y + card.height(); }

        Pt in(double inset) { return new Pt(x + inset, y + card.height() / 2); }

        Pt out(String port, double inset) {
            Double at = port == null ? null : card.ports().get(port);
            return new Pt(right() - inset, y + (at == null ? card.height() / 2 : at));
        }
    }

    /** The path of every link whose two cards both have a place in {@code at}. */
    public static List<Route> of(List<Card> cards, Map<String, Pt> at, List<Link> links, Direction direction,
                                 Rules rules) {
        Map<String, Placed> byId = new LinkedHashMap<>();
        for (Card card : GraphLayout.flip(cards, direction)) {
            Pt p = at.get(card.id());
            if (p != null) {
                Pt flipped = p.flip(direction);
                byId.put(card.id(), new Placed(card, flipped.x(), flipped.y()));
            }
        }
        List<Placed> placed = List.copyOf(byId.values());
        Map<Link, Double> arrival = arrivals(byId, links, rules.portInset());
        int[] lanes = {0, 0};
        List<Route> routes = new ArrayList<>();
        for (Link link : links) {
            Placed from = byId.get(link.from());
            Placed to = byId.get(link.to());
            if (from == null || to == null) continue;
            Pt start = from.out(link.port(), rules.portInset());
            Pt end = new Pt(to.in(rules.portInset()).x(), arrival.get(link));
            Route route;
            if (from != to && end.x() - start.x() >= 2 * CLEAR) {
                List<Placed> others = new ArrayList<>(placed);
                others.remove(from);
                others.remove(to);
                route = new Route(link, smooth(forward(start, end, others)));
            } else {
                route = new Route(link, rounded(back(start, end, placed, lanes, rules.top())));
            }
            routes.add(route.flip(direction));
        }
        return routes;
    }

    /** The box every placed card and every route takes; null when there is neither. */
    public static Box extent(List<Card> cards, Map<String, Pt> at, List<Route> routes) {
        Box box = null;
        for (Card card : cards) {
            Pt p = at.get(card.id());
            if (p != null) box = card.at(p).union(box);
        }
        for (Route route : routes) {
            Box bounds = route.bounds();
            box = box == null ? bounds : box.union(bounds);
        }
        return box;
    }

    /** Where each link meets its card: links into one card fan in a few pixels apart, by where they come from. */
    private static Map<Link, Double> arrivals(Map<String, Placed> cards, List<Link> links, double inset) {
        Map<String, List<Link>> byTarget = new LinkedHashMap<>();
        for (Link link : links) {
            if (cards.containsKey(link.from()) && cards.containsKey(link.to())) {
                byTarget.computeIfAbsent(link.to(), k -> new ArrayList<>()).add(link);
            }
        }
        Map<Link, Double> arrival = new HashMap<>();
        byTarget.forEach((target, incoming) -> {
            Placed to = cards.get(target);
            incoming.sort(Comparator.comparingDouble(l -> cards.get(l.from()).out(l.port(), inset).y()));
            double room = Math.max(0, to.height() / 2 - 2 * FAN);
            for (int i = 0; i < incoming.size(); i++) {
                double shift = Math.clamp((i - (incoming.size() - 1) / 2.0) * FAN, -room, room);
                arrival.put(incoming.get(i), to.in(inset).y() + shift);
            }
        });
        return arrival;
    }

    /**
     * A link to a card further on: straight across where nothing is in the way, else through a free gap in each
     * rank of cards between its ends. The turns happen between ranks, where there is no card.
     */
    private static List<Pt> forward(Pt start, Pt end, List<Placed> others) {
        List<Placed> between = new ArrayList<>();
        for (Placed card : others) {
            if (card.right() + CLEAR > start.x() && card.x() - CLEAR < end.x()) between.add(card);
        }
        List<Pt> points = new ArrayList<>();
        points.add(start);
        double floor = start.x() + CLEAR;
        double ceiling = end.x() - CLEAR;
        double y = start.y();
        for (List<Placed> column : columns(between)) {
            double minX = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE;
            for (Placed card : column) {
                minX = Math.min(minX, card.x() - CLEAR);
                maxX = Math.max(maxX, card.right() + CLEAR);
            }
            // A rank the link is already level with, or still level with at its end, has no room before or
            // after it to turn: the link keeps to the gap it is in.
            boolean noTurnBefore = minX <= Math.max(floor, points.getLast().x());
            boolean noTurnAfter = maxX >= ceiling;
            double entry = Math.max(minX, Math.max(floor, points.getLast().x()));
            double exit = Math.max(entry, Math.min(maxX, ceiling));
            double t = ((entry + exit) / 2 - start.x()) / (end.x() - start.x());
            double target = start.y() + (end.y() - start.y()) * t;
            y = freeY(column, target, noTurnBefore ? y : Double.NaN, noTurnAfter ? end.y() : Double.NaN);
            points.add(new Pt(entry, y));
            points.add(new Pt(exit, y));
        }
        points.add(end);
        return points;
    }

    /** {@code cards} in groups that overlap along the direction, kept {@link #CLEAR} apart: the ranks a link crosses. */
    private static List<List<Placed>> columns(List<Placed> cards) {
        List<Placed> sorted = new ArrayList<>(cards);
        sorted.sort(Comparator.comparingDouble(Placed::x));
        List<List<Placed>> columns = new ArrayList<>();
        double reach = -Double.MAX_VALUE;
        for (Placed card : sorted) {
            if (columns.isEmpty() || card.x() - CLEAR >= reach + CLEAR) columns.add(new ArrayList<>());
            columns.getLast().add(card);
            reach = Math.max(reach, card.right());
        }
        return columns;
    }

    /**
     * The height nearest {@code target} at which a link passes {@code column} at least {@link #CLEAR} from each
     * card, within the gap holding {@code keep} and the one holding {@code reach} when those are numbers.
     */
    private static double freeY(List<Placed> column, double target, double keep, double reach) {
        List<double[]> blocked = new ArrayList<>();
        for (Placed card : column) blocked.add(new double[]{card.y() - CLEAR, card.bottom() + CLEAR});
        blocked.sort(Comparator.comparingDouble(b -> b[0]));
        List<double[]> gaps = new ArrayList<>();
        double low = -Double.MAX_VALUE;
        for (double[] b : blocked) {
            if (b[0] > low) gaps.add(new double[]{low, b[0]});
            low = Math.max(low, b[1]);
        }
        gaps.add(new double[]{low, Double.MAX_VALUE});

        List<double[]> allowed = gaps.stream().filter(g -> holds(g, keep) && holds(g, reach)).toList();
        if (allowed.isEmpty()) allowed = gaps.stream().filter(g -> holds(g, keep)).toList();
        if (allowed.isEmpty()) allowed = gaps;
        double best = target;
        double nearest = Double.MAX_VALUE;
        for (double[] gap : allowed) {
            double y = Math.clamp(target, gap[0], gap[1]);
            if (Math.abs(y - target) < nearest) {
                nearest = Math.abs(y - target);
                best = y;
            }
        }
        return best;
    }

    private static boolean holds(double[] gap, double y) {
        return Double.isNaN(y) || (y >= gap[0] && y <= gap[1]);
    }

    /**
     * A link back to a card behind, or onto its own card: forward out of the port, down (or up, when that is
     * shorter and stays at or under {@code top}) past every card between its ends, across, and back into the card
     * from behind. Each link on one side runs {@link #BACK_STEP} further out than the one before.
     */
    private static List<Pt> back(Pt start, Pt end, List<Placed> cards, int[] lanes, double top) {
        double right = start.x() + CLEAR;
        double left = end.x() - CLEAR;
        double y = start.y();
        boolean below = true;
        for (int pass = 0; pass < 8; pass++) {
            double lo = Math.min(left, right);
            double hi = Math.max(left, right);
            double highest = Double.MAX_VALUE;
            double lowest = -Double.MAX_VALUE;
            for (Placed card : cards) {
                if (card.right() + CLEAR > lo && card.x() - CLEAR < hi) {
                    highest = Math.min(highest, card.y());
                    lowest = Math.max(lowest, card.bottom());
                }
            }
            double under = lowest + CLEAR;
            double over = highest - CLEAR;
            below = over - lanes[1] * BACK_STEP < top
                    || Math.abs(start.y() - under) + Math.abs(end.y() - under)
                    <= Math.abs(start.y() - over) + Math.abs(end.y() - over);
            y = below ? under : over;
            double nextRight = clearOf(cards, right, start.y(), y, true);
            double nextLeft = clearOf(cards, left, end.y(), y, false);
            if (nextRight == right && nextLeft == left) break;
            right = nextRight;
            left = nextLeft;
        }
        int lane = below ? lanes[0]++ : lanes[1]++;
        y += (below ? 1 : -1) * lane * BACK_STEP;
        return List.of(start, new Pt(right, start.y()), new Pt(right, y), new Pt(left, y), new Pt(left, end.y()), end);
    }

    /** {@code x}, moved past every card an upright from {@code y0} to {@code y1} at {@code x} would cut through. */
    private static double clearOf(List<Placed> cards, double x, double y0, double y1, boolean rightwards) {
        double lo = Math.min(y0, y1);
        double hi = Math.max(y0, y1);
        boolean moved = true;
        while (moved) {
            moved = false;
            for (Placed card : cards) {
                boolean across = card.x() - CLEAR < x && x < card.right() + CLEAR;
                boolean along = card.y() - CLEAR < hi && lo < card.bottom() + CLEAR;
                if (across && along) {
                    x = rightwards ? card.right() + CLEAR : card.x() - CLEAR;
                    moved = true;
                }
            }
        }
        return x;
    }

    /** Joins {@code points} with flat S-curves: each leaves and arrives level, which reads as "this way on". */
    private static List<Segment> smooth(List<Pt> points) {
        List<Pt> distinct = distinct(points);
        List<Segment> segments = new ArrayList<>();
        for (int i = 1; i < distinct.size(); i++) {
            Pt a = distinct.get(i - 1);
            Pt b = distinct.get(i);
            double dx = b.x() - a.x();
            if (Math.abs(a.y() - b.y()) < 0.5 || Math.abs(dx) < 0.5) {
                segments.add(new Line(a, b));
            } else {
                segments.add(new Cubic(a, new Pt(a.x() + dx / 2, a.y()), new Pt(b.x() - dx / 2, b.y()), b));
            }
        }
        if (segments.isEmpty()) segments.add(new Line(points.getFirst(), points.getLast()));
        return segments;
    }

    /** Joins {@code points}, a path of level and upright legs, with its corners rounded. */
    private static List<Segment> rounded(List<Pt> points) {
        List<Pt> path = new ArrayList<>();
        for (Pt p : distinct(points)) {
            // A point in line with the two around it is no corner.
            if (path.size() >= 2 && inLine(path.get(path.size() - 2), path.getLast(), p)) path.removeLast();
            path.add(p);
        }
        List<Segment> segments = new ArrayList<>();
        Pt at = path.getFirst();
        for (int i = 1; i < path.size() - 1; i++) {
            Pt corner = path.get(i);
            Pt before = path.get(i - 1);
            Pt after = path.get(i + 1);
            double r = Math.min(RADIUS, Math.min(distance(before, corner), distance(corner, after)) / 2);
            Pt in = toward(corner, before, r);
            Pt out = toward(corner, after, r);
            if (!at.equals(in)) segments.add(new Line(at, in));
            segments.add(new Cubic(in, toward(in, corner, r * 2 / 3), toward(out, corner, r * 2 / 3), out));
            at = out;
        }
        if (!at.equals(path.getLast()) || segments.isEmpty()) segments.add(new Line(at, path.getLast()));
        return segments;
    }

    private static List<Pt> distinct(List<Pt> points) {
        List<Pt> distinct = new ArrayList<>();
        for (Pt p : points) {
            if (distinct.isEmpty() || distance(distinct.getLast(), p) >= 0.5) distinct.add(p);
        }
        return distinct;
    }

    private static boolean inLine(Pt a, Pt b, Pt c) {
        return (Math.abs(a.x() - b.x()) < 0.5 && Math.abs(b.x() - c.x()) < 0.5)
                || (Math.abs(a.y() - b.y()) < 0.5 && Math.abs(b.y() - c.y()) < 0.5);
    }

    /** The point {@code by} from {@code from} toward {@code to}. */
    private static Pt toward(Pt from, Pt to, double by) {
        double length = distance(from, to);
        if (length == 0) return from;
        return new Pt(from.x() + (to.x() - from.x()) / length * by, from.y() + (to.y() - from.y()) / length * by);
    }

    private static double distance(Pt a, Pt b) {
        return Math.hypot(a.x() - b.x(), a.y() - b.y());
    }
}
