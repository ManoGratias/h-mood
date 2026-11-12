package com.hmood.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmood.model.Mood;
import com.hmood.model.Track;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue Audius (https://audius.org) via son API publique, sans clé.
 * Les artistes y publient eux-mêmes leurs morceaux et autorisent leur diffusion par des applications tierces.
 * Seuls les termes de recherche, le genre et l'humeur sont envoyés ; aucune donnée personnelle.
 */
public final class AudiusCatalog implements MusicCatalog {

    public static final String PREFIX = "audius:";
    private static final String DISCOVERY = "https://api.audius.co";
    private static final String APP_NAME = "HMOOD";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private volatile String host;

    @Override
    public String name() {
        return "Audius";
    }

    @Override
    public List<Track> trending(String genre) throws CatalogException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("time", "week");
        if (genre != null && !genre.isBlank()) params.put("genre", genre);
        return tracks(get("/v1/tracks/trending", params));
    }

    @Override
    public List<Track> search(String query) throws CatalogException {
        return tracks(get("/v1/tracks/search", Map.of("query", query)));
    }

    @Override
    public List<Track> forMood(Mood mood) throws CatalogException {
        AudiusMoods.Query q = AudiusMoods.queryFor(mood);
        Map<String, String> byMood = new LinkedHashMap<>();
        byMood.put("query", "a");
        byMood.put("mood", q.audiusMood());
        if (q.genre() != null) byMood.put("genre", q.genre());
        Map<String, Track> merged = new LinkedHashMap<>();
        for (Track t : tracks(get("/v1/tracks/search", byMood))) merged.putIfAbsent(t.filePath(), t);
        // Complète avec les tendances de la semaine qui ont la même humeur
        for (Track t : trending(q.genre())) {
            if (t.mood() == mood) merged.putIfAbsent(t.filePath(), t);
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public String resolveStream(Track track) throws CatalogException {
        String id = track.filePath().substring(PREFIX.length());
        URI uri = URI.create(host() + "/v1/tracks/" + enc(id) + "/stream?app_name=" + APP_NAME);
        try {
            // Audius enchaîne plusieurs redirections (API → nœud → stockage) que le lecteur JavaFX
            // ne suit pas toutes : on suit la chaîne ici et on donne l'adresse finale au lecteur.
            for (int hop = 0; hop < 6; hop++) {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                        .header("Range", "bytes=0-1").GET().build();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                int code = response.statusCode();
                if (code / 100 == 3) {
                    String location = response.headers().firstValue("location")
                            .orElseThrow(() -> new CatalogException("Redirection Audius sans adresse"));
                    uri = uri.resolve(location);
                } else if (code / 100 == 2) {
                    return uri.toString();
                } else {
                    throw new CatalogException("Audius a répondu " + code);
                }
            }
            throw new CatalogException("Trop de redirections Audius");
        } catch (IOException e) {
            throw new CatalogException("Audius injoignable : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogException("Requête interrompue", e);
        }
    }

    // ------------------------------------------------------------------ HTTP / JSON

    private String host() throws CatalogException {
        if (host == null) {
            JsonNode root = fetch(URI.create(DISCOVERY));
            JsonNode hosts = root.path("data");
            host = hosts.isArray() && !hosts.isEmpty() ? hosts.get(0).asText() : DISCOVERY;
        }
        return host;
    }

    private JsonNode get(String path, Map<String, String> params) throws CatalogException {
        StringBuilder sb = new StringBuilder(host()).append(path).append("?app_name=").append(APP_NAME);
        params.forEach((k, v) -> sb.append('&').append(k).append('=').append(enc(v)));
        return fetch(URI.create(sb.toString()));
    }

    private JsonNode fetch(URI uri) throws CatalogException {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).header("Accept", "application/json").GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new CatalogException("Audius a répondu " + response.statusCode());
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new CatalogException("Audius injoignable (connexion Internet ?) : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogException("Requête interrompue", e);
        }
    }

    /** BPM déclarés par les artistes Audius (renseignés au fil des réponses de l'API). */
    private static final java.util.Map<String, Double> BPM = new java.util.concurrent.ConcurrentHashMap<>();

    public static Double bpmHint(String audiusId) {
        return BPM.get(audiusId);
    }

    static List<Track> tracks(JsonNode root) {
        List<Track> result = new ArrayList<>();
        for (JsonNode n : root.path("data")) {
            if (n.has("is_streamable") && !n.path("is_streamable").asBoolean(true)) continue;
            if (n.path("is_delete").asBoolean(false)) continue;
            String id = n.path("id").asText(null);
            if (id == null) continue;
            if (n.path("bpm").isNumber() && n.path("bpm").asDouble() > 0) BPM.put(id, n.path("bpm").asDouble());
            JsonNode art = n.path("artwork");
            String cover = text(art, "480x480");
            if (cover == null) cover = text(art, "150x150");
            Integer year = null;
            String release = text(n, "release_date");
            if (release != null && release.length() >= 4 && release.substring(0, 4).chars().allMatch(Character::isDigit)) {
                year = Integer.parseInt(release.substring(0, 4));
            }
            result.add(new Track(
                    remoteId(id),
                    PREFIX + id,
                    n.path("title").asText("Sans titre").trim(),
                    n.path("user").path("name").asText(null),
                    null,
                    text(n, "genre"),
                    year,
                    n.path("duration").asLong(0) * 1000,
                    AudiusMoods.fromAudius(text(n, "mood")),
                    cover,
                    n.path("play_count").asInt(0),
                    false));
        }
        return result;
    }

    /** Identifiant négatif stable : ne peut jamais entrer en collision avec un morceau local. */
    static long remoteId(String audiusId) {
        return -((audiusId.hashCode() & 0x7fffffffL) + 1L);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
