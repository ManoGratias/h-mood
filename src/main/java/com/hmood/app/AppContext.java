package com.hmood.app;

import com.hmood.ai.AiAssistantService;
import com.hmood.ai.AiProviderFactory;
import com.hmood.ai.LocalIntentParser;
import com.hmood.catalog.AudiusCatalog;
import com.hmood.catalog.MusicCatalog;
import com.hmood.controller.Navigator;
import com.hmood.music.CatalogCache;
import com.hmood.music.provider.AppleMusicProvider;
import com.hmood.music.provider.AudiusProvider;
import com.hmood.music.provider.DeezerProvider;
import com.hmood.music.provider.MusicBrainzProvider;
import com.hmood.music.service.MusicCatalogService;
import com.hmood.music.service.MusicDiscoveryService;
import com.hmood.music.service.MusicRecommendationService;
import com.hmood.music.service.NewReleasesService;
import com.hmood.music.service.PlaybackService;
import com.hmood.music.service.TrendingService;
import javafx.application.HostServices;
import com.hmood.player.SleepTimerService;
import com.hmood.util.PersistentCookieStore;
import java.util.List;
import java.util.Map;
import com.hmood.database.Database;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.player.PlaybackListener;
import com.hmood.player.PlayerService;
import com.hmood.recommendation.RecommendationEngine;
import com.hmood.recommendation.RuleBasedRecommendationEngine;
import com.hmood.repository.HistoryRepository;
import com.hmood.repository.PlaylistRepository;
import com.hmood.repository.PreferenceRepository;
import com.hmood.repository.TrackRepository;
import com.hmood.service.LibraryService;
import com.hmood.service.MetadataReader;
import com.hmood.service.MoodService;
import com.hmood.service.PlaylistService;
import com.hmood.service.SettingsService;
import com.hmood.ui.ThemeManager;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.stage.Stage;
import javafx.util.Callback;

import java.nio.file.Path;
import java.time.Clock;
import java.util.function.Consumer;

/**
 * Assemblage de l'application (injection de dépendances manuelle) et état partagé
 * entre les écrans : humeur courante, révision de la bibliothèque, navigation, notifications.
 */
public final class AppContext {

    private final Path dataDir;
    private final Database database;
    private final TrackRepository trackRepository;
    private final HistoryRepository historyRepository;
    private final LibraryService libraryService;
    private final PlaylistService playlistService;
    private final MoodService moodService;
    private final SettingsService settingsService;
    private final RecommendationEngine recommendationEngine;
    private final AiAssistantService assistant;
    private final MusicCatalog catalog;
    private final TrendingService trendingService;
    private final NewReleasesService newReleasesService;
    private final MusicCatalogService musicCatalogService;
    private final PlaybackService musicPlayback;
    private final com.hmood.music.provider.JamendoProvider jamendo;
    private final com.hmood.music.service.OfflineService offlineService;
    private final com.hmood.music.service.LyricsService lyricsService;
    private com.hmood.repository.WrappedRepository wrappedRepository;
    private com.hmood.service.FavoriteArtistsService favoriteArtists;
    private com.hmood.player.GlobalMediaKeys mediaKeys;
    private final MusicDiscoveryService discoveryService;
    private final MusicRecommendationService recommendationService;
    private HostServices hostServices;
    private final SleepTimerService sleepTimer;
    private final com.hmood.ai.DjService djService;
    private final com.hmood.music.service.TempoService tempoService;
    private final com.hmood.music.service.AmbienceService ambienceService;
    private final com.hmood.service.FocusTimerService focusTimer;
    private final com.hmood.repository.StatsRepository statsRepository;
    private final PersistentCookieStore cookieStore;
    private final PlayerService playerService;
    private final ThemeManager themeManager;

    private final ObjectProperty<Mood> currentMood = new SimpleObjectProperty<>();
    private final IntegerProperty libraryRevision = new SimpleIntegerProperty();

    private Navigator navigator;
    private Consumer<String> notifier = System.out::println;
    private Stage primaryStage;

    public AppContext(Path dataDir) {
        this.dataDir = dataDir;
        long user = Database.DEFAULT_USER_ID;

        database = new Database(dataDir.resolve("hmood.db"));
        database.initialize();

        trackRepository = new TrackRepository(database, user);
        PlaylistRepository playlistRepository = new PlaylistRepository(database, user);
        historyRepository = new HistoryRepository(database, user);
        PreferenceRepository preferenceRepository = new PreferenceRepository(database, user);

        recommendationEngine = new RuleBasedRecommendationEngine(trackRepository::findAll, Clock.systemDefaultZone());
        libraryService = new LibraryService(trackRepository, new MetadataReader(dataDir.resolve("covers")));
        playlistService = new PlaylistService(playlistRepository, trackRepository, recommendationEngine);
        moodService = new MoodService(historyRepository);
        settingsService = new SettingsService(preferenceRepository);
        catalog = new AudiusCatalog();
        AudiusCatalog audiusCatalog = (AudiusCatalog) catalog;
        DeezerProvider deezer = new DeezerProvider();
        AppleMusicProvider apple = new AppleMusicProvider();
        AudiusProvider audius = new AudiusProvider(audiusCatalog);
        jamendo = new com.hmood.music.provider.JamendoProvider(settingsService::jamendoClientId);
        CatalogCache cache = new CatalogCache(database);
        trendingService = new TrendingService(Map.of(deezer.id(), deezer, apple.id(), apple, audius.id(), audius, jamendo.id(), jamendo), cache);
        newReleasesService = new NewReleasesService(trendingService);
        musicCatalogService = new MusicCatalogService(List.of(deezer, audius, jamendo), cache, trendingService);
        musicPlayback = new PlaybackService(List.of(deezer, apple, audius, jamendo), audiusCatalog);
        discoveryService = new MusicDiscoveryService(deezer, new MusicBrainzProvider(), trendingService);
        recommendationService = new MusicRecommendationService(trackRepository::findAll, deezer, audius, trendingService);
        recommendationService.setUserArtists(settingsService::moodArtists);
        recommendationService.setJamendo(jamendo);
        offlineService = new com.hmood.music.service.OfflineService(database, dataDir.resolve("hors-ligne"), jamendo,
                libraryService, trackRepository);
        lyricsService = new com.hmood.music.service.LyricsService(database);
        tempoService = new com.hmood.music.service.TempoService(database, deezer);
        djService = new com.hmood.ai.DjService(new LocalIntentParser(), deezer, trendingService, recommendationService,
                tempoService, musicPlayback);
        ambienceService = new com.hmood.music.service.AmbienceService(audius, jamendo, trendingService);
        trendingService.startAutoRefresh();
        assistant = new AiAssistantService(AiProviderFactory.create(), new LocalIntentParser(),
                recommendationEngine, trackRepository::findAll, catalog);

        playerService = new PlayerService();
        playerService.setRemoteResolver(musicPlayback::resolve);
        playerService.volumeProperty().set(settingsService.volume());
        playerService.addListener(new PlaybackListener() {
            @Override
            public void onStarted(Track track) {
                if (!track.isRemote()) {
                    safely(() -> trackRepository.incrementPlayCount(track.id()));
                }
            }

            @Override
            public void onFinished(Track track, long listenedMs, boolean completed) {
                if (listenedMs >= 1000 && !track.isRemote()) {
                    safely(() -> historyRepository.recordPlay(track.id(), listenedMs, completed));
                } else if (listenedMs >= 10_000 && track.isRemote()) {
                    safely(() -> recordStream(track, listenedMs, completed));
                }
            }
        });

        sleepTimer = new SleepTimerService(playerService);
        wrappedRepository = new com.hmood.repository.WrappedRepository(database, user);
        favoriteArtists = new com.hmood.service.FavoriteArtistsService(
                new com.hmood.repository.FavoriteArtistsRepository(database, user), name -> {
                    try {
                        return discoveryService.searchArtists(name);
                    } catch (com.hmood.catalog.CatalogException e) {
                        throw new RuntimeException(e);
                    }
                });
        mediaKeys = new com.hmood.player.GlobalMediaKeys(playerService::togglePlay, playerService::next,
                playerService::previous, playerService::pause);
        if (settingsService.globalMediaKeys()) mediaKeys.start();
        statsRepository = new com.hmood.repository.StatsRepository(database, user);
        focusTimer = new com.hmood.service.FocusTimerService(playerService, settingsService);
        focusTimer.setNotifier(this::notify);
        playerService.crossfadeSecondsProperty().set(settingsService.crossfadeSeconds());
        playerService.setBpmLookup(t -> musicPlayback.info(t)
                .flatMap(e -> tempoService.cached(e.key()))
                .map(com.hmood.music.service.TempoService.Analysis::bpm)
                .orElse(null));
        cookieStore = new PersistentCookieStore(dataDir.resolve("web-cookies.json"));
        java.net.CookieHandler.setDefault(new java.net.CookieManager(cookieStore, java.net.CookiePolicy.ACCEPT_ALL));

        themeManager = new ThemeManager(settingsService.theme());
        int cycleSeconds = settingsService.themeCycleSeconds();
        if (cycleSeconds > 0) {
            javafx.application.Platform.runLater(() -> themeManager.setAutoCycle(cycleSeconds));
        }
        currentMood.set(moodService.todayMood().orElse(null));
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            notify("Historique non enregistré : " + e.getMessage());
        }
    }

    /** Fabrique de contrôleurs FXML : injecte le contexte dans les constructeurs (AppContext). */
    public Callback<Class<?>, Object> controllerFactory() {
        return type -> {
            try {
                try {
                    return type.getConstructor(AppContext.class).newInstance(this);
                } catch (NoSuchMethodException e) {
                    return type.getDeclaredConstructor().newInstance();
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Contrôleur impossible à créer : " + type.getName(), e);
            }
        };
    }

    /** Écoute en streaming gardée dans l'historique local (pour le bilan mensuel). */
    private void recordStream(Track track, long listenedMs, boolean completed) {
        var info = musicPlayback.info(track);
        String provider = info.map(com.hmood.music.CatalogEntry::provider).orElse("audius");
        String externalId = info.map(com.hmood.music.CatalogEntry::externalId).orElse(
                track.filePath().startsWith(AudiusCatalog.PREFIX) ? track.filePath().substring(AudiusCatalog.PREFIX.length()) : null);
        String genre = info.map(com.hmood.music.CatalogEntry::genre).orElse(track.genre());
        Mood mood = track.mood() != null ? track.mood() : getCurrentMood();
        wrappedRepository.recordStream(provider, externalId, track.title(), track.displayArtist(), genre, track.coverPath(),
                mood == null ? null : mood.name(), listenedMs, completed);
    }

    /** Active ou coupe les touches multimédia globales ; renvoie un message d'état lisible. */
    public String setGlobalMediaKeys(boolean on) {
        settingsService.setGlobalMediaKeys(on);
        if (!com.hmood.player.GlobalMediaKeys.supported()) return "Touches multimédia globales : Windows uniquement.";
        if (!on) {
            mediaKeys.stop();
            return "Touches multimédia : seulement quand la fenêtre H MOOD est active.";
        }
        boolean ok = mediaKeys.start();
        if (!ok) return "Touches multimédia indisponibles : un autre programme les utilise déjà.";
        return mediaKeys.failures().isEmpty()
                ? "Touches multimédia actives, même quand H MOOD est en arrière-plan."
                : "Touches multimédia actives, sauf : " + String.join(", ", mediaKeys.failures()) + " (déjà utilisées par un autre programme).";
    }

    public com.hmood.player.GlobalMediaKeys mediaKeys() { return mediaKeys; }
    public com.hmood.repository.WrappedRepository wrapped() { return wrappedRepository; }
    public com.hmood.service.FavoriteArtistsService favoriteArtists() { return favoriteArtists; }

    /** Ajoute / retire un artiste des favoris (en arrière-plan) puis notifie. */
    public void toggleFavoriteArtist(com.hmood.music.CatalogEntry e, Runnable onDone) {
        com.hmood.ui.Async.run(() -> favoriteArtists.toggle(e), (t, error) -> {
            if (error != null) {
                notify(error);
            } else {
                notify(t.favorite() ? "♥ " + t.artist().name() + " ajouté à tes artistes favoris"
                        : t.artist().name() + " retiré de tes artistes favoris");
                favoritesRevision.set(favoritesRevision.get() + 1);
            }
            if (onDone != null) onDone.run();
        });
    }

    private final javafx.beans.property.IntegerProperty favoritesRevision = new javafx.beans.property.SimpleIntegerProperty();

    /** Change à chaque ajout / retrait d'artiste favori. */
    public javafx.beans.property.IntegerProperty favoritesRevisionProperty() { return favoritesRevision; }

    public void shutdown() {
        if (mediaKeys != null) mediaKeys.stop();
        cookieStore.save();
        trendingService.shutdown();
        playerService.dispose();
    }

    // ----------------------------------------------------------------- état partagé

    public void notify(String message) {
        if (Platform.isFxApplicationThread()) {
            notifier.accept(message);
        } else {
            Platform.runLater(() -> notifier.accept(message));
        }
    }

    public void setNotifier(Consumer<String> notifier) {
        this.notifier = notifier;
    }

    public Navigator navigator() {
        return navigator;
    }

    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
    }

    public Stage primaryStage() {
        return primaryStage;
    }

    public void setPrimaryStage(Stage primaryStage) {
        this.primaryStage = primaryStage;
    }

    public ObjectProperty<Mood> currentMoodProperty() {
        return currentMood;
    }

    public Mood getCurrentMood() {
        return currentMood.get();
    }

    public void setCurrentMood(Mood mood) {
        currentMood.set(mood);
    }

    public IntegerProperty libraryRevisionProperty() {
        return libraryRevision;
    }

    /** À appeler après toute modification de la bibliothèque / des playlists. */
    public void bumpLibrary() {
        libraryRevision.set(libraryRevision.get() + 1);
    }

    // ----------------------------------------------------------------- services

    public Path dataDir() { return dataDir; }
    public Database database() { return database; }
    public HistoryRepository history() { return historyRepository; }
    public TrackRepository tracks() { return trackRepository; }
    public LibraryService library() { return libraryService; }
    public PlaylistService playlists() { return playlistService; }
    public MoodService moods() { return moodService; }
    public SettingsService settings() { return settingsService; }
    public RecommendationEngine reco() { return recommendationEngine; }
    public AiAssistantService assistant() { return assistant; }
    public MusicCatalog catalog() { return catalog; }
    public TrendingService trending() { return trendingService; }
    public NewReleasesService newReleases() { return newReleasesService; }
    public MusicCatalogService musicCatalog() { return musicCatalogService; }
    public PlaybackService musicPlayback() { return musicPlayback; }
    public com.hmood.music.provider.JamendoProvider jamendo() { return jamendo; }
    public com.hmood.music.service.OfflineService offline() { return offlineService; }
    public com.hmood.music.service.LyricsService lyrics() { return lyricsService; }
    public MusicDiscoveryService discovery() { return discoveryService; }
    public MusicRecommendationService recommendations() { return recommendationService; }

    public void setHostServices(HostServices hostServices) { this.hostServices = hostServices; }
    public SleepTimerService sleepTimer() { return sleepTimer; }
    public com.hmood.ai.DjService dj() { return djService; }
    public com.hmood.music.service.AmbienceService ambience() { return ambienceService; }
    public com.hmood.service.FocusTimerService focusTimer() { return focusTimer; }
    public com.hmood.repository.StatsRepository stats() { return statsRepository; }
    public void saveWebCookies() { cookieStore.save(); }
    public void clearWebCookies() { cookieStore.clearAll(); }

    /** Ouvre un lien officiel (page du morceau sur le service autorisé) dans le navigateur. */
    public void openLink(String url) {
        if (url != null && hostServices != null) hostServices.showDocument(url);
    }
    public PlayerService player() { return playerService; }
    public ThemeManager themes() { return themeManager; }
}
