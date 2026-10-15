package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Card;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Direction;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Link;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import com.botmaker.plugin.toolkit.graph.Routes.Cubic;
import com.botmaker.plugin.toolkit.graph.Routes.Route;
import com.botmaker.plugin.toolkit.graph.Routes.Segment;
import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.paint.Paint;
import javafx.scene.shape.CubicCurveTo;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A graph to look at: cards the plugin draws, laid out by a {@link LayeredLayout} or {@link TreeLayout}, joined by
 * links routed clear of every card, on a dot grid the user pans and zooms. The plugin owns what a card shows and what
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
 *       {@link #searchField} is a field wired to it.</li>
 *   <li><b>Export</b>: {@link #savePng} writes the whole graph at 1:1, whatever the zoom.</li>
 * </ul>
 *
 * <p>Every colour resolves a {@code -bm-*} token the host's theme publishes (its own stylesheet, {@code graph.css},
 * derives the canvas's from the general ones), so it follows the editor into a dark theme. A card the plugin draws
 * is styled by the plugin.
 *
 * <p>{@link #show} lays the whole graph out again; the zoom and the selection survive it, and a card new since the
 * last {@code show} fades in.
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

    /** Places the measured cards, from the solid links: a {@link LayeredLayout} or {@link TreeLayout} call. */
    @FunctionalInterface
    public interface Arrange {

        Placement place(List<Card> cards, List<Link> links);
    }

    private static final String SHEET = Objects.requireNonNull(GraphCanvas.class.getResource("graph.css"))
            .toExternalForm();
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass MATCH = PseudoClass.getPseudoClass("match");

    /** The space around the graph, so its outer cards are not against the edge. */
    private static final double MARGIN = 60;
    private static final double GRID_STEP = 24;
    private static final Color GRID_FALLBACK = Color.web("#d8d8d8");
    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 2.5;
    private static final double ZOOM_STEP = 1.1;
    /** What a press of {@link #zoomIn()} or {@link #zoomOut()} scales by. */
    private static final double BUTTON_STEP = 1.25;
    /** A wheel's notch, in scroll pixels: a touchpad's many small scrolls add up to a step, not a step each. */
    private static final double NOTCH = 40;
    /** How far a press may move and still be a click (a menu, a pick) rather than a pan, a drag or a box. */
    private static final double CLICK_SLOP = 4;
    /** How wide a link takes a click: its own stroke is thinner than a pointer aims. */
    private static final double HIT_WIDTH = 12;
    private static final double MINIMAP_MAX = 170;
    private static final double HEAD = 7;

    private final Function<N, String> id;
    private final Function<N, Node> view;
    private final Function<E, Arrow> arrow;
    private final Arrange arrange;
    private Function<N, Map<String, Double>> ports = n -> Map.of();
    private Direction direction = Direction.RIGHT;
    private Consumer<N> onOpen = n -> {};
    private Consumer<E> onEdgeClicked = e -> {};
    private Function<N, ContextMenu> nodeMenu = n -> null;
    private Function<E, ContextMenu> edgeMenu = e -> null;
    private Consumer<Map<String, Pt>> onMoved = moved -> {};
    private boolean showDashed = true;
    /** The corners cards were moved to, over what {@code arrange} placed. */
    private Map<String, Pt> pins = Map.of();

    private final Pane content = new Pane();
    private final Group links = new Group();
    private final Group labels = new Group();
    private final Group cells = new Group();
    private final Region grid = new Region();
    private final Region gridInk = new Region();
    private final Scale zoom = new Scale(1, 1);
    private final ReadOnlyDoubleWrapper zoomLevel = new ReadOnlyDoubleWrapper(this, "zoom", 1);
    private final ScrollPane scroller;
    private final Pane minimap = new Pane();
    private final Rectangle minimapView = new Rectangle();

    private List<N> nodes = List.of();
    private List<E> edges = List.of();
    private final Map<String, N> byId = new LinkedHashMap<>();
    private final Map<String, StackPane> cellOf = new LinkedHashMap<>();
    private final Map<String, Box> placed = new LinkedHashMap<>();
    private final ObjectProperty<N> selected = new SimpleObjectProperty<>(this, "selected");
    /** The ids of the selected cards, {@link #selected}'s among them while it is set. */
    private final Set<String> selection = new LinkedHashSet<>();
    private final Rectangle band = new Rectangle();
    private List<N> matches = List.of();
    private int matchAt;

    private final ListChangeListener<String> themeWatch = c -> Platform.runLater(this::paintGrid);
    private Parent themedRoot;

    /** The last layout, kept so hiding dashed links redraws the links without rebuilding a card. */
    private List<Card> cards = List.of();
    /** What {@code arrange} placed, before the pins. */
    private Placement arranged = new Placement(Map.of(), Set.of(), 0);
    /** {@link #arranged} with the pins: where the cards are drawn. */
    private Placement placement = arranged;
    /** How far the last draw moved the layout's corners onto the canvas: its margin, past the leftmost card. */
    private double shiftX = MARGIN;
    private double shiftY = MARGIN;
    private List<Arrow> arrows = List.of();
    /** The edge each of {@link #arrows} draws, at the same index. */
    private List<E> arrowEdges = List.of();
    /** The edge each drawn link's hit line and label stands for: what a click on one names. */
    private final Map<Node, E> edgeOf = new HashMap<>();
    /** The menu shown last, closed when another opens. */
    private ContextMenu shownMenu;

    private boolean panMoved;
    private double panX;
    private double panY;
    private double panH;
    private double panV;
    private boolean panning;

    /** A card drag: where it was pressed, in the content's pixels, and the corners of the cards it moves. */
    private Pt dragFrom;
    private Map<String, Pt> dragStart = Map.of();
    private Placement dragged;
    /** Set once a press has dragged a card or a box past the slop: the click its release makes is not a pick. */
    private boolean gestured;
    /** The drag's frame to draw at the next pulse: one route of the links a pulse, however many moves came in. */
    private Placement dragFrame;
    private final AnimationTimer dragFrames = new AnimationTimer() {
        @Override
        public void handle(long now) {
            if (dragFrame == null) return;
            Placement frame = dragFrame;
            dragFrame = null;
            drawDragged(frame);
        }
    };
    /** The scene a menu's close-on-press filter was added to, so it is taken off that very one. */
    private Scene menuScene;
    /** A box drag on the empty canvas: where it was pressed, and whether it adds to the selection. */
    private Pt bandFrom;
    private boolean bandAdds;

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
        grid.setMouseTransparent(true);
        grid.setManaged(false);
        gridInk.getStyleClass().add("graph-grid-ink");
        gridInk.setManaged(false);
        gridInk.setMouseTransparent(true);
        gridInk.resize(0, 0);
        // Only each link's hit line takes the pointer (drawLinks); the strokes and heads let it through.
        band.getStyleClass().add("graph-band");
        band.setManaged(false);
        band.setMouseTransparent(true);
        band.setVisible(false);
        content.getChildren().addAll(grid, gridInk, links, cells, labels, band);

        Group scaled = new Group(content);
        scaled.getTransforms().add(zoom);
        // As big as the graph at this zoom, whatever a dragged card overflows: the view does not jump while it moves.
        Pane sized = new Pane(scaled);
        sized.prefWidthProperty().bind(content.prefWidthProperty().multiply(zoom.xProperty()));
        sized.prefHeightProperty().bind(content.prefHeightProperty().multiply(zoom.yProperty()));
        sized.setMinSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        sized.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // At least the view's size, so a graph smaller than the view sits in its middle (2026-10-15: Fit left it in
        // the top-left corner, the rest of the window empty).
        StackPane holder = new StackPane(sized);
        scroller = new ScrollPane(holder);
        holder.minWidthProperty().bind(Bindings.createDoubleBinding(() -> scroller.getViewportBounds().getWidth(),
                scroller.viewportBoundsProperty()));
        holder.minHeightProperty().bind(Bindings.createDoubleBinding(() -> scroller.getViewportBounds().getHeight(),
                scroller.viewportBoundsProperty()));
        // Not pannable: ScrollPane pans on any button, which would steal a card's click. A right or middle drag
        // pans instead, below.
        scroller.setPannable(false);
        scroller.getStyleClass().add("graph-scroller");
        installGestures();
        installBand(holder);

        selected.addListener((o, was, is) -> {
            // Picked by a plain click, a search or the plugin: that card alone, unless it is one of several already.
            String key = is == null ? null : id.apply(is);
            if (key == null) selection.clear();
            else if (!selection.contains(key)) {
                selection.clear();
                selection.add(key);
            }
            markSelected();
        });

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
        scroller.skinProperty().addListener((o, was, is) -> relayout(false));

        getChildren().addAll(scroller, buildMinimap());
        scroller.hvalueProperty().addListener((o, was, is) -> drawMinimapView());
        scroller.vvalueProperty().addListener((o, was, is) -> drawMinimapView());
        scroller.viewportBoundsProperty().addListener((o, was, is) -> drawMinimapView());
    }

    // --- what is shown ---

    /** Shows {@code nodes} joined by {@code edges}, laid out afresh. The zoom and the selection are kept. */
    public void show(List<N> nodes, List<E> edges) {
        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        relayout(true);
    }

    /** Each item's named ports, as offsets along the side its links leave from. None by default. */
    public void setPorts(Function<N, Map<String, Double>> ports) {
        this.ports = ports;
    }

    /** Which way the routes run: the direction {@code arrange} lays out in. {@link Direction#RIGHT} by default. */
    public void setDirection(Direction direction) {
        this.direction = direction;
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
        this.onEdgeClicked = onEdgeClicked;
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
        return Collections.unmodifiableSet(new LinkedHashSet<>(selection));
    }

    /**
     * The selected item, or null: the card clicked last. Setting it selects that card alone; {@link #selection()}
     * holds every selected one. Setting it marks that card; {@link #centreOn} brings one into view. The math reads
     * the laid-out size ({@code prefWidth}), so it is right straight after {@link #show}, before the next pulse.
     */
    public ObjectProperty<N> selectedProperty() {
        return selected;
    }

    /** The card drawn for the item with {@code itemId}, for a plugin that marks one (a pulse while it is busy). */
    public Node cardOf(String itemId) {
        StackPane cell = cellOf.get(itemId);
        return cell == null || cell.getChildren().isEmpty() ? null : cell.getChildren().getFirst();
    }

    public ReadOnlyDoubleProperty zoomProperty() {
        return zoomLevel.getReadOnlyProperty();
    }

    /**
     * The part of the canvas the graph is seen through, in its own pixels: what an {@link Arrange} that picks its
     * shape by the window's proportions reads, and listens to for a resize.
     */
    public ReadOnlyObjectProperty<Bounds> viewportProperty() {
        return scroller.viewportBoundsProperty();
    }

    // --- layout ---

    private void relayout(boolean fresh) {
        Set<String> before = new HashSet<>(cellOf.keySet());
        boolean hadCards = !before.isEmpty();
        N keep = selected.get();
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

        selection.retainAll(byId.keySet());
        selected.set(keep != null && byId.containsKey(id.apply(keep)) ? byId.get(id.apply(keep)) : null);
        markSelected();
        matches = matches.stream().filter(n -> byId.containsKey(id.apply(n))).toList();
        markMatches();
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
        grid.resize(width, height);
        paintGrid();

        placeCells(placement);
        drawLinks(routes, drawn, drawnEdges, shiftX, shiftY);
        drawMinimap();
    }

    /** A drag's frame: the cards and their links where {@code at} puts them, on the canvas as the last draw sized it. */
    private void drawDragged(Placement at) {
        List<Arrow> drawn = new ArrayList<>();
        List<E> drawnEdges = new ArrayList<>();
        List<Route> routes = routesOf(at, drawn, drawnEdges);
        placeCells(at);
        drawLinks(routes, drawn, drawnEdges, shiftX, shiftY);
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
        return Routes.of(cards, at.at(), drawnLinks, direction, Routes.Rules.DEFAULT);
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
        String key = id.apply(node);
        cell.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            scroller.requestFocus();
            startDrag(key, e);
            e.consume();
        });
        cell.setOnMouseDragged(e -> {
            if (dragFrom == null) return;
            dragTo(e);
            e.consume();
        });
        cell.setOnMouseReleased(e -> {
            if (dragFrom == null) return;
            endDrag();
            e.consume();
        });
        cell.setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY || gestured || !e.isStillSincePress()) return;
            e.consume();
            if (adds(e)) {
                toggle(key);
                return;
            }
            selection.clear();
            selection.add(key);
            selected.set(node);
            markSelected();
            if (e.getClickCount() == 2) onOpen.accept(node);
        });
        return cell;
    }

    /** Whether a click or a press adds to the selection rather than replacing it. */
    private static boolean adds(MouseEvent e) {
        return e.isShortcutDown() || e.isShiftDown();
    }

    /** The point under the pointer, in the content's own pixels: the zoom and the centring undone. */
    private Pt local(MouseEvent e) {
        Point2D p = content.sceneToLocal(e.getSceneX(), e.getSceneY());
        return new Pt(p.getX(), p.getY());
    }

    /** Ctrl+click: {@code key} into the selection or out of it. The card clicked last stays the selected item. */
    private void toggle(String key) {
        if (selection.remove(key)) {
            N now = selected.get();
            if (now != null && id.apply(now).equals(key)) {
                selected.set(selection.isEmpty() ? null : byId.get(selection.iterator().next()));
            }
        } else {
            selection.add(key);
            selected.set(byId.get(key));
        }
        markSelected();
    }

    // --- moving cards ---

    private void startDrag(String key, MouseEvent e) {
        dragFrom = local(e);
        dragged = null;
        gestured = false;
        Set<String> moving = selection.contains(key) && !adds(e) ? selection : Set.of(key);
        Map<String, Pt> start = new LinkedHashMap<>();
        for (String k : moving) {
            Pt p = placement.at().get(k);
            if (p != null && !placement.hidden().contains(k)) start.put(k, p);
        }
        dragStart = start;
    }

    private void dragTo(MouseEvent e) {
        Pt now = local(e);
        double dx = now.x() - dragFrom.x();
        double dy = now.y() - dragFrom.y();
        if (dragged == null) {
            if (Math.hypot(dx, dy) * zoom.getX() <= CLICK_SLOP) return;
            gestured = true;
            // A card dragged that was not selected is selected alone, the way a click would.
            if (dragStart.size() == 1) {
                String only = dragStart.keySet().iterator().next();
                if (!selection.contains(only)) selected.set(byId.get(only));
            }
        }
        Map<String, Pt> at = new LinkedHashMap<>(placement.at());
        dragStart.forEach((k, p) -> at.put(k, new Pt(p.x() + dx, p.y() + dy)));
        dragged = new Placement(at, placement.hidden(), placement.crossings());
        dragFrame = dragged;
        dragFrames.start();
    }

    private void endDrag() {
        dragFrames.stop();
        dragFrame = null;
        dragFrom = null;
        Placement done = dragged;
        dragged = null;
        if (done == null) return;
        Map<String, Pt> moved = new LinkedHashMap<>();
        for (String k : dragStart.keySet()) moved.put(k, done.at().get(k));
        Map<String, Pt> nextPins = new LinkedHashMap<>(pins);
        nextPins.putAll(moved);
        pins = Map.copyOf(nextPins);
        // The canvas grows or shrinks round the moved cards: scrolled by as much, the rest stays still in the view.
        Pt corner = viewToContent(0, 0);
        double wasX = shiftX;
        double wasY = shiftY;
        placement = arranged.pinned(pins);
        draw();
        scroller.layout();
        scrollSo(new Pt(corner.x() + shiftX - wasX, corner.y() + shiftY - wasY), 0, 0);
        drawMinimapView();
        onMoved.accept(Collections.unmodifiableMap(moved));
    }

    // --- the selection box ---

    private void installBand(Pane holder) {
        holder.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            scroller.requestFocus();
            bandFrom = local(e);
            bandAdds = adds(e);
            gestured = false;
        });
        holder.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (bandFrom == null) return;
            Box box = bandBox(local(e));
            if (!band.isVisible() && Math.max(box.width(), box.height()) * zoom.getX() <= CLICK_SLOP) return;
            band.setVisible(true);
            gestured = true;
            band.setX(box.minX());
            band.setY(box.minY());
            band.setWidth(box.width());
            band.setHeight(box.height());
            e.consume();
        });
        holder.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            if (bandFrom == null) return;
            Box box = bandBox(local(e));
            bandFrom = null;
            if (!band.isVisible()) return;
            band.setVisible(false);
            if (!bandAdds) selection.clear();
            placed.forEach((k, card) -> {
                if (card.overlaps(box)) selection.add(k);
            });
            N now = selected.get();
            if (selection.isEmpty()) selected.set(null);
            else if (now == null || !selection.contains(id.apply(now))) {
                selected.set(byId.get(selection.iterator().next()));
            }
            markSelected();
            e.consume();
        });
        holder.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            // A click on the empty canvas, not the end of a box: nothing selected.
            if (e.getButton() == MouseButton.PRIMARY && !gestured && e.isStillSincePress() && !adds(e)) {
                selected.set(null);
            }
        });
    }

    /** The box from where the band was pressed to {@code to}, kept on the canvas so the graph's size stays put. */
    private Box bandBox(Pt to) {
        double w = content.getPrefWidth();
        double h = content.getPrefHeight();
        double x1 = Math.clamp(bandFrom.x(), 0, w);
        double y1 = Math.clamp(bandFrom.y(), 0, h);
        double x2 = Math.clamp(to.x(), 0, w);
        double y2 = Math.clamp(to.y(), 0, h);
        return new Box(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2), Math.max(y1, y2));
    }

    private static void fadeIn(Node cell) {
        cell.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(350), cell);
        fade.setToValue(1);
        fade.play();
    }

    private void drawLinks(List<Route> routes, List<Arrow> drawn, List<E> drawnEdges, double dx, double dy) {
        links.getChildren().clear();
        labels.getChildren().clear();
        edgeOf.clear();
        // Routes come back in the order of the links they were asked for, skipping none: both cards are shown.
        for (int i = 0; i < routes.size() && i < drawn.size(); i++) {
            Route route = routes.get(i);
            Arrow a = drawn.get(i);
            E edge = drawnEdges.get(i);
            Path path = route(route, dx, dy);
            path.getStyleClass().add("graph-link");
            if (a.dashed()) path.getStyleClass().add("graph-link-dashed");
            path.setMouseTransparent(true);
            Polygon head = head(route, a.dashed(), dx, dy);
            head.setMouseTransparent(true);
            // An unseen wider line along the link takes its clicks: the drawn stroke is a pixel or two.
            Path hit = route(route, dx, dy);
            hit.setFill(null);
            hit.setStroke(Color.TRANSPARENT);
            hit.setStrokeWidth(HIT_WIDTH);
            hit.setCursor(Cursor.HAND);
            clickable(hit, edge);
            links.getChildren().addAll(hit, path, head);
            // A label node can have one parent: one shared by two edges rides on the first.
            if (a.label() != null && a.label().getParent() == null) {
                Node label = a.label();
                clickable(label, edge);
                labels.getChildren().add(label);
                label.applyCss();
                if (label instanceof Region region) region.autosize();
                Bounds b = label.getLayoutBounds();
                Pt middle = route.middle();
                label.relocate(middle.x() + dx - b.getWidth() / 2, middle.y() + dy - b.getHeight() / 2);
            }
        }
    }

    /** {@code route} as a path, moved by {@code (dx, dy)}. */
    private static Path route(Route route, double dx, double dy) {
        Path path = new Path();
        Pt start = route.start();
        path.getElements().add(new MoveTo(start.x() + dx, start.y() + dy));
        for (Segment segment : route.segments()) {
            Pt to = segment.to();
            if (segment instanceof Cubic c) {
                path.getElements().add(new CubicCurveTo(c.c1().x() + dx, c.c1().y() + dy,
                        c.c2().x() + dx, c.c2().y() + dy, to.x() + dx, to.y() + dy));
            } else {
                path.getElements().add(new LineTo(to.x() + dx, to.y() + dy));
            }
        }
        return path;
    }

    /** {@code node} names {@code edge}: a click on it is {@link #setOnEdgeClicked}'s, a right click its menu. */
    private void clickable(Node node, E edge) {
        edgeOf.put(node, edge);
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getButton() != MouseButton.PRIMARY || !e.isStillSincePress()) return;
            onEdgeClicked.accept(edge);
            e.consume();
        });
    }

    private static Polygon head(Route route, boolean dashed, double dx, double dy) {
        Pt tip = route.end();
        Pt from = route.headFrom();
        double angle = Math.atan2(tip.y() - from.y(), tip.x() - from.x());
        double x = tip.x() + dx;
        double y = tip.y() + dy;
        Polygon head = new Polygon(x, y,
                x - HEAD * Math.cos(angle - 0.45), y - HEAD * Math.sin(angle - 0.45),
                x - HEAD * Math.cos(angle + 0.45), y - HEAD * Math.sin(angle + 0.45));
        head.getStyleClass().add("graph-link-head");
        if (dashed) head.getStyleClass().add("graph-link-dashed");
        return head;
    }

    // --- grid ---

    private void paintGrid() {
        int step = (int) GRID_STEP;
        WritableImage tile = new WritableImage(step, step);
        tile.getPixelWriter().setColor(0, 0, gridInk());
        grid.setBackground(new Background(new BackgroundFill(
                new ImagePattern(tile, 0, 0, step, step, false), null, null)));
    }

    /** What the stylesheet says a grid dot is: a pattern tile takes a colour, never a style class. */
    private Color gridInk() {
        if (getScene() == null) return GRID_FALLBACK;
        gridInk.applyCss();
        Background background = gridInk.getBackground();
        if (background == null || background.getFills().isEmpty()) return GRID_FALLBACK;
        return background.getFills().getFirst().getFill() instanceof Color c ? c : GRID_FALLBACK;
    }

    // --- view ---

    private void installGestures() {
        // The wheel zooms, Ctrl or not: a right or middle drag pans (the user's call, 2026-10-15). Shift+wheel and a
        // sideways swipe still scroll.
        scroller.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isShiftDown() || e.getDeltaY() == 0 || Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY())
                    || onScrollBar(e)) return;
            zoomAbout(e.getX(), e.getY(), Math.pow(ZOOM_STEP, e.getDeltaY() / NOTCH));
            e.consume();
        });
        scroller.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.ESCAPE || selection.isEmpty()) return;
            selected.set(null);
            e.consume();
        });
        scroller.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            hideMenu();
            if (e.getButton() != MouseButton.SECONDARY && e.getButton() != MouseButton.MIDDLE) return;
            panning = true;
            panMoved = false;
            panX = e.getX();
            panY = e.getY();
            panH = scroller.getHvalue();
            panV = scroller.getVvalue();
            e.consume();
        });
        scroller.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!panning) return;
            if (Math.hypot(e.getX() - panX, e.getY() - panY) > CLICK_SLOP) panMoved = true;
            Bounds view = scroller.getViewportBounds();
            double spareW = content.getPrefWidth() * zoom.getX() - view.getWidth();
            double spareH = content.getPrefHeight() * zoom.getY() - view.getHeight();
            if (spareW > 0) scroller.setHvalue(Math.clamp(panH - (e.getX() - panX) / spareW, 0, 1));
            if (spareH > 0) scroller.setVvalue(Math.clamp(panV - (e.getY() - panY) / spareH, 0, 1));
            e.consume();
        });
        scroller.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!panning) return;
            panning = false;
            e.consume();
            if (!panMoved && e.getButton() == MouseButton.SECONDARY) showMenu(e);
        });
    }

    /** The menu of the card or link under a right click, when the plugin gives it one. */
    private void showMenu(MouseEvent e) {
        ContextMenu menu = null;
        for (Node at = e.getPickResult().getIntersectedNode(); at != null && at != this; at = at.getParent()) {
            E edge = edgeOf.get(at);
            if (edge != null) {
                menu = edgeMenu.apply(edge);
                break;
            }
            N node = nodeOfCell(at);
            if (node != null) {
                selected.set(node);
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

    /** Whether {@code e} is over one of the scroller's bars, which scroll rather than zoom. */
    private static boolean onScrollBar(ScrollEvent e) {
        for (Node at = e.getTarget() instanceof Node n ? n : null; at != null; at = at.getParent()) {
            if (at instanceof ScrollBar) return true;
        }
        return false;
    }

    /** The item whose card {@code cell} is, or null. */
    private N nodeOfCell(Node cell) {
        for (Map.Entry<String, StackPane> each : cellOf.entrySet()) {
            if (each.getValue() == cell) return byId.get(each.getKey());
        }
        return null;
    }

    /** Zooms in a step about the middle of the view: the + button a window puts beside {@link #fit()}. */
    public void zoomIn() {
        zoomAboutMiddle(BUTTON_STEP);
    }

    /** Zooms out a step about the middle of the view. */
    public void zoomOut() {
        zoomAboutMiddle(1 / BUTTON_STEP);
    }

    private void zoomAboutMiddle(double factor) {
        Bounds view = scroller.getViewportBounds();
        zoomAbout(view.getWidth() / 2, view.getHeight() / 2, factor);
    }

    /** Scales by {@code factor}, keeping the graph point under the viewport point {@code (vx, vy)} where it is. */
    private void zoomAbout(double vx, double vy, double factor) {
        double next = Math.clamp(zoom.getX() * factor, MIN_ZOOM, MAX_ZOOM);
        if (next == zoom.getX()) return;
        Pt anchor = viewToContent(vx, vy);
        setZoom(next);
        scrollSo(anchor, vx, vy);
    }

    private void setZoom(double next) {
        zoom.setX(next);
        zoom.setY(next);
        zoomLevel.set(next);
        scroller.layout();
        drawMinimapView();
    }

    /** The graph point at viewport point {@code (vx, vy)}; a graph smaller than the view sits in its middle. */
    private Pt viewToContent(double vx, double vy) {
        Bounds view = scroller.getViewportBounds();
        double z = zoom.getX();
        double w = content.getPrefWidth() * z;
        double h = content.getPrefHeight() * z;
        double left = Math.max(0, w - view.getWidth()) * scroller.getHvalue() - Math.max(0, view.getWidth() - w) / 2;
        double top = Math.max(0, h - view.getHeight()) * scroller.getVvalue() - Math.max(0, view.getHeight() - h) / 2;
        return new Pt((left + vx) / z, (top + vy) / z);
    }

    /** Scrolls so graph point {@code p} sits at viewport point {@code (vx, vy)}, as far as the scroll allows. */
    private void scrollSo(Pt p, double vx, double vy) {
        Bounds view = scroller.getViewportBounds();
        double z = zoom.getX();
        double spareW = content.getPrefWidth() * z - view.getWidth();
        double spareH = content.getPrefHeight() * z - view.getHeight();
        if (spareW > 0) scroller.setHvalue(Math.clamp((p.x() * z - vx) / spareW, 0, 1));
        if (spareH > 0) scroller.setVvalue(Math.clamp((p.y() * z - vy) / spareH, 0, 1));
    }

    /** Zooms so the whole graph shows, never above 1:1, and centres it. */
    public void fit() {
        Bounds view = scroller.getViewportBounds();
        if (view.getWidth() <= 0 || content.getPrefWidth() <= 0) return;
        double fit = Math.min(1, Math.min(view.getWidth() / content.getPrefWidth(),
                view.getHeight() / content.getPrefHeight()));
        setZoom(Math.max(MIN_ZOOM, fit));
        scroller.setHvalue(0.5);
        scroller.setVvalue(0.5);
    }

    /** Scrolls the card of the item with {@code itemId} into the middle of the view. */
    public void centreOn(String itemId) {
        Box box = placed.get(itemId);
        if (box == null) return;
        Bounds view = scroller.getViewportBounds();
        scrollSo(box.centre(), view.getWidth() / 2, view.getHeight() / 2);
    }

    private void markSelected() {
        cellOf.forEach((k, cell) -> cell.pseudoClassStateChanged(SELECTED, selection.contains(k)));
        drawMinimap();
    }

    // --- search ---

    /**
     * Marks the items {@code match} accepts, selects the first and brings it into view; a null {@code match}
     * clears the marks. Returns the matches, in the order {@link #show} listed them.
     */
    public List<N> find(Predicate<N> match) {
        matches = match == null ? List.of() : nodes.stream().filter(match).toList();
        matchAt = 0;
        markMatches();
        if (!matches.isEmpty()) bringIn(matches.getFirst());
        return matches;
    }

    /** A search field over {@code text}: typing marks the cards whose text holds it, Enter steps to the next. */
    public TextField searchField(Function<N, String> text) {
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

    private void bringIn(N node) {
        selected.set(node);
        centreOn(id.apply(node));
    }

    private void markMatches() {
        Set<String> keys = new HashSet<>();
        for (N match : matches) keys.add(id.apply(match));
        cellOf.forEach((k, cell) -> cell.pseudoClassStateChanged(MATCH, keys.contains(k)));
    }

    // --- minimap ---

    private Node buildMinimap() {
        minimap.getStyleClass().add("graph-minimap");
        minimap.setManaged(true);
        minimap.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        StackPane.setAlignment(minimap, Pos.BOTTOM_RIGHT);
        minimapView.getStyleClass().add("graph-minimap-view");
        minimapView.setMouseTransparent(true);
        minimap.setOnMousePressed(this::moveFromMinimap);
        minimap.setOnMouseDragged(this::moveFromMinimap);
        return minimap;
    }

    private double minimapScale() {
        double longest = Math.max(content.getPrefWidth(), content.getPrefHeight());
        return longest <= 0 ? 1 : MINIMAP_MAX / longest;
    }

    private void drawMinimap() {
        double m = minimapScale();
        minimap.setPrefSize(content.getPrefWidth() * m, content.getPrefHeight() * m);
        List<Node> dots = new ArrayList<>();
        placed.forEach((k, box) -> {
            Rectangle dot = new Rectangle(box.minX() * m, box.minY() * m,
                    Math.max(2, box.width() * m), Math.max(2, box.height() * m));
            dot.getStyleClass().add("graph-minimap-card");
            dot.pseudoClassStateChanged(SELECTED, selection.contains(k));
            dot.setMouseTransparent(true);
            dots.add(dot);
        });
        dots.add(minimapView);
        minimap.getChildren().setAll(dots);
        minimap.setVisible(!placed.isEmpty());
        drawMinimapView();
    }

    private void drawMinimapView() {
        Bounds view = scroller.getViewportBounds();
        double m = minimapScale();
        double z = zoom.getX();
        Pt corner = viewToContent(0, 0);
        minimapView.setX(Math.max(0, corner.x()) * m);
        minimapView.setY(Math.max(0, corner.y()) * m);
        minimapView.setWidth(Math.min(content.getPrefWidth(), view.getWidth() / z) * m);
        minimapView.setHeight(Math.min(content.getPrefHeight(), view.getHeight() / z) * m);
    }

    private void moveFromMinimap(MouseEvent e) {
        double m = minimapScale();
        Bounds view = scroller.getViewportBounds();
        scrollSo(new Pt(e.getX() / m, e.getY() / m), view.getWidth() / 2, view.getHeight() / 2);
        e.consume();
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
        Background background = content.getBackground();
        if (background == null || background.getFills().isEmpty()) return Color.WHITE;
        return background.getFills().getFirst().getFill();
    }
}
