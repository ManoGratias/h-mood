package com.hmood.music;

/**
 * Définition d'un classement : quel fournisseur, quelle référence, comment le présenter.
 *
 * @param chartRef référence propre au fournisseur (ex. « playlist:1109890291 »)
 * @param source   description lisible de la source réelle affichée à l'utilisateur
 */
public record ChartDefinition(String key, String label, String providerId, String chartRef,
                              CatalogEntry.Kind kind, String source, String colorA, String colorB) {
}
