package com.hmood.model;

/** Métadonnées lues dans un fichier audio, avant insertion en base. */
public record TrackMetadata(
        String filePath,
        String title,
        String artist,
        String album,
        String genre,
        Integer year,
        long durationMs,
        String coverPath) {

    public TrackMetadata withDuration(long newDurationMs) {
        return new TrackMetadata(filePath, title, artist, album, genre, year, newDurationMs, coverPath);
    }
}
