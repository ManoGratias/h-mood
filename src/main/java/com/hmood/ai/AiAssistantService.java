package com.hmood.ai;

import com.hmood.catalog.CatalogException;
import com.hmood.catalog.MusicCatalog;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.recommendation.RecommendationEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Assistant du panneau de droite. Utilise le fournisseur IA s'il est configuré,
 * sinon l'interprétation locale — et le dit explicitement dans sa réponse.
 */
public final class AiAssistantService {

    public record AssistantReply(String message, List<Track> tracks, Mood mood, boolean external) {
    }

    private final AiProvider provider;
    private final LocalIntentParser parser;
    private final RecommendationEngine engine;
    private final Supplier<List<Track>> library;
    private final MusicCatalog catalog;

    public AiAssistantService(AiProvider provider, LocalIntentParser parser, RecommendationEngine engine,
                              Supplier<List<Track>> library, MusicCatalog catalog) {
        this.provider = provider;
        this.parser = parser;
        this.engine = engine;
        this.library = library;
        this.catalog = catalog;
    }

    public boolean isExternalConfigured() {
        return provider.isConfigured();
    }

    public String statusText() {
        return provider.isConfigured()
                ? "IA connectée : " + provider.name()
                : "IA externe non configurée · mode local + " + catalog.name();
    }

    /** Bloquant (catalogue en ligne, IA externe) : appeler hors du thread JavaFX. */
    public AssistantReply ask(String text) {
        String externalNote = "";
        if (provider.isConfigured()) {
            try {
                return new AssistantReply(provider.ask(text).text(), List.of(), null, true);
            } catch (AiException e) {
                externalNote = "(IA externe indisponible : " + e.getMessage() + ") ";
            }
        }
        Optional<LocalIntentParser.Intent> intent = parser.parse(text);
        if (intent.isEmpty()) {
            return new AssistantReply(externalNote
                    + "Je n'ai pas reconnu d'ambiance dans ta demande. Essaie par exemple « j'ai besoin de motivation », "
                    + "« un son pour un jour de pluie » ou « surprends-moi ». (Interprétation locale, sans IA.)",
                    List.of(), null, false);
        }
        LocalIntentParser.Intent i = intent.get();
        if (i.surprise()) {
            List<Track> mix = new ArrayList<>(surprise(6));
            String online = "";
            try {
                List<Track> trending = new ArrayList<>(catalog.trending(null));
                Collections.shuffle(trending);
                mix.addAll(trending.stream().limit(15 - mix.size()).toList());
                online = " + des découvertes " + catalog.name();
            } catch (CatalogException e) {
                online = " (" + catalog.name() + " indisponible : " + e.getMessage() + ")";
            }
            String msg = mix.isEmpty()
                    ? "Rien à te proposer : ta bibliothèque est vide et " + catalog.name() + " ne répond pas."
                    : "Surprise ! " + mix.size() + " morceaux : ceux que tu écoutes peu" + online + ". (Interprétation locale, sans IA.)";
            return new AssistantReply(externalNote + msg, mix, null, false);
        }
        List<Track> tracks = new ArrayList<>(engine.forMood(i.mood(), 15));
        int local = tracks.size();
        String online = "";
        if (local < 8) {
            try {
                List<Track> remote = catalog.forMood(i.mood());
                tracks.addAll(remote.stream().limit(15 - local).toList());
                online = (tracks.size() - local) + " vrai(s) morceau(x) " + catalog.name();
            } catch (CatalogException e) {
                online = catalog.name() + " indisponible (" + e.getMessage() + ")";
            }
        }
        StringBuilder msg = new StringBuilder("Ambiance « " + i.mood().label() + " » : ");
        if (tracks.isEmpty()) {
            msg.append("aucun morceau trouvé. ").append(online);
        } else {
            if (local > 0) msg.append(local).append(" morceau(x) de ta bibliothèque");
            if (!online.isEmpty()) msg.append(local > 0 ? " + " : "").append(online);
            msg.append(". (Interprétation locale du mot « ").append(i.matchedKeyword()).append(" », sans IA.)");
        }
        return new AssistantReply(externalNote + msg, tracks, i.mood(), false);
    }

    private List<Track> surprise(int limit) {
        List<Track> all = new ArrayList<>(library.get());
        Collections.shuffle(all);
        all.sort(Comparator.comparingInt(Track::playCount));
        return all.stream().limit(limit).toList();
    }
}
