package com.hmood.projet4.modele;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * Un morceau du catalogue. L'identifiant est celui de Jamendo (ex. « 1886257 ») pour les morceaux importés,
 * ou « admin-… » pour ceux ajoutés à la main par l'Admin. Deux morceaux sont égaux s'ils ont le même identifiant.
 */
public class Morceau implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String PREFIXE_ADMIN = "admin-";

    private final String id;
    private final String titre;
    private final String artiste;
    private final String genre;
    /** Pochette (facultative, pour l'affichage). */
    private final String pochette;

    public Morceau(String id, String titre, String artiste, String genre, String pochette) {
        this.id = obligatoire(id, "identifiant");
        this.titre = obligatoire(titre, "titre");
        this.artiste = obligatoire(artiste, "artiste");
        this.genre = obligatoire(genre, "genre").toLowerCase();
        this.pochette = pochette;
    }

    public Morceau(String id, String titre, String artiste, String genre) {
        this(id, titre, artiste, genre, null);
    }

    public String getId() {
        return id;
    }

    public String getTitre() {
        return titre;
    }

    public String getArtiste() {
        return artiste;
    }

    public String getGenre() {
        return genre;
    }

    public String getPochette() {
        return pochette;
    }

    /** Morceau Jamendo (écoutable en entier) ou ajouté par l'Admin (pas de fichier audio). */
    public boolean vientDeJamendo() {
        return !id.startsWith(PREFIXE_ADMIN);
    }

    private static String obligatoire(String valeur, String champ) {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Le champ « " + champ + " » est obligatoire.");
        }
        return valeur.trim();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Morceau m && id.equals(m.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return titre + " — " + artiste + " [" + genre + "]";
    }
}
