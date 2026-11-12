package com.hmood.music.provider;

import com.hmood.catalog.CatalogException;
import com.hmood.music.CatalogEntry;

import java.util.List;

/** Fournisseur capable de rechercher dans un catalogue musical réel. Appels bloquants. */
public interface MusicCatalogProvider {

    String id();

    String displayName();

    List<CatalogEntry> search(String query) throws CatalogException;

    /** Faux si le connecteur n'est pas configuré (clé manquante) : il est alors ignoré, jamais simulé. */
    default boolean available() {
        return true;
    }
}
