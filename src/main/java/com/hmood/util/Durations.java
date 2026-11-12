package com.hmood.util;

/** Formatage des durées. */
public final class Durations {

    private Durations() {
    }

    /** 3:07, 1:02:45 */
    public static String format(long ms) {
        if (ms <= 0) {
            return "0:00";
        }
        long totalSeconds = ms / 1000;
        long h = totalSeconds / 3600;
        long m = (totalSeconds % 3600) / 60;
        long s = totalSeconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
    }

    /** 42 min, 3 h 05 */
    public static String human(long ms) {
        long minutes = Math.max(0, ms) / 60_000;
        if (minutes < 60) {
            return minutes + " min";
        }
        return (minutes / 60) + " h " + String.format("%02d", minutes % 60);
    }
}
