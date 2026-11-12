package com.hmood.music.service;

import com.hmood.catalog.CatalogException;
import com.hmood.database.Database;
import com.hmood.model.Track;
import com.hmood.music.CatalogEntry;
import com.hmood.music.provider.JamendoProvider;
import com.hmood.repository.TrackRepository;
import com.hmood.service.LibraryService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Écoute hors ligne : uniquement pour les morceaux Jamendo dont l'artiste autorise le téléchargement
 * (licence Creative Commons, champ « audiodownload_allowed » de l'API officielle). Le fichier est enregistré
 * dans le dossier de données, ajouté à « Ma musique », et sa licence est conservée pour être affichée.
 * Aucun autre fournisseur n'est téléchargeable (Deezer, Apple : extraits uniquement ; Audius : streaming).
 */
public final class OfflineService {

    /** Morceau disponible hors ligne. */
    public record Download(Path file, String license, String licenseUrl, Track track) {
    }

    private final Database db;
    private final Path dir;
    private final JamendoProvider jamendo;
    private final LibraryService library;
    private final TrackRepository tracks;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public OfflineService(Database db, Path dir, JamendoProvider jamendo, LibraryService library, TrackRepository tracks) {
        this.db = db;
        this.dir = dir;
        this.jamendo = jamendo;
        this.library = library;
        this.tracks = tracks;
    }

    public Path folder() {
        return dir;
    }

    /** Vrai si le fournisseur du morceau permet le téléchargement (à confirmer au moment du téléchargement). */
    public boolean supports(CatalogEntry e) {
        return e != null && JamendoProvider.ID.equals(e.provider()) && jamendo.configured();
    }

    /** Morceau déjà téléchargé (et fichier toujours présent) ? */
    public Optional<Download> find(CatalogEntry e) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT file_path, license, license_url FROM offline_downloads WHERE provider = ? AND external_id = ?")) {
            ps.setString(1, e.provider());
            ps.setString(2, e.externalId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                Path file = Path.of(rs.getString(1));
                if (!Files.isRegularFile(file)) return Optional.empty();
                return tracks.findByPath(file.toAbsolutePath().toString())
                        .map(t -> new Download(file, rs2(rs, 2), rs2(rs, 3), t));
            }
        } catch (SQLException ex) {
            return Optional.empty();
        }
    }

    /** Licence d'un fichier local téléchargé depuis Jamendo, si c'en est un. */
    public Optional<String> licenseOf(Track local) {
        if (local == null || local.isRemote()) return Optional.empty();
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT license FROM offline_downloads WHERE file_path = ?")) {
            ps.setString(1, local.filePath());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
            }
        } catch (SQLException ex) {
            return Optional.empty();
        }
    }

    /** Bloquant. Télécharge le morceau si l'artiste l'autorise, puis l'ajoute à « Ma musique ». */
    public Download download(CatalogEntry e) throws CatalogException {
        if (!supports(e)) {
            throw new CatalogException("Téléchargement non autorisé pour cette source : seuls les morceaux Jamendo "
                    + "(Creative Commons) peuvent être enregistrés hors ligne.");
        }
        Optional<Download> existing = find(e);
        if (existing.isPresent()) return existing.get();

        JamendoProvider.DownloadInfo info = jamendo.downloadInfo(e.externalId());
        if (!info.allowed() || info.url() == null) {
            throw new CatalogException("L'artiste n'autorise pas le téléchargement de ce morceau sur Jamendo : écoute-le en streaming.");
        }
        Path target = dir.resolve(safe(info.artist()) + " - " + safe(info.title()) + " [jamendo-" + e.externalId() + "].mp3");
        try {
            Files.createDirectories(dir);
            Path tmp = Files.createTempFile(dir, "dl-", ".part");
            HttpRequest request = HttpRequest.newBuilder(URI.create(info.url()))
                    .timeout(Duration.ofMinutes(3))
                    .header("User-Agent", "HMood/0.1.0 (application de bureau)")
                    .GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                Files.deleteIfExists(tmp);
                throw new CatalogException("Jamendo a répondu " + response.statusCode() + " au téléchargement");
            }
            try (InputStream in = response.body()) {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (Files.size(tmp) < 10_000) {
                Files.deleteIfExists(tmp);
                throw new CatalogException("Fichier reçu invalide");
            }
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new CatalogException("Téléchargement impossible : " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new CatalogException("Téléchargement interrompu", ex);
        }
        library.importPaths(java.util.List.of(target), (a, b) -> { });
        Track track = tracks.findByPath(target.toAbsolutePath().toString())
                .orElseThrow(() -> new CatalogException("Fichier téléchargé mais non importé dans Ma musique"));
        String license = licenseLabel(info.licenseUrl());
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO offline_downloads (provider, external_id, file_path, title, artist, license, license_url, downloaded_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (provider, external_id) DO UPDATE SET file_path = excluded.file_path,
                    license = excluded.license, license_url = excluded.license_url, downloaded_at = excluded.downloaded_at
                """)) {
            ps.setString(1, e.provider());
            ps.setString(2, e.externalId());
            ps.setString(3, target.toAbsolutePath().toString());
            ps.setString(4, info.title());
            ps.setString(5, info.artist());
            ps.setString(6, license);
            ps.setString(7, info.licenseUrl());
            ps.setString(8, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new CatalogException("Base locale : " + ex.getMessage(), ex);
        }
        return new Download(target, license, info.licenseUrl(), track);
    }

    /** « http://creativecommons.org/licenses/by-nc-sa/3.0/ » → « CC BY-NC-SA 3.0 ». */
    static String licenseLabel(String url) {
        if (url == null || url.isBlank()) return "Licence Creative Commons (voir Jamendo)";
        String[] parts = url.replaceAll("/+$", "").split("/");
        int i = java.util.Arrays.asList(parts).indexOf("licenses");
        if (i < 0 || i + 1 >= parts.length) return "Licence Creative Commons";
        String version = i + 2 < parts.length ? " " + parts[i + 2] : "";
        return "CC " + parts[i + 1].toUpperCase() + version;
    }

    private static String safe(String s) {
        String v = s == null || s.isBlank() ? "Inconnu" : s;
        v = v.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        return v.length() > 80 ? v.substring(0, 80).trim() : v;
    }

    private static String rs2(ResultSet rs, int col) {
        try {
            return rs.getString(col);
        } catch (SQLException e) {
            return null;
        }
    }
}
