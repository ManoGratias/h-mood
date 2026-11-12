package com.hmood.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AudiusCatalogTest {

    private static final String JSON = """
            {"data":[
              {"id":"J7Xb000","title":"Murder She Wrote ","user":{"name":"Brooke Evers"},"genre":"House","mood":"Upbeat",
               "duration":186,"play_count":5400,"release_date":"2026-10-30T00:00:00Z","is_streamable":true,
               "artwork":{"150x150":"https://x/150.jpg","480x480":"https://x/480.jpg"}},
              {"id":"NOPE","title":"Hidden","user":{"name":"X"},"is_streamable":false},
              {"id":"abc","title":"Sans humeur","user":{"name":"Y"},"duration":60,"artwork":null}
            ]}""";

    @Test
    void parsesStreamableTracksAsRemoteTracks() throws Exception {
        List<Track> tracks = AudiusCatalog.tracks(new ObjectMapper().readTree(JSON));
        assertEquals(2, tracks.size());
        Track t = tracks.get(0);
        assertTrue(t.isRemote());
        assertEquals("audius:J7Xb000", t.filePath());
        assertEquals("Murder She Wrote", t.title());
        assertEquals("Brooke Evers", t.artist());
        assertEquals(Mood.HAPPY, t.mood());
        assertEquals(186_000, t.durationMs());
        assertEquals(2026, t.year());
        assertEquals("https://x/480.jpg", t.coverPath());
        assertNull(tracks.get(1).mood());
        assertNull(tracks.get(1).coverPath());
    }

    @Test
    void remoteIdsAreNegativeAndStable() {
        assertTrue(AudiusCatalog.remoteId("J7Xb000") < 0);
        assertEquals(AudiusCatalog.remoteId("abc"), AudiusCatalog.remoteId("abc"));
    }

    @Test
    void everyMoodHasAnAudiusQueryThatMapsBack() {
        for (Mood m : Mood.values()) {
            AudiusMoods.Query q = AudiusMoods.queryFor(m);
            assertNotNull(q, m.name());
            assertNotNull(AudiusMoods.fromAudius(q.audiusMood()), m.name());
        }
        assertEquals(Mood.ANGRY, AudiusMoods.fromAudius("Fiery"));
    }
}
