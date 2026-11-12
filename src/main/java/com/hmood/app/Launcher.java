package com.hmood.app;

/**
 * Lanceur sans dépendance à {@link javafx.application.Application} : nécessaire pour
 * exécuter le JAR autonome (classpath) et l'exécutable jpackage.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        HMoodApp.main(args);
    }
}
