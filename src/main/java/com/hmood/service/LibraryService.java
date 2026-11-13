package com.hmood.service;

import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.repository.TrackRepository;
import com.hmood.util.AudioFormats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/** Bibliothèque musicale locale : import, recherche, organisation. */
public final class LibraryService {

    public record ImportResult(int imported, int unsupported, int failed, List<String> errors) {
        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(imported).append(imported > 1 ? " morceaux importés" : " morceau importé");
            if (unsupported > 0) {
                sb.append(" · ").append(unsupported).append(" fichier(s) ignoré(s) (format non pris en charge)");
            }
            if (failed > 0) {
                sb.append(" · ").append(failed).append(" erreur(s)");
            }
            return sb.toString();
        }
    }

    private final TrackRepository tracks;
    private final MetadataReader reader;

    public LibraryService(TrackRepository tracks, MetadataReader reader) {
        this.tracks = tracks;
        this.reader = reader;
    }

    /**
     * Importe des fichiers et/ou dossiers (parcourus récursivement). Appelé hors du thread JavaFX.
     *
     * @param progress (traités, total)
     */
    public ImportResult importPaths(List<Path> paths, BiConsumer<Integer, Integer> progress) {
        List<Path> audioFiles = new ArrayList<>();
        int unsupported = 0;
        for (Path p : paths) {
            if (Files.isDirectory(p)) {
                try (Stream<Path> walk = Files.walk(p)) {
                    for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                        if (AudioFormats.isSupported(f)) {
                            audioFiles.add(f);
                        } else if (looksLikeAudio(f)) {
                            unsupported++;
                        }
                    }
                } catch (IOException e) {
                    return new ImportResult(0, unsupported, 1, List.of("Dossier illisible : " + p + " (" + e.getMessage() + ")"));
                }
            } else if (AudioFormats.isSupported(p)) {
                audioFiles.add(p);
            } else {
                unsupported++;
            }
        }
        int imported = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < audioFiles.size(); i++) {
            Path f = audioFiles.get(i);
            try {
                tracks.upsert(reader.read(f));
                imported++;
            } catch (RuntimeException e) {
                failed++;
                errors.add(f + " : " + e.getMessage());
            }
            progress.accept(i + 1, audioFiles.size());
        }
        return new ImportResult(imported, unsupported, failed, errors);
    }

    private static boolean looksLikeAudio(Path f) {
        return List.of("flac", "ogg", "opus", "wma", "alac", "ape").contains(AudioFormats.extension(f));
    }

    public List<Track> all() {
        return tracks.findAll();
    }

    public int count() {
        return tracks.count();
    }

    public List<Track> search(String query) {
        return tracks.findAll().stream().filter(t -> matches(t, query)).toList();
    }

    /** Tous les mots de la recherche doivent apparaître dans titre/artiste/album/genre/humeur. */
    public static boolean matches(Track t, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String haystack = String.join(" ",
                nz(t.title()), nz(t.artist()), nz(t.album()), nz(t.genre()),
                t.mood() == null ? "" : t.mood().label(),
                t.year() == null ? "" : t.year().toString()).toLowerCase(Locale.ROOT);
        return Arrays.stream(query.toLowerCase(Locale.ROOT).trim().split("\\s+")).allMatch(haystack::contains);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** Corrige le titre et l'artiste (utile pour les fichiers sans tags, et pour trouver leurs paroles). */
    public Track setInfo(Track t, String title, String artist) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le titre ne peut pas être vide.");
        }
        String a = artist == null || artist.isBlank() ? null : artist.trim();
        tracks.setInfo(t.id(), title, a);
        return new Track(t.id(), t.filePath(), title.trim(), a, t.album(), t.genre(), t.year(), t.durationMs(),
                t.mood(), t.coverPath(), t.playCount(), t.favorite());
    }

    public Track setMood(Track t, Mood mood) {
        tracks.setMood(t.id(), mood);
        return t.withMood(mood);
    }

    public Track toggleFavorite(Track t) {
        tracks.setFavorite(t.id(), !t.favorite());
        return t.withFavorite(!t.favorite());
    }

    public void remove(Track t) {
        tracks.delete(t.id());
    }

    public void clear() {
        tracks.deleteAll();
    }
}
