package com.hmood.music;

import com.hmood.catalog.CatalogException;
import com.hmood.model.Mood;
import com.hmood.music.provider.JamendoProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JamendoProviderTest {

    @Test
    void withoutClientIdItIsIgnoredAndNeverSimulated() {
        JamendoProvider p = new JamendoProvider(() -> "");
        assertFalse(p.configured());
        assertFalse(p.available());
        CatalogException e = assertThrows(CatalogException.class, () -> p.chart("popular"));
        assertTrue(e.getMessage().contains("non configuré"));
    }

    @Test
    void everyMoodSearchesWithAmbienceTags() throws Exception {
        for (Mood m : Mood.values()) {
            JamendoProvider p = new JamendoProvider(() -> null);
            assertThrows(CatalogException.class, () -> p.forMood(m), m.name());
        }
        assertTrue(new JamendoProvider(() -> "abc").configured());
    }
}
