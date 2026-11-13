package com.hmood.projet4;

import com.hmood.projet4.catalogue.Catalogue;
import com.hmood.projet4.compte.ComptesService;
import com.hmood.projet4.compte.MotDePasse;
import com.hmood.projet4.modele.Admin;
import com.hmood.projet4.modele.Auditeur;
import com.hmood.projet4.modele.Espace;
import com.hmood.projet4.modele.Morceau;
import com.hmood.projet4.modele.Utilisateur;
import com.hmood.projet4.persistance.Donnees;
import com.hmood.projet4.persistance.Persistance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComptesEtSauvegardeTest {

    @TempDir
    Path dossier;

    @Test
    void premierCompteAdminEnsuiteAuditeurs() {
        ComptesService comptes = new ComptesService(new ArrayList<>());
        Utilisateur premier = comptes.inscrire("hermann", "secret123", "secret123");
        Utilisateur second = comptes.inscrire("dima", "motdepasse", "motdepasse");
        assertInstanceOf(Admin.class, premier);
        assertInstanceOf(Auditeur.class, second);
        // polymorphisme : même appel, menus différents
        assertTrue(premier.espaces().contains(Espace.ADMINISTRATION));
        assertFalse(second.espaces().contains(Espace.ADMINISTRATION));
        assertTrue(second.espaces().contains(Espace.CATALOGUE_IA));
    }

    @Test
    void saisiesInvalidesRefusees() {
        ComptesService comptes = new ComptesService(new ArrayList<>());
        assertThrows(IllegalArgumentException.class, () -> comptes.inscrire("ab", "secret123", "secret123"));
        assertThrows(IllegalArgumentException.class, () -> comptes.inscrire("nom avec espace", "secret123", "secret123"));
        assertThrows(IllegalArgumentException.class, () -> comptes.inscrire("hermann", "court", "court"));
        assertThrows(IllegalArgumentException.class, () -> comptes.inscrire("hermann", "secret123", "autre123"));
        comptes.inscrire("hermann", "secret123", "secret123");
        assertThrows(IllegalArgumentException.class, () -> comptes.inscrire("HERMANN", "secret123", "secret123"));
    }

    @Test
    void connexion() {
        ComptesService comptes = new ComptesService(new ArrayList<>());
        comptes.inscrire("hermann", "secret123", "secret123");
        assertEquals("hermann", comptes.connecter(" hermann ", "secret123").getIdentifiant());
        assertThrows(IllegalArgumentException.class, () -> comptes.connecter("hermann", "faux"));
        assertThrows(IllegalArgumentException.class, () -> comptes.connecter("inconnu", "secret123"));
    }

    @Test
    void motDePasseHacheAvecSel() {
        String sel1 = MotDePasse.nouveauSel();
        String sel2 = MotDePasse.nouveauSel();
        assertNotEquals(sel1, sel2);
        // même mot de passe, sels différents → empreintes différentes
        assertNotEquals(MotDePasse.empreinte(sel1, "secret123"), MotDePasse.empreinte(sel2, "secret123"));
        String empreinte = MotDePasse.empreinte(sel1, "secret123");
        assertEquals(64, empreinte.length()); // SHA-256 = 32 octets = 64 caractères hexadécimaux
        assertFalse(empreinte.contains("secret123"));
        assertTrue(MotDePasse.verifier(sel1, empreinte, "secret123"));
        assertFalse(MotDePasse.verifier(sel1, empreinte, "Secret123"));
    }

    @Test
    void catalogueParGenre() {
        Catalogue c = new Catalogue();
        c.ajouter(new Morceau("1", "A", "X", "Rock"));
        c.ajouter(new Morceau("2", "B", "Y", "rock"));
        c.ajouter(new Morceau("3", "C", "X", "jazz"));
        assertEquals(2, c.morceauxDuGenre("rock").size());
        assertEquals(2, c.morceauxDe("x").size());
        assertThrows(IllegalArgumentException.class, () -> c.ajouter(new Morceau("1", "doublon", "Z", "pop")));
        assertTrue(c.supprimer("3"));
        assertFalse(c.genres().contains("jazz")); // genre vide retiré
        assertFalse(c.supprimer("3"));
        assertThrows(IllegalArgumentException.class, () -> new Morceau("4", " ", "X", "pop"));
    }

    @Test
    void favorisEtAbonnements() {
        Auditeur a = new Auditeur("dima", "sel", "empreinte");
        Morceau m = new Morceau("1", "A", "X", "pop");
        assertTrue(a.basculerMorceauFavori(m));
        assertTrue(a.aEnFavori(m));
        assertFalse(a.basculerMorceauFavori(m)); // deuxième clic : retiré
        assertTrue(a.basculerArtisteFavori("X"));
        assertTrue(a.basculerAbonnement("X"));
        assertTrue(a.estAbonne("X"));
        assertThrows(UnsupportedOperationException.class, () -> a.getAbonnements().add("triche"));
    }

    @Test
    void sauvegardeEtRelectureParSerialisation() throws Exception {
        Persistance persistance = new Persistance(dossier.resolve("donnees.ser"));
        Donnees donnees = persistance.charger(); // fichier absent : données vides
        assertTrue(donnees.getUtilisateurs().isEmpty());

        ComptesService comptes = new ComptesService(donnees.getUtilisateurs());
        comptes.inscrire("hermann", "secret123", "secret123");
        Auditeur dima = (Auditeur) comptes.inscrire("dima", "motdepasse", "motdepasse");
        Morceau m = new Morceau("1886257", "Titre", "Artiste", "pop");
        donnees.getCatalogue().ajouter(m);
        dima.basculerMorceauFavori(m);
        persistance.sauvegarder(donnees);

        Donnees relues = persistance.charger();
        assertEquals(2, relues.getUtilisateurs().size());
        assertEquals(1, relues.getCatalogue().taille());
        Auditeur dimaRelue = (Auditeur) new ComptesService(relues.getUtilisateurs()).connecter("dima", "motdepasse");
        assertTrue(dimaRelue.aEnFavori(m));
    }
}
