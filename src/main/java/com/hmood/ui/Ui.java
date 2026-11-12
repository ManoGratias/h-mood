package com.hmood.ui;

import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.util.Durations;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.util.Locale;

/** Briques d'interface communes. */
public final class Ui {

    private Ui() {
    }

    public static Label label(String text, String... styleClasses) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styleClasses);
        return l;
    }

    public static Label wrapped(String text, String... styleClasses) {
        Label l = label(text, styleClasses);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE);
        return l;
    }

    public static Region hSpacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static VBox card(String title, String subtitle) {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        if (title != null) {
            VBox header = new VBox(2, label(title, "card-title"));
            if (subtitle != null) {
                header.getChildren().add(wrapped(subtitle, "card-subtitle"));
            }
            card.getChildren().add(header);
        }
        return card;
    }

    /** Rangée de cartes de même largeur. */
    public static HBox row(Region... cards) {
        HBox row = new HBox(18);
        for (Region c : cards) {
            HBox.setHgrow(c, Priority.ALWAYS);
            c.setMinWidth(0);
            c.setPrefWidth(0);
            c.setMaxWidth(Double.MAX_VALUE);
        }
        row.getChildren().addAll(cards);
        return row;
    }

    public static Label pill(String text) {
        return label(text, "pill");
    }

    public static Label warnPill(String text) {
        return label(text, "pill", "pill-warn");
    }

    public static Label muted(String text) {
        return wrapped(text, "muted");
    }

    /** Ligne de morceau cliquable (pochette, titre, artiste, durée). */
    public static HBox trackRow(Track t, Runnable onPlay, double coverSize) {
        Label title = label(t.title(), "track-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinWidth(0);
        Label artist = label(t.displayArtist(), "track-artist");
        artist.setTextOverrun(OverrunStyle.ELLIPSIS);
        artist.setMinWidth(0);
        VBox texts = new VBox(1, title, artist);
        texts.setMinWidth(0);
        texts.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(texts, Priority.ALWAYS);
        Label duration = label(Durations.format(t.durationMs()), "track-duration");
        duration.setMinWidth(Region.USE_PREF_SIZE);
        HBox row = new HBox(10, Covers.of(t, coverSize), texts, duration);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("track-row");
        row.setOnMouseClicked(e -> onPlay.run());
        return row;
    }

    public static VBox emptyState(String title, String text) {
        VBox box = new VBox(8, label(title, "empty-title"), wrapped(text, "muted"));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state");
        return box;
    }

    public static String moodGradient(Mood m) {
        return "linear-gradient(to bottom right, " + m.colorA() + ", " + m.colorB() + ")";
    }

    public static String moodGradient(Mood m, double alpha) {
        return "linear-gradient(to bottom right, " + rgba(m.colorA(), alpha) + ", " + rgba(m.colorB(), alpha) + ")";
    }

    public static String rgba(String hex, double alpha) {
        Color c = Color.web(hex);
        return String.format(Locale.ROOT, "rgba(%d,%d,%d,%.2f)",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255), alpha);
    }

    public static String energyDots(int energy) {
        return "●".repeat(energy) + "○".repeat(Math.max(0, 5 - energy));
    }

    /** Colore la partie « remplie » d'un slider avec l'accent du thème. */
    public static void fillSlider(Slider slider) {
        Runnable update = () -> {
            Node track = slider.lookup(".track");
            if (track != null) {
                double range = slider.getMax() - slider.getMin();
                double pct = range <= 0 ? 0 : (slider.getValue() - slider.getMin()) / range * 100;
                track.setStyle(String.format(Locale.ROOT,
                        "-fx-background-color: linear-gradient(to right, -hm-accent 0%%, -hm-accent-2 %1$.2f%%, -hm-track %1$.2f%%, -hm-track 100%%);",
                        pct));
            }
        };
        slider.valueProperty().addListener((o, a, b) -> update.run());
        slider.skinProperty().addListener((o, a, b) -> Platform.runLater(update));
        Platform.runLater(update);
    }

    /** Apparition douce d'un contenu. */
    public static void animateIn(Node node) {
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(240), node);
        fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(240), node);
        slide.setFromY(10);
        slide.setToY(0);
        new ParallelTransition(fade, slide).play();
    }
}
