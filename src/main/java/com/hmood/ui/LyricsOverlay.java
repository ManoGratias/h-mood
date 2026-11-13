package com.hmood.ui;

import com.hmood.app.AppContext;
import com.hmood.model.Track;
import com.hmood.music.PlaybackMode;
import com.hmood.music.service.LyricsService;
import com.hmood.music.service.LyricsService.Line;
import com.hmood.music.service.LyricsService.Lyrics;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

/**
 * Écran « Paroles » plein cadre : pochette floutée en fond, grande pochette, paroles synchronisées qui défilent
 * (ligne en cours mise en avant, clic sur une ligne = aller à ce moment du morceau).
 */
public final class LyricsOverlay extends StackPane {

    private final AppContext ctx;
    private final Runnable onClose;
    private final ImageView backdrop = new ImageView();
    private final VBox info = new VBox(14);
    private final VBox linesBox = new VBox(14);
    private final ScrollPane scroll = new ScrollPane(linesBox);
    private final List<Label> labels = new ArrayList<>();
    private List<Line> lines = List.of();
    private int current = -2;
    private int request;
    private Timeline scrollAnim;
    private String glow = "#ffffff";

    private final ChangeListener<Number> positionListener = (o, a, v) -> highlight(v.longValue());
    private final ChangeListener<Track> trackListener = (o, a, t) -> load(t);

    public LyricsOverlay(AppContext ctx, Runnable onClose) {
        this.ctx = ctx;
        this.onClose = onClose;
        getStyleClass().add("lyrics-overlay");

        backdrop.setPreserveRatio(false);
        backdrop.fitWidthProperty().bind(widthProperty().add(160));
        backdrop.fitHeightProperty().bind(heightProperty().add(160));
        backdrop.setEffect(new GaussianBlur(63));
        backdrop.setOpacity(0.55);
        backdrop.setManaged(false);
        backdrop.layoutXProperty().set(-80);
        backdrop.layoutYProperty().set(-80);
        Region shade = new Region();
        shade.getStyleClass().add("lyrics-shade");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        info.setAlignment(Pos.CENTER_LEFT);
        info.setMinWidth(300);
        info.setMaxWidth(340);

        linesBox.getStyleClass().add("lyrics-lines");
        linesBox.setPadding(new Insets(220, 20, 260, 10));
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        HBox.setHgrow(scroll, Priority.ALWAYS);

        HBox body = new HBox(56, info, scroll);
        body.setAlignment(Pos.CENTER_LEFT);
        body.setPadding(new Insets(30, 60, 20, 60));

        Button close = new Button("✕  Fermer");
        close.getStyleClass().add("secondary-button");
        close.setOnAction(e -> close());
        StackPane.setAlignment(close, Pos.TOP_RIGHT);
        StackPane.setMargin(close, new Insets(18, 24, 0, 0));

        javafx.scene.Group backdropGroup = new javafx.scene.Group(backdrop);
        backdropGroup.setManaged(false); // le fond flouté déborde volontairement : il ne doit pas agrandir l'écran
        setMinSize(0, 0);
        setPrefSize(0, 0); // occupe exactement la zone centrale, sans la pousser
        body.setMinSize(0, 0);
        getChildren().addAll(backdropGroup, shade, body, close);
        setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) close();
        });
        setFocusTraversable(true);

        ctx.player().positionMsProperty().addListener(positionListener);
        ctx.player().currentProperty().addListener(trackListener);
        load(ctx.player().getCurrent());
    }

    public void close() {
        ctx.player().positionMsProperty().removeListener(positionListener);
        ctx.player().currentProperty().removeListener(trackListener);
        if (scrollAnim != null) scrollAnim.stop();
        onClose.run();
    }

    private void load(Track t) {
        int id = ++request;
        labels.clear();
        lines = List.of();
        current = -2;
        linesBox.getChildren().clear();
        scroll.setVvalue(0);
        buildInfo(t, null);
        if (t == null) {
            linesBox.getChildren().add(message("Lance un morceau pour afficher ses paroles."));
            return;
        }
        Image image = Covers.image(t.coverPath());
        backdrop.setImage(image);
        if (image != null) {
            Runnable palette = () -> {
                Color[] colors = CoverPalette.extract(image);
                if (colors != null) glow = toHex(colors[0]);
            };
            if (image.getProgress() >= 1) palette.run();
            else image.progressProperty().addListener((o, a, p) -> {
                if (p.doubleValue() >= 1) palette.run();
            });
        }
        boolean preview = ctx.musicPlayback().info(t).map(e -> e.playback() == PlaybackMode.PREVIEW).orElse(false);
        linesBox.getChildren().add(message("Recherche des paroles…"));
        Async.run(() -> ctx.lyrics().find(t), (lyrics, error) -> {
            if (id != request) return;
            linesBox.getChildren().clear();
            if (error != null) {
                linesBox.getChildren().add(message("Paroles indisponibles : " + error));
                return;
            }
            show(t, lyrics, preview);
        });
    }

    private void show(Track t, Lyrics lyrics, boolean preview) {
        String source = lyrics.source();
        boolean localTrack = !t.isRemote();
        if (LyricsService.NEEDS_ARTIST.equals(source)) {
            buildInfo(t, null);
            linesBox.getChildren().add(message("Paroles introuvables d'après le titre seul."));
            linesBox.getChildren().add(infoForm(t, "Le titre est peut-être mal orthographié, ou plusieurs chansons portent ce nom. "
                    + "Corrige le titre ou indique l'artiste."));
            return;
        }
        if (lyrics.instrumental()) {
            buildInfo(t, "Source : " + source);
            linesBox.getChildren().add(message("♪  Morceau instrumental"));
            return;
        }
        if (lyrics.isEmpty()) {
            buildInfo(t, null);
            linesBox.getChildren().add(message("Aucune parole trouvée sur " + source + " pour ce titre."));
            if (localTrack) {
                linesBox.getChildren().add(infoForm(t, "Titre ou artiste mal écrit ? Corrige-les et relance la recherche.\n"
                        + "Tu peux aussi placer un fichier .lrc du même nom à côté du morceau."));
            }
            return;
        }
        if (lyrics.synced() && !preview) {
            buildInfo(t, "Paroles synchronisées · " + source + "\nClique sur une ligne pour y aller.");
            addMatchedNote(t, lyrics);
            lines = lyrics.lines();
            for (Line line : lines) {
                Label l = new Label(line.text().isBlank() ? "♪" : line.text());
                l.getStyleClass().addAll("lyric-line", "lyric-future");
                l.setWrapText(true);
                l.setOnMouseClicked(e -> seekTo(line.timeMs()));
                labels.add(l);
                linesBox.getChildren().add(l);
            }
            highlight(ctx.player().positionMsProperty().get());
        } else {
            buildInfo(t, preview
                    ? "Paroles complètes · " + source + "\nExtrait 30 s : la synchronisation n'est possible qu'avec le morceau complet."
                    : "Paroles non synchronisées · " + source);
            addMatchedNote(t, lyrics);
            String text = lyrics.plain() != null ? lyrics.plain()
                    : String.join("\n", lyrics.lines().stream().map(Line::text).toList());
            Label plain = new Label(text);
            plain.getStyleClass().add("lyric-plain");
            plain.setWrapText(true);
            linesBox.setPadding(new Insets(20, 20, 60, 10));
            linesBox.getChildren().add(plain);
            return;
        }
        linesBox.setPadding(new Insets(220, 20, 260, 10));
    }

    /** Paroles reconnues d'après le titre seul : on dit quelle chanson a été retenue, avec un moyen de corriger. */
    private void addMatchedNote(Track t, Lyrics lyrics) {
        if (lyrics.matchedSong() == null) return;
        Label note = Ui.label("Reconnu d'après le titre : « " + lyrics.matchedSong() + " »."
                + (lyrics.synced() ? "" : "\nSynchronisation impossible : ton fichier est une autre version."), "lyrics-note");
        note.setWrapText(true);
        Button wrong = new Button("Pas la bonne chanson ?");
        wrong.getStyleClass().add("chip-button");
        wrong.setMnemonicParsing(false);
        wrong.setOnAction(e -> {
            linesBox.getChildren().setAll(message("Corrige le titre ou indique l'artiste :"),
                    infoForm(t, "La recherche se fera avec ces informations."));
            linesBox.setPadding(new Insets(20, 20, 60, 10));
            labels.clear();
            lines = List.of();
        });
        info.getChildren().addAll(note, wrong);
    }

    /** Formulaire pour corriger titre et artiste d'un morceau importé, puis relancer la recherche. */
    private Node infoForm(Track t, String hint) {
        TextField artist = new TextField(LyricsService.hasArtist(t) ? t.artist() : "");
        artist.setPromptText("Artiste (facultatif)");
        artist.getStyleClass().add("search-field");
        TextField title = new TextField(LyricsService.searchTitle(t));
        title.setPromptText("Titre");
        title.getStyleClass().add("search-field");
        Button save = new Button("Enregistrer et chercher les paroles");
        save.getStyleClass().add("primary-button");
        save.setMnemonicParsing(false);
        Label note = new Label(hint + "\nLe titre et l'artiste sont corrigés dans H MOOD, le fichier n'est pas modifié.");
        note.getStyleClass().add("muted");
        note.setWrapText(true);
        Runnable submit = () -> {
            if (title.getText().isBlank()) {
                ctx.notify("Indique le titre.");
                return;
            }
            try {
                Track updated = ctx.library().setInfo(t, title.getText(), artist.getText());
                ctx.player().refreshTrack(updated); // recharge les paroles si c'est le morceau en cours
                ctx.bumpLibrary();
                if (ctx.player().getCurrent() == null || ctx.player().getCurrent().id() != updated.id()) load(updated);
            } catch (RuntimeException e) {
                ctx.notify(e.getMessage());
            }
        };
        save.setOnAction(e -> submit.run());
        artist.setOnAction(e -> submit.run());
        title.setOnAction(e -> submit.run());
        VBox form = new VBox(10, artist, title, save, note);
        form.setMaxWidth(460);
        form.setPadding(new Insets(10, 0, 0, 0));
        javafx.application.Platform.runLater(artist::requestFocus);
        return form;
    }

    private void highlight(long positionMs) {
        if (lines.isEmpty()) return;
        int index = LyricsService.indexAt(lines, positionMs + 250); // léger avance : la ligne s'allume au bon moment
        if (index == current) return;
        current = index;
        for (int i = 0; i < labels.size(); i++) {
            Label l = labels.get(i);
            l.getStyleClass().removeAll("lyric-past", "lyric-current", "lyric-future");
            l.getStyleClass().add(i < index ? "lyric-past" : i == index ? "lyric-current" : "lyric-future");
            l.setStyle(i == index ? "-fx-effect: dropshadow(gaussian, " + glow + ", 26, 0.25, 0, 0);" : null);
        }
        if (index >= 0) centerOn(labels.get(index));
    }

    private void centerOn(Node node) {
        double contentH = linesBox.getHeight();
        double viewH = scroll.getViewportBounds().getHeight();
        if (contentH <= viewH || viewH <= 0) return;
        double y = node.getBoundsInParent().getMinY() + node.getBoundsInParent().getHeight() / 2 - viewH * 0.42;
        double target = Math.max(0, Math.min(1, y / (contentH - viewH)));
        if (scrollAnim != null) scrollAnim.stop();
        scrollAnim = new Timeline(new KeyFrame(Duration.millis(420),
                new KeyValue(scroll.vvalueProperty(), target, Interpolator.EASE_BOTH)));
        scrollAnim.play();
    }

    private void seekTo(long ms) {
        long duration = ctx.player().durationMsProperty().get();
        if (duration > 0) ctx.player().seek(Math.min(1, ms / (double) duration));
    }

    private void buildInfo(Track t, String note) {
        info.getChildren().clear();
        if (t == null) return;
        Node cover = Covers.of(t, 300);
        cover.getStyleClass().add("lyrics-cover");
        Label title = Ui.label(t.title(), "lyrics-title");
        title.setWrapText(true);
        Label artist = Ui.label(t.displayArtist(), "lyrics-artist");
        artist.setWrapText(true);
        info.getChildren().addAll(cover, title, artist);
        if (note != null) {
            Label n = Ui.label(note, "lyrics-note");
            n.setWrapText(true);
            info.getChildren().add(n);
        }
    }

    private static Label message(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("lyric-message");
        l.setWrapText(true);
        return l;
    }

    static String toHex(Color c) {
        return String.format("#%02X%02X%02X", (int) Math.round(c.getRed() * 255), (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
