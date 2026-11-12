package com.hmood.repository;

import com.hmood.database.Database;
import com.hmood.repository.WrappedRepository.Wrapped;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.YearMonth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WrappedRepositoryTest {

    @TempDir
    Path dir;

    @Test
    void monthlyRecapCombinesStreamingPlaysAndMoods() throws Exception {
        Database db = new Database(dir.resolve("w.db"));
        db.initialize();
        WrappedRepository repo = new WrappedRepository(db, Database.DEFAULT_USER_ID);
        repo.recordStream("deezer", "1", "Pansement", "Meiitod", "Rap/Hip Hop", null, "SAD", 155_000, true);
        repo.recordStream("deezer", "1", "Pansement", "Meiitod", "Rap/Hip Hop", null, "SAD", 155_000, true);
        repo.recordStream("deezer", "2", "Le temps", "Tayc", "R&B", null, null, 120_000, false);
        try (Connection c = db.connect(); Statement s = c.createStatement()) {
            s.executeUpdate("INSERT INTO mood_history (user_id, mood) VALUES (1, 'SAD'), (1, 'SAD'), (1, 'HAPPY')");
            // une écoute d'un autre mois ne doit pas compter
            s.executeUpdate("INSERT INTO stream_history (user_id, provider, title, artist, played_at, listened_ms) "
                    + "VALUES (1, 'deezer', 'Ancien', 'X', '2020-01-15 12:00:00', 60000)");
        }
        Wrapped w = repo.month(YearMonth.now());
        assertEquals(3, w.plays());
        assertEquals(2, w.artists());
        assertEquals(2, w.tracks());
        assertEquals((155_000 * 2 + 120_000) / 60000.0, w.minutes(), 0.01);
        assertEquals("Meiitod", w.topArtists().get(0).label());
        assertEquals("Pansement", w.topTracks().get(0).title());
        assertEquals(2, w.topTracks().get(0).plays());
        assertEquals("SAD", w.moods().get(0).label());
        assertEquals("Rap/Hip Hop", w.genres().get(0).label());
        assertTrue(repo.months().contains(YearMonth.of(2020, 1)));
        assertTrue(repo.month(YearMonth.of(2019, 5)).isEmpty());
    }
}
