package com.hmood.projet4.ia;

/**
 * Erreur lors d'un appel à l'IA, avec un message lisible par l'utilisateur.
 * Elle hérite de RuntimeException (exception "non vérifiée") pour que la méthode de l'interface garde
 * exactement la signature demandée : String ask(String question).
 */
public class AiException extends RuntimeException {

    public AiException(String message) {
        super(message);
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }
}
