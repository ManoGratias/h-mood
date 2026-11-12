package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.repository.StatsRepository.Stat;
import com.hmood.ui.DnaHelix;
import com.hmood.ui.Ui;
import com.hmood.util.Durations;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Page ADN musical (MON ADN MUSICAL). */
public final class DnaController implements PageController {

    @FXML private VBox content;
    @FXML private javafx.scene.control.ScrollPane scroll;
    private final AppContext ctx;

    public DnaController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        try {
            build();
            javafx.application.Platform.runLater(() -> scroll.setVvalue(0));
        } catch (RuntimeException e) {
            content.getChildren().add(Ui.emptyState("Statistiques indisponibles", e.getMessage()));
        }
    }

    private void build() {
        var stats = ctx.stats();
        List<Mood> sequence = new ArrayList<>();
        for (String m : stats.moodSequence(60)) Mood.fromName(m).ifPresent(sequence::add);
        List<Stat> moods = stats.moods();
        List<Stat> genresHeard = stats.genresByListening(8);
        boolean hasHistory = !genresHeard.isEmpty();
        List<Stat> genres = hasHistory ? genresHeard : stats.genresInLibrary(8);
        List<Stat> artists = stats.topArtists(8);
        long totalMs = ctx.history().totalListenedMs();
        long plays = ctx.history().totalPlays();
        int distinctArtists = stats.distinctArtistsPlayed();
        int discoveries = stats.tracksAddedLastDays(30);

        // --- Hélice + profil
        Mood topMood = moods.isEmpty() ? null : Mood.fromName(moods.get(0).label()).orElse(null);
        Color accent = Color.web(ctx.themes().theme().accent());
        DnaHelix helix = new DnaHelix(sequence, accent, 520, 170);
        VBox profile = new VBox(8,
                Ui.label("MON ADN MUSICAL", "section-label"),
                Ui.label(profileName(plays, distinctArtists, topMood), "dna-profile"),
                Ui.wrapped(profileText(genres, topMood, plays, distinctArtists, hasHistory), "card-text"));
        HBox.setHgrow(profile, Priority.ALWAYS);
        profile.setMinWidth(0);
        HBox hero = new HBox(24, helix, profile);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.getStyleClass().addAll("card", "dna-hero");
        content.getChildren().add(hero);

        // --- Chiffres clés
        GridPane kpis = new GridPane();
        kpis.setHgap(16);
        kpis.add(kpi(Durations.human(totalMs), "temps total d'écoute"), 0, 0);
        kpis.add(kpi(String.valueOf(plays), "écoutes"), 1, 0);
        kpis.add(kpi(String.valueOf(distinctArtists), "artistes écoutés"), 2, 0);
        kpis.add(kpi(String.valueOf(discoveries), "découvertes (30 j)"), 3, 0);
        content.getChildren().add(kpis);

        if (plays == 0 && sequence.isEmpty() && genres.isEmpty()) {
            content.getChildren().add(Ui.emptyState("Ton ADN se dessine au fil de tes écoutes",
                    "Importe ta musique dans Ma musique, écoute-la et choisis tes humeurs dans le Scanner d'humeur : "
                            + "tes graphiques apparaîtront ici. (Les extraits en streaming ne sont pas comptés.)"));
            return;
        }

        // --- Genres + ambiances
        VBox genreCard = Ui.card("Genres préférés", hasHistory ? "Par temps d'écoute" : "D'après ta bibliothèque (pas encore d'écoute)");
        PieChart pie = new PieChart(FXCollections.observableArrayList(
                genres.stream().map(s -> new PieChart.Data(s.label(), s.value())).toList()));
        pie.setLabelsVisible(true);
        pie.setLegendVisible(false);
        pie.setPrefHeight(300);
        genreCard.getChildren().add(pie);

        VBox moodCard = Ui.card("Ambiances dominantes", "Humeurs choisies dans le Scanner d'humeur");
        if (moods.isEmpty()) moodCard.getChildren().add(Ui.muted("Aucune humeur choisie pour l'instant."));
        double maxMood = moods.stream().mapToDouble(Stat::value).max().orElse(1);
        for (Stat s : moods) {
            Mood m = Mood.fromName(s.label()).orElse(null);
            if (m == null) continue;
            Region bar = new Region();
            bar.setStyle("-fx-background-color: " + Ui.moodGradient(m) + "; -fx-background-radius: 8;");
            bar.setPrefHeight(16);
            bar.setPrefWidth(Math.max(12, 260 * s.value() / maxMood));
            bar.setMinWidth(Region.USE_PREF_SIZE);
            Label name = Ui.label(m.label(), "track-title");
            name.setMinWidth(110);
            HBox line = new HBox(10, name, bar, Ui.label(String.valueOf((int) s.value()), "track-artist"));
            line.setAlignment(Pos.CENTER_LEFT);
            moodCard.getChildren().add(line);
        }
        content.getChildren().add(Ui.row(genreCard, moodCard));

        // --- Artistes + évolution
        VBox artistCard = Ui.card("Artistes favoris", "Nombre d'écoutes");
        if (artists.isEmpty()) {
            artistCard.getChildren().add(Ui.muted("Pas encore d'écoute."));
        } else {
            CategoryAxis y = new CategoryAxis();
            NumberAxis x = new NumberAxis();
            BarChart<Number, String> bars = new BarChart<>(x, y);
            XYChart.Series<Number, String> series = new XYChart.Series<>();
            List<Stat> reversed = new ArrayList<>(artists);
            java.util.Collections.reverse(reversed);
            for (Stat s : reversed) series.getData().add(new XYChart.Data<>(s.value(), s.label()));
            bars.getData().add(series);
            bars.setLegendVisible(false);
            bars.setAnimated(false);
            bars.setPrefHeight(300);
            artistCard.getChildren().add(bars);
        }
        VBox evolution = Ui.card("Évolution", "Minutes d'écoute par jour (30 derniers jours)");
        CategoryAxis days = new CategoryAxis();
        NumberAxis minutes = new NumberAxis();
        LineChart<String, Number> line = new LineChart<>(days, minutes);
        XYChart.Series<String, Number> perDay = new XYChart.Series<>();
        for (Map.Entry<String, Double> e : stats.minutesPerDay(30).entrySet()) {
            perDay.getData().add(new XYChart.Data<>(e.getKey().substring(8) + "/" + e.getKey().substring(5, 7), e.getValue()));
        }
        line.getData().add(perDay);
        line.setLegendVisible(false);
        line.setCreateSymbols(false);
        line.setAnimated(false);
        line.setPrefHeight(300);
        evolution.getChildren().add(line);
        content.getChildren().add(Ui.row(artistCard, evolution));

        // --- Morceaux les plus écoutés
        VBox topCard = Ui.card("Morceaux les plus écoutés", null);
        List<Stat> top = stats.topTracks(10);
        if (top.isEmpty()) topCard.getChildren().add(Ui.muted("Pas encore d'écoute."));
        int rank = 1;
        for (Stat s : top) {
            Label r = Ui.label(String.valueOf(rank++), "rank-label");
            r.setMinWidth(30);
            Label name = Ui.label(s.label(), "track-title");
            HBox.setHgrow(name, Priority.ALWAYS);
            name.setMaxWidth(Double.MAX_VALUE);
            HBox row = new HBox(12, r, name, Ui.label((int) s.value() + " écoute(s)", "track-artist"));
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("track-row");
            topCard.getChildren().add(row);
        }
        content.getChildren().add(topCard);
    }

    private static Node kpi(String value, String label) {
        VBox box = new VBox(2, Ui.label(value, "stat-value"), Ui.label(label, "stat-label"));
        box.getStyleClass().add("card");
        box.setMinWidth(180);
        return new StackPane(box);
    }

    private static String profileName(long plays, int artists, Mood topMood) {
        if (plays == 0) return "Profil en formation";
        double variety = (double) artists / Math.max(1, plays);
        String kind = variety > 0.5 ? "Explorateur" : variety > 0.2 ? "Curieux" : "Fidèle";
        return kind + (topMood != null ? " · plutôt " + topMood.label() : "");
    }

    private static String profileText(List<Stat> genres, Mood topMood, long plays, int artists, boolean history) {
        StringBuilder sb = new StringBuilder();
        if (!genres.isEmpty()) {
            sb.append("Genre dominant : ").append(genres.get(0).label());
            if (genres.size() > 1) sb.append(", puis ").append(genres.get(1).label());
            sb.append(history ? " (temps d'écoute). " : " (bibliothèque). ");
        }
        if (topMood != null) sb.append("Humeur la plus fréquente : ").append(topMood.label()).append(" — ").append(topMood.tagline()).append(" ");
        if (plays > 0) sb.append(plays).append(" écoutes réparties sur ").append(artists).append(" artiste(s).");
        if (sb.isEmpty()) sb.append("Écoute ta musique et choisis tes humeurs : ton ADN se construira automatiquement.");
        return sb.toString();
    }
}
