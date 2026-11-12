package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.catalog.CatalogException;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.music.CatalogEntry;
import com.hmood.music.CatalogEntry.Kind;
import com.hmood.music.ChartDefinition;
import com.hmood.music.ChartResult;
import com.hmood.music.PlaybackMode;
import com.hmood.music.ProviderStatus;
import com.hmood.music.service.MusicCatalogService;
import com.hmood.music.service.PlaybackService;
import com.hmood.music.service.TrendingService;
import com.hmood.ui.Covers;
import com.hmood.ui.TrackCards;
import com.hmood.ui.Ui;
import com.hmood.util.Durations;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Découvrir : « Trending Now », nouveautés, recherche, recommandations — données réelles et sourcées. */
public final class DiscoverController implements PageController {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm").withZone(ZoneId.systemDefault());

    @FXML private ScrollPane scroll;
    @FXML private StackPane heroBox;
    @FXML private TextField searchField;
    @FXML private FlowPane chartTiles;
    @FXML private Label panelTitle;
    @FXML private Label panelSource;
    @FXML private Label panelWarning;
    @FXML private Button refreshButton;
    @FXML private Button playAllButton;
    @FXML private Button deezerButton;
    @FXML private VBox listBox;
    @FXML private FlowPane cardBox;
    @FXML private VBox stateBox;
    @FXML private VBox forYouBox;
    @FXML private FlowPane moodChips;
    @FXML private VBox sourcesBox;

    private final AppContext ctx;
    private int requestId;
    private Runnable reload = () -> { };
    private Runnable forceReload = () -> { };
    private List<Track> playableTracks = List.of();
    private ChartResult currentResult;

    public DiscoverController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FunctionalInterface
    private interface Load {
        ChartResult get(boolean force) throws CatalogException;
    }

    @FXML
    private void initialize() {
        for (ChartDefinition def : TrendingService.CHARTS) {
            Button tile = new Button(def.label());
            tile.getStyleClass().add("chart-tile");
            tile.setStyle("-fx-background-color: linear-gradient(to bottom right, " + def.colorA() + ", " + def.colorB() + ");");
            tile.setOnAction(e -> openChart(def));
            chartTiles.getChildren().add(tile);
        }
        for (Mood m : Mood.values()) {
            Button chip = new Button(m.label());
            chip.getStyleClass().add("mood-chip");
            chip.setStyle("-fx-background-color: " + Ui.moodGradient(m) + ";");
            chip.setOnAction(e -> {
                show(force -> ctx.recommendations().forMood(m));
                scroll.setVvalue(0.35);
            });
            moodChips.getChildren().add(chip);
        }
        buildSources();
        loadHero();
        // Jamendo (morceaux complets) en premier s'il est configuré, sinon le Top 50 France.
        String first = ctx.jamendo().configured() ? "jamendo" : "france";
        openChart(TrendingService.CHARTS.stream().filter(c -> c.key().equals(first)).findFirst().orElseThrow());
        loadForYou();
    }

    @Override
    public void onShow(Object argument) {
        if (argument instanceof CatalogEntry artist && artist.kind() == Kind.ARTIST) {
            showArtist(artist);
        } else if (argument instanceof String query && !query.isBlank()) {
            searchField.setText(query);
            onSearch();
        }
    }

    // ------------------------------------------------------------------ chargements

    private void openChart(ChartDefinition def) {
        show(force -> ctx.trending().chart(def.key(), force));
    }

    @FXML
    private void onSearch() {
        String q = searchField.getText();
        if (q == null || q.isBlank()) return;
        show(force -> ctx.musicCatalog().search(q));
    }

    @FXML private void onRefresh() { forceReload.run(); }

    @FXML
    private void onDeezerPlayer() {
        if (currentResult != null) com.hmood.ui.DeezerPlayerWindow.open(ctx, currentResult.title(), currentResult.entries());
    }

    @FXML
    private void onPlayAll() {
        if (!playableTracks.isEmpty()) ctx.player().play(playableTracks, 0);
    }

    /** Page d'un artiste : ses titres phares + bouton « Mes artistes ». */
    private void showArtist(CatalogEntry artist) {
        show(force -> ctx.discovery().artistTop(artist));
        currentArtist = artist;
        updateArtistButton();
    }

    private CatalogEntry currentArtist;
    private Button artistFavButton;

    private void updateArtistButton() {
        if (artistFavButton == null) {
            artistFavButton = new Button();
            artistFavButton.getStyleClass().add("secondary-button");
            artistFavButton.setOnAction(e -> {
                if (currentArtist == null) return;
                artistFavButton.setDisable(true);
                ctx.toggleFavoriteArtist(currentArtist, () -> {
                    artistFavButton.setDisable(false);
                    updateArtistButton();
                });
            });
            HBox header = (HBox) refreshButton.getParent();
            header.getChildren().add(header.getChildren().indexOf(refreshButton), artistFavButton);
        }
        boolean show = currentArtist != null;
        artistFavButton.setVisible(show);
        artistFavButton.setManaged(show);
        if (!show) return;
        boolean fav = ctx.favoriteArtists().isFavorite(currentArtist);
        artistFavButton.setText(fav ? "Dans mes artistes" : "Ajouter à mes artistes");
        artistFavButton.setGraphic(com.hmood.ui.Icons.icon(fav ? com.hmood.ui.Icons.HEART : com.hmood.ui.Icons.HEART_OUTLINE, 16));
    }

    private void show(Load load) {
        currentArtist = null;
        if (artistFavButton != null) updateArtistButton();
        reload = () -> run(load, false);
        forceReload = () -> run(load, true);
        reload.run();
    }

    private void run(Load load, boolean force) {
        int id = ++requestId;
        panelTitle.setText("Chargement…");
        panelSource.setText("");
        setWarning(null);
        listBox.getChildren().clear();
        cardBox.getChildren().clear();
        playAllButton.setDisable(true);
        refreshButton.setDisable(true);
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(44, 44);
        stateBox.getChildren().setAll(spinner);
        stateBox.setVisible(true);
        async(() -> load.get(force), (result, error) -> {
            if (id != requestId) return;
            refreshButton.setDisable(false);
            if (error != null) {
                panelTitle.setText("Données indisponibles");
                stateBox.getChildren().setAll(Ui.label("Impossible de joindre le fournisseur", "empty-title"), Ui.muted(error));
                return;
            }
            render(result);
        });
    }

    private void render(ChartResult r) {
        currentResult = r;
        boolean hasDeezer = r.entries().stream().anyMatch(e -> "deezer".equals(e.provider()) && e.kind() == Kind.TRACK);
        deezerButton.setVisible(hasDeezer);
        deezerButton.setManaged(hasDeezer);
        panelTitle.setText(r.title());
        panelSource.setText("Source : " + r.source() + "  ·  " + (r.fromCache() ? "données en cache du " : "actualisé le ")
                + WHEN.format(r.updatedAt()));
        setWarning(r.warning());
        playableTracks = ctx.musicPlayback().playable(r.entries());
        playAllButton.setDisable(playableTracks.isEmpty());
        boolean previews = r.entries().stream().anyMatch(e -> e.playback() == PlaybackMode.PREVIEW);
        playAllButton.setText((previews ? "Écouter les extraits (" : "Tout écouter (") + playableTracks.size() + ")");
        if (r.entries().isEmpty()) {
            stateBox.getChildren().setAll(Ui.label("Aucun résultat", "empty-title"));
            return;
        }
        stateBox.setVisible(false);
        stateBox.getChildren().clear();
        boolean cards = r.entries().get(0).kind() != Kind.TRACK;
        cardBox.setVisible(cards);
        cardBox.setManaged(cards);
        listBox.setVisible(!cards);
        listBox.setManaged(!cards);
        if (cards) {
            for (CatalogEntry e : r.entries()) cardBox.getChildren().add(entityCard(e));
            Ui.animateIn(cardBox);
        } else {
            for (CatalogEntry e : r.entries()) listBox.getChildren().add(trackRow(e));
            Ui.animateIn(listBox);
        }
    }

    private void setWarning(String warning) {
        panelWarning.setText(warning == null ? "" : warning);
        panelWarning.setVisible(warning != null);
        panelWarning.setManaged(warning != null);
    }

    private void loadHero() {
        async(() -> ctx.trending().chart("global", false), (r, error) -> {
            if (error != null || r.entries().isEmpty()) {
                heroBox.getChildren().setAll(Ui.emptyState("Classements indisponibles", error == null ? "" : error));
                return;
            }
            List<Track> tracks = ctx.musicPlayback().playable(r.entries());
            if (tracks.isEmpty()) return;
            StackPane hero = TrackCards.hero(tracks.get(0), "N°1 MONDIAL · " + r.source().toUpperCase(), tracks,
                    i -> ctx.player().play(tracks, Math.max(0, i)));
            heroBox.getChildren().setAll(hero);
            Ui.animateIn(hero);
        });
    }

    private void loadForYou() {
        async(() -> ctx.recommendations().becauseYouListen().orElse(null), (r, error) -> {
            if (error != null || r == null || r.entries().isEmpty()) return;
            List<CatalogEntry> top = r.entries().stream().filter(CatalogEntry::playable).limit(8).toList();
            List<Track> tracks = ctx.musicPlayback().playable(top);
            FlowPane cards = new FlowPane(16, 18);
            for (int i = 0; i < tracks.size(); i++) {
                int index = i;
                cards.getChildren().add(TrackCards.card(tracks.get(i), 150, () -> ctx.player().play(tracks, index)));
            }
            forYouBox.getChildren().setAll(Ui.label("POUR TOI — " + r.title().toUpperCase(), "section-label"),
                    Ui.label("Source : " + r.source() + " · basé sur ta bibliothèque locale", "source-label"), cards);
            forYouBox.setVisible(true);
            forYouBox.setManaged(true);
            Ui.animateIn(forYouBox);
        });
    }

    // ------------------------------------------------------------------ rendu

    private HBox trackRow(CatalogEntry e) {
        Label rank = Ui.label(e.rank() == null ? "" : String.valueOf(e.rank()), "rank-label");
        rank.setMinWidth(34);
        Track t = ctx.musicPlayback().toTrack(e);
        Label title = Ui.label(e.title(), "track-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinWidth(0);
        Label artist = Ui.label(e.artist() == null ? "" : e.artist(), "track-artist");
        artist.setMinWidth(0);
        VBox texts = new VBox(1, title, artist);
        texts.setMinWidth(0);
        texts.setPrefWidth(0);
        HBox.setHgrow(texts, Priority.ALWAYS);
        Label album = Ui.label(e.album() == null ? "" : e.album(), "track-artist");
        album.setMinWidth(0);
        album.setPrefWidth(220);
        album.setTextOverrun(OverrunStyle.ELLIPSIS);
        Label duration = Ui.label(e.durationMs() > 0 ? Durations.format(e.durationMs()) : "", "track-duration");
        duration.setMinWidth(44);
        Label badge = Ui.label(e.playback().label(), "playback-badge", "playback-" + e.playback().name().toLowerCase());
        badge.setMinWidth(Region.USE_PREF_SIZE);
        Hyperlink link = new Hyperlink(PlaybackService.providerName(e) + " ↗");
        link.getStyleClass().add("external-link");
        link.setMinWidth(Region.USE_PREF_SIZE);
        link.setOnAction(ev -> ctx.openLink(e.externalUrl()));
        HBox row = new HBox(12, rank, Covers.of(t, 44), texts, album, duration, badge, link);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().addAll("track-row", "chart-row");
        row.setOnMouseClicked(ev -> {
            if (ev.getButton() == javafx.scene.input.MouseButton.SECONDARY) {
                menu(e).show(row, ev.getScreenX(), ev.getScreenY());
                return;
            }
            if (!e.playable()) {
                ctx.notify("Lecture non autorisée dans H MOOD pour ce titre : ouverture sur " + PlaybackService.providerName(e));
                ctx.openLink(e.externalUrl());
                return;
            }
            int index = playableTracks.stream().map(Track::id).toList().indexOf(t.id());
            ctx.player().play(playableTracks, Math.max(0, index));
        });
        return row;
    }

    private ContextMenu menu(CatalogEntry e) {
        MenuItem similar = new MenuItem("Titres similaires");
        similar.setOnAction(ev -> show(force -> ctx.discovery().similar(e)));
        MenuItem top = new MenuItem("Top titres de " + e.artist());
        top.setOnAction(ev -> showArtist(artistEntry(e)));
        MenuItem album = new MenuItem("Voir l'album");
        album.setOnAction(ev -> show(force -> ctx.discovery().album(e)));
        album.setDisable(e.albumId() == null);
        boolean deezer = "deezer".equals(e.provider());
        similar.setDisable(!deezer);
        top.setDisable(!deezer);
        MenuItem info = new MenuItem("Infos (MusicBrainz)");
        info.setOnAction(ev -> async(() -> ctx.discovery().metadata(e), (m, error) -> {
            if (error != null) ctx.notify("MusicBrainz : " + error);
            else if (m.isEmpty()) ctx.notify("MusicBrainz ne référence pas ce titre.");
            else ctx.notify("MusicBrainz · « " + m.get().title() + " » — " + m.get().artist()
                        + (m.get().firstReleaseDate() != null ? " · première sortie : " + m.get().firstReleaseDate() : "")
                        + (m.get().releaseTitle() != null ? " · " + m.get().releaseTitle() : ""));
        }));
        MenuItem full = new MenuItem("Écouter en entier sur Deezer ↗");
        full.setDisable(!deezer || e.kind() != Kind.TRACK);
        full.setOnAction(ev -> com.hmood.ui.DeezerPlayerWindow.open(ctx, e.title() + " — " + e.artist(), List.of(e)));
        MenuItem open = new MenuItem("Ouvrir sur " + PlaybackService.providerName(e));
        open.setOnAction(ev -> ctx.openLink(e.externalUrl()));
        boolean fav = ctx.favoriteArtists().isFavorite(e);
        MenuItem favorite = new MenuItem(fav ? "Retirer " + e.artist() + " de mes artistes" : "♥ Ajouter " + e.artist() + " à mes artistes");
        favorite.setDisable(e.artist() == null);
        favorite.setOnAction(ev -> ctx.toggleFavoriteArtist(e, null));
        return new ContextMenu(full, favorite, similar, top, album, info, open);
    }

    /** Fiche « artiste » à partir d'un titre Deezer. */
    static CatalogEntry artistEntry(CatalogEntry track) {
        return new CatalogEntry(Kind.ARTIST, track.provider(), track.artistId(), track.artist(), track.artist(),
                track.artistId(), null, null, null, null, null, 0, null, null, null, null, PlaybackMode.LINK_ONLY, null);
    }

    private VBox entityCard(CatalogEntry e) {
        Track pseudo = ctx.musicPlayback().toTrack(e);
        VBox card = TrackCards.card(pseudo, 160, () -> {
            if (e.kind() == Kind.ARTIST) showArtist(e);
            else if (e.kind() == Kind.ALBUM) show(force -> ctx.discovery().album(e));
        });
        if (e.rank() != null) {
            card.getChildren().add(0, Ui.label("#" + e.rank(), "rank-label"));
        }
        return card;
    }

    private void buildSources() {
        for (ProviderStatus s : MusicCatalogService.statuses(ctx.jamendo().configured())) {
            Label name = Ui.label(s.name(), "track-title");
            name.setMinWidth(180);
            Label state = s.active() ? Ui.pill("Connecté") : Ui.warnPill("À configurer");
            state.setMinWidth(Region.USE_PREF_SIZE);
            Label role = Ui.label(s.role(), "track-artist");
            role.setMinWidth(260);
            Label detail = Ui.wrapped(s.detail(), "muted");
            HBox.setHgrow(detail, Priority.ALWAYS);
            HBox row = new HBox(12, state, name, role, detail);
            row.setAlignment(Pos.CENTER_LEFT);
            sourcesBox.getChildren().add(row);
        }
        sourcesBox.getChildren().add(Ui.wrapped("Les extraits de 30 s sont les extraits officiels fournis par Deezer et Apple ; "
                + "le morceau complet s'écoute sur leur service via le lien ↗. Aucun flux n'est enregistré ni redistribué. "
                + "Les classements sont mis en cache 1 h (SQLite) et actualisés automatiquement.", "muted"));
    }

    // ------------------------------------------------------------------ utilitaire asynchrone

    @FunctionalInterface
    private interface Blocking<T> {
        T get() throws Exception;
    }

    private static <T> void async(Blocking<T> work, java.util.function.BiConsumer<T, String> onFx) {
        CompletableFuture.supplyAsync(() -> {
            try {
                return work.get();
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((value, error) -> Platform.runLater(() -> {
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                onFx.accept(null, cause.getMessage());
            } else {
                onFx.accept(value, null);
            }
        }));
    }
}
