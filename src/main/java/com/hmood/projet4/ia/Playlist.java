package com.hmood.projet4.ia;

import com.hmood.projet4.modele.Morceau;

import java.util.List;

/**
 * Résultat d'une recommandation.
 *
 * @param morceaux morceaux retenus (tous présents dans le catalogue)
 * @param rejetes  propositions écartées car absentes du catalogue (inventées par l'IA), pour les montrer à l'utilisateur
 * @param source   recommandeur utilisé
 */
public record Playlist(List<Morceau> morceaux, List<String> rejetes, String source) {

    public Playlist {
        morceaux = List.copyOf(morceaux);
        rejetes = List.copyOf(rejetes);
    }
}
