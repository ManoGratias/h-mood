package com.hmood.ai;

/** État explicite « aucune IA externe connectée ». Ne simule jamais de réponse. */
public final class NotConfiguredAiProvider implements AiProvider {

    @Override
    public String name() {
        return "Aucun";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public AiResponse ask(String prompt) throws AiException {
        throw new AiException("Aucun fournisseur d'IA n'est configuré dans cette version.");
    }
}
