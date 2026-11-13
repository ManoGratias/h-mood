package com.hmood.projet4.modele;

import java.io.Serial;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Utilisateur qui écoute : il gère ses morceaux et artistes favoris, ses abonnements, et demande des playlists. */
public class Auditeur extends Utilisateur {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Identifiants des morceaux favoris (un Set : pas de doublon, ordre d'ajout conservé). */
    private final Set<String> morceauxFavoris = new LinkedHashSet<>();
    private final Set<String> artistesFavoris = new LinkedHashSet<>();
    private final Set<String> abonnements = new LinkedHashSet<>();

    public Auditeur(String identifiant, String sel, String empreinteMotDePasse) {
        super(identifiant, sel, empreinteMotDePasse);
    }

    @Override
    public Set<Espace> espaces() {
        return Set.of(Espace.ECOUTE, Espace.CATALOGUE_IA, Espace.PARAMETRES);
    }

    @Override
    public String role() {
        return "Auditeur";
    }

    /** Ajoute le morceau aux favoris, ou l'en retire s'il y était. Renvoie true s'il est maintenant favori. */
    public boolean basculerMorceauFavori(Morceau morceau) {
        return basculer(morceauxFavoris, morceau.getId());
    }

    public boolean basculerArtisteFavori(String artiste) {
        return basculer(artistesFavoris, artiste);
    }

    public boolean basculerAbonnement(String artiste) {
        return basculer(abonnements, artiste);
    }

    public boolean aEnFavori(Morceau morceau) {
        return morceauxFavoris.contains(morceau.getId());
    }

    public boolean aArtisteEnFavori(String artiste) {
        return artistesFavoris.contains(artiste);
    }

    public boolean estAbonne(String artiste) {
        return abonnements.contains(artiste);
    }

    /** Vues en lecture seule : on modifie les favoris uniquement par les méthodes ci-dessus. */
    public Set<String> getMorceauxFavoris() {
        return Collections.unmodifiableSet(morceauxFavoris);
    }

    public Set<String> getArtistesFavoris() {
        return Collections.unmodifiableSet(artistesFavoris);
    }

    public Set<String> getAbonnements() {
        return Collections.unmodifiableSet(abonnements);
    }

    private static boolean basculer(Set<String> ensemble, String valeur) {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Valeur vide.");
        }
        if (ensemble.remove(valeur)) {
            return false;
        }
        ensemble.add(valeur);
        return true;
    }
}
