package com.hmood.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmood.catalog.CatalogException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Client HTTP/JSON d'un fournisseur : espacement minimal entre deux appels (respect des quotas),
 * détection des dépassements (HTTP 429 / erreurs de quota) et messages d'erreur compréhensibles.
 */
public final class ApiClient {

    private final String providerName;
    private final long minIntervalMs;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private long lastCall;
    private long blockedUntil;

    public ApiClient(String providerName, long minIntervalMs) {
        this.providerName = providerName;
        this.minIntervalMs = minIntervalMs;
    }

    public JsonNode get(String url) throws CatalogException {
        pace();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "HMood/0.1.0 (application de bureau)")
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 || response.statusCode() == 503) {
                blockedUntil = System.currentTimeMillis() + 60_000;
                throw new CatalogException("Quota " + providerName + " atteint, nouvel essai dans une minute");
            }
            if (response.statusCode() != 200) {
                throw new CatalogException(providerName + " a répondu " + response.statusCode());
            }
            JsonNode root = json.readTree(response.body());
            JsonNode error = root.path("error");
            if (error.isObject()) {
                String message = error.path("message").asText("erreur inconnue");
                if (error.path("code").asInt() == 4) { // Deezer : quota dépassé
                    blockedUntil = System.currentTimeMillis() + 10_000;
                }
                throw new CatalogException(providerName + " : " + message);
            }
            return root;
        } catch (IOException e) {
            throw new CatalogException(providerName + " injoignable (connexion Internet ?)", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatalogException("Requête interrompue", e);
        }
    }

    private synchronized void pace() throws CatalogException {
        long now = System.currentTimeMillis();
        if (now < blockedUntil) {
            throw new CatalogException("Quota " + providerName + " atteint, patiente quelques secondes");
        }
        long wait = lastCall + minIntervalMs - now;
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CatalogException("Requête interrompue", e);
            }
        }
        lastCall = System.currentTimeMillis();
    }

    public static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
