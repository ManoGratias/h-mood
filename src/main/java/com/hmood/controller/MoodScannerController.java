package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.recommendation.RuleBasedRecommendationEngine;
import com.hmood.service.MoodQuestionnaire;
import com.hmood.service.MoodQuestionnaire.Energy;
import com.hmood.service.MoodQuestionnaire.Feeling;
import com.hmood.service.MoodQuestionnaire.Need;
import com.hmood.service.MoodService;
import com.hmood.catalog.CatalogException;
import com.hmood.ui.Icons;
import com.hmood.ui.TrackCards;
import javafx.application.Platform;
import javafx.scene.control.ProgressIndicator;
import java.util.concurrent.CompletableFuture;
import com.hmood.ui.Ui;
import com.hmood.util.Durations;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TitledPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.TilePane;
import javafx.scene.layout.VBox;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Scanner d'humeur : le cœur de H MOOD. */
public final class MoodScannerController implements PageController {

    @FXML private TilePane moodGrid;
    @FXML private VBox questionnaireBox;
    @FXML private VBox resultBox;

    private final AppContext ctx;
    private final Map<Mood, Button> cards = new EnumMap<>(Mood.class);

    public MoodScannerController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        for (Mood m : Mood.values()) {
            moodGrid.getChildren().add(card(m));
        }
        questionnaireBox.getChildren().add(questionnaire());
        Mood current = ctx.getCurrentMood();
        if (current != null) {
            markSelected(current);
            showResult(current);
        }
    }

    private Button card(Mood m) {
        Label name = Ui.label(m.label(), "mood-name");
        Label tagline = Ui.wrapped(m.tagline(), "mood-tagline");
        VBox box = new VBox(4, name, tagline);
        box.setAlignment(Pos.BOTTOM_LEFT);
        Button b = new Button(null, box);
        b.getStyleClass().add("mood-card");
        b.setStyle("-fx-background-color: " + Ui.moodGradient(m) + ";");
        b.setPrefSize(200, 108);
        b.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        b.setOnAction(e -> select(m, MoodService.SOURCE_MANUAL));
        cards.put(m, b);
        return b;
    }

    private void select(Mood m, String source) {
        try {
            ctx.moods().select(m, source);
        } catch (RuntimeException e) {
            ctx.notify("Humeur non enregistrée dans l'historique : " + e.getMessage());
        }
        ctx.setCurrentMood(m);
        markSelected(m);
        showResult(m);
    }

    private void markSelected(Mood m) {
        cards.forEach((mood, button) -> {
            button.getStyleClass().remove("selected");
            if (mood == m) button.getStyleClass().add("selected");
        });
    }

    // ------------------------------------------------------------------ questionnaire

    private TitledPane questionnaire() {
        ToggleGroup energy = new ToggleGroup();
        ToggleGroup need = new ToggleGroup();
        ToggleGroup feeling = new ToggleGroup();
        VBox body = new VBox(14,
                question("Ton niveau d'énergie ?", energy, Energy.values()),
                question("De quoi as-tu besoin ?", need, Need.values()),
                question("Comment te sens-tu globalement ?", feeling, Feeling.values()));
        Label result = Ui.label("", "muted");
        Button suggest = new Button("Suggérer mon humeur", Icons.icon(Icons.SPARKLE, 16));
        suggest.getStyleClass().add("primary-button");
        suggest.setOnAction(e -> {
            if (energy.getSelectedToggle() == null || need.getSelectedToggle() == null || feeling.getSelectedToggle() == null) {
                result.setText("Réponds aux trois questions.");
                return;
            }
            Mood m = MoodQuestionnaire.suggest(
                    (Energy) energy.getSelectedToggle().getUserData(),
                    (Need) need.getSelectedToggle().getUserData(),
                    (Feeling) feeling.getSelectedToggle().getUserData());
            result.setText("Suggestion : " + m.label() + " — sélectionnée ci-dessus.");
            select(m, MoodService.SOURCE_QUESTIONNAIRE);
        });
        HBox actions = new HBox(12, suggest, result);
        actions.setAlignment(Pos.CENTER_LEFT);
        body.getChildren().add(actions);
        TitledPane pane = new TitledPane("Affiner avec le questionnaire (facultatif)", body);
        pane.setExpanded(false);
        pane.getStyleClass().add("questionnaire");
        return pane;
    }

    private static VBox question(String text, ToggleGroup group, Enum<?>[] options) {
        FlowPane chips = new FlowPane(8, 8);
        for (Enum<?> option : options) {
            ToggleButton chip = new ToggleButton(labelOf(option));
            chip.getStyleClass().add("chip");
            chip.setUserData(option);
            chip.setToggleGroup(group);
            chips.getChildren().add(chip);
        }
        return new VBox(8, Ui.label(text, "question-label"), chips);
    }

    private static String labelOf(Enum<?> option) {
        if (option instanceof Energy e) return e.label;
        if (option instanceof Need n) return n.label;
        if (option instanceof Feeling f) return f.label;
        return option.name();
    }

    // ------------------------------------------------------------------ résultat

    private void showResult(Mood m) {
        List<Track> matches = ctx.reco().forMood(m, 300);
        List<Track> playlist = RuleBasedRecommendationEngine.fitToDuration(matches, m.suggestedMinutes());
        List<Track> similar = matches.subList(playlist.size(), Math.min(matches.size(), playlist.size() + 6));
        List<String> artists = ctx.reco().topArtists(matches, 8);

        VBox card = new VBox(16);
        card.getStyleClass().add("mood-result");
        card.setStyle("-fx-background-color: " + Ui.moodGradient(m, 0.26) + ";"
                + " -fx-border-color: " + Ui.rgba(m.colorA(), 0.55) + ";");

        Label title = Ui.label("Ambiance " + m.label(), "result-title");
        Label tagline = Ui.wrapped(m.tagline(), "card-text");
        long playlistMs = playlist.stream().mapToLong(t -> t.durationMs() > 0 ? t.durationMs() : 180_000).sum();
        HBox pills = new HBox(8,
                Ui.pill("Durée suggérée : " + m.suggestedMinutes() + " min"),
                Ui.pill("Énergie " + Ui.energyDots(m.energy())),
                Ui.pill(matches.size() + " morceau(x) correspondant(s)"));
        card.getChildren().addAll(new VBox(4, title, tagline), pills);

        if (matches.isEmpty()) {
            card.getChildren().add(Ui.wrapped("Aucun morceau de ta bibliothèque ne correspond encore à « " + m.label() + " ». "
                    + "Le moteur local s'appuie sur les humeurs que tu associes à tes morceaux (Ma musique › clic droit › Humeur) "
                    + "et sur les genres présents dans leurs tags (" + String.join(", ", m.genreKeywords().subList(0, Math.min(5, m.genreKeywords().size()))) + "…).",
                    "card-text"));
            Button open = new Button("Ouvrir Ma musique");
            open.getStyleClass().add("secondary-button");
            open.setOnAction(e -> ctx.navigator().navigate(Page.MY_MUSIC));
            card.getChildren().add(open);
        } else {
            Button play = new Button("Lancer la playlist (" + playlist.size() + " titres · " + Durations.human(playlistMs) + ")",
                    Icons.icon(Icons.PLAY, 16));
            play.getStyleClass().add("primary-button");
            play.setOnAction(e -> ctx.player().play(playlist, 0));
            Button save = new Button("Enregistrer comme playlist");
            save.getStyleClass().add("secondary-button");
            save.setOnAction(e -> {
                String name = m.label() + " · " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM"));
                ctx.playlists().createWithTracks(name, "Générée par le Scanner d'humeur", m, playlist);
                ctx.bumpLibrary();
                ctx.notify("Playlist « " + name + " » enregistrée");
            });
            card.getChildren().add(new HBox(10, play, save));

            VBox playlistBox = new VBox(2, Ui.label("PLAYLIST ADAPTÉE", "section-label"));
            for (int i = 0; i < Math.min(10, playlist.size()); i++) {
                int index = i;
                playlistBox.getChildren().add(Ui.trackRow(playlist.get(i), () -> ctx.player().play(playlist, index), 40));
            }
            if (playlist.size() > 10) {
                playlistBox.getChildren().add(Ui.muted("… et " + (playlist.size() - 10) + " autre(s)"));
            }

            FlowPane artistPills = new FlowPane(6, 6);
            artists.forEach(a -> artistPills.getChildren().add(Ui.pill(a)));
            VBox side = new VBox(10, Ui.label("ARTISTES RECOMMANDÉS", "section-label"));
            side.getChildren().add(artists.isEmpty() ? Ui.muted("Artistes non renseignés dans les tags.") : artistPills);
            side.getChildren().add(Ui.label("MORCEAUX SIMILAIRES", "section-label"));
            if (similar.isEmpty()) {
                side.getChildren().add(Ui.muted("Toute la sélection tient dans la playlist."));
            }
            for (int i = 0; i < similar.size(); i++) {
                int index = i;
                side.getChildren().add(Ui.trackRow(similar.get(i), () -> ctx.player().play(similar, index), 34));
            }

            HBox columns = new HBox(24, playlistBox, side);
            for (VBox col : List.of(playlistBox, side)) {
                HBox.setHgrow(col, Priority.ALWAYS);
                col.setMinWidth(0);
                col.setPrefWidth(0);
            }
            card.getChildren().add(columns);
        }
        resultBox.getChildren().setAll(onlineSection(m), card);
        Ui.animateIn(card);
    }

    /** Artistes associés à l'humeur (les tiens + ceux de référence) et ajout d'un artiste. */
    private VBox myArtists(Mood m) {
        FlowPane chips = new FlowPane(6, 6);
        java.util.Set<String> mine = new java.util.HashSet<>();
        for (String[] a : ctx.settings().moodArtists(m)) mine.add(a[0]);
        for (String[] a : ctx.recommendations().artistsFor(m)) {
            boolean own = mine.contains(a[0]);
            Button chip = new Button(a[1] + (own ? "  ✕" : "  · référence"));
            chip.getStyleClass().addAll("artist-pill", own ? "artist-pill-own" : "artist-pill-ref");
            if (own) {
                chip.setOnAction(e -> {
                    ctx.settings().removeMoodArtist(m, a[0]);
                    showResult(m);
                });
            } else {
                chip.setDisable(false);
                chip.setOnAction(e -> ctx.navigator().navigate(Page.DISCOVER, a[1]));
            }
            chips.getChildren().add(chip);
        }
        Button add = new Button("+ Ajouter un artiste");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(e -> com.hmood.ui.Dialogs.askText(ctx, "Ajouter un artiste à l'humeur « " + m.label() + " »",
                "Nom de l'artiste :", "").ifPresent(name -> com.hmood.ui.Async.run(
                () -> ctx.discovery().searchArtists(name), (found, error) -> {
                    if (error != null) {
                        ctx.notify("Recherche impossible : " + error);
                    } else if (found.isEmpty()) {
                        ctx.notify("Aucun artiste « " + name + " » trouvé sur Deezer.");
                    } else {
                        var a = found.get(0);
                        ctx.settings().addMoodArtist(m, a.id(), a.name());
                        ctx.notify(a.name() + " (" + String.format("%,d", a.fans()).replace(',', ' ') + " fans sur Deezer) ajouté à « "
                                + m.label() + " » : ses titres et des titres similaires seront proposés en premier.");
                        showResult(m);
                    }
                })));
        chips.getChildren().add(add);
        return new VBox(6, Ui.label("TES ARTISTES POUR CETTE HUMEUR", "section-label"), chips);
    }

    /** Vrais titres et artistes du monde entier pour cette humeur (playlists éditoriales Deezer + Audius). */
    private VBox onlineSection(Mood m) {
        Label title = Ui.label("DANS LE MONDE ENTIER — DE VRAIS TITRES « " + m.label().toUpperCase() + " »", "section-label");
        Label source = Ui.label("Recherche des playlists « " + m.label() + " » chez Deezer…", "source-label");
        source.setWrapText(true);
        FlowPane artists = new FlowPane(6, 6);
        FlowPane cards = new FlowPane(16, 18);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(36, 36);
        cards.getChildren().add(spinner);
        Button playAll = new Button("Écouter");
        playAll.getStyleClass().add("primary-button");
        playAll.setDisable(true);
        Button deezer = new Button("En entier sur Deezer ↗");
        deezer.getStyleClass().add("secondary-button");
        deezer.setDisable(true);
        HBox header = new HBox(10, title, Ui.hSpacer(), playAll, deezer);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox artistBox = new VBox(6, Ui.label("ARTISTES", "section-label"), artists);
        artistBox.setVisible(false);
        artistBox.setManaged(false);
        VBox box = new VBox(12, header, myArtists(m), source, artistBox, cards);
        box.getStyleClass().add("card");
        box.setStyle("-fx-border-color: " + Ui.rgba(m.colorA(), 0.5) + ";");
        com.hmood.ui.Async.run(() -> ctx.recommendations().forMood(m), (result, error) -> {
            if (ctx.getCurrentMood() != m) return;
            cards.getChildren().clear();
            if (error != null) {
                source.setText("");
                cards.getChildren().add(Ui.muted("Catalogue en ligne indisponible : " + error));
                return;
            }
            source.setText("Source : " + result.source());
            List<com.hmood.music.CatalogEntry> entries = result.entries();
            List<Track> tracks = ctx.musicPlayback().playable(entries);
            if (tracks.isEmpty()) {
                cards.getChildren().add(Ui.muted("Aucun titre trouvé pour cette humeur."));
                return;
            }
            playAll.setDisable(false);
            playAll.setText("Écouter (" + tracks.size() + ")");
            playAll.setOnAction(e -> ctx.player().play(tracks, 0));
            List<com.hmood.music.CatalogEntry> deezerEntries = entries.stream().filter(e -> "deezer".equals(e.provider())).toList();
            deezer.setDisable(deezerEntries.isEmpty());
            deezer.setOnAction(e -> com.hmood.ui.DeezerPlayerWindow.open(ctx, "Humeur « " + m.label() + " »", deezerEntries));
            for (com.hmood.music.CatalogEntry a : com.hmood.music.service.MusicRecommendationService.topArtists(deezerEntries, 14)) {
                Button pill = new Button(a.artist());
                pill.getStyleClass().add("artist-pill");
                pill.setOnAction(e -> ctx.navigator().navigate(Page.DISCOVER, a.artist()));
                artists.getChildren().add(pill);
            }
            artistBox.setVisible(!artists.getChildren().isEmpty());
            artistBox.setManaged(!artists.getChildren().isEmpty());
            for (int i = 0; i < Math.min(18, tracks.size()); i++) {
                int index = i;
                cards.getChildren().add(TrackCards.card(tracks.get(i), 150, () -> ctx.player().play(tracks, index)));
            }
            Ui.animateIn(cards);
        });
        return box;
    }
}
