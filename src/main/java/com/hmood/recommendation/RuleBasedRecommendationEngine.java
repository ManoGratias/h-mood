package com.hmood.recommendation;

import com.hmood.model.Mood;
import com.hmood.model.Track;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Recommandation locale par règles, sans IA :
 * <ul>
 *     <li>humeur associée par l'utilisateur au morceau : +10</li>
 *     <li>genre du fichier évoquant l'humeur : +5</li>
 *     <li>Nostalgic : morceau de plus de 15 ans : +5</li>
 *     <li>favori : +2, écoutes : +0,2 par écoute (plafonné à 10)</li>
 * </ul>
 * Un morceau qui n'obtient aucun point d'humeur n'est jamais proposé pour cette humeur.
 */
public final class RuleBasedRecommendationEngine implements RecommendationEngine {

    /** Durée estimée d'un morceau dont la durée est inconnue. */
    static final long DEFAULT_TRACK_MS = 3 * 60_000L;

    private final Supplier<List<Track>> library;
    private final Clock clock;

    public RuleBasedRecommendationEngine(Supplier<List<Track>> library, Clock clock) {
        this.library = library;
        this.clock = clock;
    }

    double score(Track t, Mood mood) {
        double s = 0;
        if (t.mood() == mood) {
            s += 10;
        }
        if (mood.matchesGenre(t.genre())) {
            s += 5;
        }
        if (mood == Mood.NOSTALGIC && t.year() != null && t.year() <= LocalDate.now(clock).getYear() - 15) {
            s += 5;
        }
        if (s == 0) {
            return 0;
        }
        if (t.favorite()) {
            s += 2;
        }
        s += Math.min(t.playCount(), 10) * 0.2;
        return s;
    }

    @Override
    public List<Track> forMood(Mood mood, int limit) {
        List<Track> candidates = new ArrayList<>(library.get());
        // Mélange stable sur la journée pour varier l'ordre des morceaux à score égal
        Collections.shuffle(candidates, new Random(LocalDate.now(clock).toEpochDay() * 31 + mood.ordinal()));
        return candidates.stream()
                .filter(t -> score(t, mood) > 0)
                .sorted(Comparator.comparingDouble((Track t) -> score(t, mood)).reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public List<Track> generate(PlaylistCriteria c) {
        List<Track> candidates = new ArrayList<>(library.get().stream()
                .filter(t -> c.genre() == null || c.genre().isBlank()
                        || (t.genre() != null && t.genre().toLowerCase(Locale.ROOT).contains(c.genre().toLowerCase(Locale.ROOT).trim())))
                .filter(t -> c.decade() == null || (t.year() != null && t.year() / 10 * 10 == c.decade()))
                .filter(t -> c.mood() == null || score(t, c.mood()) > 0)
                .toList());
        Collections.shuffle(candidates, new Random(System.nanoTime()));
        if (c.mood() != null) {
            candidates.sort(Comparator.comparingDouble((Track t) -> score(t, c.mood())).reversed());
        }
        return fitToDuration(candidates, c.targetMinutes());
    }

    @Override
    public List<Track> dailyPicks(int limit) {
        List<Track> all = new ArrayList<>(library.get());
        Collections.shuffle(all, new Random(LocalDate.now(clock).toEpochDay()));
        List<Track> favorites = all.stream().filter(Track::favorite).limit(Math.max(1, limit / 2)).toList();
        List<Track> result = new ArrayList<>(favorites);
        // Complète avec les morceaux les moins écoutés : de quoi redécouvrir sa bibliothèque
        all.stream()
                .filter(t -> !favorites.contains(t))
                .sorted(Comparator.comparingInt(Track::playCount))
                .limit(Math.max(0, limit - result.size()))
                .forEach(result::add);
        return result;
    }

    @Override
    public List<String> topArtists(List<Track> tracks, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Track t : tracks) {
            if (!Track.UNKNOWN_ARTIST.equals(t.displayArtist())) {
                counts.merge(t.displayArtist(), 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    /** Garde les premiers morceaux jusqu'à atteindre la durée visée (0 = tout garder). */
    public static List<Track> fitToDuration(List<Track> ordered, int minutes) {
        if (minutes <= 0) {
            return List.copyOf(ordered);
        }
        long target = minutes * 60_000L;
        long total = 0;
        List<Track> out = new ArrayList<>();
        for (Track t : ordered) {
            if (total >= target) {
                break;
            }
            out.add(t);
            total += t.durationMs() > 0 ? t.durationMs() : DEFAULT_TRACK_MS;
        }
        return out;
    }
}
