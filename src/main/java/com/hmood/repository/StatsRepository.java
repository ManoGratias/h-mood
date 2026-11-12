package com.hmood.repository;

import com.hmood.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Statistiques d'écoute calculées sur l'historique local (ADN musical). */
public final class StatsRepository {

    /** Une valeur nommée (genre, artiste, humeur, jour…). */
    public record Stat(String label, double value) {
    }

    private final Database db;
    private final long userId;

    public StatsRepository(Database db, long userId) {
        this.db = db;
        this.userId = userId;
    }

    /** Genres par temps d'écoute (minutes). */
    public List<Stat> genresByListening(int limit) {
        return stats("""
                SELECT COALESCE(NULLIF(TRIM(t.genre), ''), 'Non renseigné') AS label, SUM(h.listened_ms) / 60000.0 AS v
                FROM listening_history h JOIN tracks t ON t.id = h.track_id
                WHERE h.user_id = ? GROUP BY label ORDER BY v DESC LIMIT ?""", userId, limit);
    }

    /** Genres présents dans la bibliothèque (nombre de morceaux), utilisé tant qu'il n'y a pas d'historique. */
    public List<Stat> genresInLibrary(int limit) {
        return stats("""
                SELECT COALESCE(NULLIF(TRIM(genre), ''), 'Non renseigné') AS label, COUNT(*) AS v
                FROM tracks WHERE ? = ? GROUP BY label ORDER BY v DESC LIMIT ?""", 1, 1, limit);
    }

    public List<Stat> topArtists(int limit) {
        return stats("""
                SELECT COALESCE(ar.name, 'Artiste inconnu') AS label, COUNT(*) AS v
                FROM listening_history h JOIN tracks t ON t.id = h.track_id LEFT JOIN artists ar ON ar.id = t.artist_id
                WHERE h.user_id = ? GROUP BY label ORDER BY v DESC LIMIT ?""", userId, limit);
    }

    public List<Stat> topTracks(int limit) {
        return stats("""
                SELECT t.title || ' — ' || COALESCE(ar.name, 'Artiste inconnu') AS label, t.play_count AS v
                FROM tracks t LEFT JOIN artists ar ON ar.id = t.artist_id
                WHERE t.play_count > 0 AND ? = ? ORDER BY t.play_count DESC, t.title LIMIT ?""", 1, 1, limit);
    }

    /** Humeurs choisies (nombre de fois). */
    public List<Stat> moods() {
        return stats("""
                SELECT mood AS label, COUNT(*) AS v FROM mood_history
                WHERE user_id = ? GROUP BY mood ORDER BY v DESC LIMIT ?""", userId, 20);
    }

    /** Séquence chronologique des humeurs (pour le « brin d'ADN »). */
    public List<String> moodSequence(int limit) {
        List<String> out = new ArrayList<>();
        for (Stat s : stats("""
                SELECT mood AS label, id AS v FROM mood_history WHERE user_id = ? ORDER BY id DESC LIMIT ?""", userId, limit)) {
            out.add(0, s.label());
        }
        return out;
    }

    /** Minutes d'écoute par jour sur les N derniers jours (jours sans écoute inclus à 0). */
    public Map<String, Double> minutesPerDay(int days) {
        Map<String, Double> result = new LinkedHashMap<>();
        java.time.LocalDate today = java.time.LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            result.put(today.minusDays(i).toString(), 0.0);
        }
        for (Stat s : stats("""
                SELECT date(played_at, 'localtime') AS label, SUM(listened_ms) / 60000.0 AS v FROM listening_history
                WHERE user_id = ? AND date(played_at, 'localtime') >= date('now', 'localtime', ?) GROUP BY label""",
                userId, "-" + (days - 1) + " days")) {
            result.computeIfPresent(s.label(), (k, v) -> s.value());
        }
        return result;
    }

    public int distinctArtistsPlayed() {
        return (int) scalar("""
                SELECT COUNT(DISTINCT t.artist_id) FROM listening_history h JOIN tracks t ON t.id = h.track_id WHERE h.user_id = ?""");
    }

    public int tracksAddedLastDays(int days) {
        return (int) scalar("SELECT COUNT(*) FROM tracks WHERE added_at >= datetime('now', '-" + days + " days') AND ? = ?", 1);
    }

    private double scalar(String sql, Object... extra) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (extra.length == 0) {
                ps.setLong(1, userId);
            } else {
                ps.setObject(1, extra[0]);
                ps.setObject(2, extra[0]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lecture des statistiques impossible", e);
        }
    }

    private List<Stat> stats(String sql, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            List<Stat> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Stat(rs.getString("label"), rs.getDouble("v")));
                }
            }
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Lecture des statistiques impossible", e);
        }
    }
}
