package com.hmood.model;

/**
 * Un morceau de la bibliothèque locale.
 *
 * @param mood humeur associée par l'utilisateur (peut être {@code null})
 */
public record Track(
        long id,
        String filePath,
        String title,
        String artist,
        String album,
        String genre,
        Integer year,
        long durationMs,
        Mood mood,
        String coverPath,
        int playCount,
        boolean favorite) {

    public static final String UNKNOWN_ARTIST = "Artiste inconnu";

    /** Morceau d'un catalogue en ligne (diffusé en streaming, non stocké en base). */
    public boolean isRemote() {
        return id < 0;
    }

    public String displayArtist() {
        return artist == null || artist.isBlank() ? UNKNOWN_ARTIST : artist;
    }

    public Track withMood(Mood newMood) {
        return new Track(id, filePath, title, artist, album, genre, year, durationMs, newMood, coverPath, playCount, favorite);
    }

    public Track withFavorite(boolean newFavorite) {
        return new Track(id, filePath, title, artist, album, genre, year, durationMs, mood, coverPath, playCount, newFavorite);
    }
}
