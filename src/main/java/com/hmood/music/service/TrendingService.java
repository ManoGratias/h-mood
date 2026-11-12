package com.hmood.music.service;

import com.hmood.catalog.CatalogException;
import com.hmood.music.CatalogCache;
import com.hmood.music.CatalogEntry;
import com.hmood.music.CatalogEntry.Kind;
import com.hmood.music.ChartDefinition;
import com.hmood.music.ChartResult;
import com.hmood.music.provider.MusicChartProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Classements réels (« Trending Now »). Chaque classement indique sa source exacte ; les données sont
 * mises en cache (SQLite) une heure, rafraîchies automatiquement pour les principaux classements,
 * et l'ancien cache est affiché — avec un avertissement — si le réseau ou le quota fait défaut.
 */
public final class TrendingService {

    public static final Duration TTL = Duration.ofMinutes(60);

    public static final List<ChartDefinition> CHARTS = List.of(
            chart("jamendo", "Jamendo · populaires", "jamendo", "popular", Kind.TRACK, "Jamendo · morceaux les plus écoutés de la semaine (complets, Creative Commons)", "#2AB7CA", "#FE4A49"),
            chart("global", "Top 50 Monde", "deezer", "playlist:3155776842", Kind.TRACK, "Deezer · playlist officielle « Top Worldwide »", "#45BFEF", "#1D4E89"),
            chart("france", "Top 50 France", "deezer", "playlist:1109890291", Kind.TRACK, "Deezer · playlist officielle « Top France »", "#3A7BD5", "#E53935"),
            chart("usa", "Top 50 USA", "apple", "most-played:us", Kind.TRACK, "Apple Music · Top Songs États-Unis", "#FF5F6D", "#3A4556"),
            chart("uk", "Top 50 Royaume-Uni", "apple", "most-played:gb", Kind.TRACK, "Apple Music · Top Songs Royaume-Uni", "#8E54E9", "#1D4E89"),
            chart("rap-fr", "Rap français", "deezer", "playlist:1071669561", Kind.TRACK, "Deezer · playlist éditoriale « Actu Rap »", "#F7971E", "#5D1A1A"),
            chart("hiphop", "Hip-Hop", "deezer", "chart:116:tracks", Kind.TRACK, "Deezer · classement Rap/Hip-Hop (pays de connexion)", "#E5533C", "#302019"),
            chart("afro", "Afrobeat", "deezer", "playlist:1440614715", Kind.TRACK, "Deezer · playlist éditoriale « Afro Hits »", "#FFD36E", "#E5533C"),
            chart("rnb", "R&B", "deezer", "chart:165:tracks", Kind.TRACK, "Deezer · classement R&B (pays de connexion)", "#FF8FB1", "#B5477A"),
            chart("pop", "Pop", "deezer", "chart:132:tracks", Kind.TRACK, "Deezer · classement Pop (pays de connexion)", "#FFBD91", "#FF8A5B"),
            chart("electro", "Électro", "deezer", "chart:106:tracks", Kind.TRACK, "Deezer · classement Électro (pays de connexion)", "#83DFFF", "#8E54E9"),
            chart("viral", "Hits viraux", "deezer", "playlist:8958697522", Kind.TRACK, "Deezer · playlist éditoriale « Viral Dance »", "#C3A6FF", "#FF5F6D"),
            chart("new", "Nouveautés", "deezer", "playlist:1478649355", Kind.TRACK, "Deezer · playlist éditoriale « Les nouveautés du vendredi »", "#7FD8BE", "#3A8D99"),
            chart("artists", "Artistes tendance", "deezer", "chart:0:artists", Kind.ARTIST, "Deezer · classement des artistes (pays de connexion)", "#BDEFFF", "#45BFEF"),
            chart("albums", "Albums tendance", "deezer", "chart:0:albums", Kind.ALBUM, "Deezer · classement des albums (pays de connexion)", "#FFE0C2", "#FFA66F"),
            chart("ci", "Top Côte d'Ivoire", "deezer", "playlist:1362497945", Kind.TRACK, "Deezer Charts · « Top Ivory Coast »", "#F7971E", "#2E7D32"),
            chart("nigeria", "Top Nigeria", "deezer", "playlist:1362516565", Kind.TRACK, "Deezer Charts · « Top Nigeria »", "#7FD8BE", "#1D4E89"),
            chart("senegal", "Top Sénégal", "deezer", "playlist:1362523075", Kind.TRACK, "Deezer Charts · « Top Senegal »", "#45BFEF", "#2E7D32"),
            chart("south-africa", "Top Afrique du Sud", "deezer", "playlist:1362528775", Kind.TRACK, "Deezer Charts · « Top South Africa »", "#8E54E9", "#F2A65A"),
            chart("rap-ci", "Rap ivoirien", "deezer", "playlist:8744994962", Kind.TRACK, "Deezer Côte d'Ivoire · « 100% RAP IVOIRE »", "#FF8A5B", "#5D1A1A"),
            chart("abidjan", "Ambiance Abidjan", "deezer", "playlist:12331056071", Kind.TRACK, "Deezer · « Ambiance Abidjan » (zouglou, coupé-décalé)", "#FFD36E", "#E5533C"),
            chart("afro-rnb", "R&B africain", "deezer", "playlist:14832350703", Kind.TRACK, "Deezer · « Afro Miel »", "#FF8FB1", "#B5477A"),
            chart("rap-us", "Rap US", "deezer", "playlist:1677006641", Kind.TRACK, "Deezer · « Hot Urban »", "#E5533C", "#302019"),
            chart("zouk", "Zouk & Konpa", "deezer", "playlist:10349775862", Kind.TRACK, "Deezer · « Hits Zouk & Konpa »", "#FF8FB1", "#F7971E"),
            chart("indie", "Indépendants", "audius", "trending", Kind.TRACK, "Audius · tendances de la semaine (morceaux complets)", "#7F8EA3", "#45BFEF"));

    private static ChartDefinition chart(String key, String label, String provider, String ref, Kind kind, String source,
                                         String a, String b) {
        return new ChartDefinition(key, label, provider, ref, kind, source, a, b);
    }

    private final Map<String, MusicChartProvider> providers;
    private final CatalogCache cache;
    private ScheduledExecutorService scheduler;

    public TrendingService(Map<String, MusicChartProvider> providers, CatalogCache cache) {
        this.providers = providers;
        this.cache = cache;
    }

    public Optional<ChartDefinition> definition(String key) {
        return CHARTS.stream().filter(c -> c.key().equals(key)).findFirst();
    }

    /** Bloquant. {@code force} ignore le cache encore valide. */
    public ChartResult chart(String key, boolean force) throws CatalogException {
        ChartDefinition def = definition(key).orElseThrow(() -> new CatalogException("Classement inconnu : " + key));
        return fetch(def.label(), def.source(), "chart:" + def.key(), def.providerId(), force,
                () -> providers.get(def.providerId()).chart(def.chartRef()));
    }

    /** Récupère via le cache (TTL) avec repli sur l'ancien cache en cas d'erreur. */
    ChartResult fetch(String title, String source, String cacheKey, String providerId, boolean force,
                      Fetcher fetcher) throws CatalogException {
        Optional<CatalogCache.Entry> cached = cache.get(cacheKey);
        if (!force && cached.isPresent() && cached.get().fetchedAt().isAfter(Instant.now().minus(TTL))) {
            return new ChartResult(title, source, cached.get().entries(), cached.get().fetchedAt(), true, null);
        }
        try {
            List<CatalogEntry> fresh = fetcher.fetch();
            Instant at = cache.put(cacheKey, providerId, fresh);
            return new ChartResult(title, source, fresh, at, false, null);
        } catch (CatalogException e) {
            if (cached.isPresent()) {
                return new ChartResult(title, source, cached.get().entries(), cached.get().fetchedAt(), true,
                        e.getMessage() + " — affichage de la dernière version enregistrée");
            }
            throw e;
        }
    }

    /** Récupération mise en cache (TTL 1 h) d'une liste quelconque d'un fournisseur. */
    public ChartResult fetchPublic(String title, String source, String cacheKey, String providerId, Fetcher fetcher)
            throws CatalogException {
        return fetch(title, source, cacheKey, providerId, false, fetcher);
    }

    @FunctionalInterface
    public interface Fetcher {
        List<CatalogEntry> fetch() throws CatalogException;
    }

    /** Actualisation périodique des principaux classements tant que l'application est ouverte. */
    public void startAutoRefresh() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "hmood-charts-refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            for (String key : List.of("global", "france", "usa", "new")) {
                try {
                    chart(key, false);
                } catch (Exception ignored) {
                    // réseau indisponible : nouvel essai au prochain cycle
                }
            }
        }, 5, TTL.toMinutes(), TimeUnit.MINUTES);
    }

    public void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}
