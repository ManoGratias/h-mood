package com.hmood.projet4.modele;

import java.io.Serial;
import java.util.Set;

/** Administrateur : il gère le catalogue (ajout et suppression de morceaux). */
public class Admin extends Utilisateur {

    @Serial
    private static final long serialVersionUID = 1L;

    public Admin(String identifiant, String sel, String empreinteMotDePasse) {
        super(identifiant, sel, empreinteMotDePasse);
    }

    @Override
    public Set<Espace> espaces() {
        return Set.of(Espace.ADMINISTRATION, Espace.ECOUTE, Espace.PARAMETRES);
    }

    @Override
    public String role() {
        return "Admin";
    }
}
