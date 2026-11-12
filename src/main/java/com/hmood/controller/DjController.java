package com.hmood.controller;

import com.hmood.ai.DjService;
import com.hmood.ai.DjService.Item;
import com.hmood.ai.DjService.Session;
import com.hmood.ai.DjService.Style;
import com.hmood.app.AppContext;
import com.hmood.model.Track;
import com.hmood.ui.Async;
import com.hmood.ui.Covers;
import com.hmood.ui.DeezerPlayerWindow;
import com.hmood.ui.Icons;
import com.hmood.ui.Ui;
import com.hmood.util.Durations;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/** Page DJ IA : univers par groupes, analyse de tempo, session enchaînée. */
public final class DjController implements PageController {

    private static final Map<String, String> GROUP_TITLES = new LinkedHashMap<>();
    static {
        GROUP_TITLES.put(DjService.AFRICA, "AFRIQUE — LES TITRES PHARES DU MOMENT");
        GROUP_TITLES.put(DjService.RAP, "RAP");
        GROUP_TITLES.put(DjService.WORLD, "MONDE");
        GROUP_TITLES.put(DjService.MOMENTS, "MOMENTS");
    }

    @FXML private ScrollPane scroll;
    @FXML private Label statusPill;
    @FXML private TextField commandField;
    @FXML private VBox stylesBox;
    @FXML private Slider crossfadeSlider;
    @FXML private Label crossfadeLabel;
    @FXML private VBox sessionBox;

    private final AppContext ctx;
    private int request;

    public DjController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        statusPill.setText("DJ local · titres réels · tempo analysé");
        for (Map.Entry<String, String> group : GROUP_TITLES.entrySet()) {
            FlowPane tiles = new FlowPane(12, 12);
            for (Style style : DjService.STYLES) {
                if (!style.group().equals(group.getKey())) continue;
                Button tile = new Button(style.label());
                tile.getStyleClass().add("chart-tile");
                tile.setStyle("-fx-background-color: linear-gradient(to bottom right, " + style.colorA() + ", " + style.colorB() + ");");
                tile.setOnAction(e -> load(style.label(), p -> ctx.dj().build(style, p)));
                tiles.getChildren().add(tile);
            }
            stylesBox.getChildren().add(new VBox(10, Ui.label(group.getValue(), "section-label"), tiles));
        }
        var favorites = ctx.favoriteArtists().list();
        if (!favorites.isEmpty()) {
            Button tile = new Button("Mes artistes (" + favorites.size() + ")");
            tile.getStyleClass().add("chart-tile");
            tile.setStyle("-fx-background-color: linear-gradient(to bottom right, #FF5F6D, #8E54E9);");
            tile.setOnAction(e -> loadFavorites());
            stylesBox.getChildren().add(0, new VBox(10, Ui.label("TES ARTISTES FAVORIS", "section-label"), new FlowPane(12, 12, tile)));
        }
        crossfadeSlider.setValue(ctx.player().crossfadeSecondsProperty().get());
        updateCrossfadeLabel();
        crossfadeSlider.valueProperty().addListener((o, a, v) -> {
            ctx.player().crossfadeSecondsProperty().set(Math.round(v.doubleValue()));
            updateCrossfadeLabel();
        });
        crossfadeSlider.setOnMouseReleased(e -> ctx.settings().setCrossfadeSeconds(crossfadeSlider.getValue()));
        Ui.fillSlider(crossfadeSlider);
        // Démo : -Dhmood.dj=<univers> lance directement une session
        String demo = System.getProperty("hmood.dj");
        if (DjService.FAVORITES.equals(demo)) loadFavorites();
        else if (demo != null) DjService.style(demo).ifPresent(st -> load(st.label(), p -> ctx.dj().build(st, p)));
    }

    private void loadFavorites() {
        List<String> ids = ctx.favoriteArtists().list().stream().map(a -> a.id()).toList();
        if (ids.isEmpty()) {
            ctx.notify("Ajoute d'abord des artistes à tes favoris (cœur ♡ dans la barre de lecture ou sur la page d'un artiste).");
            return;
        }
        Style style = DjService.favoritesStyle(ids);
        load(style.label(), p -> ctx.dj().build(style, p));
    }

    private void updateCrossfadeLabel() {
        long s = Math.round(crossfadeSlider.getValue());
        crossfadeLabel.setText(s == 0 ? "désactivé" : String.format(Locale.ROOT, "%d s", s));
    }

    @Override
    public void onShow(Object argument) {
        if (DjService.FAVORITES.equals(argument)) {
            loadFavorites();
        } else if (argument instanceof String key) {
            DjService.style(key).ifPresent(st -> load(st.label(), p -> ctx.dj().build(st, p)));
        }
    }

    @FXML
    private void onMix() {
        String text = commandField.getText();
        if (text == null || text.isBlank()) return;
        load("« " + text.trim() + " »", p -> ctx.dj().fromCommand(text, p).orElse(null));
    }

    @FunctionalInterface
    private interface Builder {
        Session build(IntConsumer progress) throws Exception;
    }

    private void load(String name, Builder builder) {
        int id = ++request;
        ProgressBar bar = new ProgressBar(-1);
        bar.setPrefWidth(320);
        Label status = Ui.label("Le DJ récupère les titres phares de " + name + "…", "muted");
        VBox loading = new VBox(10, status, bar);
        loading.getStyleClass().add("dj-session");
        sessionBox.getChildren().setAll(loading);
        scrollToSession();
        IntConsumer progress = done -> Platform.runLater(() -> {
            if (id != request) return;
            status.setText("Analyse du tempo et de l'énergie : " + done + " titre(s)…");
            bar.setProgress(Math.min(1, done / 28.0));
        });
        Async.run(() -> builder.build(progress), (session, error) -> {
            if (id != request) return;
            if (error != null) {
                sessionBox.getChildren().setAll(Ui.emptyState("Session impossible", error));
            } else if (session == null) {
                sessionBox.getChildren().setAll(Ui.emptyState("Je n'ai pas compris",
                        "Essaie « rap ivoirien », « togo », « afrobeats », « rap US », « zouk », « soirée », « pluie »…"));
            } else {
                show(session);
            }
        });
    }

    private void show(Session s) {
        VBox card = new VBox(14);
        card.getStyleClass().add("dj-session");
        Label title = Ui.label("SESSION · " + s.title().toUpperCase(Locale.ROOT), "section-label");
        Label origin = Ui.wrapped("Source : " + s.origin(), "source-label");
        Label intro = Ui.wrapped(s.intro(), "dj-intro");
        List<Track> tracks = s.tracks();
        HBox actions = new HBox(10);
        if (!tracks.isEmpty()) {
            Button play = new Button("Lancer le mix (" + tracks.size() + ")", Icons.icon(Icons.PLAY, 16));
            play.getStyleClass().add("primary-button");
            play.setOnAction(e -> {
                if (ctx.player().crossfadeSecondsProperty().get() < 4) {
                    crossfadeSlider.setValue(6); // un mix de DJ se fait en fondu enchaîné
                    ctx.settings().setCrossfadeSeconds(6);
                }
                ctx.player().play(tracks, 0);
            });
            actions.getChildren().add(play);
        }
        if (!s.deezerEntries().isEmpty()) {
            Button deezer = new Button("Écouter en entier sur Deezer ↗");
            deezer.getStyleClass().add("secondary-button");
            deezer.setOnAction(e -> DeezerPlayerWindow.open(ctx, "DJ · " + s.title(), s.deezerEntries()));
            actions.getChildren().add(deezer);
        }
        card.getChildren().addAll(title, origin, intro, actions, tempoCurve(s.items()), rows(s.items()));
        sessionBox.getChildren().setAll(card);
        Ui.animateIn(card);
        scrollToSession();
    }

    /** Fait défiler la page jusqu'au début de la session. */
    private void scrollToSession() {
        Platform.runLater(() -> {
            double content = scroll.getContent().getBoundsInLocal().getHeight();
            double view = scroll.getViewportBounds().getHeight();
            double y = sessionBox.getBoundsInParent().getMinY();
            if (content > view) scroll.setVvalue(Math.min(1, Math.max(0, (y - 10) / (content - view))));
        });
    }

    /** Courbe de tempo de la session : une barre par titre (hauteur = BPM, couleur = énergie). */
    private VBox tempoCurve(List<Item> items) {
        double width = 900, height = 110;
        Canvas canvas = new Canvas(width, height);
        GraphicsContext g = canvas.getGraphicsContext2D();
        double maxEnergy = items.stream().map(Item::energy).filter(e -> e != null).mapToDouble(Double::doubleValue).max().orElse(1);
        double bw = width / Math.max(1, items.size());
        Color accent = Color.web(ctx.themes().theme().accent());
        Color hot = Color.web("#FF5F6D");
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            if (it.bpm() == null) {
                g.setFill(Color.web("#7F8EA3", 0.35));
                g.fillRoundRect(i * bw + 2, height - 8, bw - 4, 6, 4, 4);
                continue;
            }
            double h = Math.max(6, (it.bpm() - 60) / 120.0 * (height - 18));
            double energy = it.energy() == null ? 0.5 : it.energy() / maxEnergy;
            g.setFill(accent.interpolate(hot, Math.min(1, energy)));
            g.fillRoundRect(i * bw + 2, height - h, bw - 4, h, 6, 6);
        }
        Label legend = Ui.label("Courbe de tempo (hauteur = BPM, couleur = énergie) · gris = tempo non mesurable", "source-label");
        VBox box = new VBox(6, Ui.label("ENCHAÎNEMENT", "section-label"), canvas, legend);
        box.getStyleClass().add("tempo-curve");
        return box;
    }

    private VBox rows(List<Item> items) {
        VBox list = new VBox(2);
        List<Track> tracks = items.stream().map(Item::track).toList();
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            int index = i;
            Label n = Ui.label(String.valueOf(i + 1), "rank-label");
            n.setMinWidth(30);
            Label title = Ui.label(it.track().title(), "track-title");
            title.setTextOverrun(OverrunStyle.ELLIPSIS);
            title.setMinWidth(0);
            VBox texts = new VBox(1, title, Ui.label(it.track().displayArtist(), "track-artist"));
            texts.setMinWidth(0);
            HBox.setHgrow(texts, Priority.ALWAYS);
            String bpmText = it.bpm() == null ? "— BPM" : String.format(Locale.ROOT, "%.0f BPM", it.bpm());
            Label bpm = Ui.label(bpmText, "bpm-badge");
            bpm.setMinWidth(Region.USE_PREF_SIZE);
            String delta = "";
            if (i > 0 && it.bpm() != null && items.get(i - 1).bpm() != null) {
                double d = it.bpm() - items.get(i - 1).bpm();
                delta = (d >= 0 ? "↗ +" : "↘ ") + String.format(Locale.ROOT, "%.0f", d);
            }
            Label deltaLabel = Ui.label(delta, "track-artist");
            deltaLabel.setMinWidth(54);
            Label duration = Ui.label(Durations.format(it.track().durationMs()), "track-duration");
            HBox row = new HBox(12, n, Covers.of(it.track(), 40), texts, deltaLabel, bpm, duration);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("track-row");
            if (it.bpmMethod() != null) {
                javafx.scene.control.Tooltip.install(row, new javafx.scene.control.Tooltip(it.bpmMethod()));
            }
            row.setOnMouseClicked(e -> ctx.player().play(tracks, index));
            list.getChildren().add(row);
        }
        return list;
    }
}
