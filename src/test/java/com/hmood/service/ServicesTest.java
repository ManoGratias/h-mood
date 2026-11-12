package com.hmood.service;

import com.hmood.TestData;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.model.TrackMetadata;
import com.hmood.service.MoodQuestionnaire.Energy;
import com.hmood.service.MoodQuestionnaire.Feeling;
import com.hmood.service.MoodQuestionnaire.Need;
import com.hmood.util.AudioFormats;
import com.hmood.util.Durations;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicesTest {

    @Test
    void questionnaireRules() {
        assertEquals(Mood.FOCUSED, MoodQuestionnaire.suggest(Energy.HIGH, Need.FOCUS, Feeling.NEGATIVE));
        assertEquals(Mood.ENERGETIC, MoodQuestionnaire.suggest(Energy.HIGH, Need.CELEBRATE, Feeling.POSITIVE));
        assertEquals(Mood.ANGRY, MoodQuestionnaire.suggest(Energy.HIGH, Need.MOTIVATION, Feeling.NEGATIVE));
        assertEquals(Mood.CONFIDENT, MoodQuestionnaire.suggest(Energy.MEDIUM, Need.MOTIVATION, Feeling.POSITIVE));
        assertEquals(Mood.SAD, MoodQuestionnaire.suggest(Energy.LOW, Need.COMFORT, Feeling.NEGATIVE));
        assertEquals(Mood.DREAMY, MoodQuestionnaire.suggest(Energy.LOW, Need.ESCAPE, Feeling.NEUTRAL));
    }

    @Test
    void everyAnswerCombinationGivesAMood() {
        for (Energy e : Energy.values())
            for (Need n : Need.values())
                for (Feeling f : Feeling.values())
                    assertTrue(MoodQuestionnaire.suggest(e, n, f) != null);
    }

    @Test
    void timeOfDaySuggestion() {
        assertEquals(Mood.MOTIVATED, MoodService.suggestForTime(LocalTime.of(8, 0)));
        assertEquals(Mood.FOCUSED, MoodService.suggestForTime(LocalTime.of(15, 30)));
        assertEquals(Mood.DREAMY, MoodService.suggestForTime(LocalTime.of(23, 59)));
    }

    @Test
    void fileNameFallbackParsesArtistAndTitle() {
        TrackMetadata m = MetadataReader.fromFileName(Path.of("C:/music/Daft Punk - One More Time.mp3"));
        assertEquals("Daft Punk", m.artist());
        assertEquals("One More Time", m.title());
        TrackMetadata plain = MetadataReader.fromFileName(Path.of("C:/music/track_01.wav"));
        assertNull(plain.artist());
        assertEquals("track 01", plain.title());
    }

    @Test
    void yearParsing() {
        assertEquals(1997, MetadataReader.parseYear("1997-05-21"));
        assertEquals(2004, MetadataReader.parseYear("2004"));
        assertNull(MetadataReader.parseYear("inconnue"));
        assertNull(MetadataReader.parseYear(null));
    }

    @Test
    void searchMatchesAllWords() {
        Track t = TestData.track(1, "Midnight City", "Synthwave", Mood.DREAMY);
        assertTrue(LibraryService.matches(t, "midnight"));
        assertTrue(LibraryService.matches(t, "city synthwave"));
        assertTrue(LibraryService.matches(t, "rêveur"));
        assertTrue(LibraryService.matches(t, ""));
        assertFalse(LibraryService.matches(t, "midnight jazz"));
    }

    @Test
    void moodGenreMatching() {
        assertTrue(Mood.FOCUSED.matchesGenre("Lo-Fi Hip Hop"));
        assertTrue(Mood.ENERGETIC.matchesGenre("Deep House"));
        assertFalse(Mood.SAD.matchesGenre(null));
        assertEquals(Mood.HAPPY, Mood.fromName("happy").orElseThrow());
        assertTrue(Mood.fromName("nope").isEmpty());
    }

    @Test
    void formatsAndDurations() {
        assertTrue(AudioFormats.isSupported(Path.of("a.MP3")));
        assertTrue(AudioFormats.isSupported(Path.of("a.m4a")));
        assertFalse(AudioFormats.isSupported(Path.of("a.flac")));
        assertEquals("3:07", Durations.format(187_000));
        assertEquals("1:02:45", Durations.format(3_765_000));
        assertEquals("0:00", Durations.format(0));
        assertEquals("42 min", Durations.human(42 * 60_000));
        assertEquals("2 h 05", Durations.human(125 * 60_000));
    }
}
