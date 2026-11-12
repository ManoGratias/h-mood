package com.hmood.music;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmood.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Cache SQLite des réponses des fournisseurs (classements, recherches), avec leur source et leur date.
 * Les données ne sont conservées que pour l'affichage (pas de profilage ni d'entraînement).
 */
public final class CatalogCache {

    public record Entry(List<CatalogEntry> entries, Instant fetchedAt) {
    }

    private static final TypeReference<List<CatalogEntry>> TYPE = new TypeReference<>() { };

    private final Database db;
    private final ObjectMapper json = new ObjectMapper();

    public CatalogCache(Database db) {
        this.db = db;
    }

    public Optional<Entry> get(String key) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT payload, fetched_at FROM catalog_cache WHERE cache_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new Entry(json.readValue(rs.getString(1), TYPE), Instant.parse(rs.getString(2))));
            }
        } catch (Exception e) {
            return Optional.empty(); // un cache illisible n'empêche jamais l'application de fonctionner
        }
    }

    public Instant put(String key, String provider, List<CatalogEntry> entries) {
        Instant now = Instant.now();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO catalog_cache (cache_key, provider, payload, fetched_at) VALUES (?, ?, ?, ?)
                ON CONFLICT (cache_key) DO UPDATE SET provider = excluded.provider,
                    payload = excluded.payload, fetched_at = excluded.fetched_at
                """)) {
            ps.setString(1, key);
            ps.setString(2, provider);
            ps.setString(3, json.writeValueAsString(entries));
            ps.setString(4, now.toString());
            ps.executeUpdate();
        } catch (Exception e) {
            // échec d'écriture du cache : sans conséquence pour l'affichage
        }
        return now;
    }

    public void clear() {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM catalog_cache")) {
            ps.executeUpdate();
        } catch (SQLException e) {
            // ignoré
        }
    }
}
