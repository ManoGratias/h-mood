package com.hmood.music;

import java.time.Instant;
import java.util.List;

/**
 * Résultat d'un classement ou d'une recherche, avec sa source et sa date réelle de récupération.
 *
 * @param fromCache vrai si les données viennent du cache local
 * @param warning   message à afficher (ex. réseau indisponible : données du cache)
 */
public record ChartResult(String title, String source, List<CatalogEntry> entries, Instant updatedAt,
                          boolean fromCache, String warning) {
}
