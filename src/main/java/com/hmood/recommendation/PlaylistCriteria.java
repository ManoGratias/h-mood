package com.hmood.recommendation;

import com.hmood.model.Mood;

/**
 * Critères de génération d'une playlist (Studio playlists). Tous les filtres sont facultatifs.
 *
 * @param decade        décennie (ex. 1990) ou {@code null}
 * @param targetMinutes durée visée, 0 = pas de limite
 */
public record PlaylistCriteria(Mood mood, String genre, Integer decade, int targetMinutes) {
}
