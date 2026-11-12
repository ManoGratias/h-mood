package com.hmood.ai;

import com.hmood.model.Mood;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Interprétation locale (sans IA) de demandes simples, en français ou en anglais,
 * par mots-clés. L'ordre des règles compte : la première qui correspond l'emporte.
 */
public final class LocalIntentParser {

    public record Intent(Mood mood, boolean surprise, String matchedKeyword) {
    }

    private record Rule(Mood mood, List<String> keywords) {
    }

    private static final List<String> SURPRISE = List.of("surprise", "surprends", "nouveau", "nouveaute", "new", "decouv", "discover", "random", "hasard");

    private static final List<Rule> RULES = List.of(
            new Rule(Mood.MOTIVATED, List.of("motivat", "motiv", "workout", "training", "entrainement", "sport", "gym", "courir", "running", "boost")),
            new Rule(Mood.FOCUSED, List.of("concentr", "focus", "study", "etudi", "revis", "travail", "work", "code", "coder")),
            new Rule(Mood.ROMANTIC, List.of("romant", "love", "amour", "date", "valentin")),
            new Rule(Mood.RELAXED, List.of("rain", "pluie", "pluvieux", "relax", "chill", "calme", "detente", "detendre", "zen")),
            new Rule(Mood.ENERGETIC, List.of("energ", "party", "fete", "danse", "dance", "bouger")),
            new Rule(Mood.ANGRY, List.of("angry", "colere", "enerve", "rage", "furieux")),
            new Rule(Mood.SAD, List.of("sad", "triste", "cry", "pleurer", "chagrin")),
            new Rule(Mood.LONELY, List.of("lonely", "seul", "solitude")),
            new Rule(Mood.NOSTALGIC, List.of("nostalg", "souvenir", "memories", "old school", "annees", "retro")),
            new Rule(Mood.CONFIDENT, List.of("confident", "confiance", "assurance", "boss")),
            new Rule(Mood.DREAMY, List.of("dream", "reve", "rever", "sleep", "dormir", "nuit", "night")),
            new Rule(Mood.HAPPY, List.of("happy", "heureux", "heureuse", "joie", "joyeux", "content", "bonne humeur", "sunny", "soleil"))
    );

    public Optional<Intent> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(text);
        for (String k : SURPRISE) {
            if (normalized.contains(k)) {
                return Optional.of(new Intent(null, true, k));
            }
        }
        for (Rule rule : RULES) {
            for (String k : rule.keywords()) {
                if (normalized.contains(k)) {
                    return Optional.of(new Intent(rule.mood(), false, k));
                }
            }
        }
        // Nom d'humeur cité directement (« give me something dreamy »)
        for (Mood m : Mood.values()) {
            if (normalized.contains(normalize(m.label()))) {
                return Optional.of(new Intent(m, false, m.label()));
            }
        }
        return Optional.empty();
    }

    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "");
    }
}
