package com.hmood.ui;

import com.hmood.model.Mood;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;

/** Double hélice animée : chaque barreau prend la couleur d'une humeur réellement choisie (ordre chronologique). */
public final class DnaHelix extends Canvas {

    private final List<Mood> moods;
    private final Color accent;
    private double phase;

    public DnaHelix(List<Mood> moods, Color accent, double width, double height) {
        super(width, height);
        this.moods = moods;
        this.accent = accent;
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                phase += 0.025;
                draw();
            }
        };
        sceneProperty().addListener((o, a, scene) -> {
            if (scene == null) timer.stop();
            else timer.start();
        });
    }

    private void draw() {
        GraphicsContext g = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();
        g.clearRect(0, 0, w, h);
        int rungs = 34;
        double amp = h * 0.36;
        double mid = h / 2;
        for (int i = 0; i < rungs; i++) {
            double x = 20 + i * (w - 40) / (rungs - 1);
            double a = phase + i * 0.38;
            double y1 = mid + Math.sin(a) * amp;
            double y2 = mid - Math.sin(a) * amp;
            double depth = (Math.cos(a) + 1) / 2; // 0 = arrière, 1 = avant
            Color rung = moods.isEmpty() ? accent.deriveColor(0, 0.2, 0.8, 1)
                    : Color.web(moods.get(i % moods.size()).colorA());
            g.setStroke(rung.deriveColor(0, 1, 1, 0.35 + 0.55 * depth));
            g.setLineWidth(3);
            g.strokeLine(x, y1, x, y2);
            g.setFill(accent.deriveColor(0, 1, 1, 0.5 + 0.5 * depth));
            double r = 4 + 3 * depth;
            g.fillOval(x - r, y1 - r, 2 * r, 2 * r);
            g.setFill(Color.web("#FFFFFF", 0.35 + 0.5 * (1 - depth)));
            double r2 = 4 + 3 * (1 - depth);
            g.fillOval(x - r2, y2 - r2, 2 * r2, 2 * r2);
        }
    }
}
