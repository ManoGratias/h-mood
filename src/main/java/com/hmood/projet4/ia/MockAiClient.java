package com.hmood.projet4.ia;

/** Faux client : répond sans clé ni réseau. Utile pour développer et tester la boucle de conversation. */
public class MockAiClient implements AiClient {

    private int compteur = 0;

    @Override
    public String ask(String question) {
        compteur++;
        return "[réponse factice n°" + compteur + "] Tu m'as demandé : \"" + question
                + "\". Définis la variable AI_API_KEY pour parler à une vraie IA.";
    }

    @Override
    public void reset() {
        compteur = 0;
    }
}
