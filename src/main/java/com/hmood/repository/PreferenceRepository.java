package com.hmood.repository;

import com.hmood.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/** Préférences utilisateur (clé/valeur) et profil. */
public final class PreferenceRepository {

    private final Database db;
    private final long userId;

    public PreferenceRepository(Database db, long userId) {
        this.db = db;
        this.userId = userId;
    }

    public Optional<String> get(String key) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT pref_value FROM user_preferences WHERE user_id = ? AND pref_key = ?")) {
            ps.setLong(1, userId);
            ps.setString(2, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lecture des préférences impossible", e);
        }
    }

    public void set(String key, String value) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO user_preferences (user_id, pref_key, pref_value) VALUES (?, ?, ?)
                ON CONFLICT (user_id, pref_key) DO UPDATE SET pref_value = excluded.pref_value, updated_at = CURRENT_TIMESTAMP
                """)) {
            ps.setLong(1, userId);
            ps.setString(2, key);
            ps.setString(3, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Enregistrement des préférences impossible", e);
        }
    }

    public String displayName() {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("SELECT display_name FROM users WHERE id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : "";
            }
        } catch (SQLException e) {
            throw new DataAccessException("Lecture du profil impossible", e);
        }
    }

    public void setDisplayName(String name) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("UPDATE users SET display_name = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setLong(2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Enregistrement du profil impossible", e);
        }
    }
}
