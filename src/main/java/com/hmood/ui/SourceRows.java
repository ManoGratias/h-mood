package com.hmood.ui;

import com.hmood.app.AppContext;
import com.hmood.model.Track;
import com.hmood.music.CatalogEntry;
import com.hmood.music.service.PlaybackService;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/** Liste de morceaux toutes sources confondues, avec la nature de la lecture et le lien officiel. */
public final class SourceRows {

    private SourceRows() {
    }

    public static VBox list(AppContext ctx, List<Track> tracks, int max) {
        VBox box = new VBox(2);
        for (int i = 0; i < Math.min(max, tracks.size()); i++) {
            int index = i;
            box.getChildren().add(row(ctx, tracks.get(i), () -> ctx.player().play(tracks, index)));
        }
        if (tracks.size() > max) {
            box.getChildren().add(Ui.muted("… et " + (tracks.size() - max) + " autre(s) dans la session"));
        }
        return box;
    }

    public static HBox row(AppContext ctx, Track t, Runnable onPlay) {
        HBox row = Ui.trackRow(t, onPlay, 40);
        Optional<CatalogEntry> info = ctx.musicPlayback().info(t);
        String badgeText;
        String badgeClass;
        if (info.isPresent()) {
            badgeText = info.get().playback().label() + " · " + PlaybackService.providerName(info.get());
            badgeClass = "playback-" + info.get().playback().name().toLowerCase();
        } else if (t.isRemote()) {
            badgeText = "Morceau complet · Audius";
            badgeClass = "playback-full";
        } else {
            badgeText = "Ma musique";
            badgeClass = "playback-link_only";
        }
        Label badge = Ui.label(badgeText, "playback-badge", badgeClass);
        badge.setMinWidth(Region.USE_PREF_SIZE);
        row.getChildren().add(row.getChildren().size() - 1, badge);
        info.ifPresent(e -> {
            Hyperlink link = new Hyperlink("↗");
            link.getStyleClass().add("external-link");
            link.setOnAction(ev -> ctx.openLink(e.externalUrl()));
            row.getChildren().add(link);
        });
        return row;
    }
}
