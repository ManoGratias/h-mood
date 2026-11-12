package com.hmood.ui;

import com.hmood.model.Track;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Pochettes (extraites des tags à l'import) avec un visuel de repli. */
public final class Covers {

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    private Covers() {
    }

    public static Node of(Track track, double size) {
        StackPane box = new StackPane();
        box.getStyleClass().add("cover");
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        if (track != null && track.mood() != null) {
            box.setStyle("-fx-background-color: " + Ui.moodGradient(track.mood()) + ";");
        }
        Image image = track == null ? null : image(track.coverPath());
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setSmooth(true);
            Rectangle clip = new Rectangle(size, size);
            clip.setArcWidth(size * 0.28);
            clip.setArcHeight(size * 0.28);
            view.setClip(clip);
            box.getChildren().add(view);
        } else {
            box.getChildren().add(Icons.icon(Icons.MUSIC_NOTE, size * 0.46));
        }
        return box;
    }

    /** Portrait rond (artistes). */
    public static Node round(String url, double size) {
        StackPane box = new StackPane();
        box.getStyleClass().add("artist-avatar");
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        Image image = image(url);
        if (image != null) {
            ImageView view = new ImageView(image);
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setClip(new javafx.scene.shape.Circle(size / 2, size / 2, size / 2));
            box.getChildren().add(view);
        } else {
            box.getChildren().add(Icons.icon(Icons.MUSIC_NOTE, size * 0.4));
        }
        return box;
    }

    public static Image image(String path) {
        if (path != null && (path.startsWith("http://") || path.startsWith("https://"))) {
            return CACHE.computeIfAbsent(path, p -> new Image(p, 480, 480, true, true, true));
        }
        if (path == null || path.isBlank() || !Files.isRegularFile(Path.of(path))) {
            return null;
        }
        return CACHE.computeIfAbsent(path, p -> new Image(Path.of(p).toUri().toString(), 192, 192, true, true, true));
    }
}
