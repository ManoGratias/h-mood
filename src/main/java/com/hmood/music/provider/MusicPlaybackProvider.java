package com.hmood.music.provider;

import com.hmood.catalog.CatalogException;
import com.hmood.music.CatalogEntry;

/**
 * Fournit l'URL de lecture autorisée d'un morceau (extrait officiel ou flux complet autorisé).
 * Ne doit jamais contourner les restrictions de la plateforme.
 */
public interface MusicPlaybackProvider {

    String id();

    boolean supports(CatalogEntry entry);

    String resolve(CatalogEntry entry) throws CatalogException;
}
