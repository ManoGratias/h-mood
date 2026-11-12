package com.hmood.music.service;

import com.hmood.catalog.CatalogException;
import com.hmood.music.CatalogCache;
import com.hmood.music.CatalogEntry;
import com.hmood.music.ChartResult;
import com.hmood.music.ProviderStatus;
import com.hmood.music.provider.MusicCatalogProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/** Recherche dans les catalogues réels configurés + état honnête de chaque connecteur. */
public final class MusicCatalogService {

    private final List<MusicCatalogProvider> providers;
    private final CatalogCache cache;
    private final TrendingService trending;

    public MusicCatalogService(List<MusicCatalogProvider> providers, CatalogCache cache, TrendingService trending) {
        this.providers = providers;
        this.cache = cache;
        this.trending = trending;
    }

    /** Bloquant. Interroge chaque fournisseur ; un fournisseur en panne n'empêche pas les autres de répondre. */
    public ChartResult search(String query) throws CatalogException {
        String q = query.trim();
        StringJoiner sources = new StringJoiner(" + ");
        List<CatalogEntry> all = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        java.time.Instant oldest = java.time.Instant.now();
        boolean fromCache = true;
        for (MusicCatalogProvider p : providers) {
            if (!p.available()) continue;
            try {
                ChartResult r = trending.fetch("", p.displayName(), "search:" + p.id() + ":" + q.toLowerCase(Locale.ROOT),
                        p.id(), false, () -> p.search(q));
                all.addAll(r.entries());
                sources.add(p.displayName());
                if (r.updatedAt().isBefore(oldest)) oldest = r.updatedAt();
                fromCache &= r.fromCache();
            } catch (CatalogException e) {
                failures.add(p.displayName() + " : " + e.getMessage());
            }
        }
        if (all.isEmpty() && !failures.isEmpty()) {
            throw new CatalogException(String.join(" · ", failures));
        }
        return new ChartResult("Résultats pour « " + q + " »", sources.toString(), all, oldest, fromCache,
                failures.isEmpty() ? null : String.join(" · ", failures));
    }

    public void clearCache() {
        cache.clear();
    }

    /** État réel des connecteurs : actifs, ou à configurer (jamais présentés comme opérationnels). */
    public static List<ProviderStatus> statuses(boolean jamendoConfigured) {
        return List.of(
                new ProviderStatus("Deezer", "Classements, recherche, nouveautés, extraits 30 s", true,
                        "API publique, sans clé. Morceaux complets sur Deezer via le lien fourni."),
                new ProviderStatus("Apple Music / iTunes", "Classements USA / Royaume-Uni, extraits 30 s", true,
                        "Flux RSS officiels + iTunes Search, sans clé."),
                new ProviderStatus("Audius", "Artistes indépendants, morceaux complets", true,
                        "API publique ; diffusion autorisée par les artistes."),
                new ProviderStatus("Jamendo", "Artistes indépendants (Creative Commons), morceaux complets", jamendoConfigured,
                        jamendoConfigured ? "API officielle v3.0 avec ton client_id ; streaming autorisé par Jamendo."
                                : "Non configuré : crée un client_id gratuit sur devportal.jamendo.com puis colle-le dans Paramètres."),
                new ProviderStatus("MusicBrainz", "Métadonnées (dates de sortie, références)", true,
                        "Base ouverte, 1 requête par seconde maximum."),
                new ProviderStatus("Spotify Web API", "Catalogue et lecture", false,
                        "Non configuré : nécessite un Client ID / Secret Spotify ; lecture intégrale uniquement via le SDK officiel (compte Premium)."),
                new ProviderStatus("Apple MusicKit", "Lecture intégrale Apple Music", false,
                        "Non configuré : nécessite un compte Apple Developer et un abonnement Apple Music."),
                new ProviderStatus("SoundCloud API", "Catalogue et lecture", false,
                        "Non configuré : nécessite une clé d'application SoundCloud."));
    }
}
