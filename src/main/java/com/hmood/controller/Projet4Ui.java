package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Track;
import com.hmood.music.CatalogEntry;
import com.hmood.music.PlaybackMode;
import com.hmood.projet4.modele.Morceau;
import com.hmood.ui.Icons;
import com.hmood.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/** Petits éléments d'affichage communs aux pages du Projet 4 (lecture d'un morceau, ligne de liste). */
final class Projet4Ui {

    private Projet4Ui() {
    }

    /** Lance la lecture des morceaux Jamendo de la liste, à partir de {@code depart}. */
    static void lire(AppContext ctx, List<Morceau> morceaux, Morceau depart) {
        List<Track> pistes = new ArrayList<>();
        int index = 0;
        for (Morceau m : morceaux) {
            if (!m.vientDeJamendo()) continue; // ajouté à la main par l'Admin : pas de fichier audio
            if (m.equals(depart)) index = pistes.size();
            pistes.add(ctx.musicPlayback().toTrack(entree(m)));
        }
        if (pistes.isEmpty()) {
            ctx.notify("Aucun morceau écoutable : les morceaux ajoutés par l'Admin n'ont pas de fichier audio.");
            return;
        }
        ctx.player().play(pistes, index);
    }

    /** Morceau → entrée de catalogue Jamendo (lecture en entier via le lecteur de H MOOD). */
    static CatalogEntry entree(Morceau m) {
        return new CatalogEntry(CatalogEntry.Kind.TRACK, "jamendo", m.getId(), m.getTitre(), m.getArtiste(), null,
                null, null, m.getPochette(), m.getGenre(), null, 0, null, null, null, null, PlaybackMode.FULL, null);
    }

    /** Ligne « ▶  Titre / Artiste · genre  [actions…] ». */
    static HBox ligne(AppContext ctx, Morceau m, List<Morceau> liste, Node... actions) {
        Button play = new Button(null, Icons.icon(Icons.PLAY, 14));
        play.getStyleClass().add("icon-button");
        play.setDisable(!m.vientDeJamendo());
        play.setTooltip(new Tooltip(m.vientDeJamendo() ? "Écouter (morceau complet Jamendo)" : "Ajouté par l'Admin : pas de fichier audio"));
        play.setOnAction(e -> lire(ctx, liste, m));
        Label titre = Ui.label(m.getTitre(), "track-title");
        titre.setTextOverrun(OverrunStyle.ELLIPSIS);
        titre.setMinWidth(0);
        Label artiste = Ui.label(m.getArtiste() + " · " + m.getGenre(), "track-artist");
        artiste.setMinWidth(0);
        VBox textes = new VBox(1, titre, artiste);
        textes.setMinWidth(0);
        HBox.setHgrow(textes, Priority.ALWAYS);
        HBox ligne = new HBox(10, play, textes);
        ligne.getChildren().addAll(actions);
        ligne.setAlignment(Pos.CENTER_LEFT);
        ligne.getStyleClass().add("p4-row");
        return ligne;
    }

    static Button petitBouton(String texte, String infobulle, Runnable action) {
        Button b = new Button(texte);
        b.getStyleClass().add("chip-button");
        b.setMnemonicParsing(false);
        if (infobulle != null) b.setTooltip(new Tooltip(infobulle));
        b.setOnAction(e -> action.run());
        return b;
    }
}
