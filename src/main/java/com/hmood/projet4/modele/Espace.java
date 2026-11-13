package com.hmood.projet4.modele;

/** Grandes parties de l'application. Chaque type d'utilisateur indique celles qu'il voit dans son menu. */
public enum Espace {
    /** Pages d'écoute : accueil, découvrir, ma musique, playlists, modes… */
    ECOUTE,
    /** Catalogue Jamendo par genre, favoris, abonnements et playlist IA (Auditeur). */
    CATALOGUE_IA,
    /** Ajout et suppression de morceaux du catalogue (Admin). */
    ADMINISTRATION,
    /** Paramètres de l'application. */
    PARAMETRES
}
