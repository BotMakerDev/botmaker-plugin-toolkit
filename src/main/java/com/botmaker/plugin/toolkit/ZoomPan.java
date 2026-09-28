package com.botmaker.plugin.toolkit;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

/**
 * Ctrl+scroll zoom about the cursor and middle-drag pan for a frozen-frame surface: a {@link Scale} /
 * {@link Translate} pair on the content {@link Group}, so every coordinate the caller works in stays unscaled
 * and only {@link #toContent(MouseEvent)} knows the zoom exists.
 *
 * <p>It arrived here by the shape-versus-vocabulary rule, having been written in Studio and then held in the
 * SDK plugin for two slices. It names no capture target, no colour and nothing of any plugin's API: it is a
 * gesture over a {@link Pane} and a {@link Group}, which is exactly what this module is for. Both of its
 * callers are surfaces showing a frozen frame the user has to point at with single-pixel accuracy — the case
 * where a 1:1 view is not enough — and a second plugin drawing such a surface would otherwise spell the
 * gesture again with slightly different clamps.
 *
 * <p><b>Why filters and not handlers.</b> The gesture is installed as event <em>filters</em>, which run before
 * any handler on the same pane and {@link javafx.event.Event#consume() consume} what they use. A caller can
 * therefore keep its own {@code setOnMousePressed} exactly as it was and never learn that panning exists — a
 * middle-drag simply never reaches it. Registering handlers instead would leave the caller guarding every
 * branch against a pan in flight.
 *
 * <p>Only presses and scrolls that land <em>inside the content group</em> gesture, so a control bar sharing the
 * pane keeps its own clicks and wheel events.
 */
public final class ZoomPan {

    /** Below this the frame is smaller than the surface and there is nothing left to see. */
    private static final double MIN_ZOOM = 0.4;
    /** Deliberately high: picking a single pixel out of a game frame is the point of zooming at all. */
    private static final double MAX_ZOOM = 8.0;
    private static final double ZOOM_STEP = 1.1;

    private final Group layers;
    private final Scale scale = new Scale(1, 1);
    private final Translate pan = new Translate();
    private final ReadOnlyDoubleWrapper zoom = new ReadOnlyDoubleWrapper(this, "zoom", 1);

    private boolean panning;
    private double panStartX, panStartY, panOriginX, panOriginY;

    private ZoomPan(Pane pane, Group layers) {
        this.layers = layers;
        // Order matters: local->parent applies the list front-to-back, so a point is scaled and then translated.
        layers.getTransforms().addAll(pan, scale);
        installFilters(pane);
    }

    /** Installs the gesture on {@code pane}, transforming {@code layers} (which must be a child of it). */
    public static ZoomPan attach(Pane pane, Group layers) {
        return new ZoomPan(pane, layers);
    }

    private void installFilters(Pane pane) {
        pane.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.MIDDLE || !overContent(e.getTarget())) return;
            panning = true;
            panStartX = e.getX();
            panStartY = e.getY();
            panOriginX = pan.getX();
            panOriginY = pan.getY();
            e.consume();
        });
        pane.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            if (!panning) return;
            pan.setX(panOriginX + e.getX() - panStartX);
            pan.setY(panOriginY + e.getY() - panStartY);
            e.consume();
        });
        pane.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            if (!panning) return;
            panning = false;
            e.consume();
        });
        pane.addEventFilter(ScrollEvent.SCROLL, e -> {
            // Ctrl-gated: a bare wheel event on a touchpad is easy to fire by accident mid-drag.
            if (!e.isControlDown() || e.getDeltaY() == 0 || !overContent(e.getTarget())) return;
            zoomAt(e.getX(), e.getY(), e.getDeltaY() > 0 ? ZOOM_STEP : 1 / ZOOM_STEP);
            e.consume();
        });
    }

    /** Whether {@code target} is the content group or lives inside it — a control bar in the pane is not. */
    private boolean overContent(Object target) {
        for (Node n = (target instanceof Node node) ? node : null; n != null; n = n.getParent()) {
            if (n == layers) return true;
        }
        return false;
    }

    /**
     * A mouse point in pane coordinates, expressed in the unscaled content space the caller works in — so a
     * caller multiplying by its own image scale still lands on the right pixel at any zoom.
     */
    public Point2D toContent(MouseEvent e) {
        return layers.parentToLocal(e.getX(), e.getY());
    }

    /** Scales by {@code factor} about the pane point {@code (px, py)}, so the pixel under the cursor stays put. */
    private void zoomAt(double px, double py, double factor) {
        double next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, scale.getX() * factor));
        if (next == scale.getX()) return;
        Point2D anchor = layers.parentToLocal(px, py);
        scale.setX(next);
        scale.setY(next);
        pan.setX(px - next * anchor.getX());
        pan.setY(py - next * anchor.getY());
        zoom.set(next);
    }

    /** Back to 1:1, unpanned. */
    public void reset() {
        scale.setX(1);
        scale.setY(1);
        pan.setX(0);
        pan.setY(0);
        zoom.set(1);
    }

    public double zoom() {
        return zoom.get();
    }

    /** The current scale, for a percentage readout or for keeping overlay hairlines one pixel wide. */
    public ReadOnlyDoubleProperty zoomProperty() {
        return zoom.getReadOnlyProperty();
    }
}
