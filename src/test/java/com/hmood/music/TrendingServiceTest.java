package com.hmood.music;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmood.catalog.CatalogException;
import com.hmood.database.Database;
import com.hmood.music.provider.MusicChartProvider;
import com.hmood.music.service.TrendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrendingServiceTest {

    @TempDir
    Path dir;

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicBoolean offline = new AtomicBoolean();
    private TrendingService service;

    private static CatalogEntry track(String id, int rank) {
        return new CatalogEntry(CatalogEntry.Kind.TRACK, "deezer", id, "Titre " + id, "Artiste", "1", "Album", "2",
                null, null, null, 180_000, rank, 1000L, "https://preview/" + id, "https://deezer/" + id,
                PlaybackMode.PREVIEW, null);
    }

    @BeforeEach
    void setUp() {
        Database db = new Database(dir.resolve("t.db"));
        db.initialize();
        MusicChartProvider fake = new MusicChartProvider() {
            @Override public String id() { return "deezer"; }
            @Override public String displayName() { return "Deezer"; }
            @Override public List<CatalogEntry> chart(String ref) throws CatalogException {
                calls.incrementAndGet();
                if (offline.get()) throw new CatalogException("Deezer injoignable");
                return List.of(track("a", 1), track("b", 2));
            }
        };
        service = new TrendingService(Map.of("deezer", fake), new CatalogCache(db));
    }

    @Test
    void secondCallIsServedFromCacheWithItsDate() throws Exception {
        ChartResult first = service.chart("france", false);
        ChartResult second = service.chart("france", false);
        assertEquals(1, calls.get());
        assertFalse(first.fromCache());
        assertTrue(second.fromCache());
        assertEquals(first.updatedAt(), second.updatedAt());
        assertEquals("Titre a", second.entries().get(0).title());
        assertTrue(second.source().contains("Top France"));
    }

    @Test
    void networkFailureFallsBackToCacheWithWarning() throws Exception {
        service.chart("france", false);
        offline.set(true);
        ChartResult r = service.chart("france", true);
        assertTrue(r.fromCache());
        assertNotNull(r.warning());
        assertEquals(2, r.entries().size());
    }

    @Test
    void networkFailureWithoutCacheIsReported() {
        offline.set(true);
        assertThrows(CatalogException.class, () -> service.chart("rnb", false));
    }

    @Test
    void deezerJsonIsMappedWithoutInventingData() throws Exception {
        String json = """
                {"data":[{"id":1,"title":"Djadja","duration":170,"rank":900000,"preview":"https://p/1.mp3",
                  "link":"https://www.deezer.com/track/1","artist":{"id":7,"name":"Aya Nakamura"},
                  "album":{"id":9,"title":"Nakamura","cover_big":"https://c/big.jpg"}},
                 {"id":2,"title":"Sans extrait","duration":100,"preview":"","artist":{"id":8,"name":"X"},"album":{}}]}""";
        List<CatalogEntry> list = com.hmood.music.provider.DeezerProviderAccess.parse(new ObjectMapper().readTree(json).path("data"));
        assertEquals(2, list.size());
        CatalogEntry e = list.get(0);
        assertEquals("Aya Nakamura", e.artist());
        assertEquals(1, e.rank());
        assertEquals(PlaybackMode.PREVIEW, e.playback());
        assertEquals(PlaybackMode.LINK_ONLY, list.get(1).playback());
        assertEquals(null, list.get(1).coverUrl());
        assertFalse(list.get(1).playable());
    }

    @Test
    void everyChartDeclaresARealSource() {
        for (ChartDefinition def : TrendingService.CHARTS) {
            assertFalse(def.source().isBlank(), def.key());
        }
        assertTrue(TrendingService.CHARTS.size() >= 15);
    }
}
