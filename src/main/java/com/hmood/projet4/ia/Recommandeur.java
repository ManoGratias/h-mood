package com.hmood.projet4.ia;

import com.hmood.projet4.catalogue.Catalogue;

/**
 * Propose une playlist à partir d'une humeur. C'est une interface : l'écran « Catalogue IA » ne connaît que
 * ce contrat, et on peut lui donner {@link MockRecommandeur} (sans réseau) ou {@link IaRecommandeur} (vraie IA)
 * sans rien changer d'autre — comme AiClient dans le Projet 3.
 */
public interface Recommandeur {

    /**
     * @param humeur    texte libre saisi par l'utilisateur (« triste », « motivé pour courir »…)
     * @param catalogue morceaux disponibles : la playlist ne contient QUE des morceaux de ce catalogue
     * @param taille    nombre maximum de morceaux
     */
    Playlist recommander(String humeur, Catalogue catalogue, int taille);

    /** Nom affiché (« IA (Groq) », « Sans réseau »). */
    String nom();
}
