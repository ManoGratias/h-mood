package com.hmood.repository;

import com.hmood.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Artistes favoris de l'utilisateur (identifiants Deezer), stockés en local. */
public final class FavoriteArtistsRepository {

    /** Un artiste favori. */
    public record FavoriteArtist(String id, String name, String picture) {
    }

    private final Database db;
    private final long userId;

    public FavoriteArtistsRepository(Database db, long userId) {
        this.db = db;
        this.userId = userId;
    }

    /** Du plus récent au plus ancien. */
    public List<FavoriteArtist> list() {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT artist_id, name, picture FROM favorite_artists WHERE user_id = ? ORDER BY added_at DESC, rowid DESC")) {
            ps.setLong(1, userId);
            List<FavoriteArtist> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(new FavoriteArtist(rs.getString(1), rs.getString(2), rs.getString(3)));
            }
            return out;
        } catch (SQLException e) {
            throw new DataAccessException("Lecture des artistes favoris impossible", e);
        }
    }

    public boolean containsId(String artistId) {
        return artistId != null && list().stream().anyMatch(a -> a.id().equals(artistId));
    }

    /** Comparaison par nom, sans tenir compte de la casse (titres sans identifiant Deezer). */
    public boolean containsName(String name) {
        return name != null && list().stream().anyMatch(a -> a.name().equalsIgnoreCase(name.trim()));
    }

    public void add(FavoriteArtist a) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO favorite_artists (user_id, artist_id, name, picture) VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, artist_id) DO UPDATE SET name = excluded.name, picture = excluded.picture""")) {
            ps.setLong(1, userId);
            ps.setString(2, a.id());
            ps.setString(3, a.name());
            ps.setString(4, a.picture());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Artiste favori non enregistré", e);
        }
    }

    public void remove(String artistId) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM favorite_artists WHERE user_id = ? AND artist_id = ?")) {
            ps.setLong(1, userId);
            ps.setString(2, artistId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Artiste favori non retiré", e);
        }
    }

    public void removeByName(String name) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM favorite_artists WHERE user_id = ? AND LOWER(name) = LOWER(?)")) {
            ps.setLong(1, userId);
            ps.setString(2, name.trim());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Artiste favori non retiré", e);
        }
    }
}
