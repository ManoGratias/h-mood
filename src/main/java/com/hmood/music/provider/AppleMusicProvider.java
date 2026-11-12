package com.hmood.music.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hmood.catalog.CatalogException;
import com.hmood.music.ApiClient;
import com.hmood.music.CatalogEntry;
import com.hmood.music.CatalogEntry.Kind;
import com.hmood.music.PlaybackMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adaptateur Apple : classements officiels Apple Music (flux RSS « Apple Marketing Tools ») et
 * catalogue iTunes Search (extraits officiels de 30 s). Aucune clé requise.
 * La lecture intégrale via MusicKit nécessiterait un compte Apple Developer (non configuré).
 * Référence de classement : {@code most-played:<pays>} (ex. us, gb, fr).
 */
public final class AppleMusicProvider implements MusicCatalogProvider, MusicChartProvider, MusicPlaybackProvider {

    public static final String ID = "apple";
    private final ApiClient api = new ApiClient("Apple Music", 250);

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Apple Music"; }

    @Override
    public List<CatalogEntry> search(String query) throws CatalogException {
        JsonNode root = api.get("https://itunes.apple.com/search?media=music&entity=song&limit=30&country=fr&term="
                + ApiClient.enc(query));
        List<CatalogEntry> out = new ArrayList<>();
        for (JsonNode n : root.path("results")) {
            out.add(fromItunes(n, null));
        }
        return out;
    }

    @Override
    public List<CatalogEntry> chart(String ref) throws CatalogException {
        String[] p = ref.split(":");
        if (!"most-played".equals(p[0])) throw new CatalogException("Référence Apple inconnue : " + ref);
        String country = p[1];
        JsonNode feed = api.get("https://rss.marketingtools.apple.com/api/v2/" + country + "/music/most-played/50/songs.json")
                .path("feed").path("results");
        // Les extraits et durées viennent de l'API iTunes Lookup (une seule requête pour tout le classement)
        List<String> ids = new ArrayList<>();
        feed.forEach(n -> ids.add(n.path("id").asText()));
        Map<String, JsonNode> details = new HashMap<>();
        if (!ids.isEmpty()) {
            JsonNode lookup = api.get("https://itunes.apple.com/lookup?country=" + country + "&id=" + String.join(",", ids));
            lookup.path("results").forEach(n -> details.put(n.path("trackId").asText(), n));
        }
        List<CatalogEntry> out = new ArrayList<>();
        int rank = 0;
        for (JsonNode n : feed) {
            rank++;
            JsonNode d = details.get(n.path("id").asText());
            String preview = d == null ? null : DeezerProvider.text(d, "previewUrl");
            String genre = n.path("genres").path(0).path("name").asText(null);
            out.add(new CatalogEntry(Kind.TRACK, ID, n.path("id").asText(), DeezerProvider.text(n, "name"),
                    DeezerProvider.text(n, "artistName"), n.path("artistId").asText(null),
                    d == null ? null : DeezerProvider.text(d, "collectionName"), null,
                    bigArtwork(DeezerProvider.text(n, "artworkUrl100")), genre, DeezerProvider.text(n, "releaseDate"),
                    d == null ? 0 : d.path("trackTimeMillis").asLong(0), rank, null, preview,
                    DeezerProvider.text(n, "url"), preview == null ? PlaybackMode.LINK_ONLY : PlaybackMode.PREVIEW, null));
        }
        return out;
    }

    @Override
    public boolean supports(CatalogEntry entry) {
        return ID.equals(entry.provider());
    }

    @Override
    public String resolve(CatalogEntry entry) throws CatalogException {
        if (entry.previewUrl() == null) {
            throw new CatalogException("Apple ne fournit pas d'extrait pour ce titre : écoute-le sur Apple Music");
        }
        return entry.previewUrl();
    }

    private static CatalogEntry fromItunes(JsonNode n, Integer rank) {
        String preview = DeezerProvider.text(n, "previewUrl");
        String date = DeezerProvider.text(n, "releaseDate");
        return new CatalogEntry(Kind.TRACK, ID, n.path("trackId").asText(), DeezerProvider.text(n, "trackName"),
                DeezerProvider.text(n, "artistName"), n.path("artistId").asText(null),
                DeezerProvider.text(n, "collectionName"), n.path("collectionId").asText(null),
                bigArtwork(DeezerProvider.text(n, "artworkUrl100")), DeezerProvider.text(n, "primaryGenreName"),
                date == null ? null : date.substring(0, Math.min(10, date.length())),
                n.path("trackTimeMillis").asLong(0), rank, null, preview, DeezerProvider.text(n, "trackViewUrl"),
                preview == null ? PlaybackMode.LINK_ONLY : PlaybackMode.PREVIEW, null);
    }

    private static String bigArtwork(String url) {
        return url == null ? null : url.replace("100x100", "400x400");
    }
}
