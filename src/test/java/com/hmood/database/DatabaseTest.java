package com.hmood.database;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    @TempDir
    Path dir;

    @Test
    void createsAllTablesAndIsIdempotent() throws Exception {
        Database db = new Database(dir.resolve("test.db"));
        db.initialize();
        db.initialize(); // second passage sans erreur

        Set<String> tables = new HashSet<>();
        try (Connection c = db.connect(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        for (String expected : List.of("users", "tracks", "artists", "albums", "playlists", "playlist_tracks",
                "favorites", "listening_history", "mood_history", "user_preferences")) {
            assertTrue(tables.contains(expected), "table manquante : " + expected);
        }
    }

    @Test
    void createsDefaultUserAndEnablesForeignKeys() throws Exception {
        Database db = new Database(dir.resolve("test.db"));
        db.initialize();
        try (Connection c = db.connect(); Statement st = c.createStatement()) {
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM users WHERE id = 1")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
                rs.next();
                assertEquals(1, rs.getInt(1));
            }
        }
    }
}
