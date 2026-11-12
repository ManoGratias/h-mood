package com.hmood.ui;

import com.hmood.app.AppContext;
import com.hmood.music.CatalogEntry;

import java.util.List;

/**
 * Écoute intégrale sur Deezer : ouvre le titre sur deezer.com dans le navigateur de l'utilisateur, où il est
 * connecté à son compte (lecture complète selon son abonnement). Le navigateur intégré de JavaFX ne permet pas
 * la connexion à Deezer (API Web Crypto absente), d'où ce passage par le navigateur. H MOOD se met en pause.
 */
public final class DeezerPlayerWindow {

    private DeezerPlayerWindow() {
    }

    /** Ouvre le premier titre Deezer de la sélection sur deezer.com. */
    public static void open(AppContext ctx, String heading, List<CatalogEntry> entries) {
        List<CatalogEntry> tracks = entries.stream()
                .filter(e -> "deezer".equals(e.provider()) && e.kind() == CatalogEntry.Kind.TRACK).toList();
        if (tracks.isEmpty()) {
            ctx.notify("Aucun titre Deezer dans cette sélection.");
            return;
        }
        CatalogEntry first = currentOrFirst(ctx, tracks);
        ctx.player().pause(); // un seul lecteur à la fois
        ctx.openLink("https://www.deezer.com/fr/track/" + first.externalId());
        ctx.notify("« " + first.title() + " » s'ouvre sur Deezer dans ton navigateur : connecté à ton compte, il se lit en entier."
                + (tracks.size() > 1 ? " Deezer ne permet pas d'y envoyer toute la liste : la suite reste dans H MOOD." : ""));
    }

    /** Si le titre en cours fait partie de la sélection, c'est lui qu'on ouvre. */
    private static CatalogEntry currentOrFirst(AppContext ctx, List<CatalogEntry> tracks) {
        return ctx.musicPlayback().info(ctx.player().getCurrent())
                .filter(cur -> tracks.stream().anyMatch(t -> t.key().equals(cur.key())))
                .orElse(tracks.get(0));
    }
}
