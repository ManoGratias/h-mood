package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Track;
import com.hmood.player.PlaybackListener;
import com.hmood.player.PlayerService;
import com.hmood.player.RepeatMode;
import com.hmood.ui.Covers;
import com.hmood.ui.Icons;
import com.hmood.ui.Logo;
import com.hmood.ui.Ui;
import com.hmood.ui.Visualizer;
import com.hmood.util.Durations;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Fenêtre principale : navigation, barre de lecture, notifications. */
public final class MainController implements Navigator {

    @FXML private javafx.scene.layout.BorderPane appRoot;
    @FXML private HBox brandBox;
    @FXML private VBox navBox;
    @FXML private StackPane searchIcon;
    @FXML private TextField globalSearch;
    @FXML private Button themeButton;
    @FXML private StackPane contentPane;
    @FXML private VBox aiPanel;

    @FXML private StackPane coverPane;
    @FXML private Label trackTitle;
    @FXML private Label trackArtist;
    @FXML private javafx.scene.control.Hyperlink sourceLink;
    @FXML private Button favButton;
    @FXML private Button shuffleButton;
    @FXML private Button prevButton;
    @FXML private Button playButton;
    @FXML private Button nextButton;
    @FXML private Button repeatButton;
    @FXML private Label elapsedLabel;
    @FXML private Label totalLabel;
    @FXML private Slider progressSlider;
    @FXML private Canvas visualizerCanvas;
    @FXML private Button queueButton;
    @FXML private StackPane volumeIcon;
    @FXML private Slider volumeSlider;

    private final AppContext ctx;
    private final Map<Page, ToggleButton> navButtons = new EnumMap<>(Page.class);
    private final ToggleGroup navGroup = new ToggleGroup();
    private Visualizer visualizer;
    private boolean seeking;
    private Button lyricsButton;
    private Button offlineButton;
    private com.hmood.ui.LyricsOverlay lyricsOverlay;
    private final javafx.beans.property.ObjectProperty<Color> glowA = new javafx.beans.property.SimpleObjectProperty<>(Color.TRANSPARENT);
    private final javafx.beans.property.ObjectProperty<Color> glowB = new javafx.beans.property.SimpleObjectProperty<>(Color.TRANSPARENT);
    private javafx.animation.Timeline glowAnimation;
    private HBox playerBar;

    public MainController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        ctx.setNavigator(this);
        ctx.setNotifier(this::toast);
        buildBrand();
        buildNav();
        setupPlayerBar();
        new AiPanelController(aiPanel, ctx);
        setupAssistantToggle();
        searchIcon.getChildren().setAll(Icons.icon(Icons.SEARCH, 18));
        updateThemeButton();
        ctx.themes().themeProperty().addListener((o, a, t) -> {
            updateThemeButton();
            visualizer.setColor(Color.web(t.accent()));
        });
        Page start = Page.HOME;
        try {
            start = Page.valueOf(System.getProperty("hmood.page", "HOME")); // page de démarrage (utile en démo)
        } catch (IllegalArgumentException ignored) {
            // valeur inconnue : accueil
        }
        navigate(start);
        appRoot.sceneProperty().addListener((o, old, scene) -> {
            if (scene != null) scene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, this::onShortcut);
        });
        String demo = System.getProperty("hmood.lyrics");
        if (demo != null) { // démo : lance le premier résultat de la recherche puis ouvre l'écran des paroles
            com.hmood.ui.Async.run(() -> ctx.musicCatalog().search(demo), (r, error) -> {
                if (error != null || r.entries().isEmpty()) return;
                ctx.player().play(ctx.musicPlayback().playable(r.entries().subList(0, 1)), 0);
                PauseTransition wait = new PauseTransition(Duration.seconds(4));
                wait.setOnFinished(e -> toggleLyrics());
                wait.play();
            });
        }
    }

    // ------------------------------------------------------------------ navigation

    private void buildBrand() {
        VBox texts = new VBox(0, Ui.label("H MOOD", "brand-title"), Ui.label("Ressens ton humeur. Vis ta musique.", "brand-slogan"));
        texts.setAlignment(Pos.CENTER_LEFT);
        brandBox.getChildren().setAll(Logo.create(36), texts);
    }

    private void buildNav() {
        Page.Section section = null;
        for (Page page : Page.values()) {
            if (page.section() != section) {
                section = page.section();
                if (section.title() != null) {
                    navBox.getChildren().add(Ui.label(section.title(), "nav-section"));
                } else {
                    Region spacer = new Region();
                    VBox.setVgrow(spacer, Priority.ALWAYS);
                    navBox.getChildren().add(spacer);
                }
            }
            ToggleButton button = new ToggleButton(page.label(), Icons.icon(page.icon(), 18));
            button.getStyleClass().add("nav-button");
            button.setToggleGroup(navGroup);
            button.setMaxWidth(Double.MAX_VALUE);
            if (!page.isAvailable()) {
                Label soon = Ui.label("bientôt", "nav-soon");
                HBox graphic = new HBox(12, Icons.icon(page.icon(), 18));
                graphic.setAlignment(Pos.CENTER_LEFT);
                button.setGraphic(graphic);
                button.setContentDisplay(javafx.scene.control.ContentDisplay.LEFT);
                button.setText(null);
                Label text = new Label(page.label());
                text.getStyleClass().add("nav-text");
                graphic.getChildren().addAll(text, Ui.hSpacer(), soon);
                graphic.setPrefWidth(196);
            }
            button.setOnAction(e -> {
                if (!button.isSelected()) {
                    button.setSelected(true);
                }
                navigate(page);
            });
            navButtons.put(page, button);
            navBox.getChildren().add(button);
        }
    }

    @Override
    public void navigate(Page page, Object argument) {
        if (lyricsOverlay != null) lyricsOverlay.close();
        try {
            String fxml = page.isAvailable() ? page.fxml() : "PlaceholderView.fxml";
            FXMLLoader loader = new FXMLLoader(MainController.class.getResource("/com/hmood/fxml/" + fxml));
            loader.setControllerFactory(ctx.controllerFactory());
            Parent view = loader.load();
            if (loader.getController() instanceof PageController controller) {
                controller.onShow(page.isAvailable() ? argument : page);
            }
            contentPane.getChildren().setAll(view);
            Ui.animateIn(view);
            ToggleButton button = navButtons.get(page);
            if (button != null) {
                button.setSelected(true);
            }
        } catch (Exception e) {
            e.printStackTrace();
            contentPane.getChildren().setAll(Ui.emptyState("Impossible d'afficher la page " + page.label(),
                    String.valueOf(e.getMessage())));
        }
    }

    @FXML
    private void onGlobalSearch() {
        navigate(Page.MY_MUSIC, globalSearch.getText());
    }

    @FXML
    private void onToggleTheme() {
        ctx.settings().setTheme(ctx.themes().toggle());
    }

    /** Bouton « Assistant » : affiche ou masque le panneau de droite (choix mémorisé). */
    private void setupAssistantToggle() {
        javafx.scene.control.ToggleButton toggle = new javafx.scene.control.ToggleButton("Assistant", Icons.icon(Icons.SPARKLE, 16));
        toggle.getStyleClass().add("assistant-toggle");
        toggle.setTooltip(new Tooltip("Afficher / masquer l'assistant IA"));
        boolean visible = ctx.settings().assistantVisible();
        toggle.setSelected(visible);
        aiPanel.setVisible(visible);
        aiPanel.setManaged(visible);
        toggle.selectedProperty().addListener((o, a, on) -> {
            aiPanel.setVisible(on);
            aiPanel.setManaged(on);
            ctx.settings().setAssistantVisible(on);
        });
        HBox topBar = (HBox) themeButton.getParent();
        topBar.getChildren().add(topBar.getChildren().indexOf(themeButton), toggle);
    }

    private void updateThemeButton() {
        themeButton.setGraphic(Icons.icon(Icons.PALETTE, 16));
        themeButton.setText(ctx.themes().theme().label());
        themeButton.setTooltip(new Tooltip("Basculer vers " + ctx.themes().theme().other().label()));
    }

    // ------------------------------------------------------------------ barre de lecture

    private void setupPlayerBar() {
        PlayerService player = ctx.player();
        visualizer = new Visualizer(visualizerCanvas, Color.web(ctx.themes().theme().accent()));
        player.setSpectrumConsumer(visualizer::update);

        playButton.setGraphic(Icons.icon(Icons.PLAY, 22));
        prevButton.setGraphic(Icons.icon(Icons.PREVIOUS, 22));
        nextButton.setGraphic(Icons.icon(Icons.NEXT, 22));
        shuffleButton.setGraphic(Icons.icon(Icons.SHUFFLE, 18));
        queueButton.setGraphic(Icons.icon(Icons.QUEUE, 18));
        volumeIcon.getChildren().setAll(Icons.icon(Icons.VOLUME, 18));
        tooltip(prevButton, "Précédent");
        tooltip(nextButton, "Suivant");
        tooltip(playButton, "Lecture / pause");
        tooltip(shuffleButton, "Lecture aléatoire");
        tooltip(queueButton, "File d'attente");
        tooltip(favButton, "Favori");
        updateRepeatButton();

        lyricsButton = new Button(null, Icons.icon(Icons.LYRICS, 18));
        lyricsButton.getStyleClass().add("icon-button");
        tooltip(lyricsButton, "Paroles");
        lyricsButton.setOnAction(e -> toggleLyrics());
        offlineButton = new Button(null, Icons.icon(Icons.DOWNLOAD, 18));
        offlineButton.getStyleClass().add("icon-button");
        offlineButton.setOnAction(e -> downloadCurrent());
        HBox right = (HBox) queueButton.getParent();
        right.getChildren().add(right.getChildren().indexOf(queueButton), lyricsButton);
        right.getChildren().add(right.getChildren().indexOf(lyricsButton), offlineButton);
        playerBar = (HBox) right.getParent();
        glowA.addListener((o, a, c) -> updateGlow());
        glowB.addListener((o, a, c) -> updateGlow());

        player.playingProperty().addListener((o, a, isPlaying) -> {
            playButton.setGraphic(Icons.icon(isPlaying ? Icons.PAUSE : Icons.PLAY, 22));
            visualizer.setActive(isPlaying);
        });
        player.shuffleProperty().addListener((o, a, on) -> toggleClass(shuffleButton, "active", on));
        player.repeatProperty().addListener((o, a, r) -> updateRepeatButton());
        player.currentProperty().addListener((o, a, t) -> updateNowPlaying(t));
        updateNowPlaying(null);

        player.positionMsProperty().addListener((o, a, v) -> {
            long pos = v.longValue();
            long dur = player.durationMsProperty().get();
            elapsedLabel.setText(Durations.format(pos));
            if (!seeking) {
                progressSlider.setValue(dur > 0 ? Math.min(1, (double) pos / dur) : 0);
            }
        });
        player.durationMsProperty().addListener((o, a, v) -> totalLabel.setText(Durations.format(v.longValue())));
        progressSlider.setOnMousePressed(e -> seeking = true);
        progressSlider.setOnMouseReleased(e -> {
            player.seek(progressSlider.getValue());
            seeking = false;
        });

        volumeSlider.setValue(player.volumeProperty().get());
        volumeSlider.valueProperty().bindBidirectional(player.volumeProperty());
        volumeSlider.setOnMouseReleased(e -> ctx.settings().setVolume(volumeSlider.getValue()));
        Ui.fillSlider(progressSlider);
        Ui.fillSlider(volumeSlider);

        Label sleepBadge = Ui.label("", "sleep-badge");
        sleepBadge.setVisible(false);
        sleepBadge.setManaged(false);
        ((HBox) volumeIcon.getParent()).getChildren().add(0, sleepBadge);
        ctx.sleepTimer().remainingSecondsProperty().addListener((o, a, v) -> {
            long left = v.longValue();
            boolean on = ctx.sleepTimer().isRunning() && left > 0;
            sleepBadge.setVisible(on);
            sleepBadge.setManaged(on);
            sleepBadge.setText(String.format("☾ %d:%02d", left / 60, left % 60));
        });
        player.addListener(new PlaybackListener() {
            @Override
            public void onError(Track track, String message) {
                toast("Lecture impossible de « " + track.title() + " » : " + message);
            }
        });
    }

    private void updateNowPlaying(Track t) {
        coverPane.getChildren().setAll(Covers.of(t, 54));
        updateOfflineButton(t);
        applyCoverGlow(t);
        if (t == null) {
            updateSourceLink(null);
            trackTitle.setText("Aucune lecture");
            trackArtist.setText("Importe ta musique dans Ma musique");
            favButton.setDisable(true);
            favButton.setGraphic(Icons.icon(Icons.HEART_OUTLINE, 18));
            toggleClass(favButton, "active", false);
            elapsedLabel.setText("0:00");
            totalLabel.setText("0:00");
            return;
        }
        updateSourceLink(t);
        trackTitle.setText(t.title());
        trackArtist.setText(t.displayArtist() + (t.album() != null ? " · " + t.album() : "")
                );
        favButton.setDisable(t.isRemote()); // morceaux en streaming : pas de favori local
        favButton.setGraphic(Icons.icon(t.favorite() ? Icons.HEART : Icons.HEART_OUTLINE, 18));
        toggleClass(favButton, "active", t.favorite());
        totalLabel.setText(Durations.format(t.durationMs()));
    }

    // ------------------------------------------------------------------ raccourcis clavier

    private double volumeBeforeMute = -1;

    private void onShortcut(javafx.scene.input.KeyEvent e) {
        PlayerService player = ctx.player();
        javafx.scene.Node focus = appRoot.getScene().getFocusOwner();
        boolean typing = focus instanceof javafx.scene.control.TextInputControl;
        boolean ctrl = e.isShortcutDown();
        boolean handled = true;
        switch (e.getCode()) {
            case SPACE -> {
                if (typing || ctrl || e.isAltDown()) handled = false;
                else player.togglePlay();
            }
            case PLAY, PAUSE -> player.togglePlay();
            case TRACK_NEXT -> player.next();
            case TRACK_PREV -> player.previous();
            case STOP -> player.pause();
            case RIGHT, LEFT -> {
                int dir = e.getCode() == javafx.scene.input.KeyCode.RIGHT ? 1 : -1;
                if (ctrl && !typing) {
                    if (dir > 0) player.next();
                    else player.previous();
                } else if (e.isShiftDown() && !typing) {
                    long dur = player.durationMsProperty().get();
                    if (dur > 0) {
                        long target = Math.max(0, Math.min(dur - 500, player.positionMsProperty().get() + dir * 10_000L));
                        player.seek(target / (double) dur);
                    }
                } else handled = false;
            }
            case UP, DOWN -> {
                if (ctrl) {
                    double v = player.volumeProperty().get() + (e.getCode() == javafx.scene.input.KeyCode.UP ? 0.05 : -0.05);
                    player.volumeProperty().set(Math.max(0, Math.min(1, v)));
                    ctx.settings().setVolume(player.volumeProperty().get());
                    toast("Volume " + Math.round(player.volumeProperty().get() * 100) + " %");
                } else handled = false;
            }
            case M -> {
                if (!ctrl) {
                    handled = false;
                } else if (volumeBeforeMute < 0) {
                    volumeBeforeMute = player.volumeProperty().get();
                    player.volumeProperty().set(0);
                    toast("Son coupé (Ctrl + M pour le remettre)");
                } else {
                    player.volumeProperty().set(volumeBeforeMute);
                    volumeBeforeMute = -1;
                    toast("Son rétabli");
                }
            }
            case L -> {
                if (ctrl) toggleLyrics();
                else handled = false;
            }
            case F -> {
                if (ctrl) {
                    globalSearch.requestFocus();
                    globalSearch.selectAll();
                } else handled = false;
            }
            case S -> {
                if (ctrl) {
                    onShuffle();
                    toast(player.shuffleProperty().get() ? "Lecture aléatoire activée" : "Lecture aléatoire désactivée");
                } else handled = false;
            }
            case R -> {
                if (ctrl) onRepeat();
                else handled = false;
            }
            case ESCAPE -> {
                if (lyricsOverlay != null) lyricsOverlay.close();
                else handled = false;
            }
            default -> handled = false;
        }
        if (handled) e.consume();
    }

    // ------------------------------------------------------------------ paroles

    private void toggleLyrics() {
        if (lyricsOverlay != null) {
            lyricsOverlay.close();
            return;
        }
        lyricsOverlay = new com.hmood.ui.LyricsOverlay(ctx, () -> {
            contentPane.getChildren().remove(lyricsOverlay);
            lyricsOverlay = null;
            toggleClass(lyricsButton, "active", false);
        });
        contentPane.getChildren().add(lyricsOverlay);
        toggleClass(lyricsButton, "active", true);
        Ui.animateIn(lyricsOverlay);
        lyricsOverlay.requestFocus();
    }

    // ------------------------------------------------------------------ hors ligne (Jamendo)

    private void updateOfflineButton(Track t) {
        var info = ctx.musicPlayback().info(t).filter(e -> ctx.offline().supports(e));
        boolean show = info.isPresent();
        offlineButton.setVisible(show);
        offlineButton.setManaged(show);
        if (!show) return;
        boolean done = ctx.offline().find(info.get()).isPresent();
        offlineButton.setGraphic(Icons.icon(done ? Icons.DOWNLOAD_DONE : Icons.DOWNLOAD, 18));
        offlineButton.setDisable(done);
        toggleClass(offlineButton, "active", done);
        tooltip(offlineButton, done ? "Déjà disponible hors ligne (dans Ma musique)"
                : "Télécharger pour l'écoute hors ligne (Jamendo, licence Creative Commons)");
    }

    private void downloadCurrent() {
        Track t = ctx.player().getCurrent();
        var info = ctx.musicPlayback().info(t);
        if (info.isEmpty()) return;
        offlineButton.setDisable(true);
        toast("Téléchargement de « " + t.title() + " » depuis Jamendo…");
        com.hmood.ui.Async.run(() -> ctx.offline().download(info.get()), (d, error) -> {
            if (error != null) {
                offlineButton.setDisable(false);
                toast(error);
                return;
            }
            ctx.bumpLibrary();
            toast("« " + t.title() + " » est disponible hors ligne dans Ma musique · " + d.license()
                    + " (usage personnel ; respecte la licence de l'artiste).");
            if (t.equals(ctx.player().getCurrent())) updateOfflineButton(t);
        });
    }

    // ------------------------------------------------------------------ fond aux couleurs de la pochette

    private void applyCoverGlow(Track t) {
        javafx.scene.image.Image image = t == null ? null : Covers.image(t.coverPath());
        if (image == null) {
            animateGlow(Color.TRANSPARENT, Color.TRANSPARENT);
            return;
        }
        Runnable apply = () -> {
            if (t != ctx.player().getCurrent()) return; // le morceau a changé entre-temps
            Color[] colors = com.hmood.ui.CoverPalette.extract(image);
            if (colors == null) animateGlow(Color.TRANSPARENT, Color.TRANSPARENT);
            else animateGlow(colors[0], colors[1]);
        };
        if (image.getProgress() >= 1) {
            apply.run();
        } else {
            image.progressProperty().addListener((o, a, p) -> {
                if (p.doubleValue() >= 1) apply.run();
            });
        }
    }

    private void animateGlow(Color a, Color b) {
        if (glowAnimation != null) glowAnimation.stop();
        glowAnimation = new javafx.animation.Timeline(new javafx.animation.KeyFrame(Duration.millis(1400),
                new javafx.animation.KeyValue(glowA, a, javafx.animation.Interpolator.EASE_BOTH),
                new javafx.animation.KeyValue(glowB, b, javafx.animation.Interpolator.EASE_BOTH)));
        glowAnimation.play();
    }

    private void updateGlow() {
        Color a = glowA.get(), b = glowB.get();
        if (a.getOpacity() == 0 && b.getOpacity() == 0) {
            appRoot.setStyle(null);
            playerBar.setStyle(null);
            return;
        }
        appRoot.setStyle("-fx-background-color: -hm-app-bg, "
                + "radial-gradient(center 38% 0%, radius 70%, " + rgba(a, 0.34) + ", transparent), "
                + "radial-gradient(center 100% 100%, radius 65%, " + rgba(b, 0.24) + ", transparent);");
        playerBar.setStyle("-fx-background-color: -hm-player, linear-gradient(to right, " + rgba(a, 0.30) + ", "
                + rgba(b, 0.10) + " 55%, transparent);");
    }

    private static String rgba(Color c, double alpha) {
        return String.format(java.util.Locale.ROOT, "rgba(%d,%d,%d,%.3f)", (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255), (int) Math.round(c.getBlue() * 255), alpha * c.getOpacity());
    }

    /** Indique honnêtement la nature de la lecture (extrait officiel ou flux complet) et le lien vers le service. */
    private void updateSourceLink(Track t) {
        var info = ctx.musicPlayback().info(t);
        boolean audius = t != null && t.filePath().startsWith(com.hmood.catalog.AudiusCatalog.PREFIX);
        var license = info.isEmpty() && !audius ? ctx.offline().licenseOf(t) : java.util.Optional.<String>empty();
        if (license.isPresent()) {
            sourceLink.setText("Hors ligne · Jamendo · " + license.get());
            sourceLink.setOnAction(e -> ctx.openLink("https://www.jamendo.com"));
            sourceLink.setVisible(true);
            sourceLink.setManaged(true);
            return;
        }
        if (info.isEmpty() && !audius) {
            sourceLink.setVisible(false);
            sourceLink.setManaged(false);
            return;
        }
        String provider = info.map(com.hmood.music.service.PlaybackService::providerName).orElse("Audius");
        boolean preview = info.map(e -> e.playback() == com.hmood.music.PlaybackMode.PREVIEW).orElse(false);
        sourceLink.setText(preview ? "Extrait 30 s · Écouter en entier sur " + provider + " ↗" : "Morceau complet · " + provider + " ↗");
        String url = info.map(com.hmood.music.CatalogEntry::externalUrl)
                .orElse("https://audius.co/tracks/" + t.filePath().substring(com.hmood.catalog.AudiusCatalog.PREFIX.length()));
        boolean deezer = info.map(e -> "deezer".equals(e.provider())).orElse(false);
        if (deezer && preview) {
            sourceLink.setText("Extrait 30 s · En entier sur Deezer ↗");
            sourceLink.setOnAction(e -> com.hmood.ui.DeezerPlayerWindow.open(ctx, t.title() + " — " + t.displayArtist(), List.of(info.get())));
        } else {
            sourceLink.setOnAction(e -> ctx.openLink(url));
        }
        sourceLink.setVisible(true);
        sourceLink.setManaged(true);
    }

    private void updateRepeatButton() {
        RepeatMode mode = ctx.player().repeatProperty().get();
        repeatButton.setGraphic(Icons.icon(mode == RepeatMode.ONE ? Icons.REPEAT_ONE : Icons.REPEAT, 18));
        toggleClass(repeatButton, "active", mode != RepeatMode.OFF);
        tooltip(repeatButton, switch (mode) {
            case OFF -> "Répétition désactivée";
            case ALL -> "Répéter la file";
            case ONE -> "Répéter le morceau";
        });
    }

    @FXML private void onPlayPause() { ctx.player().togglePlay(); }
    @FXML private void onNext() { ctx.player().next(); }
    @FXML private void onPrevious() { ctx.player().previous(); }
    @FXML private void onRepeat() { ctx.player().cycleRepeat(); }

    @FXML
    private void onShuffle() {
        ctx.player().shuffleProperty().set(!ctx.player().shuffleProperty().get());
    }

    @FXML
    private void onToggleFavorite() {
        Track current = ctx.player().getCurrent();
        if (current == null || current.isRemote()) {
            return;
        }
        try {
            Track updated = ctx.library().toggleFavorite(current);
            ctx.player().refreshTrack(updated);
            ctx.bumpLibrary();
        } catch (RuntimeException e) {
            toast(e.getMessage());
        }
    }

    @FXML
    private void onShowQueue() {
        ContextMenu menu = new ContextMenu();
        List<Track> queue = ctx.player().getQueue();
        if (queue.isEmpty()) {
            MenuItem empty = new MenuItem("La file d'attente est vide");
            empty.setDisable(true);
            menu.getItems().add(empty);
        } else {
            int currentIndex = ctx.player().currentIndex();
            int from = Math.max(0, currentIndex - 3);
            int to = Math.min(queue.size(), from + 30);
            for (int i = from; i < to; i++) {
                Track t = queue.get(i);
                String prefix = i == currentIndex ? "▶  " : (i + 1) + ".  ";
                MenuItem item = new MenuItem(prefix + t.title() + " — " + t.displayArtist());
                if (i == currentIndex) {
                    item.getStyleClass().add("current-item");
                }
                int target = i;
                item.setOnAction(e -> ctx.player().playAt(target));
                menu.getItems().add(item);
            }
            if (to < queue.size()) {
                MenuItem more = new MenuItem("… " + (queue.size() - to) + " autre(s)");
                more.setDisable(true);
                menu.getItems().add(more);
            }
        }
        menu.show(queueButton, Side.TOP, 0, -8);
    }

    // ------------------------------------------------------------------ notifications

    private void toast(String message) {
        Label toast = Ui.label(message, "toast");
        toast.setWrapText(true);
        toast.setMaxWidth(560);
        toast.setMouseTransparent(true);
        StackPane.setAlignment(toast, Pos.BOTTOM_CENTER);
        StackPane.setMargin(toast, new Insets(0, 0, 26, 0));
        contentPane.getChildren().add(toast);
        FadeTransition in = new FadeTransition(Duration.millis(180), toast);
        in.setFromValue(0);
        in.setToValue(1);
        FadeTransition out = new FadeTransition(Duration.millis(400), toast);
        out.setToValue(0);
        SequentialTransition seq = new SequentialTransition(in, new PauseTransition(Duration.seconds(3.6)), out);
        seq.setOnFinished(e -> contentPane.getChildren().remove(toast));
        seq.play();
    }

    private static void tooltip(Button b, String text) {
        b.setTooltip(new Tooltip(text));
    }

    private static void toggleClass(javafx.scene.Node node, String styleClass, boolean on) {
        if (on && !node.getStyleClass().contains(styleClass)) {
            node.getStyleClass().add(styleClass);
        } else if (!on) {
            node.getStyleClass().remove(styleClass);
        }
    }
}
