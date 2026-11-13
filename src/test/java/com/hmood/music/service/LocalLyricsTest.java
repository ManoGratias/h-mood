package com.hmood.music.service;

import com.hmood.model.Track;
import com.hmood.music.service.LyricsService.Lyrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLyricsTest {

    @TempDir
    Path dir;

    private static Track track(Path file, String title, String artist) {
        return new Track(1, file.toString(), title, artist, null, null, null, 0, null, null, 0, false);
    }

    @Test
    void readsSyncedLrcNextToTheFile() throws Exception {
        Path audio = Files.write(dir.resolve("19 Heaven.mp3"), new byte[]{1, 2, 3});
        Files.writeString(dir.resolve("19 Heaven.lrc"), "[ti:Heaven]\n[00:12.50] Oh thinkin' about all our younger years\n[00:18.00] There was only you and me\n",
                StandardCharsets.UTF_8);
        Optional<Lyrics> l = LyricsService.local(audio);
        assertTrue(l.isPresent());
        assertTrue(l.get().synced());
        assertEquals(2, l.get().lines().size());
        assertEquals(12_500, l.get().lines().get(0).timeMs());
        assertEquals(LyricsService.SOURCE_LRC_FILE, l.get().source());
    }

    @Test
    void readsPlainLrcInWindows1252() throws Exception {
        Path audio = Files.write(dir.resolve("chanson.mp3"), new byte[]{1});
        Files.write(dir.resolve("chanson.lrc"), "[ar:Moi]\nJ'ai rêvé d'été\n".getBytes(Charset.forName("windows-1252")));
        Lyrics l = LyricsService.local(audio).orElseThrow();
        assertFalse(l.synced());
        assertEquals("J'ai rêvé d'été", l.plain());
    }

    @Test
    void nothingOnDiskMeansEmpty() throws Exception {
        Path audio = Files.write(dir.resolve("vide.mp3"), new byte[]{1});
        assertTrue(LyricsService.local(audio).isEmpty());
    }

    private static com.fasterxml.jackson.databind.JsonNode results(String... entries) throws Exception {
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree("[" + String.join(",", entries) + "]");
    }

    private static String entry(String artist, String title, int duration, String plain) {
        return "{\"artistName\":\"" + artist + "\",\"trackName\":\"" + title + "\",\"duration\":" + duration
                + ",\"plainLyrics\":\"" + plain + "\",\"syncedLyrics\":\"\"}";
    }

    @Test
    void titleOnlyKeepsTheSongMostVersionsAgreeOn() throws Exception {
        String song = "Say you say me say it for always that is the way it should be";
        Lyrics l = LyricsService.pickByConsensus(results(
                entry("Lionel Richie", "Say You, Say Me", 240, song),
                entry("Lionel Richie", "Say You Say Me (Remastered)", 241, song),
                entry("Cover Band", "Say you say me", 230, song + " yeah"),
                entry("Autre", "Say You Say Me", 200, "une chanson complètement différente sans rapport")), "say you say me", 234);
        assertEquals("Lionel Richie – Say You, Say Me", l.matchedSong());
        assertFalse(l.synced()); // autre version : pas de synchronisation
        assertEquals(song, l.plain());
    }

    @Test
    void titleOnlyRefusesAmbiguousTitles() throws Exception {
        assertEquals(null, LyricsService.pickByConsensus(results(
                entry("A", "Heaven", 200, "baby you are all that I want when you are lying here in my arms"),
                entry("A", "Heaven", 200, "baby you are all that I want when you are lying here in my arms"),
                entry("B", "Heaven", 210, "un autre texte qui parle du paradis et des nuages blancs"),
                entry("C", "Heaven", 220, "encore une troisième chanson totalement distincte des deux autres"),
                entry("D", "Heaven", 230, "quatrième morceau homonyme avec ses propres paroles")), "Heaven", 249));
    }

    @Test
    void otherVersionIsStretchedToTheFileDuration() throws Exception {
        String song = "Say you say me say it for always that is the way it should be";
        String synced = "{\"artistName\":\"Lionel Richie\",\"trackName\":\"Say You, Say Me\",\"duration\":200,"
                + "\"plainLyrics\":\"" + song + "\",\"syncedLyrics\":\"[00:10.00] Say you, say me\\n[01:40.00] Say it for always\"}";
        Lyrics l = LyricsService.pickByConsensus(results(synced,
                entry("Lionel Richie", "Say You Say Me", 300, song),
                entry("Cover", "Say You Say Me", 280, song)), "say you say me", 240);
        assertTrue(l.synced());
        assertTrue(l.approximate());
        assertEquals(12_000, l.lines().get(0).timeMs());   // 10 s × 240/200
        assertEquals(120_000, l.lines().get(1).timeMs());  // 100 s × 240/200
    }

    @Test
    void tooDifferentDurationsAreNotStretched() throws Exception {
        com.fasterxml.jackson.databind.JsonNode n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"duration\":120,\"syncedLyrics\":\"[00:10.00] a\"}");
        assertTrue(LyricsService.stretched(n, 300).isEmpty());
        assertEquals(1, LyricsService.stretched(n, 130).size());
        assertEquals(1, LyricsService.parse("[hm:approx]\n[00:01.00] x").size());
    }

    @Test
    void cleansMessyImportedNames() {
        assertEquals("FANNY ancrée à ton", LyricsService.cleanName("Zou.FANNY - ancrée à ton"));
        assertEquals("REGAL avec toi", LyricsService.cleanName("REGAL avec toi11"));
        assertEquals("BCBG ZOUK", LyricsService.cleanName("Zou.BCBG ZOUK (1)"));
        assertEquals("", LyricsService.cleanName("Piste 14"));
        assertEquals("", LyricsService.cleanName("Track 03"));
        assertFalse(LyricsService.usableArtist("artist", "Track 15"));
        assertFalse(LyricsService.usableArtist("ZOUK4", "Piste 04"));
        assertFalse(LyricsService.usableArtist("Unknown", "MARIO-la 1ere fois"));
        assertFalse(LyricsService.usableArtist("aj fille du soleil", "aj fille du soleil"));
        assertTrue(LyricsService.usableArtist("Zou.FANNY", "Fanny - Ancrée à ton port"));
    }

    @Test
    void verifiedMatchNeedsTitleWordsAndSameArtist() throws Exception {
        java.util.Set<String> known = java.util.Set.of("jean", "marie", "ragald", "laisse", "moi", "te", "dire");
        com.fasterxml.jackson.databind.JsonNode tayc = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"artistName\":\"No Limit, Tayc\",\"trackName\":\"Laisse moi te dire\",\"duration\":224,\"plainLyrics\":\"x\"}");
        assertEquals(0.0, LyricsService.verifiedScore(tayc, known, 224, true)); // même titre, autre interprète
        com.fasterxml.jackson.databind.JsonNode other = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"artistName\":\"Jean-Marie Ragald\",\"trackName\":\"Laisse moi partir\",\"duration\":224,\"plainLyrics\":\"x\"}");
        assertEquals(0.0, LyricsService.verifiedScore(other, known, 224, true)); // « partir » absent de nos noms
        com.fasterxml.jackson.databind.JsonNode good = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
                "{\"artistName\":\"Jean-Marie Ragald\",\"trackName\":\"Laisse moi te dire\",\"duration\":225,\"plainLyrics\":\"x\"}");
        assertTrue(LyricsService.verifiedScore(good, known, 224, true) > 0);
    }

    @Test
    void stripsTrackNumberOnlyFromFileNameTitles() {
        Path file = dir.resolve("09 Nothing gonna change my love for you.mp3");
        assertEquals("Nothing gonna change my love for you",
                LyricsService.searchTitle(track(file, "09 Nothing gonna change my love for you", null)));
        assertEquals("7 Rings", LyricsService.searchTitle(track(dir.resolve("01 - piste.mp3"), "7 Rings", "Ariana Grande")));
    }
}
