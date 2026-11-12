package com.hmood.music.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hmood.catalog.CatalogException;
import com.hmood.music.ApiClient;
import com.hmood.music.CatalogEntry;
import com.hmood.music.CatalogEntry.Kind;
import com.hmood.music.PlaybackMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur Deezer (API publique https://developers.deezer.com, sans authentification pour la lecture du catalogue).
 * Lecture : extraits officiels de 30 secondes uniquement ; le morceau complet s'écoute sur Deezer (lien fourni).
 * <p>Références de classement acceptées :
 * {@code playlist:<id>}, {@code chart:<genreId>:tracks|artists|albums}, {@code artist-top:<id>},
 * {@code artist-radio:<id>}, {@code album:<id>}.
 */
public final class DeezerProvider implements MusicCatalogProvider, MusicChartProvider, MusicPlaybackProvider {

    public static final String ID = "deezer";
    private static final String BASE = "https://api.deezer.com";

    private final ApiClient api = new ApiClient("Deezer", 120);

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Deezer"; }

    @Override
    public List<CatalogEntry> search(String query) throws CatalogException {
        return tracks(api.get(BASE + "/search?limit=40&q=" + ApiClient.enc(query)).path("data"), false);
    }

    /** Artiste Deezer trouvé par recherche. */
    public record ArtistRef(String id, String name, long fans, String picture) {
    }

    public List<ArtistRef> searchArtists(String query) throws CatalogException {
        List<ArtistRef> out = new ArrayList<>();
        for (JsonNode n : api.get(BASE + "/search/artist?limit=8&q=" + ApiClient.enc(query)).path("data")) {
            out.add(new ArtistRef(n.path("id").asText(), text(n, "name"), n.path("nb_fan").asLong(), text(n, "picture_medium")));
        }
        return out;
    }

    /** Playlist réelle trouvée par recherche (éditoriale Deezer de préférence). */
    public record PlaylistRef(String id, String title, String creator, int trackCount) {
    }

    public List<PlaylistRef> searchPlaylists(String query) throws CatalogException {
        List<PlaylistRef> editorial = new ArrayList<>();
        List<PlaylistRef> others = new ArrayList<>();
        for (JsonNode n : api.get(BASE + "/search/playlist?limit=25&q=" + ApiClient.enc(query)).path("data")) {
            String creator = n.path("user").path("name").asText("");
            PlaylistRef ref = new PlaylistRef(n.path("id").asText(), text(n, "title"), creator, n.path("nb_tracks").asInt());
            if (ref.trackCount() < 10) continue;
            (creator.toLowerCase().contains("deezer") ? editorial : others).add(ref);
        }
        editorial.addAll(others);
        return editorial;
    }

    @Override
    public List<CatalogEntry> chart(String ref) throws CatalogException {
        String[] p = ref.split(":");
        return switch (p[0]) {
            case "playlist" -> tracks(api.get(BASE + "/playlist/" + p[1] + "/tracks?limit=50").path("data"), true);
            case "chart" -> switch (p[2]) {
                case "artists" -> artists(api.get(BASE + "/chart/" + p[1] + "/artists?limit=30").path("data"));
                case "albums" -> albums(api.get(BASE + "/chart/" + p[1] + "/albums?limit=30").path("data"));
                default -> tracks(api.get(BASE + "/chart/" + p[1] + "/tracks?limit=50").path("data"), true);
            };
            case "artist-top" -> tracks(api.get(BASE + "/artist/" + p[1] + "/top?limit=25").path("data"), false);
            case "artist-radio" -> tracks(api.get(BASE + "/artist/" + p[1] + "/radio?limit=25").path("data"), false);
            case "album" -> {
                JsonNode album = api.get(BASE + "/album/" + p[1]);
                String cover = text(album, "cover_big", "cover_medium");
                String title = text(album, "title");
                String genre = album.path("genres").path("data").path(0).path("name").asText(null);
                List<CatalogEntry> out = new ArrayList<>();
                for (CatalogEntry e : tracks(album.path("tracks").path("data"), false)) {
                    out.add(new CatalogEntry(e.kind(), e.provider(), e.externalId(), e.title(), e.artist(), e.artistId(),
                            title, p[1], cover, genre, text(album, "release_date"), e.durationMs(), e.rank(),
                            e.popularity(), e.previewUrl(), e.externalUrl(), e.playback(), null));
                }
                yield out;
            }
            default -> throw new CatalogException("Référence Deezer inconnue : " + ref);
        };
    }

    @Override
    public boolean supports(CatalogEntry entry) {
        return ID.equals(entry.provider());
    }

    /** Les URL d'extrait Deezer expirent : on redemande l'URL officielle au moment de la lecture. */
    @Override
    public String resolve(CatalogEntry entry) throws CatalogException {
        String preview = api.get(BASE + "/track/" + entry.externalId()).path("preview").asText("");
        if (preview.isBlank()) {
            throw new CatalogException("Deezer ne fournit pas d'extrait pour ce titre : écoute-le sur Deezer");
        }
        return preview;
    }

    // ------------------------------------------------------------------ conversion JSON → CatalogEntry

    static List<CatalogEntry> tracks(JsonNode data, boolean ranked) {
        List<CatalogEntry> out = new ArrayList<>();
        int i = 0;
        for (JsonNode n : data) {
            i++;
            if (n.has("readable") && !n.path("readable").asBoolean(true)) continue;
            String preview = text(n, "preview");
            JsonNode album = n.path("album");
            Integer position = n.has("position") ? Integer.valueOf(n.path("position").asInt()) : (ranked ? i : null);
            out.add(new CatalogEntry(Kind.TRACK, ID, n.path("id").asText(),
                    text(n, "title"), n.path("artist").path("name").asText(null), n.path("artist").path("id").asText(null),
                    text(album, "title"), album.path("id").asText(null), text(album, "cover_big", "cover_medium"),
                    null, text(n, "release_date"), n.path("duration").asLong(0) * 1000,
                    position, n.has("rank") ? n.path("rank").asLong() : null,
                    preview, text(n, "link"),
                    preview == null ? PlaybackMode.LINK_ONLY : PlaybackMode.PREVIEW, null));
        }
        return out;
    }

    static List<CatalogEntry> artists(JsonNode data) {
        List<CatalogEntry> out = new ArrayList<>();
        for (JsonNode n : data) {
            out.add(new CatalogEntry(Kind.ARTIST, ID, n.path("id").asText(), text(n, "name"), text(n, "name"),
                    n.path("id").asText(), null, null, text(n, "picture_big", "picture_medium"), null, null, 0,
                    n.has("position") ? n.path("position").asInt() : null, null, null, text(n, "link"),
                    PlaybackMode.LINK_ONLY, null));
        }
        return out;
    }

    static List<CatalogEntry> albums(JsonNode data) {
        List<CatalogEntry> out = new ArrayList<>();
        for (JsonNode n : data) {
            out.add(new CatalogEntry(Kind.ALBUM, ID, n.path("id").asText(), text(n, "title"),
                    n.path("artist").path("name").asText(null), n.path("artist").path("id").asText(null),
                    text(n, "title"), n.path("id").asText(), text(n, "cover_big", "cover_medium"), null,
                    text(n, "release_date"), 0, n.has("position") ? n.path("position").asInt() : null, null, null,
                    text(n, "link"), PlaybackMode.LINK_ONLY, null));
        }
        return out;
    }

    static String text(JsonNode n, String... fields) {
        for (String f : fields) {
            JsonNode v = n.path(f);
            if (!v.isMissingNode() && !v.isNull() && !v.asText().isBlank()) return v.asText().trim();
        }
        return null;
    }
}
