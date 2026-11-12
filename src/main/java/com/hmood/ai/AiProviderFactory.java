package com.hmood.ai;

/**
 * Choix du fournisseur IA. Dans le MVP, aucun fournisseur n'est livré : l'application
 * l'indique clairement et utilise l'interprétation locale ({@link LocalIntentParser}).
 * Phase 2 : lire le fournisseur et sa clé dans les paramètres et instancier l'implémentation.
 */
public final class AiProviderFactory {

    private AiProviderFactory() {
    }

    public static AiProvider create() {
        return new NotConfiguredAiProvider();
    }
}
