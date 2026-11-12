package com.hmood.ai;

import com.hmood.catalog.CatalogException;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.music.CatalogEntry;
import com.hmood.music.ChartResult;
import com.hmood.music.provider.DeezerProvider;
import com.hmood.music.service.MusicRecommendationService;
import com.hmood.music.service.PlaybackService;
import com.hmood.music.service.TempoService;
import com.hmood.music.service.TrendingService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * DJ de H MOOD. Chaque univers repose sur de vraies sources (classements par pays, playlists d'actualité,
 * titres les plus écoutés d'artistes) et reste cohérent : un univers = un style. Les titres phares
 * (popularité Deezer) sont retenus, leur tempo est analysé, puis la session est enchaînée comme un DJ :
 * montée puis descente du tempo, écarts de BPM réduits, jamais deux fois le même artiste d'affilée.
 * Les commandes en langage naturel sont interprétées localement (aucune IA externe).
 */
public final class DjService {

    /**
     * Un univers musical.
     *
     * @param sources références : {@code playlist:<id>}, {@code chart:<clé>}, {@code artists:<id,id>}, {@code mood:<MOOD>}
     * @param origin  explication honnête de la provenance affichée à l'utilisateur
     */
    public record Style(String key, String label, String group, List<String> sources, String origin,
                        String colorA, String colorB) {
    }

    public record Item(Track track, String artist, Double bpm, Double energy, String bpmMethod, Long popularity) {
    }

    public record Session(String title, String origin, String intro, List<Item> items, List<CatalogEntry> deezerEntries) {
        public List<Track> tracks() {
            return items.stream().map(Item::track).toList();
        }
    }

    public static final String AFRICA = "AFRIQUE";
    public static final String RAP = "RAP";
    public static final String WORLD = "MONDE";
    public static final String MOMENTS = "MOMENTS";

    /** Artistes togolais vérifiés sur Deezer (Deezer ne publie pas de classement togolais). */
    private static final String TOGO_ARTISTS = "185131,59502732,10559445,289360,11074108,5243327,1676224,5813136";

    public static final List<Style> STYLES = List.of(
            // --- Afrique
            new Style("ci", "Top Côte d'Ivoire", AFRICA, List.of("playlist:1362497945"),
                    "Deezer Charts · « Top Ivory Coast » (les titres les plus écoutés en Côte d'Ivoire)", "#F7971E", "#2E7D32"),
            new Style("rap-ci", "Rap ivoirien", AFRICA, List.of("playlist:8744994962"),
                    "Deezer Côte d'Ivoire · « 100% RAP IVOIRE »", "#FF8A5B", "#5D1A1A"),
            new Style("abidjan", "Ambiance Abidjan", AFRICA, List.of("playlist:12331056071", "playlist:13953353541"),
                    "Deezer · « Ambiance Abidjan » + « Essentiel Coupé-décalé » (zouglou, coupé-décalé)", "#FFD36E", "#E5533C"),
            new Style("togo", "Togo", AFRICA, List.of("artists:" + TOGO_ARTISTS),
                    "Sélection d'artistes togolais par H MOOD (Toofan, Santrinos Raphaël, Fanicko, King Mensah, Mic Flammez, "
                            + "Almok, Elom 20ce, Vaudou Game) · leurs titres les plus écoutés sur Deezer — Deezer ne publie pas de classement togolais",
                    "#FFD36E", "#2E7D32"),
            new Style("nigeria", "Afrobeats", AFRICA, List.of("playlist:1440614715", "playlist:3153080842"),
                    "Deezer · « Afro Hits » + « Afrobeats Hits » (Deezer Afro Editor)", "#7FD8BE", "#1D4E89"),
            new Style("senegal", "Top Sénégal", AFRICA, List.of("playlist:1362523075"),
                    "Deezer Charts · « Top Senegal »", "#45BFEF", "#2E7D32"),
            new Style("afro-rnb", "R&B africain", AFRICA, List.of("playlist:14832350703", "playlist:11093412482"),
                    "Deezer · « Afro Miel » + « Afro Love » (R&B et love afro)", "#FF8FB1", "#B5477A"),
            new Style("soiree-afro", "Soirée Afro", AFRICA, List.of("playlist:9032932862", "playlist:1440933255"),
                    "Deezer · « Soirée Afro » + « Hits Afro France »", "#FF5F6D", "#FFC371"),
            new Style("afro-chill", "Afro Chill", AFRICA, List.of("playlist:7437750224"),
                    "Deezer · « Afro Chill »", "#7FD8BE", "#3A8D99"),
            new Style("amapiano", "Amapiano · Afrique du Sud", AFRICA, List.of("playlist:14489327943", "playlist:1362528775"),
                    "Deezer · « 2025 Amapiano » + Deezer Charts « Top South Africa »", "#8E54E9", "#F2A65A"),
            new Style("rumba", "Rumba congolaise", AFRICA, List.of("playlist:13956980501"),
                    "Deezer · « Ambiance Kitoko »", "#C9A27E", "#7A5C61"),
            // --- Rap
            new Style("rap-fr", "Rap français", RAP, List.of("playlist:1071669561", "playlist:14478423703"),
                    "Deezer · « Actu Rap » + « 2025 Rap Français »", "#3A7BD5", "#E53935"),
            new Style("rap-us", "Rap US", RAP, List.of("playlist:1677006641", "playlist:1996494362"),
                    "Deezer · « Hot Urban » + « Rap Bangers »", "#E5533C", "#302019"),
            new Style("rap-uk", "Rap UK", RAP, List.of("playlist:10361171462"),
                    "Deezer · « UK Rap »", "#5B7BB4", "#3A4556"),
            new Style("drill", "Drill FR", RAP, List.of("playlist:14073975061"),
                    "Deezer · « Drill FR »", "#3A4556", "#101F32"),
            // --- Monde
            new Style("france", "Top France", WORLD, List.of("chart:france"),
                    "Deezer · playlist officielle « Top France »", "#3A7BD5", "#E53935"),
            new Style("monde", "Top Monde", WORLD, List.of("chart:global"),
                    "Deezer · playlist officielle « Top Worldwide »", "#45BFEF", "#1D4E89"),
            new Style("zouk", "Zouk & Konpa", WORLD, List.of("playlist:10349775862", "playlist:14828102663"),
                    "Deezer · « Hits Zouk & Konpa » + « Zouk Love »", "#FF8FB1", "#F7971E"),
            new Style("reggaeton", "Reggaeton", WORLD, List.of("playlist:1273315391", "playlist:11120289724"),
                    "Deezer · « Reggaeton Hits » + « New Reggaeton »", "#FF5F6D", "#8E54E9"),
            new Style("rnb", "R&B", WORLD, List.of("chart:rnb"),
                    "Deezer · classement R&B", "#FF8FB1", "#5B2C83"),
            // --- Moments
            new Style("party", "Soirée entre amis", MOMENTS, List.of("mood:ENERGETIC"),
                    "Deezer · playlists de soirée", "#FF5F6D", "#FFC371"),
            new Style("run", "Footing", MOMENTS, List.of("mood:MOTIVATED"),
                    "Deezer · playlists motivation / running", "#F7971E", "#E5533C"),
            new Style("rain", "Jour de pluie", MOMENTS, List.of("mood:RELAXED"),
                    "Deezer · playlists chill", "#5B7BB4", "#7FD8BE"),
            new Style("dinner", "Dîner romantique", MOMENTS, List.of("mood:ROMANTIC"),
                    "Deezer · playlists love", "#FF8FB1", "#B5477A"),
            new Style("focus", "Concentration", MOMENTS, List.of("mood:FOCUSED"),
                    "Deezer · playlists concentration", "#45BFEF", "#1D4E89"));

    private static final int SESSION_SIZE = 28;
    private static final int MAX_PER_ARTIST = 3;

    private final LocalIntentParser parser;
    private final DeezerProvider deezer;
    private final TrendingService trending;
    private final MusicRecommendationService recommendations;
    private final TempoService tempo;
    private final PlaybackService playback;

    public DjService(LocalIntentParser parser, DeezerProvider deezer, TrendingService trending,
                     MusicRecommendationService recommendations, TempoService tempo, PlaybackService playback) {
        this.parser = parser;
        this.deezer = deezer;
        this.trending = trending;
        this.recommendations = recommendations;
        this.tempo = tempo;
        this.playback = playback;
    }

    public static Optional<Style> style(String key) {
        return STYLES.stream().filter(s -> s.key().equals(key)).findFirst();
    }

    /** Commande libre : style reconnu par son nom (« rap ivoirien », « togo »…) sinon humeur. Bloquant. */
    public Optional<Session> fromCommand(String text, IntConsumer progress) throws CatalogException {
        String t = LocalIntentParser.normalize(text);
        for (Style s : STYLES) {
            String label = LocalIntentParser.normalize(s.label());
            if (t.contains(label) || t.contains(s.key().replace('-', ' '))) return Optional.of(build(s, progress));
        }
        if (t.contains("ivoir") || t.contains("abidjan")) return Optional.of(build(style("ci").orElseThrow(), progress));
        if (t.contains("togo") || t.contains("lome")) return Optional.of(build(style("togo").orElseThrow(), progress));
        if (t.contains("nigeria") || t.contains("afrobeat")) return Optional.of(build(style("nigeria").orElseThrow(), progress));
        if (t.contains("americain") || t.contains("us rap")) return Optional.of(build(style("rap-us").orElseThrow(), progress));
        if (t.contains("rap")) return Optional.of(build(style("rap-fr").orElseThrow(), progress));
        Optional<LocalIntentParser.Intent> intent = parser.parse(text);
        if (intent.isEmpty()) return Optional.empty();
        Mood mood = intent.get().surprise() ? Mood.HAPPY : intent.get().mood();
        return Optional.of(build(new Style("mood", "Ambiance « " + mood.label() + " »", MOMENTS, List.of("mood:" + mood.name()),
                "Deezer · playlists « " + mood.label() + " »", mood.colorA(), mood.colorB()), progress));
    }

    /** Construit une session pour un univers. {@code progress} : nombre de titres analysés. Bloquant. */
    public Session build(Style style, IntConsumer progress) throws CatalogException {
        List<CatalogEntry> pool = new ArrayList<>();
        CatalogException last = null;
        for (String source : style.sources()) {
            try {
                pool.addAll(fetch(source));
            } catch (CatalogException e) {
                last = e;
            }
        }
        if (pool.isEmpty()) {
            if (last != null) throw last;
            throw new CatalogException("Aucun titre disponible pour « " + style.label() + " »");
        }
        List<CatalogEntry> selected = selectHits(pool);
        Map<String, TempoService.Analysis> analyses = tempo.analyzeAll(selected, progress);
        List<Item> items = new ArrayList<>();
        for (CatalogEntry e : selected) {
            TempoService.Analysis a = analyses.get(e.key());
            items.add(new Item(playback.toTrack(e), e.artist(), a == null ? null : a.bpm(), a == null ? null : a.energy(),
                    a == null ? null : a.method(), e.popularity()));
        }
        List<Item> ordered = djOrder(items);
        return new Session(style.label(), style.origin(), intro(ordered), ordered,
                selectedDeezerInOrder(ordered, selected));
    }

    private List<CatalogEntry> fetch(String source) throws CatalogException {
        String[] p = source.split(":", 2);
        return switch (p[0]) {
            case "playlist" -> trending.fetchPublic("Playlist", "Deezer", "playlist:" + p[1], DeezerProvider.ID,
                    () -> deezer.chart("playlist:" + p[1])).entries();
            case "chart" -> trending.chart(p[1], false).entries();
            case "mood" -> recommendations.worldForMood(Mood.valueOf(p[1])).entries();
            case "artists" -> {
                List<CatalogEntry> all = new ArrayList<>();
                for (String id : p[1].split(",")) {
                    try {
                        // uniquement les titres dont l'artiste principal fait partie de la sélection
                        all.addAll(trending.fetchPublic("Artiste", "Deezer", "artist-top:" + id, DeezerProvider.ID,
                                () -> deezer.chart("artist-top:" + id)).entries().stream()
                                .filter(e -> id.equals(e.artistId())).limit(8).toList());
                    } catch (CatalogException ignored) {
                        // un artiste indisponible n'empêche pas les autres
                    }
                }
                yield all;
            }
            default -> throw new CatalogException("Source inconnue : " + source);
        };
    }

    /** Titres phares : popularité décroissante, sans doublon, au plus 3 titres par artiste. */
    static List<CatalogEntry> selectHits(List<CatalogEntry> pool) {
        Map<String, CatalogEntry> unique = new LinkedHashMap<>();
        for (CatalogEntry e : pool) {
            if (e.playable() && e.kind() == CatalogEntry.Kind.TRACK) unique.putIfAbsent(e.key(), e);
        }
        List<CatalogEntry> sorted = new ArrayList<>(unique.values());
        sorted.sort(Comparator.comparingLong((CatalogEntry e) -> e.popularity() == null ? 0 : e.popularity()).reversed());
        Map<String, Integer> perArtist = new HashMap<>();
        List<CatalogEntry> out = new ArrayList<>();
        for (CatalogEntry e : sorted) {
            String artist = e.artist() == null ? "?" : e.artist();
            if (perArtist.merge(artist, 1, Integer::sum) > MAX_PER_ARTIST) continue;
            out.add(e);
            if (out.size() == SESSION_SIZE) break;
        }
        return out;
    }

    /**
     * Ordre de DJ : tempo croissant puis décroissant (chaque transition saute au plus un cran dans la
     * liste triée, donc de faibles écarts de BPM), titres sans tempo connu en fin de session,
     * puis séparation des titres d'un même artiste.
     */
    public static List<Item> djOrder(List<Item> items) {
        List<Item> known = new ArrayList<>(items.stream().filter(i -> i.bpm() != null && i.bpm() > 0).toList());
        List<Item> unknown = items.stream().filter(i -> i.bpm() == null || i.bpm() <= 0).toList();
        known.sort(Comparator.comparingDouble(Item::bpm));
        List<Item> rising = new ArrayList<>();
        List<Item> falling = new ArrayList<>();
        for (int i = 0; i < known.size(); i++) (i % 2 == 0 ? rising : falling).add(known.get(i));
        Collections.reverse(falling);
        List<Item> out = new ArrayList<>(rising);
        out.addAll(falling);
        out.addAll(unknown);
        separateArtists(out);
        return out;
    }

    static void separateArtists(List<Item> list) {
        for (int i = 1; i < list.size(); i++) {
            if (!sameArtist(list.get(i), list.get(i - 1))) continue;
            for (int j = i + 1; j < list.size(); j++) {
                boolean fitsHere = !sameArtist(list.get(j), list.get(i - 1))
                        && (i + 1 >= list.size() || !sameArtist(list.get(j), list.get(i + 1)) || j == i + 1);
                if (fitsHere) {
                    Collections.swap(list, i, j);
                    break;
                }
            }
        }
    }

    private static boolean sameArtist(Item a, Item b) {
        return a.artist() != null && a.artist().equalsIgnoreCase(b.artist());
    }

    private static List<CatalogEntry> selectedDeezerInOrder(List<Item> ordered, List<CatalogEntry> selected) {
        Map<Long, CatalogEntry> byTrackId = new HashMap<>();
        for (CatalogEntry e : selected) byTrackId.put(-((e.key().hashCode() & 0x7fffffffL) + 1L), e);
        List<CatalogEntry> out = new ArrayList<>();
        for (Item i : ordered) {
            CatalogEntry e = byTrackId.get(i.track().id());
            if (e != null && DeezerProvider.ID.equals(e.provider())) out.add(e);
        }
        return out;
    }

    private static String intro(List<Item> items) {
        List<Double> bpms = items.stream().map(Item::bpm).filter(b -> b != null && b > 0).toList();
        long artists = items.stream().map(Item::artist).distinct().count();
        StringBuilder sb = new StringBuilder();
        sb.append(items.size()).append(" titres phares de ").append(artists).append(" artistes, choisis par popularité. ");
        if (bpms.size() >= 3) {
            double min = Collections.min(bpms), max = Collections.max(bpms);
            double avgStep = 0;
            for (int i = 1; i < bpms.size(); i++) avgStep += Math.abs(bpms.get(i) - bpms.get(i - 1));
            avgStep /= (bpms.size() - 1);
            sb.append(String.format("Tempo de %.0f à %.0f BPM : je démarre posé, je monte jusqu'au pic puis je redescends ", min, max))
                    .append(String.format("(écart moyen entre deux titres : %.0f BPM). ", avgStep));
        }
        if (bpms.size() < items.size()) {
            sb.append(items.size() - bpms.size()).append(" titre(s) sans tempo mesurable placés en fin de session. ");
        }
        sb.append("Mix en fondu croisé avec calage du tempo (extraits de 30 s) — en entier sur Deezer.");
        return sb.toString();
    }
}
