package com.hmood.projet4.ia;

/**
 * Le "contrat" d'un client d'IA : n'importe quelle classe qui sait répondre à une question.
 * Main ne connaît que cette interface ; on peut brancher MockAiClient ou HttpAiClient sans rien changer d'autre.
 */
public interface AiClient {

    /**
     * Envoie la question et renvoie le texte de la réponse.
     * En cas de problème (réseau, clé invalide, statut HTTP différent de 200), lève une AiException.
     */
    String ask(String question);

    /** Oublie l'historique de la conversation. Par défaut : rien à oublier. */
    default void reset() {
    }
}
