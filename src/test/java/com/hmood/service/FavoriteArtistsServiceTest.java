package com.hmood.service;

import com.hmood.catalog.CatalogException;
import com.hmood.database.Database;
import com.hmood.music.provider.DeezerProvider.ArtistRef;
import com.hmood.repository.FavoriteArtistsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FavoriteArtistsServiceTest {

    @TempDir
    Path dir;

    private FavoriteArtistsService service() {
        Database db = new Database(dir.resolve("f.db"));
        db.initialize();
        return new FavoriteArtistsService(new FavoriteArtistsRepository(db, Database.DEFAULT_USER_ID),
                name -> name.equalsIgnoreCase("Tayc")
                        ? List.of(new ArtistRef("5292512", "Tayc", 1_000_000, "https://img/tayc.jpg"))
                        : List.of());
    }

    @Test
    void togglesAnArtistFoundByNameOnDeezer() throws Exception {
        FavoriteArtistsService s = service();
        var added = s.toggle(null, "Tayc feat. Dadju", null);
        assertTrue(added.favorite());
        assertEquals("5292512", added.artist().id());
        assertEquals("https://img/tayc.jpg", s.list().get(0).picture());
        assertTrue(s.isFavorite(null, "tayc"));
        assertTrue(s.isFavorite("5292512", null));

        assertFalse(s.toggle(null, "Tayc", null).favorite());
        assertTrue(s.list().isEmpty());
    }

    @Test
    void keepsTheDeezerIdWhenKnownAndRefusesUnknownArtists() throws Exception {
        FavoriteArtistsService s = service();
        assertTrue(s.toggle("14498725", "Meiitod", "https://img/m.jpg").favorite());
        assertEquals("14498725", s.list().get(0).id());
        assertThrows(CatalogException.class, () -> s.toggle(null, "Artiste inexistant", null));
        assertEquals("Tayc", FavoriteArtistsService.primary("Tayc, Dadju"));
    }
}
