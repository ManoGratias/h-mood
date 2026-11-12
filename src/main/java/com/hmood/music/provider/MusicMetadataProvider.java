package com.hmood.music.provider;

import com.hmood.catalog.CatalogException;

import java.util.Optional;

/** Fournisseur de métadonnées de référence (dates de sortie, identifiants…). Appels bloquants. */
public interface MusicMetadataProvider {

    record Metadata(String source, String recordingId, String title, String artist, String firstReleaseDate,
                    String releaseTitle, String url) {
    }

    String id();

    Optional<Metadata> lookup(String title, String artist) throws CatalogException;
}
