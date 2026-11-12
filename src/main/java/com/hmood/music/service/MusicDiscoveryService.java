package com.hmood.music.service;

import com.hmood.catalog.CatalogException;
import com.hmood.music.CatalogEntry;
import com.hmood.music.ChartResult;
import com.hmood.music.provider.DeezerProvider;
import com.hmood.music.provider.MusicMetadataProvider;

import java.util.Optional;

/** Découverte : top d'un artiste, contenu d'un album, titres similaires, métadonnées de référence. */
public final class MusicDiscoveryService {

    private final DeezerProvider deezer;
    private final MusicMetadataProvider metadata;
    private final TrendingService trending;

    public MusicDiscoveryService(DeezerProvider deezer, MusicMetadataProvider metadata, TrendingService trending) {
        this.deezer = deezer;
        this.metadata = metadata;
        this.trending = trending;
    }

    public ChartResult artistTop(CatalogEntry e) throws CatalogException {
        String artistId = requireDeezerArtist(e);
        return trending.fetch("Top titres · " + e.artist(), "Deezer · titres les plus écoutés de l'artiste",
                "artist-top:" + artistId, DeezerProvider.ID, false, () -> deezer.chart("artist-top:" + artistId));
    }

    public ChartResult similar(CatalogEntry e) throws CatalogException {
        String artistId = requireDeezerArtist(e);
        return trending.fetch("Dans l'esprit de " + e.artist(), "Deezer · radio de l'artiste (titres similaires)",
                "artist-radio:" + artistId, DeezerProvider.ID, false, () -> deezer.chart("artist-radio:" + artistId));
    }

    public ChartResult album(CatalogEntry e) throws CatalogException {
        if (!DeezerProvider.ID.equals(e.provider()) || e.albumId() == null) {
            throw new CatalogException("Album disponible uniquement pour les éléments Deezer");
        }
        return trending.fetch("Album · " + e.album() + " — " + e.artist(), "Deezer · album",
                "album:" + e.albumId(), DeezerProvider.ID, false, () -> deezer.chart("album:" + e.albumId()));
    }

    public java.util.List<DeezerProvider.ArtistRef> searchArtists(String query) throws CatalogException {
        return deezer.searchArtists(query);
    }

    /** Playlists réelles correspondant à une recherche (éditoriales Deezer en premier). */
    public java.util.List<DeezerProvider.PlaylistRef> searchPlaylists(String query) throws CatalogException {
        return deezer.searchPlaylists(query);
    }

    public ChartResult playlist(DeezerProvider.PlaylistRef ref) throws CatalogException {
        return trending.fetch(ref.title(), "Deezer · playlist « " + ref.title() + " » par " + ref.creator(),
                "playlist:" + ref.id(), DeezerProvider.ID, false, () -> deezer.chart("playlist:" + ref.id()));
    }

    /** Morceaux émergents : tendances des artistes indépendants (Audius). */
    public ChartResult emerging(boolean force) throws CatalogException {
        return trending.chart("indie", force);
    }

    public Optional<MusicMetadataProvider.Metadata> metadata(CatalogEntry e) throws CatalogException {
        return metadata.lookup(e.title(), e.artist());
    }

    private static String requireDeezerArtist(CatalogEntry e) throws CatalogException {
        if (!DeezerProvider.ID.equals(e.provider()) || e.artistId() == null) {
            throw new CatalogException("Disponible pour les titres et artistes Deezer");
        }
        return e.artistId();
    }
}
