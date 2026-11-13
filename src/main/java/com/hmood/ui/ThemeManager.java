package com.hmood.ui;

import com.hmood.model.Theme;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Applique le thème courant (base.css + feuille du thème) à toutes les scènes enregistrées. */
public final class ThemeManager {

    private final ObjectProperty<Theme> theme;
    private final List<Scene> scenes = new ArrayList<>();
    private Timeline cycle;
    private int cycleSeconds;

    public ThemeManager(Theme initial) {
        theme = new SimpleObjectProperty<>(initial);
        theme.addListener((o, a, t) -> scenes.forEach(this::apply));
    }

    public void register(Scene scene) {
        scenes.add(scene);
        apply(scene);
    }

    public List<String> stylesheets() {
        return List.of(css("base.css"), css(theme.get().stylesheet()));
    }

    public void styleDialog(Dialog<?> dialog) {
        dialog.getDialogPane().getStylesheets().setAll(stylesheets());
        dialog.getDialogPane().getStyleClass().add("hm-dialog");
    }

    public Theme theme() {
        return theme.get();
    }

    public void setTheme(Theme t) {
        theme.set(t);
    }

    public Theme toggle() {
        theme.set(theme.get().next());
        return theme.get();
    }

    /** Change de thème automatiquement toutes les {@code seconds} secondes (0 = désactivé). */
    public void setAutoCycle(int seconds) {
        if (cycle != null) cycle.stop();
        cycle = null;
        cycleSeconds = Math.max(0, seconds);
        if (cycleSeconds > 0) {
            cycle = new Timeline(new KeyFrame(Duration.seconds(cycleSeconds), e -> theme.set(theme.get().next())));
            cycle.setCycleCount(Timeline.INDEFINITE);
            cycle.play();
        }
    }

    public int autoCycleSeconds() {
        return cycleSeconds;
    }

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    private void apply(Scene scene) {
        scene.getStylesheets().setAll(stylesheets());
    }

    private static String css(String name) {
        URL url = ThemeManager.class.getResource("/com/hmood/css/" + name);
        if (url == null) {
            throw new IllegalStateException("Feuille de style introuvable : " + name);
        }
        return url.toExternalForm();
    }
}
