package com.hmood.ai;

/**
 * Fournisseur d'IA externe (LLM). Point d'extension : une implémentation par fournisseur
 * (OpenAI, Anthropic, Mistral, modèle local…), choisie par {@link AiProviderFactory}.
 */
public interface AiProvider {

    String name();

    /** Faux tant qu'aucune clé / URL n'est configurée. */
    boolean isConfigured();

    /** Appel bloquant : à exécuter hors du thread JavaFX. */
    AiResponse ask(String prompt) throws AiException;
}
