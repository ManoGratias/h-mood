package com.hmood.controller;

import com.hmood.ui.Icons;

import java.util.List;

/** Pages de l'application. Une page sans FXML affiche un état « prévu, non disponible ». */
public enum Page {

    HOME("Accueil", Icons.HOME, "HomeView.fxml", Section.MENU, null, null, List.of()),
    DISCOVER("Découvrir", Icons.EXPLORE, "DiscoverView.fxml", Section.MENU, null, null, List.of()),
    MY_MUSIC("Ma musique", Icons.LIBRARY, "LibraryView.fxml", Section.MENU, null, null, List.of()),
    PLAYLISTS("Playlists", Icons.PLAYLIST, "PlaylistsView.fxml", Section.MENU, null, null, List.of()),

    MOOD_SCANNER("Scanner d'humeur", Icons.MOOD, "MoodScannerView.fxml", Section.INTELLIGENCE, null, null, List.of()),
    AI_DJ("DJ IA", Icons.HEADPHONES, "DjView.fxml", Section.INTELLIGENCE, null, null, List.of()),
    MUSIC_DNA("ADN musical", Icons.DNA, "DnaView.fxml", Section.INTELLIGENCE, null, null, List.of()),
    TIME_MACHINE("Voyage temporel", Icons.HISTORY, "TimeMachineView.fxml", Section.INTELLIGENCE, null, null, List.of()),
    WRAPPED("Mon bilan", Icons.SPARKLE, "WrappedView.fxml", Section.INTELLIGENCE, null, null, List.of()),

    FOCUS("Concentration", Icons.FOCUS, "FocusView.fxml", Section.MODES, null, null, List.of()),
    SLEEP("Sommeil", Icons.MOON, "SleepView.fxml", Section.MODES, null, null, List.of()),

    SETTINGS("Paramètres", Icons.SETTINGS, "SettingsView.fxml", Section.SYSTEM, null, null, List.of());

    public enum Section {
        MENU("MENU"), INTELLIGENCE("INTELLIGENCE"), MODES("MODES"), SYSTEM(null);

        private final String title;

        Section(String title) {
            this.title = title;
        }

        public String title() {
            return title;
        }
    }

    private final String label;
    private final String icon;
    private final String fxml;
    private final Section section;
    private final String description;
    private final String hint;
    private final List<String> plannedFeatures;

    Page(String label, String icon, String fxml, Section section, String description, String hint,
         List<String> plannedFeatures) {
        this.label = label;
        this.icon = icon;
        this.fxml = fxml;
        this.section = section;
        this.description = description;
        this.hint = hint;
        this.plannedFeatures = plannedFeatures;
    }

    public String label() { return label; }
    public String icon() { return icon; }
    public String fxml() { return fxml; }
    public Section section() { return section; }
    public String description() { return description; }
    public String hint() { return hint; }
    public List<String> plannedFeatures() { return plannedFeatures; }
    public boolean isAvailable() { return fxml != null; }
}
