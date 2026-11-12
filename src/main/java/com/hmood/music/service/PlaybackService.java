package com.hmood.music.service;

import com.hmood.catalog.AudiusCatalog;
import com.hmood.catalog.CatalogException;
import com.hmood.model.Track;
import com.hmood.music.CatalogEntry;
import com.hmood.music.PlaybackMode;
import com.hmood.music.provider.MusicPlaybackProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pont entre le catalogue et le lecteur : convertit les éléments en morceaux lisibles et résout,
 * au moment de la lecture, l'URL autorisée auprès du bon fournisseur (fichiers locaux, extraits officiels,
 * flux complets autorisés). Les morceaux non lisibles sont signalés comme « écoute sur la plateforme ».
 */
public final class PlaybackService {

    private final List<MusicPlaybackProvider> providers;
    private final AudiusCatalog audius;
    private final Map<Long, CatalogEntry> entries = new ConcurrentHashMap<>();

    public PlaybackService(List<MusicPlaybackProvider> providers, AudiusCatalog audius) {
        this.providers = providers;
        this.audius = audius;
    }

    public Track toTrack(CatalogEntry e) {
        long id = -((e.key().hashCode() & 0x7fffffffL) + 1L);
        entries.put(id, e);
        String path = "audius".equals(e.provider()) ? AudiusCatalog.PREFIX + e.externalId() : "catalog:" + e.key();
        Integer year = e.releaseDate() != null && e.releaseDate().length() >= 4 && e.releaseDate().substring(0, 4).chars().allMatch(Character::isDigit)
                ? Integer.parseInt(e.releaseDate().substring(0, 4)) : null;
        return new Track(id, path, e.title(), e.artist(), e.album(), e.genre(), year, e.durationMs(), e.mood(),
                e.coverUrl(), 0, false);
    }

    /** Morceaux lisibles uniquement, dans l'ordre. */
    public List<Track> playable(List<CatalogEntry> list) {
        List<Track> out = new ArrayList<>();
        for (CatalogEntry e : list) {
            if (e.playable()) out.add(toTrack(e));
        }
        return out;
    }

    public Optional<CatalogEntry> info(Track track) {
        return track == null ? Optional.empty() : Optional.ofNullable(entries.get(track.id()));
    }

    /** Résolution de l'URL de lecture (bloquant). */
    public String resolve(Track track) throws CatalogException {
        CatalogEntry e = entries.get(track.id());
        if (e == null) {
            if (track.filePath().startsWith(AudiusCatalog.PREFIX)) return audius.resolveStream(track);
            throw new CatalogException("Source de lecture inconnue");
        }
        if (e.playback() == PlaybackMode.LINK_ONLY) {
            throw new CatalogException("Lecture non autorisée dans H MOOD : écoute ce titre sur " + providerName(e));
        }
        for (MusicPlaybackProvider p : providers) {
            if (p.supports(e)) return p.resolve(e);
        }
        throw new CatalogException("Aucun lecteur pour la source " + e.provider());
    }

    public static String providerName(CatalogEntry e) {
        return switch (e.provider()) {
            case "deezer" -> "Deezer";
            case "apple" -> "Apple Music";
            case "audius" -> "Audius";
            case "jamendo" -> "Jamendo";
            default -> e.provider();
        };
    }
}
