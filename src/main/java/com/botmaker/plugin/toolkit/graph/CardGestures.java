package com.botmaker.plugin.toolkit.graph;

import com.botmaker.plugin.toolkit.graph.GraphLayout.Box;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Placement;
import com.botmaker.plugin.toolkit.graph.GraphLayout.Pt;
import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link GraphCanvas}'s left-button gestures: a click on a card selects it (Ctrl or Shift adds it), a double-click
 * opens it, a drag moves it or every selected card, and a drag on the empty canvas selects what its box touches.
 */
final class CardGestures<N> {

    /** What a gesture reads off the canvas and hands back to it. */
    interface Board<N> {

        /** Where the cards are drawn now, before the drag. */
        Placement placement();

        /** Each shown card's box on the canvas. */
        Map<String, Box> placed();

        /** Draws one frame of a drag: the cards and their links where {@code at} puts them. */
        void drawFrame(Placement at);

        /** A drag let go: the corner each moved card was left at. */
        void dropped(Map<String, Pt> moved);

        /** A card double-clicked. */
        void open(N node);
    }

    private final Pane content;
    private final Viewport viewport;
    private final Selection<N> selection;
    private final Board<N> board;
    private final Rectangle band = new Rectangle();

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
            board.drawFrame(frame);
        }
    };
    /** A box drag on the empty canvas: where it was pressed, and whether it adds to the selection. */
    private Pt bandFrom;
    private boolean bandAdds;

    CardGestures(Pane content, Viewport viewport, Selection<N> selection, Board<N> board) {
        this.content = content;
        this.viewport = viewport;
        this.selection = selection;
        this.board = board;
        band.getStyleClass().add("graph-band");
        band.setManaged(false);
        band.setMouseTransparent(true);
        band.setVisible(false);
    }

    /** The selection box, drawn over the cards. */
    Rectangle band() {
        return band;
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

    // --- a card ---

    /** Makes {@code cell}, the card of {@code node} whose id is {@code key}, take the left button's gestures. */
    void wire(Pane cell, String key, N node) {
        cell.setOnMousePressed(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            viewport.scroller().requestFocus();
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
                selection.toggle(key);
                return;
            }
            selection.only(key);
            if (e.getClickCount() == 2) board.open(node);
        });
    }

    private void startDrag(String key, MouseEvent e) {
        dragFrom = local(e);
        dragged = null;
        gestured = false;
        Placement placement = board.placement();
        Set<String> moving = selection.contains(key) && !adds(e) ? selection.ids() : Set.of(key);
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
            if (Math.hypot(dx, dy) * viewport.zoom() <= GraphCanvas.CLICK_SLOP) return;
            gestured = true;
            // A card dragged that was not selected is selected alone, the way a click would.
            if (dragStart.size() == 1) {
                String only = dragStart.keySet().iterator().next();
                if (!selection.contains(only)) selection.only(only);
            }
        }
        Placement placement = board.placement();
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
        board.dropped(moved);
    }

    // --- the selection box ---

    /** Makes a left drag on {@code holder}, the empty canvas, draw the selection box, and a click there clear it. */
    void installBand(Pane holder) {
        holder.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            viewport.scroller().requestFocus();
            bandFrom = local(e);
            bandAdds = adds(e);
            gestured = false;
        });
        holder.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (bandFrom == null) return;
            Box box = bandBox(local(e));
            if (!band.isVisible() && Math.max(box.width(), box.height()) * viewport.zoom() <= GraphCanvas.CLICK_SLOP) {
                return;
            }
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
            List<String> touched = new ArrayList<>();
            board.placed().forEach((k, card) -> {
                if (card.overlaps(box)) touched.add(k);
            });
            selection.boxed(touched, bandAdds);
            e.consume();
        });
        holder.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            // A click on the empty canvas, not the end of a box: nothing selected.
            if (e.getButton() == MouseButton.PRIMARY && !gestured && e.isStillSincePress() && !adds(e)) {
                selection.set(null);
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
}
