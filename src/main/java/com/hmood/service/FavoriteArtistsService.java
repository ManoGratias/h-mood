package com.hmood.service;

import com.hmood.catalog.CatalogException;
import com.hmood.music.CatalogEntry;
import com.hmood.music.provider.DeezerProvider;
import com.hmood.repository.FavoriteArtistsRepository;
import com.hmood.repository.FavoriteArtistsRepository.FavoriteArtist;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Artistes favoris. Chaque artiste est rattaché à sa fiche Deezer (photo, titres phares, mix du DJ) :
 * directement quand le titre vient de Deezer, sinon par une recherche exacte de son nom.
 */
public final class FavoriteArtistsService {

    private final FavoriteArtistsRepository repo;
    private final Function<String, List<DeezerProvider.ArtistRef>> search;

    public FavoriteArtistsService(FavoriteArtistsRepository repo, Function<String, List<DeezerProvider.ArtistRef>> search) {
        this.repo = repo;
        this.search = search;
    }

    public List<FavoriteArtist> list() {
        return repo.list();
    }

    /** Vrai si l'artiste (par identifiant Deezer, sinon par nom) est déjà en favori. Non bloquant. */
    public boolean isFavorite(String deezerArtistId, String name) {
        return repo.containsId(deezerArtistId) || repo.containsName(primary(name));
    }

    public boolean isFavorite(CatalogEntry e) {
        if (e == null) return false;
        String id = DeezerProvider.ID.equals(e.provider()) ? e.artistId() : null;
        String name = e.kind() == CatalogEntry.Kind.ARTIST ? e.title() : e.artist();
        return isFavorite(id, name);
    }

    /**
     * Ajoute ou retire l'artiste ; renvoie l'artiste et son nouvel état. Bloquant (recherche Deezer si besoin).
     *
     * @param deezerArtistId identifiant Deezer s'il est connu, sinon {@code null}
     */
    public Toggle toggle(String deezerArtistId, String name, String picture) throws CatalogException {
        String clean = primary(name);
        if (isFavorite(deezerArtistId, clean)) {
            if (deezerArtistId != null) repo.remove(deezerArtistId);
            repo.removeByName(clean);
            return new Toggle(new FavoriteArtist(deezerArtistId, clean, picture), false);
        }
        FavoriteArtist artist = resolve(deezerArtistId, clean, picture);
        repo.add(artist);
        return new Toggle(artist, true);
    }

    public Toggle toggle(CatalogEntry e) throws CatalogException {
        boolean artist = e.kind() == CatalogEntry.Kind.ARTIST;
        String id = DeezerProvider.ID.equals(e.provider()) ? e.artistId() : null;
        return toggle(id, artist ? e.title() : e.artist(), artist ? e.coverUrl() : null);
    }

    public void remove(FavoriteArtist a) {
        repo.remove(a.id());
    }

    /** Résultat d'un ajout / retrait. */
    public record Toggle(FavoriteArtist artist, boolean favorite) {
    }

    private FavoriteArtist resolve(String id, String name, String picture) throws CatalogException {
        List<DeezerProvider.ArtistRef> found;
        try {
            found = search.apply(name);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof CatalogException ce) throw ce;
            throw e;
        }
        Optional<DeezerProvider.ArtistRef> match = found.stream()
                .filter(a -> id != null ? a.id().equals(id) : a.name().equalsIgnoreCase(name)).findFirst();
        if (match.isEmpty() && id == null) {
            throw new CatalogException("« " + name + " » est introuvable sur Deezer : impossible de l'ajouter à tes artistes.");
        }
        return match.map(a -> new FavoriteArtist(a.id(), a.name(), a.picture()))
                .orElse(new FavoriteArtist(id, name, picture));
    }

    /** « Tayc, Dadju » / « Tayc feat. Dadju » → « Tayc ». */
    static String primary(String name) {
        if (name == null) return "";
        String n = name.split("(?i)\\s*(,| feat\\.? | ft\\.? | & | x )\\s*")[0].trim();
        return n.isEmpty() ? name.trim() : n;
    }
}
