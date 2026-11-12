package com.hmood;

import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.model.TrackMetadata;

/** Fabriques de données de test. */
public final class TestData {

    private TestData() {
    }

    public static Track track(long id, String title, String genre, Mood mood) {
        return new Track(id, "C:/music/" + title + ".mp3", title, "Artist " + id, "Album", genre, 2020,
                200_000, mood, null, 0, false);
    }

    public static Track track(long id, String title, String genre, Mood mood, Integer year, long durationMs,
                              int playCount, boolean favorite) {
        return new Track(id, "C:/music/" + title + ".mp3", title, "Artist " + id, "Album", genre, year,
                durationMs, mood, null, playCount, favorite);
    }

    public static TrackMetadata metadata(String path, String title, String artist, String album, String genre) {
        return new TrackMetadata(path, title, artist, album, genre, 2001, 180_000, null);
    }
}
