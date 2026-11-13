package com.hmood.projet4.modele;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * Un compte de l'application. La classe est abstraite : un utilisateur est forcément un {@link Auditeur}
 * ou un {@link Admin}, jamais « un utilisateur tout court ». Ce qui change d'un type à l'autre (le menu,
 * le rôle affiché) est déclaré ici en méthodes abstraites et défini dans chaque classe fille.
 */
public abstract class Utilisateur implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String identifiant;
    /** Sel aléatoire propre à ce compte et empreinte SHA-256 du mot de passe : le mot de passe lui-même n'est jamais gardé. */
    private final String sel;
    private final String empreinteMotDePasse;

    protected Utilisateur(String identifiant, String sel, String empreinteMotDePasse) {
        this.identifiant = Objects.requireNonNull(identifiant);
        this.sel = Objects.requireNonNull(sel);
        this.empreinteMotDePasse = Objects.requireNonNull(empreinteMotDePasse);
    }

    public String getIdentifiant() {
        return identifiant;
    }

    public String getSel() {
        return sel;
    }

    public String getEmpreinteMotDePasse() {
        return empreinteMotDePasse;
    }

    /** Parties de l'application visibles dans le menu de ce type d'utilisateur (polymorphisme). */
    public abstract Set<Espace> espaces();

    /** Nom du rôle affiché sous l'identifiant (« Auditeur », « Admin »). */
    public abstract String role();

    @Override
    public String toString() {
        return identifiant + " (" + role() + ")";
    }
}
