package com.hmood.music.service;

import com.hmood.catalog.AudiusCatalog;
import com.hmood.database.Database;
import com.hmood.music.CatalogEntry;
import com.hmood.music.TempoAnalyzer;
import com.hmood.music.provider.DeezerProvider;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * Tempo et énergie des morceaux en ligne : Audius fournit le BPM des artistes ; pour Deezer, le BPM
 * est estimé localement à partir de l'extrait officiel (téléchargé en mémoire, analysé, puis oublié).
 * Résultats conservés en base (table track_analysis) pour ne jamais réanalyser un titre.
 */
public final class TempoService {

    public record Analysis(double bpm, double energy, String method) {
    }

    private static final int MAX_BYTES = 2_000_000;

    private final Database db;
    private final DeezerProvider deezer;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final Map<String, Analysis> memory = new ConcurrentHashMap<>();

    public TempoService(Database db, DeezerProvider deezer) {
        this.db = db;
        this.deezer = deezer;
    }

    /** Analyse plusieurs titres en parallèle (4 à la fois). {@code progress} reçoit le nombre de titres traités. */
    public Map<String, Analysis> analyzeAll(List<CatalogEntry> entries, IntConsumer progress) {
        Map<String, Analysis> out = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "hmood-tempo");
            t.setDaemon(true);
            return t;
        });
        AtomicInteger done = new AtomicInteger();
        try {
            List<Future<?>> futures = entries.stream().map(e -> pool.submit(() -> {
                analyze(e).ifPresent(a -> out.put(e.key(), a));
                progress.accept(done.incrementAndGet());
            })).<Future<?>>map(f -> f).toList();
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (Exception ignored) {
                    // un titre non analysable est simplement placé sans tempo
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return out;
    }

    /** Tempo déjà connu (mémoire ou base), sans aucun accès réseau. */
    public Optional<Analysis> cached(String key) {
        Analysis known = memory.get(key);
        if (known != null) return Optional.of(known);
        Optional<Analysis> stored = load(key);
        stored.ifPresent(a -> memory.put(key, a));
        return stored;
    }

    public Optional<Analysis> analyze(CatalogEntry e) {
        Analysis known = memory.get(e.key());
        if (known != null) return Optional.of(known);
        Optional<Analysis> stored = load(e.key());
        if (stored.isPresent()) {
            memory.put(e.key(), stored.get());
            return stored;
        }
        Analysis a = null;
        try {
            if ("audius".equals(e.provider())) {
                Double bpm = AudiusCatalog.bpmHint(e.externalId());
                if (bpm != null && bpm > 0) {
                    double energy = e.mood() == null ? 0.5 : e.mood().energy() / 5.0;
                    a = new Analysis(fold(bpm), energy, "BPM indiqué par l'artiste (Audius)");
                }
            } else if (DeezerProvider.ID.equals(e.provider()) && e.playable()) {
                byte[] mp3 = download(deezer.resolve(e));
                TempoAnalyzer.Result r = TempoAnalyzer.analyzeMp3(mp3);
                if (r.bpm() > 0) a = new Analysis(r.bpm(), r.energy(), "BPM estimé par H MOOD (extrait)");
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
        if (a == null) return Optional.empty();
        memory.put(e.key(), a);
        save(e.key(), a);
        return Optional.of(a);
    }

    private static double fold(double bpm) {
        while (bpm < 70) bpm *= 2;
        while (bpm > 170) bpm /= 2;
        return bpm;
    }

    private byte[] download(String url) throws Exception {
        HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        try (InputStream in = response.body()) {
            return in.readNBytes(MAX_BYTES);
        }
    }

    private Optional<Analysis> load(String key) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT bpm, energy, method FROM track_analysis WHERE cache_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Analysis(rs.getDouble(1), rs.getDouble(2), rs.getString(3))) : Optional.empty();
            }
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void save(String key, Analysis a) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO track_analysis (cache_key, bpm, energy, method) VALUES (?, ?, ?, ?)
                ON CONFLICT (cache_key) DO UPDATE SET bpm = excluded.bpm, energy = excluded.energy, method = excluded.method
                """)) {
            ps.setString(1, key);
            ps.setDouble(2, a.bpm());
            ps.setDouble(3, a.energy());
            ps.setString(4, a.method());
            ps.executeUpdate();
        } catch (Exception ignored) {
            // cache facultatif
        }
    }
}
