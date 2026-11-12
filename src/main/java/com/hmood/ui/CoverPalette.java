package com.hmood.ui;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;

/**
 * Couleurs dominantes d'une pochette : on regroupe les pixels par teinte (pondérés par saturation et luminosité)
 * et on garde les deux teintes les plus présentes, ravivées pour rester lisibles sur le fond sombre.
 */
public final class CoverPalette {

    private static final int BUCKETS = 24;

    private CoverPalette() {
    }

    /** Deux couleurs [principale, secondaire], ou {@code null} si l'image n'est pas exploitable. */
    public static Color[] extract(Image image) {
        if (image == null || image.isError() || image.getProgress() < 1 || image.getPixelReader() == null) return null;
        PixelReader reader = image.getPixelReader();
        int w = (int) image.getWidth(), h = (int) image.getHeight();
        if (w == 0 || h == 0) return null;
        double[] weight = new double[BUCKETS];
        double[] r = new double[BUCKETS], g = new double[BUCKETS], b = new double[BUCKETS];
        double grey = 0, greyR = 0, greyG = 0, greyB = 0;
        int step = Math.max(1, Math.min(w, h) / 40);
        for (int y = 0; y < h; y += step) {
            for (int x = 0; x < w; x += step) {
                Color c = reader.getColor(x, y);
                double s = c.getSaturation(), v = c.getBrightness();
                if (v < 0.12) continue; // noirs : pas d'information de couleur
                double wgt = s * s * (0.35 + v);
                if (s < 0.18) {
                    grey += 1;
                    greyR += c.getRed();
                    greyG += c.getGreen();
                    greyB += c.getBlue();
                    continue;
                }
                int k = (int) (c.getHue() / 360.0 * BUCKETS) % BUCKETS;
                weight[k] += wgt;
                r[k] += c.getRed() * wgt;
                g[k] += c.getGreen() * wgt;
                b[k] += c.getBlue() * wgt;
            }
        }
        int first = -1, second = -1;
        for (int k = 0; k < BUCKETS; k++) {
            if (weight[k] <= 0) continue;
            if (first < 0 || weight[k] > weight[first]) {
                second = first;
                first = k;
            } else if (second < 0 || weight[k] > weight[second]) {
                second = k;
            }
        }
        if (first < 0) {
            if (grey == 0) return null;
            Color c = Color.color(greyR / grey, greyG / grey, greyB / grey);
            return new Color[]{tone(c, 0.55), tone(c.darker(), 0.4)};
        }
        Color main = Color.color(r[first] / weight[first], g[first] / weight[first], b[first] / weight[first]);
        // couleur secondaire : teinte suffisamment différente, sinon variante de la principale
        Color alt = second >= 0 && weight[second] > weight[first] * 0.12 && hueDistance(first, second) >= 2
                ? Color.color(r[second] / weight[second], g[second] / weight[second], b[second] / weight[second])
                : Color.hsb((main.getHue() + 35) % 360, main.getSaturation(), main.getBrightness());
        return new Color[]{tone(main, 0.85), tone(alt, 0.75)};
    }

    private static int hueDistance(int a, int b) {
        int d = Math.abs(a - b);
        return Math.min(d, BUCKETS - d);
    }

    /** Couleur ravivée, luminosité bornée pour un halo lumineux mais pas criard. */
    private static Color tone(Color c, double saturationFloor) {
        double s = Math.max(c.getSaturation(), Math.min(saturationFloor, c.getSaturation() * 1.4));
        double v = Math.max(0.55, Math.min(0.95, c.getBrightness() * 1.15));
        return Color.hsb(c.getHue(), Math.min(1, s), v);
    }
}
