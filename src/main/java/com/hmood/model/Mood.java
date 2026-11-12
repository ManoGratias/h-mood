package com.hmood.model;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Les 12 humeurs de H MOOD. Chaque humeur porte son ambiance visuelle (dégradé),
 * une durée d'écoute suggérée, un niveau d'énergie et des mots-clés de genre
 * utilisés par le moteur de recommandation local.
 */
public enum Mood {

    HAPPY("Joyeux", "Des morceaux lumineux pour prolonger la bonne humeur.", "#FFD36E", "#FF8A5B", 4, 45,
            List.of("pop", "dance", "funk", "disco", "reggae", "afrobeat", "ska", "zouk", "happy")),
    SAD("Triste", "Laisse couler : des titres doux qui accompagnent le moment.", "#5B7BB4", "#2B3A67", 1, 30,
            List.of("blues", "ballad", "sad", "singer-songwriter", "emo", "slowcore")),
    ROMANTIC("Romantique", "Soul, R&B et douceur pour les moments à deux.", "#FF8FB1", "#B5477A", 2, 40,
            List.of("r&b", "rnb", "soul", "love", "bossa", "kizomba", "zouk", "quiet storm")),
    ENERGETIC("Énergique", "Du rythme et de l'intensité pour bouger.", "#FF5F6D", "#FFC371", 5, 35,
            List.of("edm", "electro", "house", "techno", "drum", "dnb", "dubstep", "trance", "rock", "punk")),
    RELAXED("Détendu", "Respire. Des sons posés pour décompresser.", "#7FD8BE", "#3A8D99", 1, 50,
            List.of("chill", "lounge", "ambient", "acoustic", "jazz", "bossa", "downtempo", "reggae")),
    ANGRY("En colère", "Évacue la tension avec des sons puissants.", "#E53935", "#5D1A1A", 5, 25,
            List.of("metal", "hardcore", "punk", "drill", "grunge", "industrial", "rap")),
    MOTIVATED("Motivé", "Le carburant pour avancer et se dépasser.", "#F7971E", "#E5533C", 4, 40,
            List.of("hip-hop", "hip hop", "rap", "trap", "workout", "rock", "edm")),
    NOSTALGIC("Nostalgique", "Retour vers les souvenirs et les classiques.", "#C9A27E", "#7A5C61", 2, 45,
            List.of("oldies", "classic", "80s", "90s", "retro", "disco", "soul", "chanson", "variété", "variete")),
    FOCUSED("Concentré", "Instrumental, lo-fi et ambient pour rester concentré.", "#45BFEF", "#1D4E89", 2, 50,
            List.of("lo-fi", "lofi", "instrumental", "classical", "classique", "piano", "ambient", "post-rock", "soundtrack")),
    LONELY("Solitaire", "Des titres intimes pour ne pas se sentir seul.", "#7F8EA3", "#3A4556", 1, 30,
            List.of("indie", "folk", "acoustic", "blues", "slowcore", "singer-songwriter")),
    CONFIDENT("Confiant", "Tête haute : des sons qui donnent de l'assurance.", "#8E54E9", "#F2A65A", 4, 35,
            List.of("hip-hop", "hip hop", "rap", "r&b", "funk", "afro", "pop")),
    DREAMY("Rêveur", "Flotte entre rêve et réalité.", "#C3A6FF", "#83DFFF", 1, 45,
            List.of("dream pop", "shoegaze", "ambient", "synthwave", "chillwave", "new age", "trip-hop", "trip hop"));

    private final String label;
    private final String tagline;
    private final String colorA;
    private final String colorB;
    private final int energy;
    private final int suggestedMinutes;
    private final List<String> genreKeywords;

    Mood(String label, String tagline, String colorA, String colorB, int energy, int suggestedMinutes,
         List<String> genreKeywords) {
        this.label = label;
        this.tagline = tagline;
        this.colorA = colorA;
        this.colorB = colorB;
        this.energy = energy;
        this.suggestedMinutes = suggestedMinutes;
        this.genreKeywords = genreKeywords;
    }

    public String label() { return label; }
    public String tagline() { return tagline; }
    public String colorA() { return colorA; }
    public String colorB() { return colorB; }
    /** Énergie de 1 (calme) à 5 (intense). */
    public int energy() { return energy; }
    public int suggestedMinutes() { return suggestedMinutes; }
    public List<String> genreKeywords() { return genreKeywords; }

    /** Vrai si le genre (tag du fichier) évoque cette humeur. */
    public boolean matchesGenre(String genre) {
        if (genre == null || genre.isBlank()) {
            return false;
        }
        String g = genre.toLowerCase(Locale.ROOT);
        return genreKeywords.stream().anyMatch(g::contains);
    }

    public static Optional<Mood> fromName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Mood.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
