package com.hmood.controller;

import com.hmood.ai.DjService;
import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.model.Playlist;
import com.hmood.model.Track;
import com.hmood.music.CatalogEntry;
import com.hmood.service.MoodService;
import com.hmood.ui.Async;
import com.hmood.ui.Covers;
import com.hmood.ui.Icons;
import com.hmood.ui.TrackCards;
import com.hmood.ui.Ui;
import com.hmood.util.Durations;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Accueil : le meilleur du moment, en vrai, dès l'ouverture. */
public final class HomeController implements PageController {

    @FXML private VBox content;

    private final AppContext ctx;

    public HomeController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        try {
            build();
        } catch (RuntimeException e) {
            content.getChildren().setAll(Ui.emptyState("Accueil indisponible", e.getMessage()));
        }
    }

    private void build() {
        content.getChildren().setAll(
                hero(),
                moodStrip(),
                carousel("Top France", "Les titres les plus écoutés en France", "france"),
                djShortcuts(),
                carousel("Top Côte d'Ivoire", "Ce qui tourne à Abidjan en ce moment", "ci"),
                artistsOfTheMoment(),
                carousel("Afrobeats", "Les hits afro du moment", "nigeria"),
                carousel("Rap US", "Le rap américain du moment", "rap-us"),
                carousel("Nouveautés", "Les sorties de la semaine", "new"),
                myMusic());
    }

    // ------------------------------------------------------------------ bannière

    private Node hero() {
        StackPane box = new StackPane();
        box.setMinHeight(300);
        String name = ctx.settings().displayName();
        String date = capitalize(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)));
        String hello = greeting() + (name.isBlank() ? "" : ", " + name);
        VBox placeholder = new VBox(8, Ui.label(hello, "home-greeting"), Ui.label(date + " · Ressens ton humeur. Vis ta musique.", "home-date"),
                new HBox(10, spinner(), Ui.muted("Chargement du n°1 du moment…")));
        placeholder.getStyleClass().addAll("card", "hero-card");
        placeholder.setPadding(new Insets(30));
        box.getChildren().setAll(placeholder);
        Async.run(() -> ctx.trending().chart("france", false), (chart, error) -> {
            List<Track> tracks = chart == null ? List.of() : ctx.musicPlayback().playable(chart.entries());
            if (error != null || tracks.isEmpty()) {
                placeholder.getChildren().setAll(Ui.label(hello, "home-greeting"), Ui.label(date, "home-date"),
                        Ui.muted("Les classements sont indisponibles (connexion Internet ?). Ta bibliothèque reste accessible dans Ma musique."));
                return;
            }
            StackPane banner = TrackCards.hero(tracks.get(0), "N°1 EN FRANCE CETTE SEMAINE · DEEZER", tracks,
                    i -> ctx.player().play(tracks, Math.max(0, i)));
            VBox greetingBox = new VBox(2, Ui.label(hello, "home-greeting-on"), Ui.label(date, "home-date-on"));
            greetingBox.setAlignment(Pos.TOP_RIGHT);
            greetingBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
            StackPane.setAlignment(greetingBox, Pos.TOP_RIGHT);
            StackPane.setMargin(greetingBox, new Insets(22, 30, 0, 0));
            banner.getChildren().add(greetingBox);
            box.getChildren().setAll(banner);
            Ui.animateIn(banner);
        });
        return box;
    }

    // ------------------------------------------------------------------ humeurs

    private Node moodStrip() {
        Mood current = ctx.getCurrentMood();
        Mood suggested = MoodService.suggestForTime(LocalTime.now());
        Label title = Ui.label(current != null ? "TON HUMEUR DU JOUR : " + current.label().toUpperCase(Locale.ROOT)
                + " — clique pour en changer" : "COMMENT TE SENS-TU ? (suggestion pour cette heure : " + suggested.label() + ")", "section-label");
        HBox chips = new HBox(8);
        for (Mood m : Mood.values()) {
            Button chip = new Button(m.label());
            chip.getStyleClass().add("mood-chip");
            if (m == current) chip.getStyleClass().add("mood-chip-current");
            chip.setStyle("-fx-background-color: " + Ui.moodGradient(m) + ";");
            chip.setOnAction(e -> {
                ctx.moods().select(m, MoodService.SOURCE_MANUAL);
                ctx.setCurrentMood(m);
                ctx.navigator().navigate(Page.MOOD_SCANNER);
            });
            chips.getChildren().add(chip);
        }
        return new VBox(10, title, horizontal(chips));
    }

    // ------------------------------------------------------------------ carrousels

    private Node carousel(String title, String subtitle, String chartKey) {
        HBox row = new HBox(16);
        row.getChildren().add(spinner());
        Button all = new Button("Tout voir");
        all.getStyleClass().add("ghost-button");
        all.setOnAction(e -> ctx.navigator().navigate(Page.DISCOVER));
        Button play = new Button("Écouter", Icons.icon(Icons.PLAY, 14));
        play.getStyleClass().add("secondary-button");
        play.setDisable(true);
        Label source = Ui.label("", "source-label");
        HBox header = new HBox(12, new VBox(2, Ui.label(title, "home-section-title"), Ui.label(subtitle, "muted")),
                Ui.hSpacer(), source, play, all);
        header.setAlignment(Pos.CENTER_LEFT);
        Async.run(() -> ctx.trending().chart(chartKey, false), (chart, error) -> {
            row.getChildren().clear();
            if (error != null) {
                row.getChildren().add(Ui.muted("Indisponible : " + error));
                return;
            }
            List<Track> tracks = ctx.musicPlayback().playable(chart.entries());
            source.setText(chart.source());
            play.setDisable(tracks.isEmpty());
            play.setOnAction(e -> ctx.player().play(tracks, 0));
            for (int i = 0; i < Math.min(14, tracks.size()); i++) {
                int index = i;
                VBox card = TrackCards.card(tracks.get(i), 150, () -> ctx.player().play(tracks, index));
                card.getChildren().add(0, Ui.label("#" + (i + 1), "rank-label"));
                row.getChildren().add(card);
            }
            Ui.animateIn(row);
        });
        return new VBox(12, header, horizontal(row));
    }

    private Node djShortcuts() {
        HBox tiles = new HBox(12);
        for (String key : List.of("rap-ci", "abidjan", "nigeria", "togo", "afro-rnb", "rap-fr", "rap-us", "zouk")) {
            DjService.style(key).ifPresent(style -> {
                VBox texts = new VBox(2, Ui.label("MIX DU DJ", "tile-kicker"), Ui.label(style.label(), "tile-title"));
                Button tile = new Button(null, texts);
                tile.getStyleClass().addAll("chart-tile", "dj-tile");
                tile.setStyle("-fx-background-color: linear-gradient(to bottom right, " + style.colorA() + ", " + style.colorB() + ");");
                tile.setOnAction(e -> ctx.navigator().navigate(Page.AI_DJ, style.key()));
                tiles.getChildren().add(tile);
            });
        }
        VBox header = new VBox(2, Ui.label("Les mix du DJ", "home-section-title"),
                Ui.label("Titres phares, tempo analysé, enchaînés en fondu comme en soirée", "muted"));
        return new VBox(12, header, horizontal(tiles));
    }

    private Node artistsOfTheMoment() {
        HBox row = new HBox(22);
        row.getChildren().add(spinner());
        Label source = Ui.label("", "source-label");
        HBox header = new HBox(12, new VBox(2, Ui.label("Artistes du moment", "home-section-title"),
                Ui.label("Les artistes les plus écoutés en ce moment", "muted")), Ui.hSpacer(), source);
        header.setAlignment(Pos.CENTER_LEFT);
        Async.run(() -> ctx.trending().chart("artists", false), (chart, error) -> {
            row.getChildren().clear();
            if (error != null) {
                row.getChildren().add(Ui.muted("Indisponible : " + error));
                return;
            }
            source.setText(chart.source());
            for (CatalogEntry a : chart.entries().stream().limit(16).toList()) {
                Label name = Ui.label(a.title(), "artist-name");
                name.setMaxWidth(120);
                name.setTextOverrun(OverrunStyle.ELLIPSIS);
                VBox box = new VBox(8, Covers.round(a.coverUrl(), 116), name);
                box.setAlignment(Pos.TOP_CENTER);
                box.getStyleClass().add("artist-card");
                box.setOnMouseClicked(e -> ctx.navigator().navigate(Page.DISCOVER, a.title()));
                row.getChildren().add(box);
            }
            Ui.animateIn(row);
        });
        return new VBox(12, header, horizontal(row));
    }

    // ------------------------------------------------------------------ ma musique

    private Node myMusic() {
        List<Track> library = ctx.library().all();
        VBox section = new VBox(12, Ui.label("Ta musique", "home-section-title"));
        if (library.isEmpty()) {
            Button go = new Button("Importer ma musique", Icons.icon(Icons.FOLDER, 16));
            go.getStyleClass().add("primary-button");
            go.setOnAction(e -> ctx.navigator().navigate(Page.MY_MUSIC));
            HBox invite = new HBox(16, new VBox(4, Ui.label("Ajoute tes propres fichiers", "card-title"),
                    Ui.muted("MP3, M4A, WAV, AIFF : H MOOD les intègre à tes humeurs, à ton ADN musical et au DJ.")), Ui.hSpacer(), go);
            invite.setAlignment(Pos.CENTER_LEFT);
            invite.getStyleClass().add("card");
            section.getChildren().add(invite);
            return section;
        }
        VBox recent = Ui.card("Récemment écoutés", null);
        List<Track> recentTracks = new ArrayList<>();
        for (Long id : ctx.history().recentTrackIds(5)) ctx.tracks().findById(id).ifPresent(recentTracks::add);
        addRows(recent, recentTracks, "Aucune écoute pour l'instant.");
        VBox picks = Ui.card("Recommandations du jour", "Favoris et morceaux à redécouvrir");
        addRows(picks, ctx.reco().dailyPicks(5), "Rien à recommander pour l'instant.");
        VBox stats = Ui.card("Statistiques", null);
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(14);
        grid.add(stat(String.valueOf(library.size()), "morceaux"), 0, 0);
        List<Playlist> playlists = ctx.playlists().all();
        grid.add(stat(String.valueOf(playlists.size()), "playlists"), 1, 0);
        grid.add(stat(Durations.human(ctx.history().totalListenedMs()), "d'écoute"), 0, 1);
        grid.add(stat(String.valueOf(ctx.history().totalPlays()), "écoutes"), 1, 1);
        stats.getChildren().add(grid);
        section.getChildren().add(Ui.row(recent, picks, stats));
        return section;
    }

    private void addRows(VBox card, List<Track> tracks, String empty) {
        if (tracks.isEmpty()) {
            card.getChildren().add(Ui.muted(empty));
            return;
        }
        for (int i = 0; i < tracks.size(); i++) {
            int index = i;
            card.getChildren().add(Ui.trackRow(tracks.get(i), () -> ctx.player().play(tracks, index), 38));
        }
    }

    // ------------------------------------------------------------------ utilitaires

    /** Carrousel horizontal ; la molette fait défiler la page, pas le carrousel. */
    private static ScrollPane horizontal(HBox row) {
        row.setPadding(new Insets(4, 4, 10, 4));
        ScrollPane sp = new ScrollPane(row);
        sp.setFitToHeight(true);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.getStyleClass().add("carousel");
        sp.addEventFilter(javafx.scene.input.ScrollEvent.SCROLL, e -> {
            if (Math.abs(e.getDeltaX()) >= Math.abs(e.getDeltaY())) return;
            e.consume();
            Node parent = sp.getParent();
            while (parent != null && !(parent instanceof ScrollPane)) parent = parent.getParent();
            if (parent instanceof ScrollPane outer) {
                double h = outer.getContent().getBoundsInLocal().getHeight() - outer.getViewportBounds().getHeight();
                if (h > 0) outer.setVvalue(outer.getVvalue() - e.getDeltaY() / h);
            }
        });
        return sp;
    }

    private static Node spinner() {
        ProgressIndicator p = new ProgressIndicator();
        p.setMaxSize(34, 34);
        return p;
    }

    private static Node stat(String value, String label) {
        VBox box = new VBox(0, Ui.label(value, "stat-value"), Ui.label(label, "stat-label"));
        box.setMinWidth(110);
        return box;
    }

    private static String greeting() {
        int h = LocalTime.now().getHour();
        if (h < 5) return "Bonne nuit";
        if (h < 12) return "Bonjour";
        if (h < 18) return "Bon après-midi";
        return "Bonsoir";
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
