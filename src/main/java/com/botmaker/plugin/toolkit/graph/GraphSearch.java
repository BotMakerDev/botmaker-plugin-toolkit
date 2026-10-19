package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphCanvas.Hit;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** {@link GraphCanvas}'s search: the matching cards marked, and the fields that find them. */
final class GraphSearch<N> {

    private static final PseudoClass MATCH = PseudoClass.getPseudoClass("match");
    /** The zoom a picked search hit is shown at: a card's text reads at it. */
    private static final double READABLE = 1;
    /** The most hits the search lists; the rest are marked on the map only. */
    private static final int MOST_HITS = 12;
    /** How much of a long term a hit shows, around what matched. */
    private static final int HIT_TEXT = 48;

    private final GraphCanvas<N, ?> canvas;
    private final Function<N, String> id;
    private List<N> matches = List.of();
    private int matchAt;
    /** The listing search's own search again, over the items {@link GraphCanvas#show} gave; nothing without one. */
    private Runnable researched = () -> {};

    GraphSearch(GraphCanvas<N, ?> canvas, Function<N, String> id) {
        this.canvas = canvas;
        this.id = id;
    }

    /** See {@link GraphCanvas#find}. */
    List<N> find(Predicate<N> match) {
        matches = match == null ? List.of() : canvas.nodes().stream().filter(match).toList();
        matchAt = 0;
        mark();
        if (!matches.isEmpty()) bringIn(matches.getFirst());
        return matches;
    }

    /** See {@link GraphCanvas#searchField(Function)}. */
    TextField field(Function<N, String> text) {
        TextField field = new TextField();
        field.setPromptText("Find…");
        field.getStyleClass().add("graph-search");
        field.textProperty().addListener((o, was, is) -> {
            String wanted = is == null ? "" : is.strip().toLowerCase(Locale.ROOT);
            find(wanted.isEmpty() ? null : n -> Objects.toString(text.apply(n), "").toLowerCase(Locale.ROOT).contains(wanted));
        });
        field.setOnKeyPressed(e -> {
            if (e.getCode() != KeyCode.ENTER || matches.isEmpty()) return;
            matchAt = (matchAt + 1) % matches.size();
            bringIn(matches.get(matchAt));
        });
        return field;
    }

    /** See {@link GraphCanvas#searchField(Function, Function)}. */
    TextField listing(Function<N, List<String>> terms, Function<Hit<N>, Node> row) {
        TextField field = new TextField();
        field.setPromptText("Find…");
        field.getStyleClass().add("graph-search");
        VBox list = new VBox();
        list.getStyleClass().add("graph-results");
        list.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        list.setVisible(false);
        StackPane.setAlignment(list, Pos.TOP_LEFT);
        StackPane.setMargin(list, new Insets(4, 0, 0, 8));
        canvas.getChildren().add(list);
        List<Hit<N>> shown = new ArrayList<>();
        int[] at = {0};
        Consumer<Hit<N>> pick = hit -> {
            list.setVisible(false);
            canvas.selectedProperty().set(hit.node());
            canvas.zoomTo(id.apply(hit.node()), READABLE);
        };
        Runnable draw = () -> {
            List<Node> rows = new ArrayList<>();
            for (int i = 0; i < shown.size(); i++) {
                Hit<N> hit = shown.get(i);
                StackPane cell = new StackPane(row.apply(hit));
                cell.getStyleClass().add("graph-result");
                cell.setAlignment(Pos.CENTER_LEFT);
                cell.pseudoClassStateChanged(GraphCanvas.SELECTED, i == at[0]);
                cell.setOnMousePressed(e -> {
                    pick.accept(hit);
                    e.consume();
                });
                rows.add(cell);
            }
            list.getChildren().setAll(rows);
            list.setVisible(!shown.isEmpty() && field.isFocused());
        };
        Consumer<Boolean> search = again -> {
            List<Hit<N>> all = hits(canvas.nodes(), terms, field.getText());
            matches = all.stream().map(Hit::node).toList();
            matchAt = 0;
            mark();
            shown.clear();
            shown.addAll(all.subList(0, Math.min(MOST_HITS, all.size())));
            // Searched again after a redraw: the row picked stays, as far as the list still reaches.
            at[0] = again ? Math.min(at[0], Math.max(0, shown.size() - 1)) : 0;
            if (again && !list.isVisible()) list.getChildren().clear();
            else draw.run();
        };
        field.textProperty().addListener((o, was, is) -> search.accept(false));
        researched = () -> {
            if (!field.getText().isBlank()) search.accept(true);
        };
        field.focusedProperty().addListener((o, was, is) -> list.setVisible(is && !shown.isEmpty()));
        field.setOnMouseClicked(e -> list.setVisible(!shown.isEmpty()));
        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            switch (e.getCode()) {
                case DOWN, UP -> {
                    if (shown.isEmpty()) return;
                    at[0] = Math.clamp(at[0] + (e.getCode() == KeyCode.DOWN ? 1 : -1), 0, shown.size() - 1);
                    draw.run();
                }
                case ENTER -> {
                    if (shown.isEmpty()) return;
                    pick.accept(shown.get(at[0]));
                }
                case ESCAPE -> {
                    // An empty field leaves the view as the user set it.
                    if (field.getText().isEmpty()) return;
                    field.clear();
                    canvas.fit();
                }
                default -> {
                    return;
                }
            }
            e.consume();
        });
        // A press anywhere else on the canvas puts the list away; the map's own gesture goes on.
        canvas.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (!(e.getTarget() instanceof Node target) || !within(target, list)) list.setVisible(false);
        });
        return field;
    }

    /** After a new layout: the matches still shown, marked on their new cards. */
    void relaidOut() {
        Map<String, ?> cells = canvas.cells();
        matches = matches.stream().filter(n -> cells.containsKey(id.apply(n))).toList();
        mark();
    }

    /** After {@link GraphCanvas#show}: an open listing lists the items as they are now, never the ones replaced. */
    void reshown() {
        researched.run();
    }

    /** Whether {@code node} is {@code holder} or inside it. */
    private static boolean within(Node node, Node holder) {
        for (Node at = node; at != null; at = at.getParent()) {
            if (at == holder) return true;
        }
        return false;
    }

    /**
     * The items whose {@code terms} hold {@code query}, ignoring case: those whose name (their first term) begins with
     * it, then those whose name holds it, then those another term matched, each group in {@code nodes}' order.
     */
    static <N> List<Hit<N>> hits(List<N> nodes, Function<N, List<String>> terms, String query) {
        String wanted = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (wanted.isEmpty()) return List.of();
        List<Hit<N>> starts = new ArrayList<>();
        List<Hit<N>> holds = new ArrayList<>();
        List<Hit<N>> others = new ArrayList<>();
        for (N node : nodes) {
            List<String> said = terms.apply(node);
            for (int i = 0; i < said.size(); i++) {
                // One line however the term was written: a hit is a row.
                String term = Objects.toString(said.get(i), "").strip().replaceAll("\\s+", " ");
                String low = term.toLowerCase(Locale.ROOT);
                int label = i == 0 ? 0 : low.indexOf(' ') + 1;
                int found = low.indexOf(wanted, label);
                if (found < 0) continue;
                if (i > 0) others.add(new Hit<>(node, around(term, found, wanted.length())));
                else (found == 0 ? starts : holds).add(new Hit<>(node, ""));
                break;
            }
        }
        List<Hit<N>> all = new ArrayList<>(starts);
        all.addAll(holds);
        all.addAll(others);
        return all;
    }

    /** {@code term}, or of a long one the part around what matched at {@code found}, cut ends marked. */
    static String around(String term, int found, int length) {
        if (term.length() <= HIT_TEXT) return term;
        int from = Math.clamp(found + length / 2 - HIT_TEXT / 2, 0, term.length() - HIT_TEXT);
        return (from > 0 ? "…" : "") + term.substring(from, from + HIT_TEXT).strip()
                + (from + HIT_TEXT < term.length() ? "…" : "");
    }

    private void bringIn(N node) {
        canvas.selectedProperty().set(node);
        canvas.centreOn(id.apply(node));
    }

    private void mark() {
        Set<String> keys = new HashSet<>();
        for (N match : matches) keys.add(id.apply(match));
        canvas.cells().forEach((k, cell) -> cell.pseudoClassStateChanged(MATCH, keys.contains(k)));
    }
}
