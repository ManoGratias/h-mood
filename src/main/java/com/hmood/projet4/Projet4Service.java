package com.hmood.projet4;

import com.hmood.catalog.CatalogException;
import com.hmood.music.provider.JamendoProvider;
import com.hmood.projet4.catalogue.Catalogue;
import com.hmood.projet4.catalogue.ChargeurJamendo;
import com.hmood.projet4.compte.ComptesService;
import com.hmood.projet4.ia.HttpAiClient;
import com.hmood.projet4.ia.IaRecommandeur;
import com.hmood.projet4.ia.MockRecommandeur;
import com.hmood.projet4.ia.Recommandeur;
import com.hmood.projet4.modele.Morceau;
import com.hmood.projet4.modele.Utilisateur;
import com.hmood.projet4.persistance.Donnees;
import com.hmood.projet4.persistance.Persistance;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Point d'entrée de la partie « Projet 4 » pour l'interface : comptes, utilisateur connecté, catalogue,
 * recommandeurs et sauvegarde. Aucun affichage ici (séparation logique / affichage) : les erreurs remontent
 * en exceptions avec un message lisible.
 */
public class Projet4Service {

    /** Nom de la variable d'environnement qui contient la clé de l'IA (même nom qu'au Projet 3). */
    public static final String VARIABLE_CLE_IA = "AI_API_KEY";
    private static final String CONSIGNE_SYSTEME =
            "Tu es le DJ de l'application H MOOD. Tu respectes strictement le format de réponse demandé.";

    private final Persistance persistance;
    private final Donnees donnees;
    private final ComptesService comptes;
    private final ChargeurJamendo chargeur;
    private final String chargementEchoue;
    private Utilisateur connecte;

    public Projet4Service(Path fichierSauvegarde, JamendoProvider jamendo) {
        this.persistance = new Persistance(fichierSauvegarde);
        Donnees lues;
        String erreur = null;
        try {
            lues = persistance.charger();
        } catch (IOException e) {
            lues = new Donnees(); // sauvegarde illisible : on repart de zéro sans l'écraser tant que rien ne change
            erreur = e.getMessage();
        }
        this.donnees = lues;
        this.chargementEchoue = erreur;
        this.comptes = new ComptesService(donnees.getUtilisateurs());
        this.chargeur = new ChargeurJamendo(jamendo);
    }

    // ------------------------------------------------------------------ comptes

    public Utilisateur inscrire(String identifiant, String motDePasse, String confirmation) throws IOException {
        Utilisateur u = comptes.inscrire(identifiant, motDePasse, confirmation);
        sauvegarder();
        connecte = u;
        return u;
    }

    public Utilisateur connecter(String identifiant, String motDePasse) {
        connecte = comptes.connecter(identifiant, motDePasse);
        return connecte;
    }

    public void deconnecter() {
        connecte = null;
    }

    public Utilisateur utilisateur() {
        return connecte;
    }

    public boolean aucunCompte() {
        return comptes.aucunCompte();
    }

    /** Message si la sauvegarde n'a pas pu être relue au lancement (sinon null). */
    public String getChargementEchoue() {
        return chargementEchoue;
    }

    // ------------------------------------------------------------------ catalogue

    public Catalogue catalogue() {
        return donnees.getCatalogue();
    }

    public boolean jamendoDisponible() {
        return chargeur.disponible();
    }

    /** Bloquant (appels réseau) : à lancer hors du thread de l'interface. */
    public int chargerDepuisJamendo() throws CatalogException, IOException {
        int ajoutes = chargeur.remplir(donnees.getCatalogue());
        sauvegarder();
        return ajoutes;
    }

    /** Ajout manuel par l'Admin. */
    public Morceau ajouterMorceau(String titre, String artiste, String genre) throws IOException {
        Morceau m = new Morceau(Morceau.PREFIXE_ADMIN + UUID.randomUUID().toString().substring(0, 8), titre, artiste, genre);
        donnees.getCatalogue().ajouter(m);
        sauvegarder();
        return m;
    }

    public boolean supprimerMorceau(String id) throws IOException {
        boolean supprime = donnees.getCatalogue().supprimer(id);
        if (supprime) sauvegarder();
        return supprime;
    }

    // ------------------------------------------------------------------ recommandation

    public boolean iaDisponible() {
        String cle = System.getenv(VARIABLE_CLE_IA);
        return cle != null && !cle.isBlank();
    }

    /** IaRecommandeur si la clé est présente dans l'environnement, sinon erreur explicite. */
    public Recommandeur recommandeurIa() {
        if (!iaDisponible()) {
            throw new IllegalStateException("Clé de l'IA absente : définis la variable d'environnement "
                    + VARIABLE_CLE_IA + " (clé Groq), puis relance H MOOD.");
        }
        return new IaRecommandeur(new HttpAiClient(System.getenv(VARIABLE_CLE_IA), CONSIGNE_SYSTEME));
    }

    public Recommandeur recommandeurSansReseau() {
        return new MockRecommandeur();
    }

    // ------------------------------------------------------------------ sauvegarde

    public void sauvegarder() throws IOException {
        persistance.sauvegarder(donnees);
    }

    public Path fichierSauvegarde() {
        return persistance.getFichier();
    }
}
