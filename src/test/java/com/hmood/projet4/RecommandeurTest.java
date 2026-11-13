package com.hmood.projet4;

import com.hmood.projet4.catalogue.Catalogue;
import com.hmood.projet4.ia.AiClient;
import com.hmood.projet4.ia.IaRecommandeur;
import com.hmood.projet4.ia.MockRecommandeur;
import com.hmood.projet4.ia.Playlist;
import com.hmood.projet4.ia.Recommandeur;
import com.hmood.projet4.modele.Morceau;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecommandeurTest {

    private Catalogue catalogue;

    @BeforeEach
    void creerCatalogue() {
        catalogue = new Catalogue();
        catalogue.ajouter(new Morceau("101", "Pluie d'automne", "Lena", "ambient"));
        catalogue.ajouter(new Morceau("102", "Nuit calme", "Sora", "classical"));
        catalogue.ajouter(new Morceau("201", "Sprint", "Vega", "electronic"));
        catalogue.ajouter(new Morceau("202", "Riff", "Bolt", "rock"));
        catalogue.ajouter(new Morceau("301", "Soleil", "Mia", "pop"));
    }

    /** Faux AiClient : renvoie toujours la même réponse, sans réseau. */
    private static AiClient iaQuiRepond(String reponse) {
        return question -> reponse;
    }

    @Test
    void mockNeProposeQueDesMorceauxDuCatalogue() {
        Playlist p = new MockRecommandeur().recommander("je suis triste", catalogue, 10);
        assertTrue(catalogue.tous().containsAll(p.morceaux()));
        // « triste » → ambient, classical, soul : ici les deux premiers existent
        assertEquals(List.of("101", "102"), p.morceaux().stream().map(Morceau::getId).toList());
    }

    @Test
    void mockRespecteLaTailleDemandee() {
        Playlist p = new MockRecommandeur().recommander("humeur inconnue", catalogue, 3);
        assertEquals(3, p.morceaux().size());
    }

    @Test
    void iaRejetteLesMorceauxInventes() {
        Recommandeur ia = new IaRecommandeur(iaQuiRepond("""
                Voici ta playlist :
                201
                999 | Bohemian Rhapsody | Queen
                202
                Titre imaginé par l'IA
                """));
        Playlist p = ia.recommander("motivé", catalogue, 10);
        assertEquals(List.of("201", "202"), p.morceaux().stream().map(Morceau::getId).toList());
        assertEquals(2, p.rejetes().size()); // le 999 inventé et la ligne sans identifiant
        assertTrue(p.rejetes().get(0).contains("Bohemian Rhapsody"));
    }

    @Test
    void iaSansDoublonEtLimiteeALaTaille() {
        Recommandeur ia = new IaRecommandeur(iaQuiRepond("101\n101\n- 102\n3) 301\n201"));
        Playlist p = ia.recommander("calme", catalogue, 3);
        assertEquals(List.of("101", "102", "301"), p.morceaux().stream().map(Morceau::getId).toList());
    }

    @Test
    void iaReponseVideDonnePlaylistVide() {
        Playlist p = new IaRecommandeur(iaQuiRepond("")).recommander("joyeux", catalogue, 5);
        assertTrue(p.morceaux().isEmpty());
        assertTrue(p.rejetes().isEmpty());
    }

    @Test
    void iaNestPasAppeleeSiLeCatalogueEstVide() {
        AiClient interdit = question -> {
            throw new AssertionError("l'IA ne doit pas être appelée");
        };
        Playlist p = new IaRecommandeur(interdit).recommander("joyeux", new Catalogue(), 5);
        assertTrue(p.morceaux().isEmpty());
    }

    @Test
    void lePromptContientLHumeurEtToutLeCatalogue() {
        final String[] question = new String[1];
        AiClient espion = q -> {
            question[0] = q;
            return "";
        };
        new IaRecommandeur(espion).recommander("nostalgique", catalogue, 5);
        assertTrue(question[0].contains("nostalgique"));
        for (Morceau m : catalogue.tous()) {
            assertTrue(question[0].contains(m.getId() + " | " + m.getTitre()));
        }
    }
}
