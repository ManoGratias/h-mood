package com.hmood.music.service;

import com.hmood.catalog.CatalogException;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.music.CatalogEntry;
import com.hmood.music.ChartResult;
import com.hmood.music.provider.AudiusProvider;
import com.hmood.music.provider.DeezerProvider;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Recommandations en ligne combinant le profil local (favoris, écoutes, humeur choisie) et les
 * métadonnées des fournisseurs. Rien n'est stocké au-delà du cache d'affichage : les données des
 * fournisseurs ne servent ni à l'entraînement ni au profilage.
 */
public final class MusicRecommendationService {

    /** Classement Deezer par genre associé à certaines humeurs (choix éditorial explicite). */
    private static final Map<Mood, String> MOOD_TO_CHART = Map.of(
            Mood.HAPPY, "pop", Mood.ENERGETIC, "electro", Mood.ROMANTIC, "rnb",
            Mood.MOTIVATED, "hiphop", Mood.CONFIDENT, "rap-fr", Mood.NOSTALGIC, "france");

    /**
     * Playlists éditoriales Deezer du son urbain francophone / afro, prioritaires pour certaines humeurs.
     */
    static final Map<Mood, List<String>> MOOD_PLAYLISTS = Map.of(
            Mood.SAD, List.of("14794188903"),            // « goumin » (Deezer Moods) : chagrin, rap & R&B FR / afro
            Mood.LONELY, List.of("14794188903"),
            Mood.ROMANTIC, List.of("14832350703", "5416043002"), // « Afro Miel », « Midnight Love »
            Mood.HAPPY, List.of("9032932862"),            // « Soirée Afro »
            Mood.CONFIDENT, List.of("1071669561"));       // « Actu Rap »

    /** Artistes de référence choisis par H MOOD pour une humeur (identifiants Deezer vérifiés). */
    static final Map<Mood, List<String[]>> MOOD_SEEDS = Map.of(
            Mood.SAD, List.<String[]>of(new String[]{"14498725", "Meiitod"}));

    private static final Map<String, String> PLAYLIST_NAMES = Map.of(
            "14794188903", "« goumin » (Deezer Moods Editor)",
            "14832350703", "« Afro Miel » (Deezer Rap & R&B)",
            "5416043002", "« Midnight Love » (Deezer R&B Editor)",
            "9032932862", "« Soirée Afro » (Deezer Rap & R&B)",
            "1071669561", "« Actu Rap » (Deezer Rap & R&B)");

    private final Supplier<List<Track>> library;
    private java.util.function.Function<Mood, List<String[]>> userArtists = m -> List.of();
    private final DeezerProvider deezer;
    private final AudiusProvider audius;
    private final TrendingService trending;

    public MusicRecommendationService(Supplier<List<Track>> library, DeezerProvider deezer, AudiusProvider audius,
                                      TrendingService trending) {
        this.library = library;
        this.deezer = deezer;
        this.audius = audius;
        this.trending = trending;
    }

    /** Artistes que l'utilisateur a lui-même associés aux humeurs. */
    private com.hmood.music.provider.JamendoProvider jamendo;

    /** Jamendo (facultatif) : morceaux complets d'artistes indépendants, choisis par tags d'ambiance. */
    public void setJamendo(com.hmood.music.provider.JamendoProvider jamendo) {
        this.jamendo = jamendo;
    }

    public void setUserArtists(java.util.function.Function<Mood, List<String[]>> provider) {
        this.userArtists = provider == null ? m -> List.of() : provider;
    }

    /** Artistes associés à une humeur : ceux de l'utilisateur puis ceux de référence (sans doublon). */
    public List<String[]> artistsFor(Mood mood) {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (String[] a : userArtists.apply(mood)) out.putIfAbsent(a[0], a);
        for (String[] a : MOOD_SEEDS.getOrDefault(mood, List.of())) out.putIfAbsent(a[0], a);
        return new ArrayList<>(out.values());
    }

    /** Artiste local préféré (favoris puis écoutes), s'il y en a un. */
    public Optional<String> favoriteArtist() {
        Map<String, Integer> score = new LinkedHashMap<>();
        for (Track t : library.get()) {
            if (t.isRemote() || Track.UNKNOWN_ARTIST.equals(t.displayArtist())) continue;
            score.merge(t.displayArtist(), (t.favorite() ? 5 : 0) + t.playCount() + 1, Integer::sum);
        }
        return score.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
    }

    /** « Parce que tu écoutes X » : titres proches de ton artiste préféré (radio d'artiste Deezer). */
    public Optional<ChartResult> becauseYouListen() throws CatalogException {
        Optional<String> artist = favoriteArtist();
        if (artist.isEmpty()) return Optional.empty();
        String name = artist.get();
        List<CatalogEntry> hits = deezer.search("artist:\"" + name + "\"");
        Optional<CatalogEntry> match = hits.stream()
                .filter(e -> e.artist() != null && e.artist().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))
                .findFirst();
        if (match.isEmpty() || match.get().artistId() == null) return Optional.empty();
        String id = match.get().artistId();
        return Optional.of(trending.fetch("Parce que tu écoutes " + name, "Deezer · radio de l'artiste",
                "artist-radio:" + id, DeezerProvider.ID, false, () -> deezer.chart("artist-radio:" + id)));
    }

    /**
     * Recherches de playlists éditoriales Deezer pour chaque humeur (playlists « Moods » des éditeurs
     * Deezer : artistes du monde entier). Deux thèmes par humeur pour varier les artistes.
     */
    static final Map<Mood, List<String>> MOOD_QUERIES = Map.ofEntries(
            Map.entry(Mood.HAPPY, List.of("feel good", "happy hits")),
            Map.entry(Mood.SAD, List.of("sad songs", "chansons tristes")),
            Map.entry(Mood.ROMANTIC, List.of("love songs", "chansons d'amour")),
            Map.entry(Mood.ENERGETIC, List.of("party hits", "workout")),
            Map.entry(Mood.RELAXED, List.of("chill hits", "no stress")),
            Map.entry(Mood.ANGRY, List.of("rock metal", "rage")),
            Map.entry(Mood.MOTIVATED, List.of("motivation", "running hits")),
            Map.entry(Mood.NOSTALGIC, List.of("throwback", "nostalgia")),
            Map.entry(Mood.FOCUSED, List.of("focus", "concentration")),
            Map.entry(Mood.LONELY, List.of("melancolie", "sad songs")),
            Map.entry(Mood.CONFIDENT, List.of("confidence anthems", "boss")),
            Map.entry(Mood.DREAMY, List.of("dream pop", "dreaming")));

    /** Vrais titres d'artistes du monde entier pour une humeur (playlists éditoriales Deezer). Bloquant. */
    public ChartResult worldForMood(Mood mood) throws CatalogException {
        List<List<CatalogEntry>> lists = new ArrayList<>();
        List<String> names = new ArrayList<>();
        java.util.Set<String> usedPlaylists = new java.util.HashSet<>();
        Instant oldest = Instant.now();
        boolean fromCache = true;
        CatalogException last = null;
        // 1) Artistes de l'humeur (choisis par l'utilisateur ou de référence) : leurs titres phares + titres similaires
        for (String[] artist : artistsFor(mood)) {
            String id = artist[0];
            try {
                List<CatalogEntry> own = trending.fetch(artist[1], "Deezer", "artist-top:" + id, DeezerProvider.ID, false,
                        () -> deezer.chart("artist-top:" + id)).entries().stream().limit(5).toList();
                List<CatalogEntry> similar = trending.fetch(artist[1], "Deezer", "artist-radio:" + id, DeezerProvider.ID, false,
                        () -> deezer.chart("artist-radio:" + id)).entries().stream().limit(10).toList();
                lists.add(own);
                lists.add(similar);
                names.add(artist[1] + " et artistes similaires");
            } catch (CatalogException e) {
                last = e;
            }
        }
        // 2) Playlists éditoriales du son urbain francophone / afro pour cette humeur
        for (String pid : MOOD_PLAYLISTS.getOrDefault(mood, List.of())) {
            try {
                ChartResult playlist = trending.fetch("Playlist", "Deezer", "playlist:" + pid, DeezerProvider.ID, false,
                        () -> deezer.chart("playlist:" + pid));
                lists.add(playlist.entries());
                usedPlaylists.add(pid);
                names.add(PLAYLIST_NAMES.getOrDefault(pid, "playlist Deezer"));
            } catch (CatalogException e) {
                last = e;
            }
        }
        // 3) Playlists « Moods » internationales
        for (String query : MOOD_QUERIES.getOrDefault(mood, List.of(mood.label()))) {
            try {
                Optional<DeezerProvider.PlaylistRef> ref = deezer.searchPlaylists(query).stream()
                        .filter(r -> usedPlaylists.add(r.id())).findFirst();
                if (ref.isEmpty()) continue;
                DeezerProvider.PlaylistRef r = ref.get();
                ChartResult playlist = trending.fetch(r.title(), "Deezer", "playlist:" + r.id(), DeezerProvider.ID, false,
                        () -> deezer.chart("playlist:" + r.id()));
                lists.add(playlist.entries());
                names.add("« " + r.title() + " » (" + r.creator() + ")");
                if (playlist.updatedAt().isBefore(oldest)) oldest = playlist.updatedAt();
                fromCache &= playlist.fromCache();
            } catch (CatalogException e) {
                last = e;
            }
        }
        if (lists.isEmpty()) {
            if (last != null) throw last;
            return new ChartResult("Humeur « " + mood.label() + " » dans le monde", "Deezer", List.of(), Instant.now(), false, null);
        }
        // Entrelace les playlists et supprime les doublons pour varier les artistes
        List<CatalogEntry> merged = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        int max = lists.stream().mapToInt(List::size).max().orElse(0);
        for (int i = 0; i < max && merged.size() < 80; i++) {
            for (List<CatalogEntry> l : lists) {
                if (i < l.size() && seen.add(l.get(i).externalId())) merged.add(l.get(i).withRank(null));
            }
        }
        return new ChartResult("Humeur « " + mood.label() + " » dans le monde",
                "Deezer · " + String.join(" + ", names), merged, oldest, fromCache, null);
    }

    /** Artistes les plus présents dans une sélection (nom, id Deezer), du plus au moins fréquent. */
    public static List<CatalogEntry> topArtists(List<CatalogEntry> entries, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, CatalogEntry> first = new LinkedHashMap<>();
        for (CatalogEntry e : entries) {
            if (e.artist() == null) continue;
            counts.merge(e.artist(), 1, Integer::sum);
            first.putIfAbsent(e.artist(), e);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit).map(en -> first.get(en.getKey())).toList();
    }

    /** Humeur : vrais titres du monde (Deezer) d'abord, puis artistes indépendants en morceaux complets (Audius). */
    public ChartResult forMood(Mood mood) throws CatalogException {
        List<CatalogEntry> out = new ArrayList<>();
        List<String> sources = new ArrayList<>();
        CatalogException last = null;
        Instant at = Instant.now();
        try {
            ChartResult world = worldForMood(mood);
            out.addAll(world.entries());
            sources.add(world.source());
            at = world.updatedAt();
        } catch (CatalogException e) {
            last = e;
        }
        try {
            out.addAll(audius.forMood(mood).stream().limit(12).toList());
            sources.add("Audius (artistes indépendants, morceaux complets)");
        } catch (CatalogException e) {
            last = e;
        }
        if (jamendo != null && jamendo.configured()) {
            try {
                Mood m = mood;
                out.addAll(trending.fetch("Jamendo", "Jamendo", "jamendo-mood:" + mood.name(), com.hmood.music.provider.JamendoProvider.ID,
                        false, () -> jamendo.forMood(m)).entries().stream().limit(12).toList());
                sources.add("Jamendo (Creative Commons, morceaux complets)");
            } catch (CatalogException e) {
                last = e;
            }
        }
        if (out.isEmpty() && last != null) throw last;
        return new ChartResult("Humeur « " + mood.label() + " »", String.join(" + ", sources), out, at, false, null);
    }
}
