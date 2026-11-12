package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.music.ChartResult;
import com.hmood.music.service.AmbienceService.Ambience;
import com.hmood.ui.Async;
import com.hmood.ui.Icons;
import com.hmood.ui.SourceRows;
import com.hmood.ui.Ui;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

/** Boutons d'ambiance (Audius + bibliothèque locale) partagés par Concentration et Sommeil. */
final class AmbienceLoader {

    private final AppContext ctx;
    private final VBox musicBox;
    private int request;

    AmbienceLoader(AppContext ctx, VBox musicBox) {
        this.ctx = ctx;
        this.musicBox = musicBox;
    }

    void fill(FlowPane sources, List<Ambience> ambiences, Mood localMood, String localLabel, String[] colors) {
        int i = 0;
        for (Ambience a : ambiences) {
            Button b = new Button(a.label);
            b.getStyleClass().add("ambience-tile");
            b.setStyle("-fx-background-color: linear-gradient(to bottom right, " + colors[i % colors.length] + ", "
                    + colors[(i + 1) % colors.length] + ");");
            b.setOnAction(e -> load(a));
            sources.getChildren().add(b);
            i++;
        }
        Button local = new Button(localLabel);
        local.getStyleClass().add("ambience-tile");
        local.setStyle("-fx-background-color: " + Ui.moodGradient(localMood) + ";");
        local.setOnAction(e -> showLocal(localMood, localLabel));
        sources.getChildren().add(local);
    }

    private void load(Ambience a) {
        int id = ++request;
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setMaxSize(36, 36);
        musicBox.getChildren().setAll(new HBox(10, spinner, Ui.muted("Chargement de « " + a.label + " »…")));
        Async.run(() -> ctx.ambience().load(a), (r, error) -> {
            if (id != request) return;
            if (error != null) {
                musicBox.getChildren().setAll(Ui.emptyState("Ambiance indisponible", error));
                return;
            }
            show(r);
        });
    }

    private void show(ChartResult r) {
        List<Track> tracks = ctx.musicPlayback().playable(r.entries());
        VBox card = Ui.card(r.title(), "Source : " + r.source());
        if (tracks.isEmpty()) {
            card.getChildren().add(Ui.muted("Aucun morceau trouvé."));
        } else {
            Button play = new Button("Lancer (" + tracks.size() + " morceaux)", Icons.icon(Icons.PLAY, 16));
            play.getStyleClass().add("primary-button");
            play.setOnAction(e -> ctx.player().play(tracks, 0));
            card.getChildren().addAll(play, SourceRows.list(ctx, tracks, 15));
            ctx.player().play(tracks, 0);
        }
        musicBox.getChildren().setAll(card);
        Ui.animateIn(card);
    }

    private void showLocal(Mood mood, String label) {
        List<Track> tracks = ctx.reco().forMood(mood, 60);
        VBox card = Ui.card(label, "Morceaux de ta bibliothèque associés à « " + mood.label() + " »");
        if (tracks.isEmpty()) {
            card.getChildren().add(Ui.muted("Aucun morceau de ta bibliothèque n'est associé à cette humeur (Ma musique › clic droit › Humeur)."));
        } else {
            card.getChildren().add(SourceRows.list(ctx, tracks, 15));
            ctx.player().play(tracks, 0);
        }
        musicBox.getChildren().setAll(card);
    }
}
