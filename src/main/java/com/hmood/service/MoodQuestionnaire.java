package com.hmood.service;

import com.hmood.model.Mood;

/**
 * Questionnaire facultatif du Scanner d'humeur : trois réponses → une humeur suggérée.
 * Règles explicites et déterministes (aucune détection automatique d'émotion).
 */
public final class MoodQuestionnaire {

    public enum Energy {
        LOW("Faible"), MEDIUM("Moyenne"), HIGH("Élevée");
        public final String label;
        Energy(String label) { this.label = label; }
    }

    public enum Need {
        COMFORT("Me réconforter"), MOTIVATION("Me motiver"), ESCAPE("M'évader"), FOCUS("Me concentrer"), CELEBRATE("Faire la fête");
        public final String label;
        Need(String label) { this.label = label; }
    }

    public enum Feeling {
        POSITIVE("Plutôt bien"), NEUTRAL("Neutre"), NEGATIVE("Pas terrible");
        public final String label;
        Feeling(String label) { this.label = label; }
    }

    private MoodQuestionnaire() {
    }

    public static Mood suggest(Energy energy, Need need, Feeling feeling) {
        return switch (need) {
            case FOCUS -> Mood.FOCUSED;
            case CELEBRATE -> energy == Energy.HIGH ? Mood.ENERGETIC : Mood.HAPPY;
            case MOTIVATION -> {
                if (feeling == Feeling.NEGATIVE && energy == Energy.HIGH) yield Mood.ANGRY;
                yield feeling == Feeling.POSITIVE ? Mood.CONFIDENT : Mood.MOTIVATED;
            }
            case ESCAPE -> switch (energy) {
                case LOW -> Mood.DREAMY;
                case MEDIUM -> Mood.NOSTALGIC;
                case HIGH -> Mood.HAPPY;
            };
            case COMFORT -> switch (feeling) {
                case NEGATIVE -> energy == Energy.LOW ? Mood.SAD : Mood.LONELY;
                case NEUTRAL -> Mood.RELAXED;
                case POSITIVE -> Mood.ROMANTIC;
            };
        };
    }
}
