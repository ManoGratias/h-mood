package com.hmood.music.service;

import com.hmood.music.service.LyricsService.Line;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LyricsAndOfflineTest {

    @Test
    void parsesLrcWithSeveralTimestampsAndSortsThem() {
        List<Line> lines = LyricsService.parse("""
                [00:12.50] Première ligne
                [00:05.00][01:00.25] Refrain
                [00:20] Sans centièmes
                pas de temps : ignorée
                """);
        assertEquals(4, lines.size());
        assertEquals(5_000, lines.get(0).timeMs());
        assertEquals("Refrain", lines.get(0).text());
        assertEquals(12_500, lines.get(1).timeMs());
        assertEquals(20_000, lines.get(2).timeMs());
        assertEquals(60_250, lines.get(3).timeMs());
    }

    @Test
    void findsTheLineForAPosition() {
        List<Line> lines = List.of(new Line(1000, "a"), new Line(5000, "b"), new Line(9000, "c"));
        assertEquals(-1, LyricsService.indexAt(lines, 500));
        assertEquals(0, LyricsService.indexAt(lines, 1000));
        assertEquals(1, LyricsService.indexAt(lines, 8999));
        assertEquals(2, LyricsService.indexAt(lines, 120_000));
    }

    @Test
    void cleansTitlesBeforeSearching() {
        assertEquals("Pansement", LyricsService.clean("Pansement (feat. Tayc)"));
        assertEquals("Song", LyricsService.clean("Song - Radio Edit"));
        assertEquals("Ok (Intro)", LyricsService.clean("Ok (Intro)"));
    }

    @Test
    void readableCreativeCommonsLicense() {
        assertEquals("CC BY-NC-SA 3.0", OfflineService.licenseLabel("http://creativecommons.org/licenses/by-nc-sa/3.0/"));
        assertEquals("CC BY 4.0", OfflineService.licenseLabel("https://creativecommons.org/licenses/by/4.0"));
    }
}
