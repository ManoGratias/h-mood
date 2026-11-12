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

    private static final int MAX_CACHED = 150;
    private static final Map<String, java.lang.ref.SoftReference<Image>> CACHE =
            new java.util.LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, java.lang.ref.SoftReference<Image>> eldest) {
                    return size() > MAX_CACHED;
                }
            };

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
            return cached(path, p -> new Image(p, 480, 480, true, true, true));
        }
        if (path == null || path.isBlank() || !Files.isRegularFile(Path.of(path))) {
            return null;
        }
        return cached(path, p -> new Image(Path.of(p).toUri().toString(), 192, 192, true, true, true));
    }

    /**
     * Cache borné (les plus récemment utilisées) à références « douces » : la mémoire est rendue
     * au besoin, au lieu de garder toutes les pochettes vues depuis le lancement (cause d'un manque de mémoire).
     */
    private static Image cached(String key, java.util.function.Function<String, Image> loader) {
        synchronized (CACHE) {
            java.lang.ref.SoftReference<Image> ref = CACHE.get(key);
            Image image = ref == null ? null : ref.get();
            if (image == null) {
                image = loader.apply(key);
                CACHE.put(key, new java.lang.ref.SoftReference<>(image));
            }
            return image;
        }
    }
}
