package com.hmood.projet4.ia;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Vrai client : appelle l'API Groq (format compatible OpenAI) avec java.net.http.HttpClient.
 * L'historique est renvoyé à chaque requête pour que l'IA se souvienne du contexte.
 */
public class HttpAiClient implements AiClient {

    private static final String URL = "https://api.groq.com/openai/v1/chat/completions";
    /**
     * Modèle par défaut. L'énoncé indiquait llama-3.1-8b-instant, mais Groq l'a retiré (erreur 404) :
     * on peut en choisir un autre sans toucher au code avec la variable d'environnement AI_MODEL.
     */
    static final String MODELE_PAR_DEFAUT = "openai/gpt-oss-20b";
    private static final int ESSAIS_MAX = 3;

    private final String cleApi;
    private final String messageSysteme;
    private final String modele;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final JsonArray historique = new JsonArray();

    /** La clé est passée en paramètre : elle vient de la variable d'environnement, jamais du code. */
    public HttpAiClient(String cleApi, String messageSysteme) {
        this.cleApi = cleApi;
        this.messageSysteme = messageSysteme;
        this.modele = modeleChoisi();
        reset();
    }

    /** AI_MODEL si elle est définie, sinon le modèle par défaut. */
    static String modeleChoisi() {
        String m = System.getenv("AI_MODEL");
        return m == null || m.isBlank() ? MODELE_PAR_DEFAUT : m.trim();
    }

    @Override
    public void reset() {
        while (historique.size() > 0) {
            historique.remove(0);
        }
        historique.add(message("system", messageSysteme));
    }

    @Override
    public String ask(String question) {
        historique.add(message("user", question));
        try {
            String reponse = envoyer();
            historique.add(message("assistant", reponse));
            return reponse;
        } catch (AiException e) {
            // La question n'a pas eu de réponse : on la retire pour garder un historique cohérent.
            historique.remove(historique.size() - 1);
            throw e;
        }
    }

    private String envoyer() throws AiException {
        // Corps construit avec Gson : les guillemets ou retours à la ligne de la question sont échappés correctement.
        JsonObject corps = new JsonObject();
        corps.addProperty("model", modele);
        corps.add("messages", historique);

        HttpRequest requete = HttpRequest.newBuilder(URI.create(URL))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + cleApi)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corps.toString(), StandardCharsets.UTF_8))
                .build();

        for (int essai = 1; ; essai++) {
            HttpResponse<String> reponse;
            try {
                reponse = http.send(requete, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new AiException("Impossible de joindre l'API (réseau coupé ou proxy ?) : " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AiException("Requête interrompue.", e);
            }

            int statut = reponse.statusCode();
            if (statut == 200) {
                return extraireTexte(reponse.body());
            }
            if (statut == 429 && essai < ESSAIS_MAX) {
                long attente = delaiAvantNouvelEssai(reponse, essai);
                System.out.println("(trop de requêtes, nouvel essai dans " + attente + " s...)");
                pause(attente);
                continue;
            }
            throw new AiException(messagePourStatut(statut));
        }
    }

    /** Le texte de l'IA se trouve dans choices[0].message.content. */
    static String extraireTexte(String json) throws AiException {
        try {
            JsonObject racine = JsonParser.parseString(json).getAsJsonObject();
            return racine.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString();
        } catch (JsonParseException | IllegalStateException | NullPointerException | IndexOutOfBoundsException e) {
            throw new AiException("Réponse de l'API dans un format inattendu.", e);
        }
    }

    private static String messagePourStatut(int statut) {
        return switch (statut) {
            case 400 -> "Requête refusée (400) : vérifie le modèle et le format JSON.";
            case 401 -> "Clé API invalide ou expirée (401) : vérifie la variable AI_API_KEY.";
            case 403 -> "Accès refusé (403) : cette clé n'a pas le droit d'utiliser ce modèle.";
            case 404 -> "Adresse ou modèle introuvable (404).";
            case 429 -> "Trop de requêtes (429) : attends un peu avant de réessayer.";
            default -> statut >= 500
                    ? "Le serveur de l'IA a un problème (" + statut + "), réessaie plus tard."
                    : "Erreur HTTP " + statut + ".";
        };
    }

    /** Utilise l'en-tête Retry-After s'il existe, sinon 2 s, 4 s... */
    private static long delaiAvantNouvelEssai(HttpResponse<String> reponse, int essai) {
        try {
            String retryAfter = reponse.headers().firstValue("retry-after").orElse("");
            long secondes = (long) Math.ceil(Double.parseDouble(retryAfter));
            return Math.min(Math.max(secondes, 1), 30);
        } catch (NumberFormatException e) {
            return 2L * essai;
        }
    }

    private static void pause(long secondes) throws AiException {
        try {
            Thread.sleep(secondes * 1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiException("Attente interrompue.", e);
        }
    }

    private static JsonObject message(String role, String contenu) {
        JsonObject m = new JsonObject();
        m.addProperty("role", role);
        m.addProperty("content", contenu);
        return m;
    }
}
