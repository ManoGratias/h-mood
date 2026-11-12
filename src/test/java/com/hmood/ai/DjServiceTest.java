package com.hmood.ai;

import com.hmood.model.Track;
import com.hmood.music.TempoAnalyzer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DjServiceTest {

    /** Batterie synthétique : grosse caisse sur chaque temps + bruit. */
    private static float[] drums(double bpm, int sampleRate, int seconds) {
        float[] x = new float[sampleRate * seconds];
        Random random = new Random(1);
        double beat = 60.0 / bpm * sampleRate;
        for (int i = 0; i < x.length; i++) x[i] = (float) (random.nextGaussian() * 0.01);
        for (double t = 0; t < x.length; t += beat) {
            int start = (int) t;
            for (int j = 0; j < 2000 && start + j < x.length; j++) {
                x[start + j] += (float) (Math.sin(2 * Math.PI * 60 * j / sampleRate) * Math.exp(-j / 400.0) * 0.8);
            }
        }
        return x;
    }

    @ParameterizedTest
    @ValueSource(doubles = {90, 100, 120, 128, 140})
    void tempoIsEstimated(double bpm) {
        TempoAnalyzer.Result r = TempoAnalyzer.analyze(drums(bpm, 22050, 30), 22050);
        assertTrue(Math.abs(r.bpm() - bpm) <= 3, "attendu " + bpm + ", estimé " + r.bpm());
        assertTrue(r.energy() > 0);
    }

    @Test
    void silenceGivesNoTempo() {
        assertEquals(0, TempoAnalyzer.analyze(new float[22050 * 5], 22050).bpm());
    }

    private static DjService.Item item(String artist, Double bpm) {
        Track t = new Track(-Math.abs((artist + bpm).hashCode()) - 1L, "x", artist + " " + bpm, artist, null, null, null,
                180_000, null, null, 0, false);
        return new DjService.Item(t, artist, bpm, 0.5, "test", 0L);
    }

    @Test
    void djOrderRisesThenFallsWithSmallSteps() {
        List<DjService.Item> items = new ArrayList<>();
        double[] bpms = {140, 90, 100, 128, 95, 120, 110, 132, 105, 115};
        for (int i = 0; i < bpms.length; i++) items.add(item("Artiste " + i, bpms[i]));
        List<DjService.Item> ordered = DjService.djOrder(items);
        assertEquals(items.size(), ordered.size());
        // le pic est au milieu, pas au début ni à la fin
        int peak = 0;
        for (int i = 1; i < ordered.size(); i++) if (ordered.get(i).bpm() > ordered.get(peak).bpm()) peak = i;
        assertTrue(peak > 0 && peak < ordered.size() - 1);
        // écarts entre deux titres consécutifs limités (au plus deux crans de la liste triée)
        for (int i = 1; i < ordered.size(); i++) {
            assertTrue(Math.abs(ordered.get(i).bpm() - ordered.get(i - 1).bpm()) <= 20,
                    "saut trop grand : " + ordered.get(i - 1).bpm() + " → " + ordered.get(i).bpm());
        }
    }

    @Test
    void sameArtistIsNeverBackToBackWhenAvoidable() {
        List<DjService.Item> items = new ArrayList<>();
        items.add(item("Toofan", 100.0));
        items.add(item("Toofan", 101.0));
        items.add(item("Toofan", 102.0));
        items.add(item("Fanicko", 103.0));
        items.add(item("Almok", 104.0));
        items.add(item("King Mensah", 105.0));
        List<DjService.Item> ordered = DjService.djOrder(items);
        for (int i = 1; i < ordered.size(); i++) {
            assertNotEquals(ordered.get(i).artist(), ordered.get(i - 1).artist(), "même artiste d'affilée à la position " + i);
        }
    }

    @Test
    void tracksWithoutTempoGoLast() {
        List<DjService.Item> items = List.of(item("A", null), item("B", 120.0), item("C", 100.0));
        List<DjService.Item> ordered = DjService.djOrder(new ArrayList<>(items));
        assertEquals(null, ordered.get(2).bpm());
    }

    @Test
    void everyStyleHasARealSourceDescription() {
        for (DjService.Style s : DjService.STYLES) {
            assertTrue(!s.sources().isEmpty() && !s.origin().isBlank(), s.key());
        }
    }
}
