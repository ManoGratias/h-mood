package com.hmood.projet4.catalogue;

import com.hmood.catalog.CatalogException;
import com.hmood.music.CatalogEntry;
import com.hmood.music.provider.JamendoProvider;
import com.hmood.projet4.modele.Morceau;

import java.util.ArrayList;
import java.util.List;

/**
 * Remplit le catalogue à partir de Jamendo (bibliothèque de musiques libres existante), genre par genre,
 * en réutilisant le client Jamendo de H MOOD. La clé (client_id) est lue dans les paramètres ou dans la
 * variable d'environnement JAMENDO_CLIENT_ID : elle n'est jamais écrite dans le code.
 */
public class ChargeurJamendo {

    /** Genres demandés à Jamendo (tags Jamendo). */
    public static final List<String> GENRES = List.of(
            "pop", "rock", "electronic", "hiphop", "jazz", "classical", "ambient", "reggae", "soul", "metal");
    public static final int MORCEAUX_PAR_GENRE = 15;

    private final JamendoProvider jamendo;

    public ChargeurJamendo(JamendoProvider jamendo) {
        this.jamendo = jamendo;
    }

    public boolean disponible() {
        return jamendo.configured();
    }

    /**
     * Ajoute au catalogue les morceaux populaires de chaque genre. Les morceaux déjà présents sont ignorés.
     * Renvoie le nombre de morceaux ajoutés.
     */
    public int remplir(Catalogue catalogue) throws CatalogException {
        if (!jamendo.configured()) {
            throw new CatalogException("Jamendo n'est pas configuré : renseigne ton client_id dans Paramètres.");
        }
        int ajoutes = 0;
        List<String> erreurs = new ArrayList<>();
        for (String genre : GENRES) {
            List<CatalogEntry> pistes;
            try {
                pistes = jamendo.chart("tags:" + genre);
            } catch (CatalogException e) {
                erreurs.add(genre + " (" + e.getMessage() + ")");
                continue;
            }
            int pris = 0;
            for (CatalogEntry e : pistes) {
                if (pris >= MORCEAUX_PAR_GENRE) break;
                if (e.externalId() == null || e.title() == null || e.artist() == null) continue;
                if (catalogue.contient(e.externalId())) continue;
                catalogue.ajouter(new Morceau(e.externalId(), e.title(), e.artist(), genre, e.coverUrl()));
                ajoutes++;
                pris++;
            }
        }
        if (ajoutes == 0 && !erreurs.isEmpty()) {
            throw new CatalogException("Jamendo n'a rien renvoyé : " + String.join(", ", erreurs));
        }
        return ajoutes;
    }
}
