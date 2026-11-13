package com.hmood.music.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.hmood.catalog.CatalogException;
import com.hmood.database.Database;
import com.hmood.model.Track;
import com.hmood.music.ApiClient;

import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Paroles d'un morceau. Pour un fichier importé : d'abord un fichier .lrc du même nom à côté du morceau, puis
 * les paroles enregistrées dans ses tags. Sinon LRCLIB (lrclib.net) : base ouverte et gratuite de paroles,
 * synchronisées (format LRC) quand elles existent. Seuls le titre, l'artiste et la durée sont envoyés.
 * Sans artiste connu, aucune recherche en ligne : le titre seul donnerait trop souvent une autre chanson.
 * Résultats en ligne mis en cache dans SQLite.
 */
public final class LyricsService {

    /** Une ligne synchronisée. */
    public record Line(long timeMs, String text) {
    }

    /**
     * Paroles d'un morceau : {@code lines} vide si seules les paroles brutes existent. {@code matchedSong} :
     * chanson reconnue d'après le titre seul (« Artiste – Titre »), quand l'artiste du fichier est inconnu.
     */
    public record Lyrics(List<Line> lines, String plain, boolean instrumental, String source, String matchedSong,
                         boolean approximate) {
        public Lyrics(List<Line> lines, String plain, boolean instrumental, String source) {
            this(lines, plain, instrumental, source, null, false);
        }

        public Lyrics(List<Line> lines, String plain, boolean instrumental, String source, String matchedSong) {
            this(lines, plain, instrumental, source, matchedSong, false);
        }

        public boolean synced() {
            return !lines.isEmpty();
        }

        public boolean isEmpty() {
            return !instrumental && lines.isEmpty() && (plain == null || plain.isBlank());
        }
    }

    public static final String SOURCE_LRCLIB = "LRCLIB";
    public static final String SOURCE_LRC_FILE = "fichier .lrc";
    public static final String SOURCE_TAGS = "tags du fichier";
    /** Source d'un résultat vide : artiste inconnu, recherche en ligne non tentée. */
    public static final String NEEDS_ARTIST = "artiste inconnu";

    private static final String BASE = "https://lrclib.net/api";
    /** Première ligne du cache pour une synchronisation étirée (ignorée par le lecteur LRC). */
    private static final String APPROX_MARK = "[hm:approx]";
    private static final Pattern TRACK_NUMBER = Pattern.compile("^\\d{1,3}\\s*[-._)]?\\s+(?=\\S)");

    static {
        java.util.logging.Logger.getLogger("org.jaudiotagger").setLevel(java.util.logging.Level.OFF);
    }
    private static final Pattern TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

    private final Database db;
    private final ApiClient api = new ApiClient("LRCLIB", 200);

    public LyricsService(Database db) {
        this.db = db;
    }

    /** Bloquant. */
    public Lyrics find(Track t) throws CatalogException {
        Path file = localFile(t);
        if (file != null) {
            Optional<Lyrics> local = local(file);
            if (local.isPresent()) return local.get();
        }
        if (!hasArtist(t)) {
            Lyrics byTitle = byTitleOnly(searchTitle(t), Math.round(t.durationMs() / 1000.0));
            return byTitle != null ? byTitle : new Lyrics(List.of(), null, false, NEEDS_ARTIST);
        }
        String artist = t.artist().trim();
        String title = searchTitle(t);
        String key = (artist + "|" + clean(title)).toLowerCase(Locale.ROOT);
        Optional<Lyrics> cached = cached(key);
        if (cached.isPresent()) return cached.get();

        JsonNode hit = null;
        long seconds = Math.round(t.durationMs() / 1000.0);
        if (seconds > 0) {
            try {
                hit = api.get(BASE + "/get?artist_name=" + ApiClient.enc(firstArtist(artist)) + "&track_name="
                        + ApiClient.enc(clean(title)) + "&duration=" + seconds);
            } catch (CatalogException notFound) {
                hit = null; // 404 : on passe à la recherche
            }
        }
        if (hit == null || hit.isMissingNode()) {
            hit = bestMatch(api.get(BASE + "/search?track_name=" + ApiClient.enc(clean(title))
                    + "&artist_name=" + ApiClient.enc(firstArtist(artist))), seconds);
        }
        if (hit == null) {
            hit = bestMatch(api.get(BASE + "/search?q=" + ApiClient.enc(firstArtist(artist) + " " + clean(title))), seconds);
        }
        if (hit == null) {
            // aucune version de même durée : on garde les paroles mais sans synchronisation (autre version du titre)
            JsonNode loose = bestMatch(api.get(BASE + "/search?q=" + ApiClient.enc(firstArtist(artist) + " " + clean(title))), 0);
            if (loose != null && sameArtist(loose, artist)) {
                String plain = text(loose, "plainLyrics");
                if (plain == null) plain = String.join("\n", parse(loose.path("syncedLyrics").asText("")).stream().map(Line::text).toList());
                List<Line> stretched = stretched(loose, seconds);
                Lyrics l = stretched.isEmpty()
                        ? new Lyrics(List.of(), plain, loose.path("instrumental").asBoolean(false), SOURCE_LRCLIB)
                        : new Lyrics(stretched, plain, false, SOURCE_LRCLIB, null, true);
                store(key, l);
                return l;
            }
        }
        Lyrics lyrics = hit == null
                ? new Lyrics(List.of(), null, false, SOURCE_LRCLIB)
                : new Lyrics(parse(hit.path("syncedLyrics").asText("")), text(hit, "plainLyrics"),
                hit.path("instrumental").asBoolean(false), SOURCE_LRCLIB);
        store(key, lyrics);
        return lyrics;
    }

    /**
     * Artiste inconnu : les paroles d'une chanson ne dépendent pas de l'interprète, on cherche donc par le titre.
     * Plusieurs chansons peuvent porter le même titre : les résultats sont regroupés par texte des paroles et on
     * ne retient que s'il y a un net consensus (au moins 3 versions et 60 % des résultats). Sinon rien (pas de
     * devinette). Paroles non synchronisées, sauf version de même durée (même enregistrement probable).
     */
    private Lyrics byTitleOnly(String title, long seconds) throws CatalogException {
        if (title.isBlank()) return null;
        JsonNode results = api.get(BASE + "/search?track_name=" + ApiClient.enc(title));
        return pickByConsensus(results, title, seconds);
    }

    static Lyrics pickByConsensus(JsonNode results, String title, long seconds) {
        String target = norm(title);
        List<JsonNode> exact = new ArrayList<>();
        List<java.util.Set<String>> words = new ArrayList<>();
        for (JsonNode n : results) {
            String name = n.path("trackName").asText("").replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "");
            if (!norm(name).equals(target)) continue;
            String text = lyricsText(n);
            if (text.isBlank()) continue;
            exact.add(n);
            words.add(new java.util.HashSet<>(List.of(norm(text).split(" "))));
        }
        // regroupement glouton : même chanson si les paroles partagent au moins la moitié de leurs mots
        List<List<Integer>> clusters = new ArrayList<>();
        for (int i = 0; i < exact.size(); i++) {
            boolean placed = false;
            for (List<Integer> cl : clusters) {
                if (jaccard(words.get(cl.get(0)), words.get(i)) >= 0.5) {
                    cl.add(i);
                    placed = true;
                    break;
                }
            }
            if (!placed) clusters.add(new ArrayList<>(List.of(i)));
        }
        List<Integer> best = clusters.stream().max(java.util.Comparator.comparingInt(List::size)).orElse(List.of());
        if (best.size() < 3 || best.size() < 0.6 * exact.size()) return null;

        JsonNode sameRecording = null;
        java.util.Map<String, Integer> artists = new java.util.HashMap<>();
        for (int i : best) {
            JsonNode n = exact.get(i);
            artists.merge(n.path("artistName").asText(""), 1, Integer::sum);
            boolean synced = !n.path("syncedLyrics").asText("").isBlank();
            if (synced && seconds > 0 && Math.abs(n.path("duration").asDouble(0) - seconds) <= 2 && sameRecording == null) {
                sameRecording = n;
            }
        }
        String topArtist = artists.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse("");
        JsonNode ref = null;
        for (int i : best) {
            JsonNode n = exact.get(i);
            if (n.path("artistName").asText("").equals(topArtist) && (ref == null || text(ref, "plainLyrics") == null)) ref = n;
        }
        if (ref == null) ref = exact.get(best.get(0));
        String song = topArtist + " – " + ref.path("trackName").asText(title).replaceAll("\\s*[(\\[][^)\\]]*[)\\]]", "").trim();
        if (sameRecording != null) {
            return new Lyrics(parse(sameRecording.path("syncedLyrics").asText("")), text(sameRecording, "plainLyrics"),
                    false, SOURCE_LRCLIB, song);
        }
        String plain = text(ref, "plainLyrics");
        if (plain == null) plain = lyricsText(ref);
        // autre version : on reprend la version synchronisée de durée la plus proche, étirée à la durée du fichier
        JsonNode closest = null;
        for (int i : best) {
            JsonNode n = exact.get(i);
            if (n.path("syncedLyrics").asText("").isBlank()) continue;
            if (closest == null || Math.abs(n.path("duration").asDouble(0) - seconds) < Math.abs(closest.path("duration").asDouble(0) - seconds)) {
                closest = n;
            }
        }
        List<Line> stretched = closest == null ? List.of() : stretched(closest, seconds);
        if (!stretched.isEmpty()) {
            return new Lyrics(stretched, plain, false, SOURCE_LRCLIB, song, true);
        }
        return new Lyrics(List.of(), plain, false, SOURCE_LRCLIB, song);
    }

    /**
     * Horodatages d'une autre version, mis à l'échelle de la durée du fichier (synchronisation approximative).
     * Vide si la durée de référence est inconnue ou trop différente (écart de plus d'un tiers).
     */
    static List<Line> stretched(JsonNode n, long fileSeconds) {
        List<Line> lines = parse(n.path("syncedLyrics").asText(""));
        double ref = n.path("duration").asDouble(0);
        if (lines.isEmpty() || ref <= 0 || fileSeconds <= 0) return List.of();
        double ratio = fileSeconds / ref;
        if (ratio < 0.75 || ratio > 1.33) return List.of();
        return lines.stream().map(l -> new Line(Math.round(l.timeMs() * ratio), l.text())).toList();
    }

    private static String lyricsText(JsonNode n) {
        String plain = n.path("plainLyrics").asText("");
        if (!plain.isBlank()) return plain;
        return String.join("\n", parse(n.path("syncedLyrics").asText("")).stream().map(Line::text).toList());
    }

    private static double jaccard(java.util.Set<String> a, java.util.Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1;
        int inter = 0;
        for (String w : a) if (b.contains(w)) inter++;
        return (double) inter / (a.size() + b.size() - inter);
    }

    /** Minuscules sans accents ni ponctuation, espaces normalisés. */
    static String norm(String s) {
        String n = java.text.Normalizer.normalize(s.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}\\p{N}\\s]", "");
        return n.trim().replaceAll("\\s+", " ");
    }

    /** Artiste renseigné (pas « Artiste inconnu »). */
    public static boolean hasArtist(Track t) {
        return t.artist() != null && !t.artist().isBlank() && !t.artist().trim().equalsIgnoreCase(Track.UNKNOWN_ARTIST);
    }

    /**
     * Titre à chercher : sans le numéro de piste quand le titre vient du nom du fichier (« 19 Heaven » → « Heaven »).
     * Un titre lu dans les tags est gardé tel quel (« 7 Rings » reste « 7 Rings »).
     */
    public static String searchTitle(Track t) {
        String title = t.title() == null ? "" : t.title().trim();
        Path file = localFile(t);
        if (file != null && title.equalsIgnoreCase(stem(file).replace('_', ' ').trim())) {
            String stripped = TRACK_NUMBER.matcher(title).replaceFirst("").trim();
            if (!stripped.isEmpty()) return stripped;
        }
        return title;
    }

    private static Path localFile(Track t) {
        if (t.isRemote() || t.filePath() == null || t.filePath().isBlank()) return null;
        try {
            return Path.of(t.filePath());
        } catch (java.nio.file.InvalidPathException e) {
            return null;
        }
    }

    private static String stem(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Paroles présentes sur le disque : fichier .lrc du même nom, puis tags du fichier audio. */
    static Optional<Lyrics> local(Path audio) {
        Path dir = audio.getParent();
        if (dir != null) {
            for (String ext : new String[]{".lrc", ".LRC", ".txt"}) {
                Path side = dir.resolve(stem(audio) + ext);
                if (Files.isRegularFile(side)) {
                    Lyrics l = fromText(readText(side), ext.equalsIgnoreCase(".txt") ? "fichier .txt" : SOURCE_LRC_FILE);
                    if (l != null) return Optional.of(l);
                }
            }
        }
        try {
            Tag tag = AudioFileIO.read(audio.toFile()).getTag();
            if (tag != null) {
                Lyrics l = fromText(tag.getFirst(FieldKey.LYRICS), SOURCE_TAGS);
                if (l != null) return Optional.of(l);
            }
        } catch (Exception e) {
            // tags illisibles : on passe à la recherche en ligne
        }
        return Optional.empty();
    }

    private static Lyrics fromText(String text, String source) {
        if (text == null || text.isBlank()) return null;
        List<Line> lines = parse(text);
        if (!lines.isEmpty()) {
            String plain = String.join("\n", lines.stream().map(Line::text).toList());
            return new Lyrics(lines, plain, false, source);
        }
        // pas d'horodatage : paroles brutes (on retire les éventuelles balises LRC d'en-tête)
        String plain = text.replaceAll("(?m)^\\[[a-zA-Z]+:[^\\]]*\\]\\s*$", "").strip();
        return plain.isEmpty() ? null : new Lyrics(List.of(), plain, false, source);
    }

    /** Lit un texte en UTF-8, ou en Windows-1252 pour les vieux fichiers .lrc. */
    private static String readText(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            try {
                return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .decode(java.nio.ByteBuffer.wrap(bytes)).toString().replace("\uFEFF", "");
            } catch (CharacterCodingException e) {
                return new String(bytes, Charset.forName("windows-1252"));
            }
        } catch (java.io.IOException e) {
            return null;
        }
    }

    /** Index de la ligne à afficher pour une position donnée (−1 avant la première ligne). */
    public static int indexAt(List<Line> lines, long positionMs) {
        int lo = 0, hi = lines.size() - 1, found = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (lines.get(mid).timeMs() <= positionMs) {
                found = mid;
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return found;
    }

    /** Format LRC : « [mm:ss.xx] texte » (plusieurs horodatages possibles par ligne). */
    static List<Line> parse(String lrc) {
        List<Line> out = new ArrayList<>();
        if (lrc == null) return out;
        for (String raw : lrc.split("\\R")) {
            Matcher m = TIME.matcher(raw);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                String frac = m.group(3) == null ? "0" : m.group(3);
                long ms = Long.parseLong(frac) * (frac.length() == 1 ? 100 : frac.length() == 2 ? 10 : 1);
                times.add(Long.parseLong(m.group(1)) * 60_000 + Long.parseLong(m.group(2)) * 1000 + ms);
                end = m.end();
            }
            String text = raw.substring(end).trim();
            for (long time : times) out.add(new Line(time, text));
        }
        out.sort(java.util.Comparator.comparingLong(Line::timeMs));
        return out;
    }

    /** « Titre (feat. X) [Remix] - Radio Edit » → « Titre ». */
    static String clean(String title) {
        String t = title.replaceAll("\\s*[(\\[][^)\\]]*(feat|ft\\.|with|remaster|version|edit|live|prod)[^)\\]]*[)\\]]", "");
        t = t.replaceAll("(?i)\\s+-\\s+(radio edit|remaster.*|single version|live.*)$", "");
        return t.trim().isEmpty() ? title : t.trim();
    }

    private static String firstArtist(String artist) {
        String a = artist.split("(?i)\\s*(,|&| feat\\.? | ft\\.? | x | et )\\s*")[0].trim();
        return a.isEmpty() ? artist : a;
    }

    private static boolean sameArtist(JsonNode n, String artist) {
        String a = n.path("artistName").asText("").toLowerCase(Locale.ROOT);
        String b = firstArtist(artist).toLowerCase(Locale.ROOT);
        return !b.isBlank() && (a.contains(b) || b.contains(a));
    }

    private static JsonNode bestMatch(JsonNode results, long seconds) {
        JsonNode best = null;
        double bestScore = Double.MAX_VALUE;
        for (JsonNode n : results) {
            boolean synced = !n.path("syncedLyrics").asText("").isBlank();
            boolean plain = !n.path("plainLyrics").asText("").isBlank();
            if (!synced && !plain && !n.path("instrumental").asBoolean(false)) continue;
            double diff = seconds > 0 ? Math.abs(n.path("duration").asDouble(0) - seconds) : 0;
            if (seconds > 0 && diff > 15) continue; // autre version du morceau
            double score = diff + (synced ? 0 : 20);
            if (score < bestScore) {
                bestScore = score;
                best = n;
            }
        }
        return best;
    }

    private Optional<Lyrics> cached(String key) {
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement(
                "SELECT synced, plain, instrumental FROM lyrics_cache WHERE cache_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                String synced = rs.getString(1);
                boolean approximate = synced != null && synced.startsWith(APPROX_MARK);
                return Optional.of(new Lyrics(parse(synced), rs.getString(2), rs.getInt(3) == 1, SOURCE_LRCLIB, null, approximate));
            }
        } catch (SQLException e) {
            return Optional.empty();
        }
    }

    private void store(String key, Lyrics l) {
        StringBuilder lrc = new StringBuilder(l.approximate() ? APPROX_MARK + "\n" : "");
        for (Line line : l.lines()) {
            long ms = line.timeMs();
            lrc.append(String.format("[%02d:%02d.%02d] %s%n", ms / 60_000, (ms / 1000) % 60, (ms % 1000) / 10, line.text()));
        }
        try (Connection c = db.connect(); PreparedStatement ps = c.prepareStatement("""
                INSERT INTO lyrics_cache (cache_key, synced, plain, instrumental, fetched_at) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (cache_key) DO UPDATE SET synced = excluded.synced, plain = excluded.plain,
                    instrumental = excluded.instrumental, fetched_at = excluded.fetched_at
                """)) {
            ps.setString(1, key);
            ps.setString(2, lrc.toString());
            ps.setString(3, l.plain());
            ps.setInt(4, l.instrumental() ? 1 : 0);
            ps.setString(5, Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            // cache facultatif
        }
    }

    private static String text(JsonNode n, String field) {
        String v = n.path(field).asText("");
        return v.isBlank() ? null : v;
    }
}
