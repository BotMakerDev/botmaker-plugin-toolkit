package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes.Route;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.scene.CacheHint;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A graph to look at: cards the plugin draws, laid out by a {@link LayeredLayout}, joined by links routed clear of
 * every card ({@link #setRules}), on a dot grid the user pans and zooms. The plugin owns what a card shows and what
 * an edge means; this owns where they go and the gestures.
 *
 * <ul>
 *   <li><b>Cards</b>: {@code view} draws one for each item; the canvas measures it, so a card is as big as its
 *       content. A card is selected by a click ({@link #selectedProperty()}) and opened by a double-click
 *       ({@link #setOnOpen}). Ctrl or Shift+click adds a card to the {@link #selection()} or takes it out, a left drag
 *       on the empty canvas selects what its box touches, and Esc or a click on the empty canvas clears it.</li>
 *   <li><b>Moving</b>: a left drag moves a card, or every selected card when it is one of them. The canvas keeps the
 *       new corners as pins over what {@code arrange} placed and tells {@link #setOnMoved}; a plugin that keeps them
 *       hands them back with {@link #setPinned}.</li>
 *   <li><b>Links</b>: {@code arrow} says where each edge runs, whether it is dashed and what label rides on it. Only
 *       solid links shape the layout; dashed ones are drawn between the placed cards and can be hidden
 *       ({@link #setShowDashed}). A link or its label is clicked ({@link #setOnEdgeClicked}).</li>
 *   <li><b>Gestures</b>: the wheel zooms about the cursor (Shift+wheel scrolls sideways), {@link #zoomIn()} and
 *       {@link #zoomOut()} about the middle, a right- or middle-drag pans, a right click that did not drag opens the
 *       card's or the link's menu ({@link #setMenus}), the minimap moves the view; {@link #fit()} shows everything,
 *       and a graph smaller than the view sits in its middle.</li>
 *   <li><b>Search</b>: {@link #find} marks the matching cards and brings the first into view;
 *       {@link #searchField(Function)} is a field wired to it. {@link #searchField(Function, Function)} lists its
 *       hits under the field instead, and a hit picked zooms its card to a readable size ({@link #zoomTo}).</li>
 *   <li><b>Export</b>: {@link #savePng} writes the whole graph at 1:1, whatever the zoom.</li>
 * </ul>
 *
 * <p>Every colour resolves a {@code -bm-*} token the host's theme publishes (its own stylesheet, {@code graph.css},
 * derives the canvas's from the general ones), so it follows the editor into a dark theme. A card the plugin draws
 * is styled by the plugin.
 *
 * <p>{@link #show} lays the whole graph out again; the zoom and the selection survive it, and a card new since the
 * last {@code show} fades in.
 *
 * <p>This class lays out and draws the cards; its parts, each package-private, are the {@link Viewport} (zoom and
 * pan), the {@link Selection}, the {@link CardGestures} (click, drag, box), the {@link LinkLayer}, the
 * {@link DotGrid}, the {@link Minimap} and the {@link GraphSearch}.
 */
public final class GraphCanvas<N, E> extends StackPane {

    /**
     * How one edge is drawn.
     *
     * @param port  the port of {@code from} it leaves, or null for the middle of the card's side
     * @param label a node riding at the middle of the link, or null
     */
    public record Arrow(String from, String port, String to, boolean dashed, Node label) {

        public static Arrow solid(String from, String to) {
            return new Arrow(from, null, to, false, null);
        }

        public static Arrow dashed(String from, String to) {
            return new Arrow(from, null, to, true, null);
        }

        public Arrow withLabel(Node label) {
            return new Arrow(from, port, to, dashed, label);
        }
    }

    /** Places the measured cards, from the solid links: a {@link LayeredLayout} call, as a rule. */
    @FunctionalInterface
    public interface Arrange {

        Placement place(List<Card> cards, List<Link> links);
    }

    /** One search hit: the item, and the term of it that matched, empty when its first (its name) did. */
    public record Hit<N>(N node, String matched) {}

    private static final String SHEET = Objects.requireNonNull(GraphCanvas.class.getResource("graph.css"))
            .toExternalForm();
    static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /** The space around the graph, so its outer cards are not against the edge. */
    private static final double MARGIN = 60;
    /** How far a press may move and still be a click (a menu, a pick) rather than a pan, a drag or a box. */
    static final double CLICK_SLOP = 4;

    private final Function<N, String> id;
    private final Function<N, Node> view;
    private final Function<E, Arrow> arrow;
    private final Arrange arrange;
    private Function<N, Map<String, Double>> ports = n -> Map.of();
    private Direction direction = Direction.RIGHT;
    private Routes.Rules rules = Routes.Rules.DEFAULT;
    private Consumer<N> onOpen = n -> {};
    private Function<N, ContextMenu> nodeMenu = n -> null;
    private Function<E, ContextMenu> edgeMenu = e -> null;
    private Consumer<Map<String, Pt>> onMoved = moved -> {};
    private boolean showDashed = true;
    /** The corners cards were moved to, over what {@code arrange} placed. */
    private Map<String, Pt> pins = Map.of();

    private final Pane content = new Pane();
    private final Group cells = new Group();
    private final DotGrid grid = new DotGrid();
    private final LinkLayer<E> links = new LinkLayer<>();
    private final Viewport viewport;
    private final Selection<N> selection;
    private final CardGestures<N> gestures;
    private final Minimap minimap;
    private final GraphSearch<N> search;

    private List<N> nodes = List.of();
    private List<E> edges = List.of();
    private final Map<String, N> byId = new LinkedHashMap<>();
    private final Map<String, StackPane> cellOf = new LinkedHashMap<>();
    private final Map<String, Box> placed = new LinkedHashMap<>();

    private final ListChangeListener<String> themeWatch = c -> Platform.runLater(grid::paint);
    private Parent themedRoot;

    /** The last layout, kept so hiding dashed links redraws the links without rebuilding a card. */
    private List<Card> cards = List.of();
    /** What {@code arrange} placed, before the pins. */
    private Placement arranged = new Placement(Map.of(), Set.of(), 0);
    /** {@link #arranged} with the pins: where the cards are drawn. */
    private Placement placement = arranged;
    /** How the cards' pictures are scaled: {@link CacheHint#SPEED} while the view moves. */
    private CacheHint cacheHint = CacheHint.QUALITY;
    /** How far the last draw moved the layout's corners onto the canvas: its margin, past the leftmost card. */
    private double shiftX = MARGIN;
    private double shiftY = MARGIN;
    private List<Arrow> arrows = List.of();
    /** The edge each of {@link #arrows} draws, at the same index. */
    private List<E> arrowEdges = List.of();
    /** The menu shown last, closed when another opens. */
    private ContextMenu shownMenu;
    /** The scene a menu's close-on-press filter was added to, so it is taken off that very one. */
    private Scene menuScene;

    /**
     * @param id      each item's id, unique in the graph: what {@link Arrow} names
     * @param view    draws an item's card; called again on every {@link #show}
     * @param arrow   how each edge is drawn
     * @param arrange where the cards go
     */
    public GraphCanvas(Function<N, String> id, Function<N, Node> view, Function<E, Arrow> arrow, Arrange arrange) {
        this.id = id;
        this.view = view;
        this.arrow = arrow;
        this.arrange = arrange;
        getStylesheets().add(SHEET);
        getStyleClass().add("graph-canvas");
        content.getStyleClass().add("graph-canvas-content");
        viewport = new Viewport(this, content);
        selection = new Selection<>(this, id, byId);
        gestures = new CardGestures<>(content, viewport, selection, new CardGestures.Board<>() {
            @Override
            public Placement placement() {
                return placement;
            }

            @Override
            public Map<String, Box> placed() {
                return placed;
            }

            @Override
            public void drawFrame(Placement at) {
                drawDragged(at);
            }

            @Override
            public void dropped(Map<String, Pt> moved) {
                pin(moved);
            }

            @Override
            public void open(N node) {
                onOpen.accept(node);
            }
        });
        minimap = new Minimap(content, viewport);
        search = new GraphSearch<>(this, id);
        content.getChildren().addAll(grid.ink(), links.links(), cells, links.labels(), gestures.band());
        // The grid moves with the content: a scroll, a zoom, a resize or a graph sized again.
        viewport.scroller().viewportBoundsProperty().addListener((o, was, is) -> followGrid());
        viewport.scroller().hvalueProperty().addListener((o, was, is) -> followGrid());
        viewport.scroller().vvalueProperty().addListener((o, was, is) -> followGrid());
        viewport.zoomProperty().addListener((o, was, is) -> followGrid());
        viewport.onMoving(this::moving);

        viewport.scroller().addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ESCAPE || selection.isEmpty()) return;
            selection.set(null);
            e.consume();
        });
        viewport.scroller().addEventFilter(MouseEvent.MOUSE_PRESSED, e -> hideMenu());
        viewport.installGestures(this::showMenu);
        gestures.installBand(viewport.holder());
        selection.onChanged(this::markSelected);

        // The grid is painted, not styled, so it is painted again once CSS has something to say: when the canvas
        // joins a scene, and when the host restyles the scene's root for another theme (after that pulse's CSS).
        // The cards are measured again once the scroller's skin puts them in the scene: before, nothing styles them.
        sceneProperty().addListener((o, was, is) -> {
            if (themedRoot != null) themedRoot.getStyleClass().removeListener(themeWatch);
            themedRoot = is == null ? null : is.getRoot();
            if (themedRoot == null) return;
            themedRoot.getStyleClass().addListener(themeWatch);
            relayout(false);
        });
        viewport.scroller().skinProperty().addListener((o, was, is) -> relayout(false));

        getChildren().addAll(grid.dots(), viewport.scroller(), minimap.node());
    }

    // --- what is shown ---

    /** Shows {@code nodes} joined by {@code edges}, laid out afresh. The zoom and the selection are kept. */
    public void show(List<N> nodes, List<E> edges) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        relayout(true);
        search.reshown();
    }

    /** Each item's named ports, as offsets along the side its links leave from. None by default. */
    public void setPorts(Function<N, Map<String, Double>> ports) {
        this.ports = ports;
    }

    /** Which way the routes run: the direction {@code arrange} lays out in. {@link Direction#RIGHT} by default. */
    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    /**
     * How the links are routed: {@link Routes.Rules#DEFAULT} unless said. A layout that keeps room for links back
     * ({@link GraphLayout.Loops#THROUGH}) wants the same {@code loops} here, or the room stays empty.
     */
    public void setRules(Routes.Rules rules) {
        this.rules = rules;
    }

    /** Whether dashed links are drawn. Hiding them changes no card's place. */
    public void setShowDashed(boolean show) {
        if (showDashed == show) return;
        showDashed = show;
        draw();
    }

    /** Called with an item when its card is double-clicked. */
    public void setOnOpen(Consumer<N> onOpen) {
        this.onOpen = onOpen;
    }

    /** Called with an edge when its link or its label is clicked. */
    public void setOnEdgeClicked(Consumer<E> onEdgeClicked) {
        links.setOnClicked(onEdgeClicked);
    }

    /**
     * The menus a right click that did not drag opens: {@code nodes} for a card, which is selected first, and
     * {@code edges} for a link or its label. Either answers null for no menu. A right press goes to the canvas,
     * which pans on a drag, so a card's own right-click handler never sees it: this is how a card gets a menu.
     */
    public void setMenus(Function<N, ContextMenu> nodes, Function<E, ContextMenu> edges) {
        this.nodeMenu = nodes;
        this.edgeMenu = edges;
    }

    /**
     * Called, after a drag, with the corner each moved card was left at, in the coordinates {@code arrange} places
     * in. The canvas pins them already; a plugin keeps them to hand back through {@link #setPinned}.
     */
    public void setOnMoved(Consumer<Map<String, Pt>> onMoved) {
        this.onMoved = onMoved;
    }

    /**
     * Puts each card {@code pins} names at the corner it gives, over what {@code arrange} places it at: cards a user
     * moved and the plugin kept. Empty puts every card back where the layout puts it.
     */
    public void setPinned(Map<String, Pt> pins) {
        Map<String, Pt> now = new LinkedHashMap<>();
        if (pins != null) pins.forEach((k, p) -> {
            if (k != null && p != null) now.put(k, p);
        });
        if (now.equals(this.pins)) return;
        this.pins = Map.copyOf(now);
        placement = arranged.pinned(this.pins);
        draw();
    }

    /** The ids of the selected cards, in the order they were picked: what a drag of one of them moves. */
    public Set<String> selection() {
        return selection.ids();
    }

    /**
     * The selected item, or null: the card clicked last. Setting it selects that card alone; {@link #selection()}
     * holds every selected one. Setting it marks that card; {@link #centreOn} brings one into view. The math reads
     * the laid-out size ({@code prefWidth}), so it is right straight after {@link #show}, before the next pulse.
     */
    public ObjectProperty<N> selectedProperty() {
        return selection.property();
    }

    /** The card drawn for the item with {@code itemId}, for a plugin that marks one (a pulse while it is busy). */
    public Node cardOf(String itemId) {
        StackPane cell = cellOf.get(itemId);
        return cell == null || cell.getChildren().isEmpty() ? null : cell.getChildren().getFirst();
    }

    public ReadOnlyDoubleProperty zoomProperty() {
        return viewport.zoomProperty();
    }

    /**
     * The part of the canvas the graph is seen through, in its own pixels: what an {@link Arrange} that picks its
     * shape by the window's proportions reads, and listens to for a resize.
     */
    public ReadOnlyObjectProperty<Bounds> viewportProperty() {
        return viewport.scroller().viewportBoundsProperty();
    }

    /** The items {@link #show} gave, for the search. */
    List<N> nodes() {
        return nodes;
    }

    /** Each shown item's cell by id, for the search's marks. */
    Map<String, StackPane> cells() {
        return cellOf;
    }

    // --- layout ---

    private void relayout(boolean fresh) {
        Set<String> before = new HashSet<>(cellOf.keySet());
        boolean hadCards = !before.isEmpty();
        N keep = selection.property().get();
        byId.clear();
        cellOf.clear();
        cells.getChildren().clear();
        for (N node : nodes) {
            String key = id.apply(node);
            byId.put(key, node);
            StackPane cell = cell(node);
            cellOf.put(key, cell);
            cells.getChildren().add(cell);
            if (fresh && hadCards && !before.contains(key)) fadeIn(cell);
        }
        content.applyCss();
        List<Card> measured = new ArrayList<>();
        for (N node : nodes) {
            StackPane cell = cellOf.get(id.apply(node));
            cell.applyCss();
            double width = Math.ceil(cell.prefWidth(-1));
            double height = Math.ceil(cell.prefHeight(width));
            cell.resize(width, height);
            measured.add(new Card(id.apply(node), width, height, portsOf(node)));
        }
        cards = measured;
        List<Arrow> kept = new ArrayList<>();
        List<E> keptEdges = new ArrayList<>();
        for (E edge : edges) {
            Arrow a = arrow.apply(edge);
            if (a == null || !byId.containsKey(a.from()) || !byId.containsKey(a.to())) continue;
            kept.add(a);
            keptEdges.add(edge);
        }
        arrows = List.copyOf(kept);
        arrowEdges = List.copyOf(keptEdges);
        List<Link> solid = arrows.stream().filter(a -> !a.dashed()).map(a -> new Link(a.from(), a.port(), a.to()))
                .toList();
        arranged = arrange.place(cards, solid);
        // A pin of a card no longer shown goes: a card of that id made again later is laid out afresh.
        Map<String, Pt> stillShown = new LinkedHashMap<>(pins);
        stillShown.keySet().retainAll(byId.keySet());
        pins = Map.copyOf(stillShown);
        placement = arranged.pinned(pins);
        draw();

        selection.keepShown(keep);
        search.relaidOut();
    }

    /** A plugin's ports with nothing null in them: a card is never refused for its ports. */
    private Map<String, Double> portsOf(N node) {
        Map<String, Double> named = ports.apply(node);
        if (named == null) return Map.of();
        Map<String, Double> kept = new LinkedHashMap<>();
        named.forEach((k, v) -> {
            if (k != null && v != null) kept.put(k, v);
        });
        return kept;
    }

    /** Routes the links between the placed cards and puts cards, links and labels where they go. */
    private void draw() {
        List<Arrow> drawn = new ArrayList<>();
        List<E> drawnEdges = new ArrayList<>();
        List<Route> routes = routesOf(placement, drawn, drawnEdges);

        Box extent = Routes.extent(cards, placement.at(), routes);
        shiftX = extent == null ? MARGIN : MARGIN - extent.minX();
        shiftY = extent == null ? MARGIN : MARGIN - extent.minY();
        double width = extent == null ? 2 * MARGIN : extent.width() + 2 * MARGIN;
        double height = extent == null ? 2 * MARGIN : extent.height() + 2 * MARGIN;
        content.setPrefSize(width, height);
        content.setMinSize(width, height);
        grid.paint();
        followGrid();

        placeCells(placement);
        links.draw(routes, drawn, drawnEdges, shiftX, shiftY);
        minimap.draw(placed, selection::contains);
    }

    /**
     * A drag's frame: the cards and their links where {@code at} puts them, on the canvas as the last draw sized it.
     * Every link is routed again, which is arithmetic, but only the paths whose route changed are touched: a frame
     * that built every link's nodes again lagged behind the pointer (2026-10-22).
     */
    private void drawDragged(Placement at) {
        List<Arrow> drawn = new ArrayList<>();
        List<E> drawnEdges = new ArrayList<>();
        List<Route> routes = routesOf(at, drawn, drawnEdges);
        placeCells(at);
        links.move(routes, drawn, drawnEdges, shiftX, shiftY);
    }

    /** Lines the grid behind the scroller up with the content as it is scrolled and zoomed now. */
    private void followGrid() {
        double z = viewport.zoom();
        Pt corner = viewport.viewToContent(0, 0);
        grid.follow(-corner.x() * z, -corner.y() * z, z);
    }

    /**
     * While the view pans or zooms, the cards and labels are scaled as the pictures last drawn of them, and drawn
     * sharp again once it rests: a card of pictures drawn afresh at each step of a zoom made the zoom stutter.
     */
    private void moving(boolean is) {
        cacheHint = is ? CacheHint.SPEED : CacheHint.QUALITY;
        cellOf.values().forEach(cell -> cell.setCacheHint(cacheHint));
        links.setCacheHint(cacheHint);
    }

    /** Pins the cards a drag {@code moved}, draws them there and tells the plugin. */
    private void pin(Map<String, Pt> moved) {
        Map<String, Pt> nextPins = new LinkedHashMap<>(pins);
        nextPins.putAll(moved);
        pins = Map.copyOf(nextPins);
        // The canvas grows or shrinks round the moved cards: scrolled by as much, the rest stays still in the view.
        Pt corner = viewport.viewToContent(0, 0);
        double wasX = shiftX;
        double wasY = shiftY;
        placement = arranged.pinned(pins);
        draw();
        viewport.putCorner(new Pt(corner.x() + shiftX - wasX, corner.y() + shiftY - wasY));
        onMoved.accept(Collections.unmodifiableMap(moved));
    }

    /**
     * The routes of the links between two cards {@code at} shows, filling {@code drawn} and {@code drawnEdges} with
     * their arrows and edges, one for one, in this order.
     */
    private List<Route> routesOf(Placement at, List<Arrow> drawn, List<E> drawnEdges) {
        Set<String> shownIds = new HashSet<>(at.at().keySet());
        shownIds.removeAll(at.hidden());
        for (int i = 0; i < arrows.size(); i++) {
            Arrow a = arrows.get(i);
            if ((showDashed || !a.dashed()) && shownIds.contains(a.from()) && shownIds.contains(a.to())) {
                drawn.add(a);
                drawnEdges.add(arrowEdges.get(i));
            }
        }
        List<Link> drawnLinks = drawn.stream().map(a -> new Link(a.from(), a.port(), a.to())).toList();
        return Routes.of(cards, at.at(), drawnLinks, direction, rules);
    }

    /** Puts each card where {@code at} places it, shifted onto the canvas; a hidden one is hidden. */
    private void placeCells(Placement at) {
        placed.clear();
        for (Card card : cards) {
            StackPane cell = cellOf.get(card.id());
            Pt p = at.at().get(card.id());
            boolean shown = p != null && !at.hidden().contains(card.id());
            cell.setVisible(shown);
            if (!shown) continue;
            cell.relocate(p.x() + shiftX, p.y() + shiftY);
            placed.put(card.id(), new Box(p.x() + shiftX, p.y() + shiftY, p.x() + shiftX + card.width(),
                    p.y() + shiftY + card.height()));
        }
    }

    private StackPane cell(N node) {
        StackPane cell = new StackPane(view.apply(node));
        cell.getStyleClass().add("graph-node");
        cell.setManaged(false);
        // Drawn once and moved as a picture while the view pans: a card of pictures and effects is slow to draw.
        cell.setCache(true);
        cell.setCacheHint(cacheHint);
        gestures.wire(cell, id.apply(node), node);
        return cell;
    }

    private static void fadeIn(Node cell) {
        cell.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(350), cell);
        fade.setToValue(1);
        fade.play();
    }

    private void markSelected() {
        cellOf.forEach((k, cell) -> cell.pseudoClassStateChanged(SELECTED, selection.contains(k)));
        minimap.draw(placed, selection::contains);
    }

    // --- menus ---

    /** The menu of the card or link under a right click, when the plugin gives it one. */
    private void showMenu(MouseEvent e) {
        ContextMenu menu = null;
        for (Node at = e.getPickResult().getIntersectedNode(); at != null && at != this; at = at.getParent()) {
            E edge = links.edgeOf(at);
            if (edge != null) {
                menu = edgeMenu.apply(edge);
                break;
            }
            N node = nodeOfCell(at);
            if (node != null) {
                selection.set(node);
                menu = nodeMenu.apply(node);
                break;
            }
        }
        hideMenu();
        shownMenu = menu;
        if (menu == null) return;
        // However it closes (an item, Esc, the press below), the filter goes with it.
        menu.setOnHidden(h -> hideMenu());
        menu.show(this, e.getScreenX(), e.getScreenY());
        // A press anywhere else in the window closes it (2026-10-15: a left click on the panel left it open, the
        // press taken by the canvas's own handlers before the popup saw it).
        menuScene = getScene();
        if (menuScene != null) menuScene.addEventFilter(MouseEvent.MOUSE_PRESSED, closeMenu);
    }

    private final EventHandler<MouseEvent> closeMenu = e -> hideMenu();

    private void hideMenu() {
        if (menuScene != null) menuScene.removeEventFilter(MouseEvent.MOUSE_PRESSED, closeMenu);
        menuScene = null;
        ContextMenu menu = shownMenu;
        shownMenu = null;
        if (menu != null && menu.isShowing()) menu.hide();
    }

    /** The item whose card {@code cell} is, or null. */
    private N nodeOfCell(Node cell) {
        for (Map.Entry<String, StackPane> each : cellOf.entrySet()) {
            if (each.getValue() == cell) return byId.get(each.getKey());
        }
        return null;
    }

    // --- view ---

    /** Zooms in a step about the middle of the view: the + button a window puts beside {@link #fit()}. */
    public void zoomIn() {
        viewport.zoomIn();
    }

    /** Zooms out a step about the middle of the view. */
    public void zoomOut() {
        viewport.zoomOut();
    }

    /** Zooms so the whole graph shows, never above 1:1, and centres it. */
    public void fit() {
        viewport.fit();
    }

    /** Scrolls the card of the item with {@code itemId} into the middle of the view. */
    public void centreOn(String itemId) {
        Box box = placed.get(itemId);
        if (box != null) viewport.centreOn(box.centre());
    }

    /**
     * Zooms to {@code scale} with the card of the item {@code itemId} in the middle of the view, eased over a quarter
     * second: what a picked search hit does.
     */
    public void zoomTo(String itemId, double scale) {
        Box box = placed.get(itemId);
        if (box != null) viewport.zoomTo(box.centre(), scale);
    }

    // --- search ---

    /**
     * Marks the items {@code match} accepts, selects the first and brings it into view; a null {@code match}
     * clears the marks. Returns the matches, in the order {@link #show} listed them.
     */
    public List<N> find(Predicate<N> match) {
        return search.find(match);
    }

    /** A search field over {@code text}: typing marks the cards whose text holds it, Enter steps to the next. */
    public TextField searchField(Function<N, String> text) {
        return search.field(text);
    }

    /**
     * A search field that lists its hits under it, over the canvas's top-left corner, as {@code row} draws each: up
     * to twelve, picked with the arrows and Enter or a click, and a hit picked is selected and zoomed to a readable
     * size. Every hit is marked on the map, listed or not. Esc clears the field and fits the map again.
     *
     * <p>{@code terms} is what an item is found by, its name first: a name that matches lists it above one whose
     * other terms do, and a hit says which term matched when it was not the name. A term after the name may begin
     * with a label and a space ({@code input beerBuy}): the hit shows the label, and only the rest is searched.
     *
     * <p>Called once per canvas: its list sits over the canvas's corner for the canvas's life.
     */
    public TextField searchField(Function<N, List<String>> terms, Function<Hit<N>, Node> row) {
        return search.listing(terms, row);
    }

    // --- export ---

    /** The whole graph at 1:1, whatever the zoom, on the canvas's own background. */
    public WritableImage image() {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(background());
        return content.snapshot(params, null);
    }

    /** Writes {@link #image()} to {@code file} as a PNG. A file path, named in full: {@code Path} here is a shape. */
    public void savePng(java.nio.file.Path file) throws IOException {
        WritableImage image = image();
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        int[] argb = new int[width * height];
        image.getPixelReader().getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), argb, 0, width);
        Files.write(file, Png.encode(width, height, argb));
    }

    private Paint background() {
        Background background = getBackground();
        if (background == null || background.getFills().isEmpty()) return Color.WHITE;
        return background.getFills().getFirst().getFill();
    }
}
