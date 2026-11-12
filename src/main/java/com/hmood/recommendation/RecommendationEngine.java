package com.hmood.recommendation;

import com.hmood.model.Mood;
import com.hmood.model.Track;

import java.util.List;

/**
 * Moteur de recommandation. L'implémentation actuelle est locale et déterministe
 * ({@link RuleBasedRecommendationEngine}) ; une implémentation fondée sur un service
 * externe pourra être branchée derrière cette interface.
 */
public interface RecommendationEngine {

    /** Morceaux pertinents pour l'humeur, du plus au moins pertinent. Liste vide si rien ne correspond. */
    List<Track> forMood(Mood mood, int limit);

    /** Génère une playlist selon les critères du Studio playlists. */
    List<Track> generate(PlaylistCriteria criteria);

    /** Sélection du jour (stable pendant la journée). */
    List<Track> dailyPicks(int limit);

    /** Artistes les plus représentés dans une liste de morceaux. */
    List<String> topArtists(List<Track> tracks, int limit);
}
