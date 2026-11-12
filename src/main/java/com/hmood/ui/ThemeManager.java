package com.hmood.ui;

import com.hmood.model.Theme;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.Dialog;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Applique le thème courant (base.css + feuille du thème) à toutes les scènes enregistrées. */
public final class ThemeManager {

    private final ObjectProperty<Theme> theme;
    private final List<Scene> scenes = new ArrayList<>();

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
        theme.set(theme.get().other());
        return theme.get();
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
