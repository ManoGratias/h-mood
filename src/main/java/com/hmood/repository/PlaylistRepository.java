package com.hmood.repository;

import com.hmood.database.Database;
import com.hmood.model.Mood;
import com.hmood.model.Playlist;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Playlists et leur contenu ordonné. */
public final class PlaylistRepository {

    private static final String SELECT = """
            SELECT p.id, p.name, p.description, p.mood,
                   COUNT(pt.id) AS track_count,
                   COALESCE(SUM(t.duration_ms), 0) AS total_ms
            FROM playlists p
            LEFT JOIN playlist_tracks pt ON pt.playlist_id = p.id
            LEFT JOIN tracks t ON t.id = pt.track_id
            WHERE p.user_id = ?
            """;

    private final Database db;
    private final long userId;

    public PlaylistRepository(Database db, long userId) {
        this.db = db;
        this.userId = userId;
    }

    public List<Playlist> findAll() {
        return query(SELECT + " GROUP BY p.id ORDER BY p.updated_at DESC, p.id DESC");
    }

    public Optional<Playlist> findById(long id) {
        return query(SELECT + " AND p.id = ? GROUP BY p.id", id).stream().findFirst();
    }

    public long create(String name, String description, Mood mood) {
        try (Connection c = db.connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO playlists (user_id, name, description, mood) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, name);
            Jdbc.setString(ps, 3, description);
            Jdbc.setString(ps, 4, mood == null ? null : mood.name());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Création de la playlist impossible", e);
        }
    }

    public void rename(long id, String name) {
        update("UPDATE playlists SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND user_id = ?", name, id, userId);
    }

    public void delete(long id) {
        update("DELETE FROM playlists WHERE id = ? AND user_id = ?", id, userId);
    }

    /** Ajoute des morceaux à la fin de la playlist. */
    public void addTracks(long playlistId, List<Long> trackIds) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                int position;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT COALESCE(MAX(position), -1) FROM playlist_tracks WHERE playlist_id = ?")) {
                    ps.setLong(1, playlistId);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        position = rs.getInt(1) + 1;
                    }
                }
                insertEntries(c, playlistId, trackIds, position);
                touch(c, playlistId);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Ajout à la playlist impossible", e);
        }
    }

    /** Remplace tout le contenu (sert au réordonnancement et aux suppressions). */
    public void replaceTracks(long playlistId, List<Long> trackIds) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM playlist_tracks WHERE playlist_id = ?")) {
                    ps.setLong(1, playlistId);
                    ps.executeUpdate();
                }
                insertEntries(c, playlistId, trackIds, 0);
                touch(c, playlistId);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Mise à jour de la playlist impossible", e);
        }
    }

    private static void insertEntries(Connection c, long playlistId, List<Long> trackIds, int firstPosition) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO playlist_tracks (playlist_id, track_id, position) VALUES (?, ?, ?)")) {
            int position = firstPosition;
            for (Long trackId : trackIds) {
                ps.setLong(1, playlistId);
                ps.setLong(2, trackId);
                ps.setInt(3, position++);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void touch(Connection c, long playlistId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("UPDATE playlists SET updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            ps.setLong(1, playlistId);
            ps.executeUpdate();
        }
    }

    private List<Playlist> query(String sql, Object... extraParams) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            for (int i = 0; i < extraParams.length; i++) {
                ps.setObject(i + 2, extraParams[i]);
            }
            List<Playlist> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new Playlist(
                            rs.getLong("id"),
                            rs.getString("name"),
                            rs.getString("description"),
                            Mood.fromName(rs.getString("mood")).orElse(null),
                            rs.getInt("track_count"),
                            rs.getLong("total_ms")));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Lecture des playlists impossible", e);
        }
    }

    private void update(String sql, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Mise à jour de la playlist impossible", e);
        }
    }
}
