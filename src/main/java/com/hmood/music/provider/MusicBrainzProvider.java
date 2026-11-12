package com.hmood.music.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hmood.catalog.CatalogException;
import com.hmood.music.ApiClient;

import java.util.Optional;

/**
 * Métadonnées MusicBrainz (base ouverte, sans clé). Règle d'usage respectée : au plus 1 requête par seconde.
 */
public final class MusicBrainzProvider implements MusicMetadataProvider {

    private final ApiClient api = new ApiClient("MusicBrainz", 1100);

    @Override
    public String id() {
        return "musicbrainz";
    }

    @Override
    public Optional<Metadata> lookup(String title, String artist) throws CatalogException {
        String query = "recording:\"" + clean(title) + "\"" + (artist == null ? "" : " AND artist:\"" + clean(artist) + "\"");
        JsonNode recordings = api.get("https://musicbrainz.org/ws/2/recording?fmt=json&limit=1&query=" + ApiClient.enc(query))
                .path("recordings");
        if (!recordings.isArray() || recordings.isEmpty()) {
            return Optional.empty();
        }
        JsonNode r = recordings.get(0);
        String id = r.path("id").asText();
        return Optional.of(new Metadata("MusicBrainz", id, r.path("title").asText(null),
                r.path("artist-credit").path(0).path("name").asText(null),
                r.path("first-release-date").asText(null),
                r.path("releases").path(0).path("title").asText(null),
                "https://musicbrainz.org/recording/" + id));
    }

    private static String clean(String s) {
        return s.replace("\"", " ").replaceAll("\\s*[(\\[].*?[)\\]]", "").trim();
    }
}
