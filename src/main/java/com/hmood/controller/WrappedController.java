package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.repository.WrappedRepository.Item;
import com.hmood.repository.WrappedRepository.TopTrack;
import com.hmood.repository.WrappedRepository.Wrapped;
import com.hmood.ui.Async;
import com.hmood.ui.Covers;
import com.hmood.ui.Icons;
import com.hmood.ui.Logo;
import com.hmood.ui.Ui;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Scale;
import javafx.stage.FileChooser;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Page « Mon bilan » : H MOOD Wrapped du mois, format story 9:16 exportable en PNG 1080 × 1920. */
public final class WrappedController implements PageController {

    private static final double W = 405, H = 720; // 9:16 ; export ×(1080/405)
    private static final Locale FR = Locale.FRENCH;

    @FXML private HBox body;
    private final AppContext ctx;
    private final List<Image> pendingImages = new ArrayList<>();
    private StackPane story;
    private Wrapped current;

    public WrappedController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        List<YearMonth> months;
        try {
            months = ctx.wrapped().months();
        } catch (RuntimeException e) {
            body.getChildren().setAll(Ui.emptyState("Bilan indisponible", e.getMessage()));
            return;
        }
        ComboBox<YearMonth> picker = new ComboBox<>();
        picker.getItems().setAll(months);
        picker.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(YearMonth m) { return m == null ? "" : monthLabel(m); }
            @Override public YearMonth fromString(String s) { return null; }
        });
        Button export = new Button("Exporter l'image (story)", Icons.icon(Icons.DOWNLOAD, 16));
        export.getStyleClass().add("primary-button");
        export.setOnAction(e -> export());
        VBox side = new VBox(14, Ui.label("MOIS", "section-label"), picker, export,
                Ui.wrapped("Image 1080 × 1920, prête pour Instagram, Snapchat ou WhatsApp.", "muted"),
                Ui.wrapped("Les écoutes sont comptées dès 10 secondes, pour tes fichiers comme pour les titres en streaming "
                        + "(Deezer, Apple, Audius, Jamendo). Les humeurs viennent du Scanner d'humeur.", "muted"));
        side.setMinWidth(240);
        side.setMaxWidth(260);
        StackPane holder = new StackPane();
        holder.setAlignment(Pos.TOP_LEFT);
        body.getChildren().setAll(side, holder);
        picker.valueProperty().addListener((o, a, m) -> show(m, holder, export));
        picker.setValue(months.get(0));
    }

    private void show(YearMonth month, StackPane holder, Button export) {
        holder.getChildren().setAll(Ui.muted("Calcul du bilan…"));
        Async.run(() -> ctx.wrapped().month(month), (w, error) -> {
            if (error != null) {
                holder.getChildren().setAll(Ui.emptyState("Bilan indisponible", error));
                return;
            }
            current = w;
            if (w.isEmpty()) {
                story = null;
                export.setDisable(true);
                holder.getChildren().setAll(Ui.emptyState("Pas encore d'écoutes en " + monthLabel(month),
                        "Écoute de la musique dans H MOOD : ton bilan se remplira tout seul au fil du mois."));
                return;
            }
            export.setDisable(false);
            story = buildStory(w);
            holder.getChildren().setAll(story);
            Ui.animateIn(story);
            String demoExport = System.getProperty("hmood.wrapped.export"); // démo : export direct sans boîte de dialogue
            if (demoExport != null) {
                PauseTransition wait = new PauseTransition(Duration.seconds(2));
                wait.setOnFinished(e -> whenImagesReady(0, () -> {
                    try {
                        write(story, new File(demoExport));
                    } catch (IOException ex) {
                        ctx.notify("Export impossible : " + ex.getMessage());
                    }
                }));
                wait.play();
            }
        });
    }

    // ------------------------------------------------------------------ carte story

    private StackPane buildStory(Wrapped w) {
        pendingImages.clear();
        Mood mood = w.moods().isEmpty() ? null : Mood.fromName(w.moods().get(0).label()).orElse(null);
        String a = mood != null ? mood.colorA() : "#45BFEF", b = mood != null ? mood.colorB() : "#8E54E9";

        VBox col = new VBox(8);
        col.setPadding(new Insets(24, 24, 18, 24));

        HBox brand = new HBox(10, Logo.create(30), new VBox(-2, text("H MOOD", 17, true, 1),
                text("WRAPPED · " + monthLabel(w.month()).toUpperCase(FR), 11, true, 0.8)));
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox minutes = new VBox(-8, text(formatInt(Math.round(w.minutes())), 50, true, 1),
                text("minutes de musique", 14, false, 0.9));

        HBox numbers = new HBox(10, chip(formatInt(w.plays()), "écoutes"), chip(formatInt(w.artists()), "artistes"),
                chip(formatInt(w.tracks()), "titres"));

        col.getChildren().addAll(brand, minutes, numbers);

        if (mood != null) {
            int times = (int) w.moods().get(0).value();
            Label label = text(mood.label(), 22, true, 1);
            Label count = text("choisie " + times + " fois", 11, false, 0.8);
            HBox line = new HBox(10, label, count);
            line.setAlignment(Pos.BASELINE_LEFT);
            col.getChildren().add(block("TON HUMEUR DU MOIS", line));
        }
        if (!w.topArtists().isEmpty()) {
            Item top = w.topArtists().get(0);
            StackPane avatar = new StackPane(Icons.icon(Icons.MUSIC_NOTE, 22));
            avatar.setMinSize(46, 46);
            avatar.setMaxSize(46, 46);
            avatar.setStyle("-fx-background-color: rgba(255,255,255,0.18); -fx-background-radius: 23;");
            loadArtistPicture(top.label(), avatar);
            VBox names = new VBox(0, text(top.label(), 20, true, 1), text((int) top.value() + " écoutes"
                    + (w.topArtists().size() > 1 ? " · puis " + w.topArtists().get(1).label() : ""), 11, false, 0.8));
            HBox artist = new HBox(12, avatar, names);
            artist.setAlignment(Pos.CENTER_LEFT);
            col.getChildren().add(block("TON ARTISTE N°1", artist));
        }
        VBox tracks = new VBox(3);
        int rank = 1;
        for (TopTrack t : w.topTracks()) {
            Node cover = cover(t.cover());
            VBox titles = new VBox(-2, text(rank + ". " + t.title(), 12.5, true, 1), text(t.artist() + " · " + t.plays() + "×", 10, false, 0.75));
            titles.setMinWidth(0);
            HBox row = new HBox(10, cover, titles);
            row.setAlignment(Pos.CENTER_LEFT);
            tracks.getChildren().add(row);
            rank++;
        }
        col.getChildren().add(block("TES TITRES DU MOIS", tracks));

        List<String> facts = new ArrayList<>();
        if (!w.genres().isEmpty()) facts.add("Genre : " + w.genres().get(0).label());
        if (!w.dayParts().isEmpty()) facts.add("Tu écoutes surtout " + w.dayParts().get(0).label());
        if (w.bestDay() != null) {
            LocalDate d = LocalDate.parse(w.bestDay());
            facts.add("Jour record : " + d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.FULL, FR)
                    + " (" + Math.round(w.bestDayMinutes()) + " min)");
        }
        if (!facts.isEmpty()) {
            VBox f = new VBox(0);
            for (String s : facts) f.getChildren().add(text(s, 11.5, false, 0.92));
            col.getChildren().add(f);
        }
        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        col.getChildren().addAll(grow, text("Ressens ton humeur. Vis ta musique.", 11, false, 0.75));

        Region bg = new Region();
        bg.setStyle("-fx-background-color: linear-gradient(to bottom right, " + a + ", " + b + "), "
                + "radial-gradient(center 85% 10%, radius 60%, rgba(255,255,255,0.28), transparent), "
                + "linear-gradient(to bottom, transparent 55%, rgba(0,0,0,0.38));");
        StackPane card = new StackPane(bg, col);
        card.setAlignment(Pos.TOP_LEFT);
        card.setMinSize(W, H);
        card.setPrefSize(W, H);
        card.setMaxSize(W, H);
        Rectangle clip = new Rectangle(W, H);
        clip.setArcWidth(36);
        clip.setArcHeight(36);
        card.setClip(clip);
        card.setEffect(new javafx.scene.effect.DropShadow(40, Color.rgb(0, 0, 0, 0.45)));
        return card;
    }

    private Node block(String title, Node... content) {
        VBox box = new VBox(4, text(title, 9.5, true, 0.75));
        box.getChildren().addAll(content);
        box.setPadding(new Insets(8, 12, 9, 12));
        box.setStyle("-fx-background-color: rgba(0,0,0,0.20); -fx-background-radius: 16;");
        return box;
    }

    private Node chip(String value, String label) {
        VBox v = new VBox(-3, text(value, 17, true, 1), text(label, 9.5, false, 0.8));
        v.setPadding(new Insets(4, 11, 5, 11));
        v.setStyle("-fx-background-color: rgba(255,255,255,0.16); -fx-background-radius: 12;");
        return v;
    }

    private Node cover(String url) {
        Image img = Covers.image(url);
        StackPane box = new StackPane();
        box.setMinSize(32, 32);
        box.setMaxSize(32, 32);
        box.setStyle("-fx-background-color: rgba(255,255,255,0.18); -fx-background-radius: 8;");
        if (img != null) {
            pendingImages.add(img);
            ImageView v = new ImageView(img);
            v.setFitWidth(32);
            v.setFitHeight(32);
            Rectangle clip = new Rectangle(32, 32);
            clip.setArcWidth(10);
            clip.setArcHeight(10);
            v.setClip(clip);
            box.getChildren().add(v);
        } else {
            box.getChildren().add(Icons.icon(Icons.MUSIC_NOTE, 16));
        }
        return box;
    }

    /** Photo officielle de l'artiste (recherche Deezer), si elle existe. */
    private void loadArtistPicture(String name, StackPane avatar) {
        Async.run(() -> ctx.discovery().searchArtists(name), (found, error) -> {
            if (error != null || found.isEmpty() || found.get(0).picture() == null) return;
            if (!found.get(0).name().equalsIgnoreCase(name)) return; // pas d'homonyme approximatif
            Image img = Covers.image(found.get(0).picture());
            if (img == null) return;
            pendingImages.add(img);
            ImageView v = new ImageView(img);
            v.setFitWidth(46);
            v.setFitHeight(46);
            v.setClip(new Circle(23, 23, 23));
            avatar.getChildren().setAll(v);
        });
    }

    private static Label text(String s, double size, boolean bold, double opacity) {
        Label l = new Label(s);
        l.setStyle("-fx-text-fill: rgba(255,255,255," + opacity + "); -fx-font-size: " + size + "px;"
                + (bold ? " -fx-font-weight: bold;" : ""));
        l.setWrapText(false);
        l.setMinWidth(0);
        l.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
        return l;
    }

    private static Region spacer(double h) {
        Region r = new Region();
        r.setMinHeight(h);
        return r;
    }

    // ------------------------------------------------------------------ export PNG

    private void export() {
        if (story == null || current == null) return;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Exporter mon bilan");
        chooser.setInitialFileName("H-MOOD-Wrapped-" + current.month() + ".png");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image PNG", "*.png"));
        Path pictures = Path.of(System.getProperty("user.home"), "Pictures");
        if (Files.isDirectory(pictures)) chooser.setInitialDirectory(pictures.toFile());
        File file = chooser.showSaveDialog(story.getScene().getWindow());
        if (file == null) return;
        whenImagesReady(0, () -> {
            try {
                write(story, file);
                ctx.notify("Bilan exporté : " + file.getAbsolutePath());
            } catch (IOException e) {
                ctx.notify("Export impossible : " + e.getMessage());
            }
        });
    }

    /** Attend (au plus ~5 s) que les pochettes et photos soient chargées avant la capture. */
    private void whenImagesReady(int attempt, Runnable then) {
        boolean ready = pendingImages.stream().allMatch(i -> i.getProgress() >= 1 || i.isError());
        if (ready || attempt >= 25) {
            then.run();
            return;
        }
        PauseTransition wait = new PauseTransition(Duration.millis(200));
        wait.setOnFinished(e -> whenImagesReady(attempt + 1, then));
        wait.play();
    }

    static void write(Node node, File file) throws IOException {
        double scale = 1080 / W;
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        params.setTransform(new Scale(scale, scale));
        javafx.scene.effect.Effect effect = node.getEffect();
        Node clip = node.getClip();
        node.setEffect(null); // pas d'ombre portée ni de coins arrondis : image plein écran pour les stories
        node.setClip(new Rectangle(W, H));
        WritableImage image = node.snapshot(params, null);
        node.setEffect(effect);
        node.setClip(clip);
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            reader.getPixels(0, y, w, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), row, 0, w);
            out.setRGB(0, y, w, 1, row, 0, w);
        }
        ImageIO.write(out, "png", file);
    }

    static String monthLabel(YearMonth m) {
        String name = m.getMonth().getDisplayName(TextStyle.FULL, FR);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + m.getYear();
    }

    private static String formatInt(long v) {
        return String.format(FR, "%,d", v).replace(' ', ' ').replace(' ', ' ');
    }
}
