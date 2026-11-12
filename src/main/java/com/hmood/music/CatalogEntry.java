package com.hmood.music;

import com.hmood.model.Mood;

/**
 * Élément réel issu d'un fournisseur (morceau, artiste ou album). Aucune donnée n'est inventée :
 * un champ inconnu reste {@code null}.
 *
 * @param rank       position dans le classement (si l'élément vient d'un classement)
 * @param popularity indicateur de popularité fourni par la source (ex. « rank » Deezer)
 * @param previewUrl URL de lecture autorisée (extrait ou flux complet selon {@link #playback()})
 * @param externalUrl lien vers la page du service officiel
 */
public record CatalogEntry(
        Kind kind,
        String provider,
        String externalId,
        String title,
        String artist,
        String artistId,
        String album,
        String albumId,
        String coverUrl,
        String genre,
        String releaseDate,
        long durationMs,
        Integer rank,
        Long popularity,
        String previewUrl,
        String externalUrl,
        PlaybackMode playback,
        Mood mood) {

    public enum Kind { TRACK, ARTIST, ALBUM }

    public String key() {
        return provider + ":" + externalId;
    }

    public boolean playable() {
        return kind == Kind.TRACK && playback != PlaybackMode.LINK_ONLY;
    }

    public CatalogEntry withRank(Integer newRank) {
        return new CatalogEntry(kind, provider, externalId, title, artist, artistId, album, albumId, coverUrl, genre,
                releaseDate, durationMs, newRank, popularity, previewUrl, externalUrl, playback, mood);
    }

    public CatalogEntry withAlbum(String albumTitle, String albumIdentifier, String cover) {
        return new CatalogEntry(kind, provider, externalId, title, artist, artistId, albumTitle, albumIdentifier,
                coverUrl != null ? coverUrl : cover, genre, releaseDate, durationMs, rank, popularity, previewUrl,
                externalUrl, playback, mood);
    }
}
