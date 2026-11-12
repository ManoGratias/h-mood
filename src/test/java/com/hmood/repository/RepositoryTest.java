package com.hmood.repository;

import com.hmood.TestData;
import com.hmood.database.Database;
import com.hmood.model.Mood;
import com.hmood.model.Playlist;
import com.hmood.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryTest {

    @TempDir
    Path dir;

    private TrackRepository tracks;
    private PlaylistRepository playlists;
    private HistoryRepository history;
    private PreferenceRepository prefs;

    @BeforeEach
    void setUp() {
        Database db = new Database(dir.resolve("test.db"));
        db.initialize();
        tracks = new TrackRepository(db, Database.DEFAULT_USER_ID);
        playlists = new PlaylistRepository(db, Database.DEFAULT_USER_ID);
        history = new HistoryRepository(db, Database.DEFAULT_USER_ID);
        prefs = new PreferenceRepository(db, Database.DEFAULT_USER_ID);
    }

    @Test
    void upsertInsertsThenUpdatesWithoutDuplicateAndKeepsMood() {
        long id = tracks.upsert(TestData.metadata("C:/a.mp3", "Song A", "Daft Punk", "Discovery", "House"));
        tracks.setMood(id, Mood.ENERGETIC);
        long again = tracks.upsert(TestData.metadata("C:/a.mp3", "Song A (remaster)", "Daft Punk", "Discovery", "House"));

        assertEquals(id, again);
        List<Track> all = tracks.findAll();
        assertEquals(1, all.size());
        Track t = all.get(0);
        assertEquals("Song A (remaster)", t.title());
        assertEquals("Daft Punk", t.artist());
        assertEquals("Discovery", t.album());
        assertEquals(Mood.ENERGETIC, t.mood());
    }

    @Test
    void missingArtistUsesUnknownArtist() {
        long id = tracks.upsert(TestData.metadata("C:/b.mp3", "Untitled", null, null, null));
        assertEquals(Track.UNKNOWN_ARTIST, tracks.findById(id).orElseThrow().artist());
    }

    @Test
    void favoritesAndPlayCount() {
        long id = tracks.upsert(TestData.metadata("C:/c.mp3", "Fav", "X", null, "Pop"));
        tracks.setFavorite(id, true);
        tracks.incrementPlayCount(id);
        tracks.incrementPlayCount(id);
        Track t = tracks.findById(id).orElseThrow();
        assertTrue(t.favorite());
        assertEquals(2, t.playCount());
        tracks.setFavorite(id, false);
        assertFalse(tracks.findById(id).orElseThrow().favorite());
    }

    @Test
    void playlistKeepsOrderAndCascadesOnTrackDeletion() {
        long a = tracks.upsert(TestData.metadata("C:/1.mp3", "One", "X", null, null));
        long b = tracks.upsert(TestData.metadata("C:/2.mp3", "Two", "X", null, null));
        long c = tracks.upsert(TestData.metadata("C:/3.mp3", "Three", "Y", null, null));
        long p = playlists.create("Test", null, Mood.HAPPY);
        playlists.addTracks(p, List.of(a, b));
        playlists.addTracks(p, List.of(c));

        assertEquals(List.of("One", "Two", "Three"), tracks.findByPlaylist(p).stream().map(Track::title).toList());

        playlists.replaceTracks(p, List.of(c, a));
        assertEquals(List.of("Three", "One"), tracks.findByPlaylist(p).stream().map(Track::title).toList());

        tracks.delete(c);
        Playlist after = playlists.findById(p).orElseThrow();
        assertEquals(1, after.trackCount());
        assertEquals(Mood.HAPPY, after.mood());
    }

    @Test
    void historyAndMoods() {
        long a = tracks.upsert(TestData.metadata("C:/1.mp3", "One", "X", null, null));
        long b = tracks.upsert(TestData.metadata("C:/2.mp3", "Two", "X", null, null));
        history.recordPlay(a, 60_000, true);
        history.recordPlay(b, 30_000, false);
        history.recordPlay(a, 10_000, false);

        assertEquals(List.of(a, b), history.recentTrackIds(10));
        assertEquals(100_000, history.totalListenedMs());
        assertEquals(3, history.totalPlays());

        history.recordMood(Mood.SAD, "manual");
        history.recordMood(Mood.FOCUSED, "questionnaire");
        assertEquals(Mood.FOCUSED, history.latestMoodToday().orElseThrow());

        history.clear();
        assertEquals(0, history.totalPlays());
        assertTrue(history.latestMoodToday().isEmpty());
    }

    @Test
    void preferencesUpsert() {
        assertTrue(prefs.get("ui.theme").isEmpty());
        prefs.set("ui.theme", "ICE_BLUE");
        prefs.set("ui.theme", "SUNSET_ORANGE");
        assertEquals("SUNSET_ORANGE", prefs.get("ui.theme").orElseThrow());
        prefs.setDisplayName("Hermann");
        assertEquals("Hermann", prefs.displayName());
    }
}
