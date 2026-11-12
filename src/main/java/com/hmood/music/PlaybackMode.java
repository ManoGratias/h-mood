package com.hmood.music;

/** Ce que le fournisseur autorise réellement pour un morceau. */
public enum PlaybackMode {
    FULL("Morceau complet"),
    PREVIEW("Extrait 30 s"),
    LINK_ONLY("Écoute sur la plateforme");

    private final String label;

    PlaybackMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
