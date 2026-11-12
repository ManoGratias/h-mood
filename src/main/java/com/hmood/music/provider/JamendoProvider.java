package com.hmood.music.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hmood.catalog.CatalogException;
import com.hmood.model.Mood;
import com.hmood.music.ApiClient;
import com.hmood.music.CatalogEntry;
import com.hmood.music.CatalogEntry.Kind;
import com.hmood.music.PlaybackMode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Adaptateur Jamendo (API v3.0) : musique d'artistes indépendants sous licence Creative Commons,
 * morceaux complets dont la diffusion en streaming est autorisée par Jamendo.
 * Nécessite un identifiant d'application gratuit (client_id) créé sur devportal.jamendo.com.
 * Références de classement : {@code popular} (popularité de la semaine) ou {@code tags:<tag+tag>}.
 */
public final class JamendoProvider implements MusicCatalogProvider, MusicChartProvider, MusicPlaybackProvider {

    public static final String ID = "jamendo";
    public static final String DEV_PORTAL = "https://devportal.jamendo.com/";
    private static final String BASE = "https://api.jamendo.com/v3.0";

    /** Tags d'ambiance Jamendo (champ « vartags ») correspondant à chaque humeur. */
    static final Map<Mood, String> MOOD_TAGS = Map.ofEntries(
            Map.entry(Mood.HAPPY, "happy+joyful+cheerful"),
            Map.entry(Mood.SAD, "sad+melancholic+emotional"),
            Map.entry(Mood.ROMANTIC, "romantic+love"),
            Map.entry(Mood.ENERGETIC, "energetic+dance+powerful"),
            Map.entry(Mood.RELAXED, "relaxing+chill+calm"),
            Map.entry(Mood.ANGRY, "aggressive+angry+metal"),
            Map.entry(Mood.MOTIVATED, "motivational+inspiring+epic"),
            Map.entry(Mood.NOSTALGIC, "nostalgic+retro+vintage"),
            Map.entry(Mood.FOCUSED, "focus+ambient+instrumental"),
            Map.entry(Mood.LONELY, "lonely+melancholic+sad"),
            Map.entry(Mood.CONFIDENT, "hiphop+groovy+confident"),
            Map.entry(Mood.DREAMY, "dreamy+atmospheric+ethereal"));

    private final Supplier<String> clientId;
    private final ApiClient api = new ApiClient("Jamendo", 250);
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public JamendoProvider(Supplier<String> clientId) {
        this.clientId = clientId;
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Jamendo"; }

    public boolean configured() {
        String id = clientId.get();
        return id != null && !id.isBlank();
    }

    @Override
    public boolean available() {
        return configured();
    }

    @Override
    public List<CatalogEntry> search(String query) throws CatalogException {
        return tracks("search=" + ApiClient.enc(query) + "&order=relevance", false);
    }

    @Override
    public List<CatalogEntry> chart(String ref) throws CatalogException {
        if (ref.startsWith("tags:")) {
            return tracks("fuzzytags=" + tagParam(ref.substring(5)) + "&order=popularity_total", true);
        }
        return tracks("order=popularity_week", true);
    }

    /** Morceaux complets dont les tags d'ambiance correspondent à l'humeur. */
    public List<CatalogEntry> forMood(Mood mood) throws CatalogException {
        return chart("tags:" + MOOD_TAGS.getOrDefault(mood, "chill"));
    }

    @Override
    public boolean supports(CatalogEntry entry) {
        return ID.equals(entry.provider());
    }

    /**
     * L'adresse audio fournie par Jamendo est signée et peut expirer : on redemande le morceau,
     * puis on suit les redirections jusqu'au fichier pour le lecteur JavaFX.
     */
    @Override
    public String resolve(CatalogEntry entry) throws CatalogException {
        String audio = entry.previewUrl();
        try {
            JsonNode results = lookup(entry.externalId());
            if (results.size() > 0 && !results.get(0).path("audio").asText().isBlank()) {
                audio = results.get(0).path("audio").asText();
            }
        } catch (CatalogException e) {
            if (audio == null) throw e;
        }
        if (audio == null || audio.isBlank()) {
            throw new CatalogException("Jamendo ne fournit pas de flux pour ce morceau");
        }
        return followRedirects(URI.create(audio));
    }

    private String followRedirects(URI uri) throws CatalogException {
        try {
            for (int hop = 0; hop < 6; hop++) {
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                        .header("Range", "bytes=0-1").GET().build();
                HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
                int code = response.statusCode();
                if (code / 100 == 3) {
                    String location = response.headers().firstValue("location")
                            .orElseThrow(() -> new CatalogException("Redirection Jamendo sans adresse"));
                    uri = uri.resolve(location);
                } else if (code / 100 == 2) {
                    return uri.toString();
                } else {
                    throw new CatalogException("Jamendo a répondu " + code + " pour le flux audio");
                }
            }
            throw new CatalogException("Trop de redirections Jamendo");
        } catch (IOException e) {
            throw new CatalogException("Jamendo injoignable : " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogException("Requête interrompue", e);
        }
    }

    /** Informations officielles de téléchargement d'un morceau (autorisation de l'artiste + licence). */
    public record DownloadInfo(boolean allowed, String url, String licenseUrl, String title, String artist) {
    }

    public DownloadInfo downloadInfo(String trackId) throws CatalogException {
        JsonNode results = lookup(trackId);
        if (results.isEmpty()) throw new CatalogException("Morceau introuvable sur Jamendo (réessaie dans un instant)");
        JsonNode n = results.get(0);
        return new DownloadInfo(n.path("audiodownload_allowed").asBoolean(false), text(n, "audiodownload"),
                text(n, "license_ccurl"), text(n, "name"), text(n, "artist_name"));
    }

    // ------------------------------------------------------------------ API

    /** Morceau par identifiant ; l'API renvoie parfois une liste vide : on retente avec des variantes d'options. */
    private JsonNode lookup(String trackId) throws CatalogException {
        String id = ApiClient.enc(trackId);
        JsonNode results = null;
        for (String variant : new String[]{"&audioformat=mp32&audiodlformat=mp32", "&audiodlformat=mp32", "&audioformat=mp32", "&imagesize=200"}) {
            results = call("/tracks/?id=" + id + variant);
            if (!results.isEmpty()) return results;
        }
        return results;
    }

    private List<CatalogEntry> tracks(String params, boolean ranked) throws CatalogException {
        JsonNode results = call("/tracks/?limit=40&include=musicinfo&imagesize=300&audioformat=mp32&" + params);
        if (results.isEmpty()) {
            // L'API renvoie parfois une liste vide pour certaines combinaisons d'options : requête minimale.
            results = call("/tracks/?limit=40&include=musicinfo&" + params);
        }
        List<CatalogEntry> out = new ArrayList<>();
        int i = 0;
        for (JsonNode n : results) {
            String audio = n.path("audio").asText("");
            if (audio.isBlank()) continue; // streaming non autorisé pour ce morceau
            i++;
            JsonNode genres = n.path("musicinfo").path("tags").path("genres");
            String genre = genres.isArray() && !genres.isEmpty() ? capitalize(genres.get(0).asText()) : null;
            String image = text(n, "image") != null ? text(n, "image") : text(n, "album_image");
            out.add(new CatalogEntry(Kind.TRACK, ID, n.path("id").asText(), text(n, "name"), text(n, "artist_name"),
                    text(n, "artist_id"), text(n, "album_name"), text(n, "album_id"), image, genre,
                    text(n, "releasedate"), n.path("duration").asLong() * 1000L, ranked ? i : null, null,
                    audio, text(n, "shareurl"), PlaybackMode.FULL, null));
        }
        return out;
    }

    private JsonNode call(String path) throws CatalogException {
        if (!configured()) {
            throw new CatalogException("Jamendo non configuré : ajoute ton client_id dans Paramètres");
        }
        String sep = path.contains("?") ? "&" : "?";
        JsonNode root = api.get(BASE + path + sep + "format=json&client_id=" + ApiClient.enc(clientId.get().trim()));
        JsonNode headers = root.path("headers");
        if (!"success".equals(headers.path("status").asText())) {
            String message = headers.path("error_message").asText("");
            int code = headers.path("code").asInt();
            if (code == 5 || message.toLowerCase().contains("client")) {
                throw new CatalogException("Jamendo : client_id refusé (vérifie-le dans Paramètres)");
            }
            throw new CatalogException("Jamendo : " + (message.isBlank() ? "erreur " + code : message));
        }
        return root.path("results");
    }

    private static String tagParam(String tags) {
        return String.join("+", java.util.Arrays.stream(tags.split("\\+")).map(ApiClient::enc).toList());
    }

    private static String text(JsonNode n, String field) {
        String v = n.path(field).asText("");
        return v.isBlank() ? null : v;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
