package com.hmood.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hmood.catalog.CatalogException;
import com.hmood.database.Database;
import com.hmood.model.Track;
import com.hmood.music.ApiClient;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paroles via LRCLIB (lrclib.net) : base ouverte et gratuite de paroles, synchronisées (format LRC) quand
 * elles existent. Seuls le titre, l'artiste et la durée sont envoyés. Résultats mis en cache dans SQLite.
 */
public final class LyricsService {

    /** Une ligne synchronisée. */
    public record Line(long timeMs, String text) {
    }

    /** Paroles d'un morceau : {@code lines} vide si seules les paroles brutes existent. */
    public record Lyrics(List<Line> lines, String plain, boolean instrumental, String source) {
        public boolean synced() {
            return !lines.isEmpty();
        }

        public boolean isEmpty() {
            return !instrumental && lines.isEmpty() && (plain == null || plain.isBlank());
        }
    }

    private static final String BASE = "https://lrclib.net/api";
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private final Database db;
    private final ApiClient api = new ApiClient("LRCLIB", 200);

    public LyricsService(Database db) {
        this.db = db;
    }

    /** Bloquant. */
    public Lyrics find(Track t) throws CatalogException {
        String artist = t.displayArtist() == null ? "" : t.displayArtist();
        String title = t.title() == null ? "" : t.title();
        String key = (artist + "|" + clean(title)).toLowerCase(Locale.ROOT);
        Optional<Lyrics> cached = cached(key);
        if (cached.isPresent()) return cached.get();

        JsonNode hit = null;
        long seconds = Math.round(t.durationMs() / 1000.0);
        if (seconds > 0) {
            try {
                hit = api.get(BASE + "/get?artist_name=" + ApiClient.enc(firstArtist(artist)) + "&track_name="
                        + ApiClient.enc(clean(title)) + "&duration=" + seconds);
            } catch (CatalogException notFound) {
                hit = null; // 404 : on passe à la recherche
            }
        }
        if (hit == null || hit.isMissingNode()) {
            hit = bestMatch(api.get(BASE + "/search?track_name=" + ApiClient.enc(clean(title))
                    + "&artist_name=" + ApiClient.enc(firstArtist(artist))), seconds);
        }
        if (hit == null) {
            hit = bestMatch(api.get(BASE + "/search?q=" + ApiClient.enc(firstArtist(artist) + " " + clean(title))), seconds);
        }
        if (hit == null) {
            // aucune version de même durée : on garde les paroles mais sans synchronisation (autre version du titre)
            JsonNode loose = bestMatch(api.get(BASE + "/search?q=" + ApiClient.enc(firstArtist(artist) + " " + clean(title))), 0);
            if (loose != null && sameArtist(loose, artist)) {
                String plain = text(loose, "plainLyrics");
                if (plain == null) plain = String.join("\n", parse(loose.path("syncedLyrics").asText("")).stream().map(Line::text).toList());
                Lyrics l = new Lyrics(List.of(), plain, loose.path("instrumental").asBoolean(false), "LRCLIB");
                store(key, l);
                return l;
            }
        }
        Lyrics lyrics = hit == null
                ? new Lyrics(List.of(), null, false, "LRCLIB")
                : new Lyrics(parse(hit.path("syncedLyrics").asText("")), text(hit, "plainLyrics"),
                hit.path("instrumental").asBoolean(false), "LRCLIB");
        store(key, lyrics);
        return lyrics;
    }

    /** Index de la ligne à afficher pour une position donnée (−1 avant la première ligne). */
    public static int indexAt(List<Line> lines, long positionMs) {
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs() <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    /** Format LRC : « [mm:ss.xx] texte » (plusieurs horodatages possibles par ligne). */
    static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null) return out;
        for (String raw : lrc.split("\\R")) {
            Matcher m = TIME.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                String frac = m.group(3) == null ? "0" : m.group(3);
                long ms = Long.parseLong(frac) * (frac.length() == 1 ? 100 : frac.length() == 2 ? 10 : 1);
                times.add(Long.parseLong(m.group(1)) * 60_000 + Long.parseLong(m.group(2)) * 1000 + ms);
                end = m.end();
            }
            String text = raw.substring(end).trim();
            for (long time : times) out.add(new Line(time, text));
        }
        out.sort(java.util.Comparator.comparingLong(Line::timeMs));
        return out;
    }

    /** « Titre (feat. X) [Remix] - Radio Edit » → « Titre ». */
    static String clean(String title) {
        String t = title.replaceAll("\\s*[(\\[][^)\\]]*(feat|ft\\.|with|remaster|version|edit|live|prod)[^)\\]]*[)\\]]", "");
        t = t.replaceAll("(?i)\\s+-\\s+(radio edit|remaster.*|single version|live.*)$", "");
        return t.trim().isEmpty() ? title : t.trim();
    }

    private static String firstArtist(String artist) {
        String a = artist.split("(?i)\\s*(,|&| feat\\.? | ft\\.? | x | et )\\s*")[0].trim();
        return a.isEmpty() ? artist : a;
    }

    private static boolean sameArtist(JsonNode n, String artist) {
        String a = n.path("artistName").asText("").toLowerCase(Locale.ROOT);
        String b = firstArtist(artist).toLowerCase(Locale.ROOT);
        return !b.isBlank() && (a.contains(b) || b.contains(a));
    }

    private static JsonNode bestMatch(JsonNode results, long seconds) {
        JsonNode best = null;
        double bestScore = Double.MAX_VALUE;
        for (JsonNode n : results) {
            boolean synced = !n.path("syncedLyrics").asText("").isBlank();
            boolean plain = !n.path("plainLyrics").asText("").isBlank();
            if (!synced && !plain && !n.path("instrumental").asBoolean(false)) continue;
            double diff = seconds > 0 ? Math.abs(n.path("duration").asDouble(0) - seconds) : 0;
            if (seconds > 0 && diff > 15) continue; // autre version du morceau
            double score = diff + (synced ? 0 : 20);
            if (score < bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best;
    }

    private Optional<Lyrics> cached(String key) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT synced, plain, instrumental FROM lyrics_cache WHERE cache_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Lyrics(parse(rs.getString(1)), rs.getString(2), rs.getInt(3) == 1, "LRCLIB"));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private void store(String key, Lyrics l) {
        StringBuilder lrc = new StringBuilder();
        for (Line line : l.lines()) {
            long ms = line.timeMs();
            lrc.append(String.format("[%02d:%02d.%02d] %s%n", ms / 60_000, (ms / 1000) % 60, (ms % 1000) / 10, line.text()));
        }
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO lyrics_cache (cache_key, synced, plain, instrumental, fetched_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (cache_key) DO UPDATE SET synced = excluded.synced, plain = excluded.plain,
                    instrumental = excluded.instrumental, fetched_at = excluded.fetched_at
                """)) {
            ps.setString(1, key);
            ps.setString(2, lrc.toString());
            ps.setString(3, l.plain());
            ps.setInt(4, l.instrumental() ? 1 : 0);
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            // cache facultatif
        }
    }

    private static String text(JsonNode n, String field) {
        String v = n.path(field).asText("");
        return v.isBlank() ? null : v;
    }
}
