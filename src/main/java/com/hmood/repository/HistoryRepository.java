package com.hmood.repository;

import com.hmood.database.Database;
import com.hmood.model.Mood;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Historique d'écoute et historique des humeurs. */
public final class HistoryRepository {

    private final Database db;
    private final long userId;

    public HistoryRepository(Database db, long userId) {
        this.db = db;
        this.userId = userId;
    }

    public void recordPlay(long trackId, long listenedMs, boolean completed) {
        update("INSERT INTO listening_history (user_id, track_id, listened_ms, completed) VALUES (?, ?, ?, ?)",
                userId, trackId, Math.max(0, listenedMs), completed ? 1 : 0);
    }

    /** Identifiants des derniers morceaux écoutés (sans doublon, du plus récent au plus ancien). */
    public List<Long> recentTrackIds(int limit) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT track_id, MAX(id) AS last_id FROM listening_history
                WHERE user_id = ? GROUP BY track_id ORDER BY last_id DESC LIMIT ?
                """)) {
            ps.setLong(1, userId);
            ps.setInt(2, limit);
            List<Long> ids = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
            return ids;
        } catch (SQLException e) {
            throw new DataAccessException("Lecture de l'historique impossible", e);
        }
    }

    public long totalListenedMs() {
        return scalarLong("SELECT COALESCE(SUM(listened_ms), 0) FROM listening_history WHERE user_id = ?");
    }

    public long totalPlays() {
        return scalarLong("SELECT COUNT(*) FROM listening_history WHERE user_id = ?");
    }

    public void recordMood(Mood mood, String source) {
        update("INSERT INTO mood_history (user_id, mood, source) VALUES (?, ?, ?)", userId, mood.name(), source);
    }

    /** Dernière humeur choisie aujourd'hui (heure locale). */
    public Optional<Mood> latestMoodToday() {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                SELECT mood FROM mood_history
                WHERE user_id = ? AND date(created_at, 'localtime') = date('now', 'localtime')
                ORDER BY id DESC LIMIT 1
                """)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Mood.fromName(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lecture de l'historique des humeurs impossible", e);
        }
    }

    public void clear() {
        update("DELETE FROM listening_history WHERE user_id = ?", userId);
        update("DELETE FROM mood_history WHERE user_id = ?", userId);
        update("DELETE FROM stream_history WHERE user_id = ?", userId);
        update("UPDATE tracks SET play_count = 0, last_played_at = NULL");
    }

    private long scalarLong(String sql) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lecture des statistiques impossible", e);
        }
    }

    private void update(String sql, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Écriture de l'historique impossible", e);
        }
    }
}
