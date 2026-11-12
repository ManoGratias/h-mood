package com.hmood.repository;

import com.hmood.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Bilan mensuel « H MOOD Wrapped », calculé uniquement sur l'historique local : morceaux de « Ma musique »
 * (listening_history) et morceaux écoutés en streaming (stream_history). Rien n'est envoyé en ligne.
 */
public final class WrappedRepository {

    /** Un titre du classement mensuel. */
    public record TopTrack(String title, String artist, String cover, int plays) {
    }

    /** Une valeur nommée. */
    public record Item(String label, double value) {
    }

    /** Bilan complet d'un mois. */
    public record Wrapped(YearMonth month, double minutes, int plays, int artists, int tracks, List<Item> topArtists,
                          List<TopTrack> topTracks, List<Item> genres, List<Item> moods, String bestDay,
                          double bestDayMinutes, List<Item> dayParts) {
        public boolean isEmpty() {
            return plays == 0;
        }
    }

    private static final String PLAYS = """
            WITH plays AS (
                SELECT t.title AS title, COALESCE(ar.name, 'Artiste inconnu') AS artist, t.genre AS genre,
                       t.cover_path AS cover, h.played_at AS played_at, h.listened_ms AS ms
                FROM listening_history h JOIN tracks t ON t.id = h.track_id LEFT JOIN artists ar ON ar.id = t.artist_id
                WHERE h.user_id = ?
                UNION ALL
                SELECT title, COALESCE(artist, 'Artiste inconnu'), genre, cover_url, played_at, listened_ms
                FROM stream_history WHERE user_id = ?
            ), month AS (SELECT * FROM plays WHERE strftime('%Y-%m', played_at, 'localtime') = ?)
            """;

    private final Database db;
    private final long userId;

    public WrappedRepository(Database db, long userId) {
        this.db = db;
        this.userId = userId;
    }

    /** Enregistre une écoute en streaming (titre, artiste, durée écoutée) dans l'historique local. */
    public void recordStream(String provider, String externalId, String title, String artist, String genre, String cover,
                             String mood, long listenedMs, boolean completed) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO stream_history (user_id, provider, external_id, title, artist, genre, cover_url, mood, listened_ms, completed)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            ps.setLong(1, userId);
            ps.setString(2, provider);
            ps.setString(3, externalId);
            ps.setString(4, title);
            ps.setString(5, artist);
            ps.setString(6, genre);
            ps.setString(7, cover);
            ps.setString(8, mood);
            ps.setLong(9, listenedMs);
            ps.setInt(10, completed ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Historique d'écoute non enregistré", e);
        }
    }

    /** Mois ayant au moins une écoute, du plus récent au plus ancien (le mois en cours est toujours inclus). */
    public List<YearMonth> months() {
        List<YearMonth> out = new ArrayList<>();
        out.add(YearMonth.now());
        for (Item i : items(PLAYS.replace("strftime('%Y-%m', played_at, 'localtime') = ?", "? IS NOT NULL")
                + "SELECT DISTINCT strftime('%Y-%m', played_at, 'localtime') AS label, 0 AS v FROM month ORDER BY label DESC", "")) {
            YearMonth ym = YearMonth.parse(i.label());
            if (!out.contains(ym)) out.add(ym);
        }
        return out;
    }

    public Wrapped month(YearMonth ym) {
        String key = ym.toString();
        double[] totals = new double[4];
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(PLAYS + """
                SELECT COALESCE(SUM(ms), 0) / 60000.0, COUNT(*), COUNT(DISTINCT LOWER(artist)),
                       COUNT(DISTINCT LOWER(title || '|' || artist)) FROM month""")) {
            bind(ps, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) for (int i = 0; i < 4; i++) totals[i] = rs.getDouble(i + 1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Bilan impossible à calculer", e);
        }
        List<Item> artists = items(PLAYS + """
                SELECT artist AS label, COUNT(*) AS v FROM month WHERE artist <> 'Artiste inconnu'
                GROUP BY LOWER(artist) ORDER BY v DESC, SUM(ms) DESC LIMIT 5""", key);
        List<TopTrack> tracks = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(PLAYS + """
                SELECT title, artist, MAX(cover), COUNT(*) AS n FROM month GROUP BY LOWER(title || '|' || artist)
                ORDER BY n DESC, SUM(ms) DESC LIMIT 5""")) {
            bind(ps, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) tracks.add(new TopTrack(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4)));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Bilan impossible à calculer", e);
        }
        List<Item> genres = items(PLAYS + """
                SELECT genre AS label, SUM(ms) / 60000.0 AS v FROM month WHERE genre IS NOT NULL AND TRIM(genre) <> ''
                GROUP BY LOWER(genre) ORDER BY v DESC LIMIT 3""", key);
        List<Item> days = items(PLAYS + """
                SELECT date(played_at, 'localtime') AS label, SUM(ms) / 60000.0 AS v FROM month
                GROUP BY label ORDER BY v DESC LIMIT 1""", key);
        List<Item> parts = items(PLAYS + """
                SELECT CASE WHEN CAST(strftime('%H', played_at, 'localtime') AS INTEGER) BETWEEN 6 AND 11 THEN 'le matin'
                            WHEN CAST(strftime('%H', played_at, 'localtime') AS INTEGER) BETWEEN 12 AND 17 THEN 'l''après-midi'
                            WHEN CAST(strftime('%H', played_at, 'localtime') AS INTEGER) BETWEEN 18 AND 23 THEN 'le soir'
                            ELSE 'la nuit' END AS label, SUM(ms) / 60000.0 AS v
                FROM month GROUP BY label ORDER BY v DESC""", key);
        List<Item> moods = new ArrayList<>();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT mood, COUNT(*) AS n FROM mood_history
                WHERE user_id = ? AND strftime('%Y-%m', created_at, 'localtime') = ? GROUP BY mood ORDER BY n DESC""")) {
            ps.setLong(1, userId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) moods.add(new Item(rs.getString(1), rs.getInt(2)));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Bilan impossible à calculer", e);
        }
        return new Wrapped(ym, totals[0], (int) totals[1], (int) totals[2], (int) totals[3], artists, tracks, genres,
                moods, days.isEmpty() ? null : days.get(0).label(), days.isEmpty() ? 0 : days.get(0).value(), parts);
    }

    private void bind(PreparedStatement ps, String key) throws SQLException {
        ps.setLong(1, userId);
        ps.setLong(2, userId);
        ps.setString(3, key);
    }

    private List<Item> items(String sql, String key) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, key);
            List<Item> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new Item(rs.getString("label"), rs.getDouble("v")));
            }
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Bilan impossible à calculer", e);
        }
    }
}
