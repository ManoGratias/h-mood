package com.hmood.catalog;

import com.hmood.model.Mood;

import java.util.Locale;
import java.util.Map;

/** Correspondance entre les humeurs H MOOD et le vocabulaire d'humeurs d'Audius. */
public final class AudiusMoods {

    /** Humeur Audius recherchée (et genre éventuel) pour chaque humeur H MOOD. */
    public record Query(String audiusMood, String genre) {
    }

    private static final Map<Mood, Query> QUERIES = Map.ofEntries(
            Map.entry(Mood.HAPPY, new Query("Upbeat", null)),
            Map.entry(Mood.SAD, new Query("Melancholy", null)),
            Map.entry(Mood.ROMANTIC, new Query("Romantic", null)),
            Map.entry(Mood.ENERGETIC, new Query("Energizing", null)),
            Map.entry(Mood.RELAXED, new Query("Peaceful", null)),
            Map.entry(Mood.ANGRY, new Query("Aggressive", null)),
            Map.entry(Mood.MOTIVATED, new Query("Empowering", null)),
            Map.entry(Mood.NOSTALGIC, new Query("Sentimental", null)),
            Map.entry(Mood.FOCUSED, new Query("Peaceful", "Lo-Fi")),
            Map.entry(Mood.LONELY, new Query("Yearning", null)),
            Map.entry(Mood.CONFIDENT, new Query("Cool", null)),
            Map.entry(Mood.DREAMY, new Query("Tender", null)));

    private static final Map<String, Mood> REVERSE = Map.ofEntries(
            Map.entry("upbeat", Mood.HAPPY), Map.entry("excited", Mood.HAPPY),
            Map.entry("melancholy", Mood.SAD), Map.entry("brooding", Mood.SAD),
            Map.entry("romantic", Mood.ROMANTIC), Map.entry("sensual", Mood.ROMANTIC),
            Map.entry("energizing", Mood.ENERGETIC), Map.entry("rowdy", Mood.ENERGETIC), Map.entry("stirring", Mood.ENERGETIC),
            Map.entry("peaceful", Mood.RELAXED), Map.entry("easygoing", Mood.RELAXED),
            Map.entry("aggressive", Mood.ANGRY), Map.entry("fiery", Mood.ANGRY), Map.entry("gritty", Mood.ANGRY), Map.entry("defiant", Mood.ANGRY),
            Map.entry("empowering", Mood.MOTIVATED),
            Map.entry("sentimental", Mood.NOSTALGIC),
            Map.entry("serious", Mood.FOCUSED), Map.entry("sophisticated", Mood.FOCUSED),
            Map.entry("yearning", Mood.LONELY),
            Map.entry("cool", Mood.CONFIDENT),
            Map.entry("tender", Mood.DREAMY));

    private AudiusMoods() {
    }

    public static Query queryFor(Mood mood) {
        return QUERIES.get(mood);
    }

    public static Mood fromAudius(String audiusMood) {
        return audiusMood == null ? null : REVERSE.get(audiusMood.toLowerCase(Locale.ROOT));
    }
}
