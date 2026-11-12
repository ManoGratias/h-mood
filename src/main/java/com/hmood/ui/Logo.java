package com.hmood.ui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Paint;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

/**
 * Logo H MOOD : un « H » dont la barre centrale est un égaliseur (ondes sonores).
 * Deux montants arrondis + cinq barres de hauteurs symétriques. Grille de 48 unités.
 */
public final class Logo {

    private static final double[] BAR_HEIGHTS = {8, 16, 24, 16, 8};

    private Logo() {
    }

    /** Logo coloré par le thème (classe CSS .logo-bar). */
    public static Node create(double size) {
        return build(size, null);
    }

    /** Icône de fenêtre : logo dégradé glacier sur fond nuit arrondi. */
    public static Image renderIcon(int size) {
        Rectangle bg = new Rectangle(size, size);
        bg.setArcWidth(size * 0.42);
        bg.setArcHeight(size * 0.42);
        bg.setFill(Color.web("#101F32"));
        Paint fill = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#BDEFFF")), new Stop(1, Color.web("#45BFEF")));
        StackPane pane = new StackPane(bg, build(size * 0.66, fill));
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        return pane.snapshot(params, null);
    }

    private static Group build(double size, Paint fill) {
        double s = size / 48.0;
        Group g = new Group();
        g.getChildren().add(bar(4 * s, 4 * s, 7 * s, 40 * s, fill));
        g.getChildren().add(bar(37 * s, 4 * s, 7 * s, 40 * s, fill));
        double width = 3.2;
        double gap = 2.0;
        double x = 12;
        for (double h : BAR_HEIGHTS) {
            g.getChildren().add(bar(x * s, (24 - h / 2) * s, width * s, h * s, fill));
            x += width + gap;
        }
        // Cadre invisible pour garder des dimensions exactes size × size
        Rectangle frame = new Rectangle(size, size, Color.TRANSPARENT);
        g.getChildren().add(0, frame);
        return g;
    }

    private static Rectangle bar(double x, double y, double w, double h, Paint fill) {
        Rectangle r = new Rectangle(x, y, w, h);
        r.setArcWidth(w);
        r.setArcHeight(w);
        r.getStyleClass().add("logo-bar");
        if (fill != null) {
            r.setFill(fill);
        }
        return r;
    }
}
