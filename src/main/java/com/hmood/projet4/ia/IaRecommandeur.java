package com.hmood.projet4.ia;

import com.hmood.projet4.catalogue.Catalogue;
import com.hmood.projet4.modele.Morceau;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recommandeur qui demande la playlist à une IA, via l'{@link AiClient} du Projet 3.
 * <p>
 * La sortie de l'IA est contrôlée : on lui envoie le catalogue avec un identifiant par morceau et on lui
 * demande de répondre uniquement par des identifiants. Chaque ligne de sa réponse est vérifiée dans le
 * catalogue ; ce qui n'y est pas (identifiant inventé, titre imaginé…) est rejeté et listé dans
 * {@link Playlist#rejetes()}. La playlist ne contient donc jamais un morceau qui n'existe pas.
 */
public class IaRecommandeur implements Recommandeur {

    /** Une ligne utile ressemble à « 1886257 » ou « 1886257 | Titre | Artiste » ou « - 1886257 ». */
    private static final Pattern LIGNE = Pattern.compile("^\\s*(?:[-*•]|\\d+[.)])?\\s*([A-Za-z0-9-]+)\\b(.*)$");

    private final AiClient client;

    public IaRecommandeur(AiClient client) {
        this.client = client;
    }

    @Override
    public Playlist recommander(String humeur, Catalogue catalogue, int taille) {
        if (catalogue.estVide()) {
            return new Playlist(List.of(), List.of(), nom());
        }
        client.reset(); // chaque playlist est une demande indépendante
        String reponse = client.ask(prompt(humeur, catalogue, taille));
        return controler(reponse, catalogue, taille);
    }

    /** Prompt envoyé à l'IA : l'humeur + le catalogue complet (un morceau par ligne, avec son identifiant). */
    static String prompt(String humeur, Catalogue catalogue, int taille) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un DJ. Compose une playlist de ").append(taille)
                .append(" morceaux maximum pour l'humeur suivante : « ").append(humeur.trim()).append(" ».\n")
                .append("Règles strictes :\n")
                .append("- Choisis UNIQUEMENT des morceaux de la liste ci-dessous, n'invente rien.\n")
                .append("- Réponds avec un identifiant par ligne (la valeur de la colonne id), sans autre texte.\n\n")
                .append("id | titre | artiste | genre\n");
        for (Morceau m : catalogue.tous()) {
            sb.append(m.getId()).append(" | ").append(m.getTitre()).append(" | ")
                    .append(m.getArtiste()).append(" | ").append(m.getGenre()).append('\n');
        }
        return sb.toString();
    }

    /** Vérifie chaque proposition de l'IA dans le catalogue : on garde les morceaux existants, on rejette le reste. */
    static Playlist controler(String reponse, Catalogue catalogue, int taille) {
        Set<Morceau> retenus = new LinkedHashSet<>(); // LinkedHashSet : pas de doublon, ordre de l'IA conservé
        List<String> rejetes = new ArrayList<>();
        for (String ligne : reponse == null ? new String[0] : reponse.split("\\R")) {
            if (ligne.isBlank()) continue;
            Matcher m = LIGNE.matcher(ligne);
            if (!m.matches()) {
                rejetes.add(ligne.trim());
                continue;
            }
            Optional<Morceau> trouve = catalogue.trouverParId(m.group(1));
            if (trouve.isPresent()) {
                if (retenus.size() < taille) retenus.add(trouve.get());
            } else if (!ligne.trim().endsWith(":")) { // « Voici ta playlist : » n'est pas une proposition
                rejetes.add(ligne.trim()); // proposition absente du catalogue : rejetée
            }
        }
        return new Playlist(new ArrayList<>(retenus), rejetes, "IA (Groq)");
    }

    @Override
    public String nom() {
        return "IA (Groq)";
    }
}
