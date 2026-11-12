package com.hmood.catalog;

import com.hmood.model.Mood;
import com.hmood.model.Track;

import java.util.List;

/**
 * Catalogue musical en ligne (diffusion autorisée par les artistes). Implémentation actuelle :
 * {@link AudiusCatalog}. Toutes les méthodes sont bloquantes : à appeler hors du thread JavaFX.
 * Les morceaux renvoyés sont « distants » ({@link Track#isRemote()}) : identifiant négatif, non stockés en base.
 */
public interface MusicCatalog {

    String name();

    /** Tendances, éventuellement filtrées par genre (null = tous). */
    List<Track> trending(String genre) throws CatalogException;

    List<Track> search(String query) throws CatalogException;

    /** Vrais morceaux correspondant à une humeur H MOOD. */
    List<Track> forMood(Mood mood) throws CatalogException;

    /** URL de lecture directe d'un morceau distant. */
    String resolveStream(Track track) throws CatalogException;
}
