package com.hmood.projet4.ia;

import com.hmood.projet4.catalogue.Catalogue;
import com.hmood.projet4.modele.Morceau;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Recommandeur sans réseau : associe des mots de l'humeur à des genres, puis pioche dans ces genres du catalogue.
 * Toujours disponible (pas de clé, pas d'internet) et prévisible, donc pratique pour les tests.
 */
public class MockRecommandeur implements Recommandeur {

    /** Mot-clé (sans accent, en minuscules) → genres qui vont avec. */
    private static final Map<String, List<String>> GENRES_PAR_MOT = new LinkedHashMap<>();

    static {
        GENRES_PAR_MOT.put("triste", List.of("ambient", "classical", "soul"));
        GENRES_PAR_MOT.put("melancol", List.of("ambient", "classical", "soul"));
        GENRES_PAR_MOT.put("calme", List.of("ambient", "jazz", "classical"));
        GENRES_PAR_MOT.put("detendu", List.of("jazz", "ambient", "reggae"));
        GENRES_PAR_MOT.put("relax", List.of("jazz", "ambient", "reggae"));
        GENRES_PAR_MOT.put("dormir", List.of("ambient", "classical"));
        GENRES_PAR_MOT.put("concentr", List.of("classical", "ambient", "electronic"));
        GENRES_PAR_MOT.put("travail", List.of("classical", "ambient", "electronic"));
        GENRES_PAR_MOT.put("joyeux", List.of("pop", "reggae", "soul"));
        GENRES_PAR_MOT.put("heureux", List.of("pop", "reggae", "soul"));
        GENRES_PAR_MOT.put("content", List.of("pop", "reggae", "soul"));
        GENRES_PAR_MOT.put("amour", List.of("soul", "pop", "jazz"));
        GENRES_PAR_MOT.put("romanti", List.of("soul", "jazz", "pop"));
        GENRES_PAR_MOT.put("energ", List.of("electronic", "rock", "hiphop"));
        GENRES_PAR_MOT.put("motiv", List.of("hiphop", "rock", "electronic"));
        GENRES_PAR_MOT.put("sport", List.of("electronic", "hiphop", "rock"));
        GENRES_PAR_MOT.put("courir", List.of("electronic", "hiphop", "rock"));
        GENRES_PAR_MOT.put("fete", List.of("electronic", "pop", "hiphop"));
        GENRES_PAR_MOT.put("colere", List.of("metal", "rock"));
        GENRES_PAR_MOT.put("enerve", List.of("metal", "rock"));
    }

    @Override
    public Playlist recommander(String humeur, Catalogue catalogue, int taille) {
        String texte = sansAccent(humeur == null ? "" : humeur);
        Set<String> genres = new LinkedHashSet<>();
        GENRES_PAR_MOT.forEach((mot, liste) -> {
            if (texte.contains(mot)) genres.addAll(liste);
        });
        if (genres.isEmpty()) {
            genres.addAll(catalogue.genres()); // humeur non reconnue : un peu de tout
        }
        // on alterne les genres (1 de chaque, puis 2e de chaque…) pour une playlist variée
        List<Morceau> playlist = new ArrayList<>();
        for (int rang = 0; playlist.size() < taille; rang++) {
            boolean ajout = false;
            for (String genre : genres) {
                List<Morceau> morceaux = catalogue.morceauxDuGenre(genre);
                if (rang < morceaux.size() && playlist.size() < taille) {
                    playlist.add(morceaux.get(rang));
                    ajout = true;
                }
            }
            if (!ajout) break; // plus rien à prendre
        }
        return new Playlist(playlist, List.of(), nom());
    }

    @Override
    public String nom() {
        return "Sans réseau";
    }

    static String sansAccent(String s) {
        return Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
