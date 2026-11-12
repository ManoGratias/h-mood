package com.hmood.database;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Accès à la base SQLite locale. Une connexion courte est ouverte par opération
 * (coût négligeable avec SQLite) ce qui évite tout partage de connexion entre threads.
 */
public final class Database {

    /** Utilisateur local du MVP. */
    public static final long DEFAULT_USER_ID = 1L;

    private static final String SCHEMA_RESOURCE = "/com/hmood/db/schema.sql";

    private final Path file;
    private final String url;

    public Database(Path file) {
        this.file = file.toAbsolutePath();
        try {
            Files.createDirectories(this.file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer le dossier de données " + this.file.getParent(), e);
        }
        this.url = "jdbc:sqlite:" + this.file;
    }

    public Path file() {
        return file;
    }

    public Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA foreign_keys = ON");
            st.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }

    /** Crée les tables manquantes. Idempotent. */
    public void initialize() {
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.execute("PRAGMA journal_mode = WAL");
            for (String sql : loadSchemaStatements()) {
                st.execute(sql);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Initialisation de la base impossible (" + file + ") : " + e.getMessage(), e);
        }
    }

    static List<String> loadSchemaStatements() {
        try (InputStream in = Database.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Ressource introuvable : " + SCHEMA_RESOURCE);
            }
            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            String withoutComments = script.lines()
                    .filter(line -> !line.trim().startsWith("--"))
                    .collect(Collectors.joining("\n"));
            return Arrays.stream(withoutComments.split(";"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
