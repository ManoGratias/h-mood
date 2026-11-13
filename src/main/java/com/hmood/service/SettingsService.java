package com.hmood.service;

import com.hmood.model.Theme;
import com.hmood.repository.PreferenceRepository;

/** Réglages persistés dans user_preferences. */
public final class SettingsService {

    private static final String THEME = "ui.theme";
    private static final String VOLUME = "audio.volume";

    private final PreferenceRepository prefs;

    public SettingsService(PreferenceRepository prefs) {
        this.prefs = prefs;
    }

    public Theme theme() {
        return prefs.get(THEME).map(v -> {
            try {
                return Theme.valueOf(v);
            } catch (IllegalArgumentException e) {
                return Theme.ICE_BLUE;
            }
        }).orElse(Theme.ICE_BLUE);
    }

    public void setTheme(Theme theme) {
        prefs.set(THEME, theme.name());
    }

    /** Intervalle du changement automatique de thème, en secondes (0 = désactivé). */
    public int themeCycleSeconds() {
        return prefs.get("theme.cycle_seconds").map(v -> {
            try {
                return Math.max(0, Integer.parseInt(v));
            } catch (NumberFormatException e) {
                return 0;
            }
        }).orElse(0);
    }

    public void setThemeCycleSeconds(int seconds) {
        prefs.set("theme.cycle_seconds", Integer.toString(Math.max(0, seconds)));
    }

    public double volume() {
        return prefs.get(VOLUME).map(v -> {
            try {
                return Math.max(0, Math.min(1, Double.parseDouble(v)));
            } catch (NumberFormatException e) {
                return 0.7;
            }
        }).orElse(0.7);
    }

    public void setVolume(double volume) {
        prefs.set(VOLUME, Double.toString(Math.max(0, Math.min(1, volume))));
    }

    /** Durée des fondus enchaînés (secondes, 0 = désactivé). */
    public double crossfadeSeconds() {
        return prefs.get("audio.crossfade").map(v -> {
            try {
                return Math.max(0, Math.min(12, Double.parseDouble(v)));
            } catch (NumberFormatException e) {
                return 6.0;
            }
        }).orElse(6.0);
    }

    public void setCrossfadeSeconds(double seconds) {
        prefs.set("audio.crossfade", Double.toString(Math.max(0, Math.min(12, seconds))));
    }

    /** Compteur de sessions de concentration terminées (par jour). */
    public int focusSessions(String day) {
        return prefs.get("focus." + day).map(Integer::parseInt).orElse(0);
    }

    public void addFocusSession(String day) {
        prefs.set("focus." + day, Integer.toString(focusSessions(day) + 1));
    }

    /** Panneau Assistant IA affiché à droite (masqué par défaut). */
    public boolean assistantVisible() {
        return prefs.get("ui.assistant").map(Boolean::parseBoolean).orElse(false);
    }

    public void setAssistantVisible(boolean visible) {
        prefs.set("ui.assistant", Boolean.toString(visible));
    }

    /** Artistes que l'utilisateur associe à une humeur : liste de « id|nom ». */
    public java.util.List<String[]> moodArtists(com.hmood.model.Mood mood) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        prefs.get("mood.artists." + mood.name()).ifPresent(v -> {
            for (String item : v.split(";")) {
                String[] p = item.split("\\|", 2);
                if (p.length == 2 && !p[0].isBlank()) out.add(p);
            }
        });
        return out;
    }

    public void addMoodArtist(com.hmood.model.Mood mood, String id, String name) {
        java.util.List<String[]> list = moodArtists(mood);
        if (list.stream().anyMatch(a -> a[0].equals(id))) return;
        list.add(new String[]{id, name.replace(";", " ").replace("|", " ")});
        saveMoodArtists(mood, list);
    }

    public void removeMoodArtist(com.hmood.model.Mood mood, String id) {
        java.util.List<String[]> list = moodArtists(mood);
        list.removeIf(a -> a[0].equals(id));
        saveMoodArtists(mood, list);
    }

    private void saveMoodArtists(com.hmood.model.Mood mood, java.util.List<String[]> list) {
        prefs.set("mood.artists." + mood.name(), String.join(";", list.stream().map(a -> a[0] + "|" + a[1]).toList()));
    }

    /** Identifiant d'application Jamendo (préférence locale, sinon variable d'environnement JAMENDO_CLIENT_ID). */
    public String jamendoClientId() {
        return prefs.get("jamendo.client_id").filter(v -> !v.isBlank())
                .orElseGet(() -> System.getenv().getOrDefault("JAMENDO_CLIENT_ID", ""));
    }

    public void setJamendoClientId(String id) {
        prefs.set("jamendo.client_id", id == null ? "" : id.trim());
    }

    /** Touches multimédia du clavier captées même quand H MOOD est en arrière-plan (activé par défaut). */
    public boolean globalMediaKeys() {
        return prefs.get("keys.global_media").map(Boolean::parseBoolean).orElse(true);
    }

    public void setGlobalMediaKeys(boolean on) {
        prefs.set("keys.global_media", Boolean.toString(on));
    }

    /** Décalage des paroles synchronisées d'un morceau, en millisecondes (positif = paroles retardées). */
    public long lyricsOffset(String trackKey) {
        return prefs.get("lyrics.offset." + trackKey).map(v -> {
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    /** Vrai si un calage a déjà été enregistré pour ce morceau (utile pour les extraits 30 s). */
    public boolean hasLyricsOffset(String trackKey) {
        return prefs.get("lyrics.offset." + trackKey).isPresent();
    }

    public void setLyricsOffset(String trackKey, long offsetMs) {
        prefs.set("lyrics.offset." + trackKey, Long.toString(offsetMs));
    }

    /** Vue de « Ma musique » : "tracks" (liste) ou "folders" (par dossier). */
    public String libraryView() {
        return prefs.get("library.view").orElse("tracks");
    }

    public void setLibraryView(String view) {
        prefs.set("library.view", view);
    }

    public String displayName() {
        return prefs.displayName();
    }

    public void setDisplayName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Le nom ne peut pas être vide.");
        }
        prefs.setDisplayName(name.trim());
    }
}
