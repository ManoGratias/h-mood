package com.hmood.repository;

import com.hmood.database.Database;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.model.TrackMetadata;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Morceaux, artistes, albums et favoris. */
public final class TrackRepository {

    private static final String SELECT = """
            SELECT t.id, t.file_path, t.title, ar.name AS artist, al.title AS album, t.genre, t.year,
                   t.duration_ms, t.mood, t.cover_path, t.play_count,
                   CASE WHEN f.track_id IS NULL THEN 0 ELSE 1 END AS favorite
            FROM tracks t
            LEFT JOIN artists ar ON ar.id = t.artist_id
            LEFT JOIN albums al ON al.id = t.album_id
            LEFT JOIN favorites f ON f.track_id = t.id AND f.user_id = ?
            """;

    private final Database db;
    private final long userId;

    public TrackRepository(Database db, long userId) {
        this.db = db;
        this.userId = userId;
    }

    public List<Track> findAll() {
        return query(SELECT + " ORDER BY ar.name COLLATE NOCASE, al.title COLLATE NOCASE, t.title COLLATE NOCASE");
    }

    public Optional<Track> findById(long id) {
        return query(SELECT + " WHERE t.id = ?", id).stream().findFirst();
    }

    public Optional<Track> findByPath(String path) {
        return query(SELECT + " WHERE t.file_path = ?", path).stream().findFirst();
    }

    public List<Track> findByPlaylist(long playlistId) {
        String sql = SELECT.replace("FROM tracks t", "FROM playlist_tracks pt JOIN tracks t ON t.id = pt.track_id")
                + " WHERE pt.playlist_id = ? ORDER BY pt.position";
        return query(sql, playlistId);
    }

    public int count() {
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM tracks")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataAccessException("Lecture de la bibliothèque impossible", e);
        }
    }

    /** Insère ou met à jour (par chemin de fichier) un morceau. L'humeur et les compteurs sont conservés. */
    public long upsert(TrackMetadata m) {
        try (Connection c = db.connect()) {
            c.setAutoCommit(false);
            try {
                String artistName = m.artist() == null || m.artist().isBlank() ? Track.UNKNOWN_ARTIST : m.artist().trim();
                long artistId = upsertArtist(c, artistName);
                Long albumId = m.album() == null || m.album().isBlank() ? null : upsertAlbum(c, m.album().trim(), artistId, m.year());
                try (PreparedStatement ps = c.prepareStatement("""
                        INSERT INTO tracks (file_path, title, artist_id, album_id, genre, year, duration_ms, cover_path)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (file_path) DO UPDATE SET
                            title = excluded.title,
                            artist_id = excluded.artist_id,
                            album_id = excluded.album_id,
                            genre = excluded.genre,
                            year = excluded.year,
                            duration_ms = excluded.duration_ms,
                            cover_path = COALESCE(excluded.cover_path, tracks.cover_path)
                        """)) {
                    ps.setString(1, m.filePath());
                    ps.setString(2, m.title());
                    ps.setLong(3, artistId);
                    Jdbc.setLong(ps, 4, albumId);
                    Jdbc.setString(ps, 5, m.genre());
                    Jdbc.setInt(ps, 6, m.year());
                    ps.setLong(7, m.durationMs());
                    Jdbc.setString(ps, 8, m.coverPath());
                    ps.executeUpdate();
                }
                long id;
                try (PreparedStatement ps = c.prepareStatement("SELECT id FROM tracks WHERE file_path = ?")) {
                    ps.setString(1, m.filePath());
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        id = rs.getLong(1);
                    }
                }
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Enregistrement du morceau impossible : " + m.filePath(), e);
        }
    }

    public void setMood(long trackId, Mood mood) {
        update("UPDATE tracks SET mood = ? WHERE id = ?", mood == null ? null : mood.name(), trackId);
    }

    public void setFavorite(long trackId, boolean favorite) {
        if (favorite) {
            update("INSERT OR IGNORE INTO favorites (user_id, track_id) VALUES (?, ?)", userId, trackId);
        } else {
            update("DELETE FROM favorites WHERE user_id = ? AND track_id = ?", userId, trackId);
        }
    }

    public void incrementPlayCount(long trackId) {
        update("UPDATE tracks SET play_count = play_count + 1, last_played_at = CURRENT_TIMESTAMP WHERE id = ?", trackId);
    }

    /** Retire le morceau de la bibliothèque (le fichier sur disque n'est jamais supprimé). */
    public void delete(long trackId) {
        update("DELETE FROM tracks WHERE id = ?", trackId);
        deleteOrphans();
    }

    public void deleteAll() {
        update("DELETE FROM tracks");
        deleteOrphans();
    }

    private void deleteOrphans() {
        update("DELETE FROM albums WHERE id NOT IN (SELECT album_id FROM tracks WHERE album_id IS NOT NULL)");
        update("""
                DELETE FROM artists
                WHERE id NOT IN (SELECT artist_id FROM tracks WHERE artist_id IS NOT NULL)
                  AND id NOT IN (SELECT artist_id FROM albums WHERE artist_id IS NOT NULL)
                """);
    }

    private long upsertArtist(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO artists (name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM artists WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private long upsertAlbum(Connection c, String title, long artistId, Integer year) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO albums (title, artist_id, year) VALUES (?, ?, ?)")) {
            ps.setString(1, title);
            ps.setLong(2, artistId);
            Jdbc.setInt(ps, 3, year);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM albums WHERE title = ? AND artist_id = ?")) {
            ps.setString(1, title);
            ps.setLong(2, artistId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    private List<Track> query(String sql, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 2, params[i]);
            }
            List<Track> result = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(map(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DataAccessException("Lecture de la bibliothèque impossible", e);
        }
    }

    private void update(String sql, Object... params) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Mise à jour de la bibliothèque impossible", e);
        }
    }

    private static Track map(ResultSet rs) throws SQLException {
        return new Track(
                rs.getLong("id"),
                rs.getString("file_path"),
                rs.getString("title"),
                rs.getString("artist"),
                rs.getString("album"),
                rs.getString("genre"),
                Jdbc.getInt(rs, "year"),
                rs.getLong("duration_ms"),
                Mood.fromName(rs.getString("mood")).orElse(null),
                rs.getString("cover_path"),
                rs.getInt("play_count"),
                rs.getInt("favorite") == 1);
    }
}
