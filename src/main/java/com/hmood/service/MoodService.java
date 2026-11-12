package com.hmood.service;

import com.hmood.model.Mood;
import com.hmood.repository.HistoryRepository;

import java.time.LocalTime;
import java.util.Optional;

/** Humeur de l'utilisateur : sélection manuelle, questionnaire, historique. */
public final class MoodService {

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_QUESTIONNAIRE = "questionnaire";

    private final HistoryRepository history;

    public MoodService(HistoryRepository history) {
        this.history = history;
    }

    public void select(Mood mood, String source) {
        history.recordMood(mood, source);
    }

    public Optional<Mood> todayMood() {
        return history.latestMoodToday();
    }

    /** Ambiance suggérée selon le moment de la journée (règle locale simple, affichée comme telle). */
    public static Mood suggestForTime(LocalTime time) {
        int h = time.getHour();
        if (h >= 5 && h < 11) return Mood.MOTIVATED;
        if (h >= 11 && h < 14) return Mood.HAPPY;
        if (h >= 14 && h < 18) return Mood.FOCUSED;
        if (h >= 18 && h < 22) return Mood.RELAXED;
        return Mood.DREAMY;
    }
}
