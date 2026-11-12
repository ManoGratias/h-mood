package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.music.ChartResult;
import com.hmood.music.provider.DeezerProvider.PlaylistRef;
import com.hmood.service.LibraryService;
import com.hmood.ui.Async;
import com.hmood.ui.DeezerPlayerWindow;
import com.hmood.ui.Icons;
import com.hmood.ui.SourceRows;
import com.hmood.ui.Ui;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.Year;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Voyage temporel : décennies × genre × ambiance, depuis de vraies playlists éditoriales. */
public final class TimeMachineController implements PageController {

    private record Era(String label, String tagline, Integer decade, String query, String colorA, String colorB) {
    }

    private static final List<Era> ERAS = List.of(
            new Era("Années 80", "Synthés, disco et tubes éternels", 1980, "années 80", "#FF5F9E", "#5B2C83"),
            new Era("Années 90", "Nostalgie eurodance, rap et R&B", 1990, "années 90", "#F7971E", "#3A4556"),
            new Era("Années 2000", "Les hits de la décennie 2000", 2000, "années 2000", "#45BFEF", "#8E54E9"),
            new Era("Années 2010", "Souvenirs des années 2010", 2010, "années 2010", "#7FD8BE", "#1D4E89"),
            new Era("Aujourd'hui", "Le son du moment", null, "hits du moment", "#FFD36E", "#E5533C"));

    private static final String[][] GENRES = {{"Tous", ""}, {"Pop", "pop"}, {"Rock", "rock"}, {"Rap", "rap"},
            {"R&B", "rnb"}, {"Électro", "dance"}, {"Funk / Disco", "disco"}, {"Variété française", "variété française"}};
    private static final String[][] AMBIANCES = {{"Toutes", ""}, {"Festive", "party"}, {"Chill", "chill"},
            {"Romantique", "love"}, {"Énergique", "workout"}};
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(ZoneId.systemDefault());

    @FXML private HBox decades;
    @FXML private FlowPane genres;
    @FXML private FlowPane ambiances;
    @FXML private VBox eraBox;
    @FXML private VBox localBox;

    private final AppContext ctx;
    private final ToggleGroup eraGroup = new ToggleGroup();
    private final ToggleGroup genreGroup = new ToggleGroup();
    private final ToggleGroup ambianceGroup = new ToggleGroup();
    private int request;

    public TimeMachineController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        for (Era era : ERAS) {
            VBox texts = new VBox(2, Ui.wrapped(era.label(), "era-title"), Ui.wrapped(era.tagline(), "era-tagline"));
            ToggleButton tile = new ToggleButton(null, texts);
            tile.getStyleClass().add("era-tile");
            tile.setStyle("-fx-background-color: linear-gradient(to bottom right, " + era.colorA() + ", " + era.colorB() + ");");
            tile.setUserData(era);
            tile.setToggleGroup(eraGroup);
            tile.setMaxWidth(Double.MAX_VALUE);
            tile.setMinWidth(120);
            tile.setPrefWidth(0);
            HBox.setHgrow(tile, Priority.ALWAYS);
            decades.getChildren().add(tile);
        }
        chips(genres, GENRES, genreGroup);
        chips(ambiances, AMBIANCES, ambianceGroup);
        eraGroup.selectedToggleProperty().addListener((o, a, t) -> {
            if (t == null) a.setSelected(true);
            else load();
        });
        genreGroup.selectedToggleProperty().addListener((o, a, t) -> {
            if (t == null) a.setSelected(true);
            else load();
        });
        ambianceGroup.selectedToggleProperty().addListener((o, a, t) -> {
            if (t == null) a.setSelected(true);
            else load();
        });
        eraGroup.getToggles().get(0).setSelected(true);
    }

    private static void chips(FlowPane pane, String[][] items, ToggleGroup group) {
        for (String[] item : items) {
            ToggleButton chip = new ToggleButton(item[0]);
            chip.getStyleClass().add("chip");
            chip.setUserData(item);
            chip.setToggleGroup(group);
            pane.getChildren().add(chip);
        }
        group.getToggles().get(0).setSelected(true);
    }

    private Era era() { return (Era) eraGroup.getSelectedToggle().getUserData(); }
    private String[] genre() { return (String[]) genreGroup.getSelectedToggle().getUserData(); }
    private String[] ambiance() { return (String[]) ambianceGroup.getSelectedToggle().getUserData(); }

    private void load() {
        if (eraGroup.getSelectedToggle() == null || genreGroup.getSelectedToggle() == null
                || ambianceGroup.getSelectedToggle() == null) return;
        Era era = era();
        String[] genre = genre();
        String[] ambiance = ambiance();
        showLocal(era, genre, ambiance);
        String query = String.join(" ", List.of(genre[1], era.query(), ambiance[1])).trim().replaceAll("\\s+", " ");
        boolean todayChart = era.decade() == null && genre[1].isEmpty() && ambiance[1].isEmpty();
        int id = ++request;
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(40, 40);
        eraBox.getChildren().setAll(new HBox(12, spinner, Ui.muted("Voyage vers « " + query + " »…")));
        Async.run(() -> {
            if (todayChart) return new Object[]{ctx.trending().chart("france", false), List.<PlaylistRef>of()};
            List<PlaylistRef> found = ctx.discovery().searchPlaylists(query);
            if (found.isEmpty()) return new Object[]{null, found};
            return new Object[]{ctx.discovery().playlist(found.get(0)), found};
        }, (res, error) -> {
            if (id != request) return;
            if (error != null) {
                eraBox.getChildren().setAll(Ui.emptyState("Voyage impossible", error));
                return;
            }
            @SuppressWarnings("unchecked") List<PlaylistRef> alternatives = (List<PlaylistRef>) res[1];
            if (res[0] == null) {
                eraBox.getChildren().setAll(Ui.emptyState("Aucune playlist trouvée",
                        "Aucune playlist réelle ne correspond à « " + query + " ». Essaie un autre genre ou une autre ambiance."));
                return;
            }
            render(era, (ChartResult) res[0], alternatives);
        });
    }

    private void render(Era era, ChartResult r, List<PlaylistRef> alternatives) {
        List<Track> tracks = ctx.musicPlayback().playable(r.entries());
        VBox card = new VBox(12);
        card.getStyleClass().add("era-card");
        card.setStyle("-fx-background-color: linear-gradient(to bottom right, " + Ui.rgba(era.colorA(), 0.30) + ", "
                + Ui.rgba(era.colorB(), 0.30) + ");");
        Label title = Ui.label(era.label().toUpperCase(Locale.ROOT) + " · " + r.title(), "section-label");
        Label source = Ui.label("Source : " + r.source() + " · " + (r.fromCache() ? "cache du " : "actualisé le ")
                + WHEN.format(r.updatedAt()), "source-label");
        HBox actions = new HBox(10);
        if (!tracks.isEmpty()) {
            Button play = new Button("Écouter les extraits (" + tracks.size() + ")", Icons.icon(Icons.PLAY, 16));
            play.getStyleClass().add("primary-button");
            play.setOnAction(e -> ctx.player().play(tracks, 0));
            Button full = new Button("En entier sur Deezer ↗");
            full.getStyleClass().add("secondary-button");
            full.setOnAction(e -> DeezerPlayerWindow.open(ctx, era.label() + " · " + r.title(), r.entries()));
            actions.getChildren().addAll(play, full);
        }
        card.getChildren().addAll(title, source, actions);
        if (alternatives.size() > 1) {
            FlowPane others = new FlowPane(6, 6);
            others.getChildren().add(Ui.label("Autres playlists :", "track-artist"));
            for (PlaylistRef ref : alternatives.subList(1, Math.min(6, alternatives.size()))) {
                Button b = new Button(ref.title());
                b.getStyleClass().add("chip-button");
                b.setOnAction(e -> Async.run(() -> ctx.discovery().playlist(ref), (res, err) -> {
                    if (err != null) ctx.notify(err);
                    else render(era, res, alternatives);
                }));
                others.getChildren().add(b);
            }
            card.getChildren().add(others);
        }
        card.getChildren().add(SourceRows.list(ctx, tracks, 25));
        eraBox.getChildren().setAll(card);
        Ui.animateIn(card);
    }

    /** Morceaux de ta bibliothèque de la même époque (année des tags), filtrés par genre et ambiance. */
    private void showLocal(Era era, String[] genre, String[] ambiance) {
        int from = era.decade() == null ? Year.now().getValue() - 2 : era.decade();
        int to = era.decade() == null ? Year.now().getValue() : era.decade() + 9;
        Mood mood = switch (ambiance[0]) {
            case "Festive" -> Mood.HAPPY;
            case "Chill" -> Mood.RELAXED;
            case "Romantique" -> Mood.ROMANTIC;
            case "Énergique" -> Mood.ENERGETIC;
            default -> null;
        };
        List<Track> local = ctx.library().all().stream()
                .filter(t -> t.year() != null && t.year() >= from && t.year() <= to)
                .filter(t -> genre[1].isEmpty() || LibraryService.matches(t, genre[0].split(" ")[0]))
                .filter(t -> mood == null || t.mood() == mood || mood.matchesGenre(t.genre()))
                .toList();
        localBox.getChildren().setAll(Ui.label("DANS TA BIBLIOTHÈQUE · " + era.label().toUpperCase(Locale.ROOT), "section-label"));
        if (local.isEmpty()) {
            localBox.getChildren().add(Ui.muted("Aucun de tes morceaux ne date de cette époque (d'après l'année de leurs tags)."));
        } else {
            localBox.getChildren().add(SourceRows.list(ctx, local, 15));
        }
    }
}
