package com.hmood.ui;

import com.hmood.player.PlayerService;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.Arrays;

/** Visualiseur audio : barres animées à partir du spectre réel fourni par JavaFX Media. */
public final class Visualizer {

    private static final int BARS = 18;

    private final Canvas canvas;
    private final double[] target = new double[BARS];
    private final double[] value = new double[BARS];
    private Color color;
    private boolean settled;

    public Visualizer(Canvas canvas, Color color) {
        this.canvas = canvas;
        this.color = color;
        new AnimationTimer() {
            @Override
            public void handle(long now) {
                step();
            }
        }.start();
    }

    /** Magnitudes en dB (seuil {@link PlayerService#SPECTRUM_THRESHOLD}). */
    public void update(float[] magnitudes) {
        int n = magnitudes.length;
        int usable = Math.max(BARS, (int) (n * 0.7)); // les bandes les plus aiguës sont presque toujours vides
        double floor = PlayerService.SPECTRUM_THRESHOLD;
        for (int i = 0; i < BARS; i++) {
            int from = i * usable / BARS;
            int to = Math.max(from + 1, (i + 1) * usable / BARS);
            double sum = 0;
            int count = 0;
            for (int j = from; j < to && j < n; j++) {
                sum += magnitudes[j] - floor;
                count++;
            }
            double v = count == 0 ? 0 : (sum / count) / -floor;
            target[i] = Math.max(0, Math.min(1, v));
        }
        settled = false;
    }

    public void setActive(boolean active) {
        if (!active) {
            Arrays.fill(target, 0);
        }
        settled = false;
    }

    public void setColor(Color color) {
        this.color = color;
        settled = false;
    }

    private void step() {
        if (settled) {
            return;
        }
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        gc.clearRect(0, 0, w, h);
        double bw = w / BARS;
        double movement = 0;
        for (int i = 0; i < BARS; i++) {
            double delta = (target[i] - value[i]) * 0.3;
            value[i] += delta;
            movement += Math.abs(delta);
            double bh = Math.max(2, value[i] * h);
            gc.setFill(color.deriveColor(0, 1, 1, 0.4 + 0.6 * value[i]));
            gc.fillRoundRect(i * bw + 1, h - bh, Math.max(1, bw - 2), bh, 3, 3);
        }
        settled = movement < 0.001 && Arrays.stream(target).allMatch(t -> t == 0);
    }
}
