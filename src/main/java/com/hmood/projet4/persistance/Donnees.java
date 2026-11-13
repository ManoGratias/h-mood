package com.hmood.projet4.persistance;

import com.hmood.projet4.catalogue.Catalogue;
import com.hmood.projet4.modele.Utilisateur;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Tout ce qui est sauvegardé pour le Projet 4 : les comptes (avec leurs favoris) et le catalogue. */
public class Donnees implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<Utilisateur> utilisateurs = new ArrayList<>();
    private final Catalogue catalogue = new Catalogue();

    public List<Utilisateur> getUtilisateurs() {
        return utilisateurs;
    }

    public Catalogue getCatalogue() {
        return catalogue;
    }
}
