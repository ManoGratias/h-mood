package com.hmood.model;

/** Thèmes visuels disponibles. */
public enum Theme {
    ICE_BLUE("Bleu Glacier", "theme-ice.css", "#45BFEF", "#BDEFFF"),
    SUNSET_ORANGE("Orange Couchant", "theme-sunset.css", "#FFA66F", "#FFE0C2");

    private final String label;
    private final String stylesheet;
    private final String accent;
    private final String accentLight;

    Theme(String label, String stylesheet, String accent, String accentLight) {
        this.label = label;
        this.stylesheet = stylesheet;
        this.accent = accent;
        this.accentLight = accentLight;
    }

    public String label() { return label; }
    public String stylesheet() { return stylesheet; }
    public String accent() { return accent; }
    public String accentLight() { return accentLight; }

    public Theme other() {
        return this == ICE_BLUE ? SUNSET_ORANGE : ICE_BLUE;
    }
}
