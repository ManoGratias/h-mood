package com.hmood.util;

import java.nio.file.Path;

/** Emplacements des données locales (%APPDATA%\HMood sous Windows). */
public final class AppPaths {

    private AppPaths() {
    }

    public static Path dataDir() {
        String override = System.getProperty("hmood.data.dir");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) {
            return Path.of(appData, "HMood");
        }
        return Path.of(System.getProperty("user.home"), ".hmood");
    }
}
