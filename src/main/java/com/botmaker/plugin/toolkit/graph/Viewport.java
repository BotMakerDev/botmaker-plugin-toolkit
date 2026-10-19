package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Scale;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * {@link GraphCanvas}'s view of its content: the zoom, the scroll that pans it, and the conversions between the view's
 * pixels and the content's. The wheel zooms about the cursor and a right or middle drag pans.
 */
final class Viewport {

    private static final double MIN_ZOOM = 0.1;
    private static final double MAX_ZOOM = 2.5;
    private static final double ZOOM_STEP = 1.1;
    /** What a press of {@link #zoomIn()} or {@link #zoomOut()} scales by. */
    private static final double BUTTON_STEP = 1.25;
    /** A wheel's notch, in scroll pixels: a touchpad's many small scrolls add up to a step, not a step each. */
    private static final double NOTCH = 40;
    private static final Duration ZOOM_TIME = Duration.millis(250);

    private final Pane content;
    private final Scale zoom = new Scale(1, 1);
    private final ReadOnlyDoubleWrapper zoomLevel;
    private final StackPane holder;
    private final ScrollPane scroller;
    /** The zoom {@link #zoomTo} is easing, stopped by the next one. */
    private Timeline zooming;
    /** Told once the zoom changed and the scroller is laid out again: what the minimap redraws its view on. */
    private Runnable zoomed = () -> {};

    private boolean panMoved;
    private double panX;
    private double panY;
    private double panH;
    private double panV;
    private boolean panning;

    Viewport(Object bean, Pane content) {
        this.content = content;
        zoomLevel = new ReadOnlyDoubleWrapper(bean, "zoom", 1);
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
        holder = new StackPane(sized);
        scroller = new ScrollPane(holder);
        holder.minWidthProperty().bind(Bindings.createDoubleBinding(() -> scroller.getViewportBounds().getWidth(),
                scroller.viewportBoundsProperty()));
        holder.minHeightProperty().bind(Bindings.createDoubleBinding(() -> scroller.getViewportBounds().getHeight(),
                scroller.viewportBoundsProperty()));
        // Not pannable: ScrollPane pans on any button, which would steal a card's click. A right or middle drag
        // pans instead, below.
        scroller.setPannable(false);
        scroller.getStyleClass().add("graph-scroller");
    }

    ScrollPane scroller() {
        return scroller;
    }

    /** The pane under the content, as big as the view at least: a press on it is a press on the empty canvas. */
    Pane holder() {
        return holder;
    }

    double zoom() {
        return zoom.getX();
    }

    ReadOnlyDoubleProperty zoomProperty() {
        return zoomLevel.getReadOnlyProperty();
    }

    void onZoomed(Runnable zoomed) {
        this.zoomed = zoomed;
    }

    /**
     * The wheel zooms, Ctrl or not, and a right or middle drag pans (the user's call, 2026-10-15); Shift+wheel and a
     * sideways swipe still scroll. A right click that did not drag goes to {@code rightClick}.
     */
    void installGestures(Consumer<MouseEvent> rightClick) {
        scroller.addEventFilter(ScrollEvent.SCROLL, e -> {
            if (e.isShiftDown() || e.getDeltaY() == 0 || Math.abs(e.getDeltaX()) > Math.abs(e.getDeltaY())
                    || onScrollBar(e)) return;
            zoomAbout(e.getX(), e.getY(), Math.pow(ZOOM_STEP, e.getDeltaY() / NOTCH));
            e.consume();
        });
        scroller.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
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
            if (Math.hypot(e.getX() - panX, e.getY() - panY) > GraphCanvas.CLICK_SLOP) panMoved = true;
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
            if (!panMoved && e.getButton() == MouseButton.SECONDARY) rightClick.accept(e);
        });
    }

    /** Whether {@code e} is over one of the scroller's bars, which scroll rather than zoom. */
    private static boolean onScrollBar(ScrollEvent e) {
        for (Node at = e.getTarget() instanceof Node n ? n : null; at != null; at = at.getParent()) {
            if (at instanceof ScrollBar) return true;
        }
        return false;
    }

    void zoomIn() {
        zoomAboutMiddle(BUTTON_STEP);
    }

    void zoomOut() {
        zoomAboutMiddle(1 / BUTTON_STEP);
    }

    private void zoomAboutMiddle(double factor) {
        Bounds view = scroller.getViewportBounds();
        zoomAbout(view.getWidth() / 2, view.getHeight() / 2, factor);
    }

    /** Scales by {@code factor}, keeping the graph point under the viewport point {@code (vx, vy)} where it is. */
    private void zoomAbout(double vx, double vy, double factor) {
        stopZooming();
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
        zoomed.run();
    }

    /** The graph point at viewport point {@code (vx, vy)}; a graph smaller than the view sits in its middle. */
    Pt viewToContent(double vx, double vy) {
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

    /**
     * Scrolls so graph point {@code corner} is the view's top-left once the content has been resized: what keeps the
     * view still while the canvas grows or shrinks round a moved card.
     */
    void putCorner(Pt corner) {
        scroller.layout();
        scrollSo(corner, 0, 0);
        zoomed.run();
    }

    /** Zooms so the whole graph shows, never above 1:1, and centres it. */
    void fit() {
        stopZooming();
        Bounds view = scroller.getViewportBounds();
        if (view.getWidth() <= 0 || content.getPrefWidth() <= 0) return;
        double fit = Math.min(1, Math.min(view.getWidth() / content.getPrefWidth(),
                view.getHeight() / content.getPrefHeight()));
        setZoom(Math.max(MIN_ZOOM, fit));
        scroller.setHvalue(0.5);
        scroller.setVvalue(0.5);
    }

    /** Scrolls graph point {@code p} into the middle of the view. */
    void centreOn(Pt p) {
        Bounds view = scroller.getViewportBounds();
        scrollSo(p, view.getWidth() / 2, view.getHeight() / 2);
    }

    /** Zooms to {@code scale} with graph point {@code to} in the middle of the view, eased over a quarter second. */
    void zoomTo(Pt to, double scale) {
        stopZooming();
        Bounds view = scroller.getViewportBounds();
        Pt from = viewToContent(view.getWidth() / 2, view.getHeight() / 2);
        double was = zoom.getX();
        double is = Math.clamp(scale, MIN_ZOOM, MAX_ZOOM);
        DoubleProperty done = new SimpleDoubleProperty();
        done.addListener((o, before, now) -> {
            double f = now.doubleValue();
            // Scaled evenly: a zoom from far out does not rush its first frames.
            setZoom(was * Math.pow(is / was, f));
            Bounds shown = scroller.getViewportBounds();
            scrollSo(new Pt(from.x() + (to.x() - from.x()) * f, from.y() + (to.y() - from.y()) * f),
                    shown.getWidth() / 2, shown.getHeight() / 2);
        });
        zooming = new Timeline(new KeyFrame(ZOOM_TIME, new KeyValue(done, 1, Interpolator.EASE_BOTH)));
        zooming.play();
    }

    /** Stops {@link #zoomTo}'s easing where it is: a zoom or Fit of the user's own wins over it. */
    private void stopZooming() {
        if (zooming != null) zooming.stop();
        zooming = null;
    }
}
