package com.hmood.music.service;

import com.hmood.catalog.CatalogException;
import com.hmood.music.CatalogEntry;
import com.hmood.music.ChartResult;
import com.hmood.music.provider.AudiusProvider;
import com.hmood.music.provider.JamendoProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Sons et musiques d'ambiance réels pour les modes Concentration et Sommeil : morceaux complets
 * publiés par des artistes sur Audius (lo-fi, ambient, piano, pluie, nuit…). Mis en cache 1 h.
 */
public final class AmbienceService {

    /** Une ambiance : recherche (ou genre) sur Audius. */
    public enum Ambience {
        LOFI("Lo-fi", "genre", "Lo-Fi", "lofi+chillhop"),
        AMBIENT("Ambient", "genre", "Ambient", "ambient+atmospheric"),
        PIANO("Piano", "search", "piano", "piano+solo"),
        INSTRUMENTAL("Instrumental", "search", "instrumental focus", "instrumental+focus"),
        RAIN("Sons de pluie", "search", "rain sounds", "rain+nature"),
        SOFT("Musique douce", "search", "calm relaxing", "calm+relaxing+soft"),
        NIGHT("Ambiance nocturne", "search", "night ambient", "night+dark+ambient"),
        SLEEP_PIANO("Piano pour dormir", "search", "sleep piano", "sleep+piano+lullaby");

        public final String label;
        final String type;
        final String value;
        final String jamendoTags;

        Ambience(String label, String type, String value, String jamendoTags) {
            this.label = label;
            this.type = type;
            this.value = value;
            this.jamendoTags = jamendoTags;
        }
    }

    private final AudiusProvider audius;
    private final JamendoProvider jamendo;
    private final TrendingService trending;

    public AmbienceService(AudiusProvider audius, JamendoProvider jamendo, TrendingService trending) {
        this.audius = audius;
        this.jamendo = jamendo;
        this.trending = trending;
    }

    /** Audius, complété par Jamendo s'il est configuré (titres alternés). */
    public ChartResult load(Ambience a) throws CatalogException {
        ChartResult fromAudius = null;
        CatalogException audiusError = null;
        try {
            fromAudius = loadAudius(a);
        } catch (CatalogException e) {
            audiusError = e;
        }
        if (jamendo == null || !jamendo.configured()) {
            if (fromAudius == null) throw audiusError;
            return fromAudius;
        }
        ChartResult fromJamendo;
        try {
            fromJamendo = trending.fetch(a.label, "Jamendo · tags « " + a.jamendoTags.replace('+', ' ') + " »",
                    "ambience-jamendo:" + a.name(), JamendoProvider.ID, false,
                    () -> jamendo.chart("tags:" + a.jamendoTags).stream().limit(30).toList());
        } catch (CatalogException e) {
            if (fromAudius == null) throw e;
            return fromAudius;
        }
        if (fromAudius == null) return fromJamendo;
        List<CatalogEntry> mixed = new ArrayList<>();
        List<CatalogEntry> x = fromAudius.entries(), y = fromJamendo.entries();
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            if (i < x.size()) mixed.add(x.get(i));
            if (i < y.size()) mixed.add(y.get(i));
        }
        return new ChartResult(a.label, fromAudius.source() + " + " + fromJamendo.source(), mixed,
                fromAudius.updatedAt().isBefore(fromJamendo.updatedAt()) ? fromAudius.updatedAt() : fromJamendo.updatedAt(),
                fromAudius.fromCache() && fromJamendo.fromCache(), null);
    }

    private ChartResult loadAudius(Ambience a) throws CatalogException {
        return trending.fetch(a.label, "Audius · " + ("genre".equals(a.type) ? "tendances " + a.value : "recherche « " + a.value + " »")
                        + " (morceaux complets)", "ambience:" + a.name(), AudiusProvider.ID, false,
                () -> {
                    List<CatalogEntry> list = "genre".equals(a.type) ? audius.chart("trending:" + a.value) : audius.search(a.value);
                    return list.stream().filter(CatalogEntry::playable).limit(40).toList();
                });
    }
}
