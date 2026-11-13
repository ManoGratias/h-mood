package com.hmood.projet4.catalogue;

import com.hmood.projet4.modele.Morceau;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Catalogue de morceaux rangés par genre : une {@code Map<genre, liste de morceaux>}.
 * La Map donne directement les morceaux d'un genre (utile pour l'affichage et pour la playlist par humeur),
 * sans parcourir tout le catalogue ; TreeMap garde les genres triés par ordre alphabétique.
 */
public class Catalogue implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Map<String, List<Morceau>> parGenre = new TreeMap<>();

    /** Ajoute un morceau. Refuse un identifiant déjà présent. */
    public void ajouter(Morceau morceau) {
        if (contient(morceau.getId())) {
            throw new IllegalArgumentException("Ce morceau est déjà dans le catalogue : " + morceau.getTitre());
        }
        parGenre.computeIfAbsent(morceau.getGenre(), g -> new ArrayList<>()).add(morceau);
    }

    /** Supprime le morceau d'identifiant donné. Renvoie false s'il n'existait pas. */
    public boolean supprimer(String id) {
        for (Map.Entry<String, List<Morceau>> entree : parGenre.entrySet()) {
            if (entree.getValue().removeIf(m -> m.getId().equals(id))) {
                if (entree.getValue().isEmpty()) {
                    parGenre.remove(entree.getKey()); // plus de genre vide
                }
                return true;
            }
        }
        return false;
    }

    public Optional<Morceau> trouverParId(String id) {
        if (id == null) return Optional.empty();
        String cherche = id.trim();
        return tous().stream().filter(m -> m.getId().equals(cherche)).findFirst();
    }

    public boolean contient(String id) {
        return trouverParId(id).isPresent();
    }

    public Set<String> genres() {
        return Collections.unmodifiableSet(parGenre.keySet());
    }

    public List<Morceau> morceauxDuGenre(String genre) {
        return Collections.unmodifiableList(parGenre.getOrDefault(genre == null ? "" : genre.toLowerCase(), List.of()));
    }

    public List<Morceau> tous() {
        List<Morceau> tous = new ArrayList<>();
        parGenre.values().forEach(tous::addAll);
        return tous;
    }

    /** Morceaux d'un artiste (pour les nouveautés des abonnements). */
    public List<Morceau> morceauxDe(String artiste) {
        return tous().stream().filter(m -> m.getArtiste().equalsIgnoreCase(artiste)).toList();
    }

    public int taille() {
        return parGenre.values().stream().mapToInt(List::size).sum();
    }

    public boolean estVide() {
        return parGenre.isEmpty();
    }
}
