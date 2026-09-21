package com.botmaker.plugin.toolkit;

import com.botmaker.plugin.api.Dialogs;
import com.botmaker.plugin.api.StudioServices;
import com.botmaker.plugin.api.slot.ValueContext;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The two windows an editor opens, themed and owned correctly without the plugin knowing how either is done.
 *
 * <p>A plugin <b>must not</b> build its own {@link Stage} and show it. Two things go wrong when it does, and
 * neither is visible to the author who did it: the window gets none of the host's stylesheet, so it is a
 * slab of default JavaFX in the middle of a dark application; and with no owner it is a top-level window, so
 * it falls behind the editor instead of over it and does not close with the project. Both are handled here,
 * through {@link com.botmaker.plugin.api.Theme} and {@link com.botmaker.plugin.api.Dialogs#owner()}, which is
 * the whole reason those two are on the contract.
 *
 * <p><b>Cancelling calls nothing.</b> Every callback here is invoked only on a real choice — the same rule
 * {@link com.botmaker.plugin.api.Capture} states, and for the same reason: an editor that writes a default
 * when the user backs out has just destroyed a value nobody asked it to touch.
 */
public final class Modals {

    private Modals() {}

    /**
     * Any body at all, in a themed and owned window with <i>OK</i> and <i>Cancel</i> under it.
     *
     * <p>The general case of the two below, and the reason it is public: an editor whose modal is a slider, a
     * grid with a search box, or four fields plus a checkbox has nowhere else to go, and the alternative is
     * the plugin building its own {@link Stage} — which is precisely what this class exists to stop. So the
     * shape is offered rather than the widget: the caller owns the body, the toolkit owns the window.
     *
     * <p>{@code onCommit} runs on <i>OK</i> and never on cancel; passing {@code null} gives a window with a
     * single <i>Close</i>, for a body that has already written whatever it was going to write. The
     * {@link Stage} is returned so a body that finishes on its own — a grid where clicking a picture is the
     * answer — can close it.
     */
    public static Stage form(ValueContext ctx, String title, Parent body, Runnable onCommit) {
        Stage stage = new Stage();
        Runnable commit = onCommit == null ? null : () -> {
            stage.close();
            onCommit.run();
        };
        VBox root = Styles.on(new VBox(10, Styles.on(new Label(title), Styles.DIALOG_HEADING), body,
                buttons(commit, stage::close)), Styles.DIALOG_COMPACT);
        show(ctx, stage, title, root);
        return stage;
    }

    /**
     * A row of labelled whole-number fields — {@code x}, {@code y}, {@code width}, {@code height} — with
     * <i>OK</i> and <i>Cancel</i>.
     *
     * <p>{@code onCommit} receives one value per label, in the same order, with anything unparsable read as
     * {@code 0}. It is not called if the user cancels or closes the window.
     */
    public static void numbers(ValueContext ctx, String title, String[] labels, int[] initial,
                               Consumer<int[]> onCommit) {
        String[] names = labels == null ? new String[0] : labels;
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        TextField[] fields = new TextField[names.length];
        for (int i = 0; i < names.length; i++) {
            fields[i] = Styles.on(new TextField(Integer.toString(
                    initial != null && i < initial.length ? initial[i] : 0)), Styles.INSET_FIELD);
            fields[i].setPrefColumnCount(5);
            VBox cell = new VBox(2, Styles.on(new Label(names[i]), Styles.CAPTION), fields[i]);
            row.getChildren().add(cell);
        }

        Stage stage = new Stage();
        Runnable commit = () -> {
            int[] out = new int[fields.length];
            for (int i = 0; i < fields.length; i++) out[i] = parseInt(fields[i].getText());
            stage.close();
            if (onCommit != null) onCommit.accept(out);
        };

        VBox root = Styles.on(new VBox(10, Styles.on(new Label(title), Styles.DIALOG_HEADING), row,
                buttons(commit, stage::close)), Styles.DIALOG_COMPACT);
        show(ctx, stage, title, root);
    }

    /**
     * A scrolling grid of pictures, one of which the user picks.
     *
     * <p>{@code emptyMessage} is shown in place of the grid when there is nothing to choose — which is a
     * normal state (no templates captured yet, no games found) and reads as a broken window if left blank.
     */
    public static void chooser(ValueContext ctx, String title, List<Thumbnail> items, String emptyMessage,
                               Consumer<Thumbnail> onChosen) {
        Stage stage = new Stage();
        Parent body;
        if (items == null || items.isEmpty()) {
            body = Styles.on(new Label(Values.labelOr(emptyMessage, "Nothing to choose from yet.")),
                    Styles.DIALOG_HINT);
        } else {
            FlowPane grid = new FlowPane(10, 10);
            for (Thumbnail item : items) {
                grid.getChildren().add(tile(item, () -> {
                    stage.close();
                    if (onChosen != null) onChosen.accept(item);
                }));
            }
            ScrollPane scroll = new ScrollPane(grid);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(360);
            body = scroll;
        }

        VBox root = Styles.on(new VBox(10, Styles.on(new Label(title), Styles.DIALOG_HEADING), body,
                buttons(null, stage::close)), Styles.DIALOG_COMPACT);
        show(ctx, stage, title, root);
    }

    /**
     * How a {@link #gallery} is presented — everything about it except what is in it.
     *
     * <p>A record because these five travel together and four of them are strings: a call site passing them
     * positionally would be five unlabelled arguments, three of which are sentences shown to a person.
     *
     * @param title        the window's title and its heading
     * @param coverWidth   the frame each picture is fitted into, in pixels — pictures keep their own ratio
     *                     inside it and are centred, so a landscape image in a portrait frame is letterboxed
     *                     rather than squashed. Library art is portrait (120×160); captured pictures are
     *                     usually square-ish (96×96)
     * @param coverHeight  the other half of that frame
     * @param emptyMessage shown instead of the grid when there is nothing — a normal state (nothing installed,
     *                     no pictures captured yet), which reads as a broken window if left blank
     * @param manualPrompt the prompt of a "…or type it yourself" row under the grid, or {@code null} for no
     *                     such row. What is typed arrives as a {@link Thumbnail} whose value and label are
     *                     both that text
     */
    public record Gallery(String title, double coverWidth, double coverHeight,
                          String emptyMessage, String manualPrompt) {

        /** A gallery of library cover art — portrait frames, a search box, and a manual-id row. */
        public static Gallery covers(String title, String emptyMessage, String manualPrompt) {
            return new Gallery(title, 120, 160, emptyMessage, manualPrompt);
        }

        /** A gallery of pictures taken off a screen — square frames, and no manual entry to offer. */
        public static Gallery pictures(String title, String emptyMessage) {
            return new Gallery(title, 96, 96, emptyMessage, null);
        }
    }

    /**
     * A searchable grid of pictures that is <b>scanned in the background</b>, with an optional row for typing
     * an answer the grid does not contain.
     *
     * <p>The three things it has that {@link #chooser} does not are the three every real library browser in
     * this application needed, each of which is a trap an editor author hits exactly once:
     *
     * <ul>
     *   <li><b>{@code items} is called off the JavaFX thread.</b> Listing an installed library or a folder of
     *       captured pictures walks the filesystem; doing that in {@code create} freezes the editor mid-draw,
     *       and the freeze is proportional to how much the user owns, so it never shows up while testing.</li>
     *   <li><b>A search box.</b> A grid is a fine way to choose among twenty things and a bad one among two
     *       hundred, which is an ordinary size for a library somebody has been adding to for years.</li>
     *   <li><b>A manual row.</b> The right answer is often not installed on the machine the bot is being
     *       written on. Without somewhere to type it, the editor is a worse way to say what the plain text
     *       field it replaced could already say.</li>
     * </ul>
     *
     * <p>{@code onChosen} runs on the JavaFX thread, on a real choice only — never on cancel, and never on an
     * empty manual row.
     */
    public static void gallery(ValueContext ctx, Gallery spec, Supplier<List<Thumbnail>> items,
                               Consumer<Thumbnail> onChosen) {
        double wrap = 4 * (spec.coverWidth() + 12) + 12;

        FlowPane grid = new FlowPane(12, 12);
        grid.setPrefWrapLength(wrap);

        Label status = Styles.on(new Label("Loading…"), Styles.DIALOG_HINT);
        ScrollPane scroll = new ScrollPane(status);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.max(240, 2.6 * spec.coverHeight()));

        List<Node> tiles = new ArrayList<>();
        List<Thumbnail> loaded = new ArrayList<>();
        TextField search = new TextField();
        search.setPromptText("Search…");
        search.textProperty().addListener((obs, was, query) -> filter(tiles, loaded, query));

        VBox body = new VBox(8, search, scroll);
        body.setPrefWidth(wrap + 36);

        Stage stage = form(ctx, spec.title(), body, null);
        Consumer<Thumbnail> pick = chosen -> {
            stage.close();
            if (onChosen != null) onChosen.accept(chosen);
        };

        if (spec.manualPrompt() != null) {
            TextField manual = new TextField();
            manual.setPromptText(spec.manualPrompt());
            HBox.setHgrow(manual, javafx.scene.layout.Priority.ALWAYS);
            Button use = new Button("Use");
            Runnable apply = () -> {
                String typed = manual.getText() == null ? "" : manual.getText().trim();
                if (!typed.isEmpty()) pick.accept(Thumbnail.of(typed, typed));
            };
            use.setOnAction(e -> apply.run());
            manual.setOnAction(e -> apply.run());
            HBox row = new HBox(8, manual, use);
            row.setAlignment(Pos.CENTER_LEFT);
            body.getChildren().add(row);
        }

        Thread scan = new Thread(() -> {
            List<Thumbnail> found = items == null ? List.of() : items.get();
            List<Thumbnail> safe = found == null ? List.<Thumbnail>of() : found;
            javafx.application.Platform.runLater(() -> {
                if (safe.isEmpty()) {
                    status.setText(Values.labelOr(spec.emptyMessage(), "Nothing to choose from yet."));
                    return;
                }
                for (Thumbnail item : safe) {
                    Node node = cover(item, spec, () -> pick.accept(item));
                    loaded.add(item);
                    tiles.add(node);
                    grid.getChildren().add(node);
                }
                scroll.setContent(grid);
            });
        }, "toolkit-gallery-scan");
        scan.setDaemon(true);
        scan.start();
    }

    /** Hides what does not match rather than rebuilding the grid — the pictures are already decoded. */
    private static void filter(List<Node> tiles, List<Thumbnail> items, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < tiles.size() && i < items.size(); i++) {
            boolean match = needle.isEmpty()
                            || items.get(i).label().toLowerCase(Locale.ROOT).contains(needle);
            tiles.get(i).setVisible(match);
            tiles.get(i).setManaged(match);
        }
    }

    /**
     * One cell of a {@link #gallery}: a picture in a fixed frame, its caption under it.
     *
     * <p>A picture with no image is its initials rather than an empty box — a cover that never downloaded is
     * an ordinary state, and initials still tell two of them apart at a glance.
     */
    private static Node cover(Thumbnail item, Gallery spec, Runnable onPicked) {
        StackPane frame = new StackPane();
        frame.setPrefSize(spec.coverWidth(), spec.coverHeight());
        frame.setMinSize(spec.coverWidth(), spec.coverHeight());
        frame.setMaxSize(spec.coverWidth(), spec.coverHeight());
        if (item.image() != null) {
            ImageView view = new ImageView(item.image());
            view.setPreserveRatio(true);
            view.setFitWidth(spec.coverWidth());
            view.setFitHeight(spec.coverHeight());
            frame.getChildren().add(view);
        } else {
            frame.getChildren().add(Styles.on(new Label(initials(item.label())), Styles.CAPTION_STRONG));
        }

        Label caption = Styles.on(new Label(item.label()), Styles.TILE_NAME);
        caption.setWrapText(true);
        caption.setMaxWidth(spec.coverWidth() + 12);
        caption.setAlignment(Pos.CENTER);

        VBox content = new VBox(6, frame, caption);
        content.setAlignment(Pos.TOP_CENTER);

        Button tile = Styles.on(new Button(), Styles.TILE);
        tile.setGraphic(content);
        tile.setPrefWidth(spec.coverWidth() + 12);
        tile.setOnAction(e -> onPicked.run());
        return tile;
    }

    /** Up to three initials — enough to tell two entries apart, short enough to stay on one line. */
    private static String initials(String label) {
        if (label == null || label.isBlank()) return "?";
        StringBuilder out = new StringBuilder();
        for (String word : label.trim().split("\\s+")) {
            if (!word.isEmpty()) out.append(Character.toUpperCase(word.charAt(0)));
            if (out.length() >= 3) break;
        }
        return out.toString();
    }

    /**
     * The native "choose a program" dialog, run <b>off</b> the JavaFX thread, falling back to JavaFX's own
     * file chooser where there is no native one.
     *
     * <p>Both halves are why this is here rather than left to each editor. A native dialog <em>blocks the
     * calling thread</em> until the user answers it, so opening one on the FX thread freezes the whole
     * application behind a window the user cannot see is theirs to answer — and it happens to work on the
     * platform where the native tool is missing, which is the worst way for a bug like that to be
     * distributed. {@link com.botmaker.plugin.api.Dialogs.Choice#nativeDialogShown()} is the fallback signal,
     * and answering it is a decision no editor should have to make twice.
     *
     * <p>{@code onChosen} runs on the JavaFX thread with a real path, and not at all if the user cancels.
     */
    public static void program(ValueContext ctx, Path initialDir, Consumer<Path> onChosen) {
        if (ctx == null || onChosen == null) return;
        Dialogs dialogs = ctx.services().dialogs();
        Thread worker = new Thread(() -> {
            Dialogs.Choice choice = dialogs.chooseProgram(initialDir);
            javafx.application.Platform.runLater(() -> {
                if (choice.nativeDialogShown()) {
                    choice.path().ifPresent(onChosen);
                } else {
                    dialogs.chooseFile("Choose a program to launch", initialDir).path().ifPresent(onChosen);
                }
            });
        }, "toolkit-program-chooser");
        worker.setDaemon(true);
        worker.start();
    }

    /** One picture-and-caption cell. A thumbnail with no image is its caption alone, never a blank box. */
    private static Button tile(Thumbnail item, Runnable onPicked) {
        VBox content = new VBox(4);
        content.setAlignment(Pos.CENTER);
        if (item.image() != null) {
            ImageView view = new ImageView(item.image());
            view.setPreserveRatio(true);
            view.setFitWidth(96);
            view.setFitHeight(96);
            content.getChildren().add(view);
        }
        content.getChildren().add(Styles.on(new Label(item.label()), Styles.TILE_NAME));

        Button tile = Styles.on(new Button(), Styles.TILE);
        tile.setGraphic(content);
        tile.setOnAction(e -> onPicked.run());
        return tile;
    }

    /** <i>Cancel</i> on the left of <i>OK</i>, right-aligned; {@code onOk} null means there is no OK. */
    private static HBox buttons(Runnable onOk, Runnable onCancel) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox bar = new HBox(8, spacer);
        Button cancel = new Button(onOk == null ? "Close" : "Cancel");
        cancel.setOnAction(e -> onCancel.run());
        bar.getChildren().add(cancel);
        if (onOk != null) {
            Button ok = Styles.on(new Button("OK"), Styles.PRIMARY_BUTTON);
            ok.setDefaultButton(true);
            ok.setOnAction(e -> onOk.run());
            bar.getChildren().add(ok);
        }
        bar.setAlignment(Pos.CENTER_RIGHT);
        return bar;
    }

    /** Themed, owned by the editor's window, modal to it, and shown without blocking the caller. */
    private static void show(ValueContext ctx, Stage stage, String title, Parent root) {
        StudioServices services = ctx == null ? null : ctx.services();
        stage.setTitle(title);
        stage.setScene(services == null ? new javafx.scene.Scene(root) : services.theme().scene(root));
        if (services != null) {
            javafx.stage.Window owner = services.dialogs().ownerWindow().orElse(null);
            if (owner != null) {
                stage.initOwner(owner);
                stage.initModality(Modality.WINDOW_MODAL);
            }
            services.theme().apply(stage);
        }
        stage.show();
    }

    /** Whatever was typed as a whole number, or {@code 0} — see {@link Values} on why nothing throws here. */
    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
