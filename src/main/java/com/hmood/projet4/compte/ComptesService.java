package com.hmood.projet4.compte;

import com.hmood.projet4.modele.Admin;
import com.hmood.projet4.modele.Auditeur;
import com.hmood.projet4.modele.Utilisateur;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Inscription et connexion. Ne fait aucun affichage : en cas de problème, lève une
 * IllegalArgumentException dont le message est montré tel quel par l'interface.
 * Règle : le tout premier compte créé devient l'Admin, les suivants sont des Auditeurs.
 */
public class ComptesService {

    private static final Pattern IDENTIFIANT = Pattern.compile("[A-Za-z0-9._-]{3,20}");
    public static final int LONGUEUR_MIN_MOT_DE_PASSE = 6;

    private final List<Utilisateur> utilisateurs;

    /** Travaille directement sur la liste sauvegardée (voir Donnees). */
    public ComptesService(List<Utilisateur> utilisateurs) {
        this.utilisateurs = utilisateurs;
    }

    public Utilisateur inscrire(String identifiant, String motDePasse, String confirmation) {
        String id = identifiant == null ? "" : identifiant.trim();
        if (!IDENTIFIANT.matcher(id).matches()) {
            throw new IllegalArgumentException("Identifiant : 3 à 20 caractères (lettres, chiffres, . _ -).");
        }
        if (trouver(id) != null) {
            throw new IllegalArgumentException("Cet identifiant est déjà pris.");
        }
        if (motDePasse == null || motDePasse.length() < LONGUEUR_MIN_MOT_DE_PASSE) {
            throw new IllegalArgumentException("Mot de passe : au moins " + LONGUEUR_MIN_MOT_DE_PASSE + " caractères.");
        }
        if (!motDePasse.equals(confirmation)) {
            throw new IllegalArgumentException("Les deux mots de passe ne sont pas identiques.");
        }
        String sel = MotDePasse.nouveauSel();
        String empreinte = MotDePasse.empreinte(sel, motDePasse);
        Utilisateur nouveau = utilisateurs.isEmpty()
                ? new Admin(id, sel, empreinte)
                : new Auditeur(id, sel, empreinte);
        utilisateurs.add(nouveau);
        return nouveau;
    }

    public Utilisateur connecter(String identifiant, String motDePasse) {
        Utilisateur u = trouver(identifiant == null ? "" : identifiant.trim());
        // même message dans les deux cas : on ne dit pas si c'est l'identifiant ou le mot de passe qui est faux
        if (u == null || motDePasse == null || !MotDePasse.verifier(u.getSel(), u.getEmpreinteMotDePasse(), motDePasse)) {
            throw new IllegalArgumentException("Identifiant ou mot de passe incorrect.");
        }
        return u;
    }

    public boolean aucunCompte() {
        return utilisateurs.isEmpty();
    }

    public List<Utilisateur> getUtilisateurs() {
        return Collections.unmodifiableList(utilisateurs);
    }

    private Utilisateur trouver(String identifiant) {
        for (Utilisateur u : utilisateurs) {
            if (u.getIdentifiant().equalsIgnoreCase(identifiant)) {
                return u;
            }
        }
        return null;
    }
}
