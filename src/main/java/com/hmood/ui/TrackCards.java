package com.hmood.ui;

import com.hmood.model.Track;
import javafx.animation.FadeTransition;
import javafx.animation.ScaleTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;
import java.util.function.Consumer;

/** Cartes de morceaux « grand format » (pochette, survol animé) et bannière vedette. */
public final class TrackCards {

    private TrackCards() {
    }

    /** Carte carrée : pochette + bouton lecture qui apparaît au survol. */
    public static VBox card(Track t, double size, Runnable onPlay) {
        Node cover = Covers.of(t, size);
        StackPane overlay = new StackPane(Icons.icon(Icons.PLAY, 26));
        overlay.getStyleClass().add("card-play");
        overlay.setMaxSize(54, 54);
        overlay.setOpacity(0);
        StackPane.setAlignment(overlay, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(overlay, new Insets(0, 10, 10, 0));
        StackPane art = new StackPane(cover, overlay);
        art.setMaxSize(size, size);

        Label title = Ui.label(t.title(), "card-track-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMaxWidth(size);
        Label artist = Ui.label(t.displayArtist(), "card-track-artist");
        artist.setTextOverrun(OverrunStyle.ELLIPSIS);
        artist.setMaxWidth(size);
        HBox meta = new HBox(6);
        meta.setAlignment(Pos.CENTER_LEFT);
        if (t.mood() != null) {
            Label mood = Ui.label(t.mood().label(), "mood-pill");
            mood.setStyle("-fx-background-color: " + Ui.moodGradient(t.mood()) + ";");
            meta.getChildren().add(mood);
        } else if (t.genre() != null) {
            meta.getChildren().add(Ui.label(t.genre(), "card-genre"));
        }

        VBox card = new VBox(8, art, title, artist, meta);
        card.getStyleClass().add("track-card");
        card.setPrefWidth(size + 24);
        card.setMaxWidth(size + 24);

        ScaleTransition grow = new ScaleTransition(Duration.millis(160), card);
        FadeTransition show = new FadeTransition(Duration.millis(160), overlay);
        card.setOnMouseEntered(e -> {
            grow.stop(); grow.setToX(1.04); grow.setToY(1.04); grow.playFromStart();
            show.stop(); show.setToValue(1); show.playFromStart();
        });
        card.setOnMouseExited(e -> {
            grow.stop(); grow.setToX(1); grow.setToY(1); grow.playFromStart();
            show.stop(); show.setToValue(0); show.playFromStart();
        });
        card.setOnMouseClicked(e -> onPlay.run());
        return card;
    }

    /** Bannière vedette : pochette floutée en fond, grande pochette, titre géant. */
    public static StackPane hero(Track t, String kicker, List<Track> list, Consumer<Integer> playFromIndex) {
        StackPane hero = new StackPane();
        hero.getStyleClass().add("hero-banner");
        hero.setMinHeight(280);
        hero.setPrefHeight(280);

        Image image = Covers.image(t.coverPath());
        if (image != null) {
            ImageView bg = new ImageView(image);
            bg.setPreserveRatio(false);
            bg.fitWidthProperty().bind(hero.widthProperty());
            bg.fitHeightProperty().bind(hero.heightProperty());
            bg.setEffect(new GaussianBlur(60));
            bg.setOpacity(0.55);
            hero.getChildren().add(bg);
        }
        Region shade = new Region();
        shade.getStyleClass().add("hero-shade");
        if (t.mood() != null) {
            shade.setStyle("-fx-background-color: linear-gradient(to right, rgba(6,10,18,0.88) 30%, "
                    + Ui.rgba(t.mood().colorA(), 0.35) + ");");
        }
        hero.getChildren().add(shade);

        Node cover = Covers.of(t, 210);
        cover.getStyleClass().add("hero-cover");
        Label kickerLabel = Ui.label(kicker, "hero-kicker");
        Label title = Ui.wrapped(t.title(), "hero-track-title");
        title.setMaxWidth(560);
        Label artist = Ui.label(t.displayArtist(), "hero-track-artist");
        HBox pills = new HBox(8);
        if (t.mood() != null) {
            Label mood = Ui.label(t.mood().label(), "mood-pill");
            mood.setStyle("-fx-background-color: " + Ui.moodGradient(t.mood()) + ";");
            pills.getChildren().add(mood);
        }
        if (t.genre() != null) pills.getChildren().add(Ui.label(t.genre(), "hero-chip"));
        if (t.playCount() > 0) pills.getChildren().add(Ui.label(String.format("%,d écoutes", t.playCount()).replace(',', ' '), "hero-chip"));

        Button play = new Button("Écouter", Icons.icon(Icons.PLAY, 18));
        play.getStyleClass().add("primary-button");
        play.setOnAction(e -> playFromIndex.accept(list.indexOf(t)));
        Button all = new Button("Lire toute la sélection (" + list.size() + ")");
        all.getStyleClass().add("glass-button");
        all.setOnAction(e -> playFromIndex.accept(0));
        HBox actions = new HBox(10, play, all);

        VBox texts = new VBox(10, kickerLabel, title, artist, pills, actions);
        texts.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(texts, Priority.ALWAYS);
        HBox content = new HBox(30, cover, texts);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(30, 36, 30, 36));
        hero.getChildren().add(content);

        Rectangle clip = new Rectangle();
        clip.setArcWidth(44);
        clip.setArcHeight(44);
        clip.widthProperty().bind(hero.widthProperty());
        clip.heightProperty().bind(hero.heightProperty());
        hero.setClip(clip);
        ColorAdjust none = new ColorAdjust();
        hero.setEffect(none);
        return hero;
    }
}
