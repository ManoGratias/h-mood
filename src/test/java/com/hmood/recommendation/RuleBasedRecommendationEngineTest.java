package com.hmood.recommendation;

import com.hmood.TestData;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleBasedRecommendationEngineTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-12T10:00:00Z"), ZoneId.of("Europe/Paris"));

    private final List<Track> library = List.of(
            TestData.track(1, "Tagged focus", "Rock", Mood.FOCUSED),
            TestData.track(2, "Lofi beat", "Lo-Fi Hip Hop", null),
            TestData.track(3, "Metal song", "Metal", null),
            TestData.track(4, "Classical piece", "Classical", null, 2015, 300_000, 0, false),
            TestData.track(5, "Old disco", "Disco", null, 1978, 240_000, 0, false));

    private final RuleBasedRecommendationEngine engine = new RuleBasedRecommendationEngine(() -> library, CLOCK);

    @Test
    void userTaggedTrackRanksFirstAndIrrelevantTracksAreExcluded() {
        List<Track> focus = engine.forMood(Mood.FOCUSED, 10);
        assertEquals("Tagged focus", focus.get(0).title());
        assertTrue(focus.stream().anyMatch(t -> t.title().equals("Lofi beat")));
        assertTrue(focus.stream().anyMatch(t -> t.title().equals("Classical piece")));
        assertFalse(focus.stream().anyMatch(t -> t.title().equals("Metal song")));
    }

    @Test
    void returnsEmptyListWhenNothingMatches() {
        assertTrue(engine.forMood(Mood.ROMANTIC, 10).isEmpty());
    }

    @Test
    void nostalgicFavorsOldTracks() {
        List<Track> nostalgic = engine.forMood(Mood.NOSTALGIC, 10);
        assertEquals("Old disco", nostalgic.get(0).title()); // genre disco + plus de 15 ans
        assertTrue(nostalgic.stream().anyMatch(t -> t.title().equals("Classical piece")));
    }

    @Test
    void generateFiltersByDecadeAndGenre() {
        assertEquals(List.of("Old disco"),
                engine.generate(new PlaylistCriteria(null, null, 1970, 0)).stream().map(Track::title).toList());
        assertEquals(List.of("Metal song"),
                engine.generate(new PlaylistCriteria(null, "metal", null, 0)).stream().map(Track::title).toList());
    }

    @Test
    void fitToDurationStopsOnceTargetReached() {
        List<Track> tracks = List.of(
                TestData.track(1, "a", null, null, 2000, 120_000, 0, false),
                TestData.track(2, "b", null, null, 2000, 120_000, 0, false),
                TestData.track(3, "c", null, null, 2000, 120_000, 0, false));
        assertEquals(2, RuleBasedRecommendationEngine.fitToDuration(tracks, 4).size());
        assertEquals(3, RuleBasedRecommendationEngine.fitToDuration(tracks, 0).size());
    }

    @Test
    void dailyPicksAreStableForTheDay() {
        assertEquals(engine.dailyPicks(3), engine.dailyPicks(3));
        assertEquals(3, engine.dailyPicks(3).size());
    }

    @Test
    void topArtistsIgnoresUnknownArtist() {
        List<Track> tracks = List.of(
                new Track(1, "p1", "t", "A", null, null, null, 0, null, null, 0, false),
                new Track(2, "p2", "t", "A", null, null, null, 0, null, null, 0, false),
                new Track(3, "p3", "t", "B", null, null, null, 0, null, null, 0, false),
                new Track(4, "p4", "t", null, null, null, null, 0, null, null, 0, false));
        assertEquals(List.of("A", "B"), engine.topArtists(tracks, 5));
    }
}
