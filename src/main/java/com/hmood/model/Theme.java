package com.hmood.model;

/** Thèmes visuels disponibles. */
public enum Theme {
    ICE_BLUE("Bleu Glacier", "theme-ice.css", "#45BFEF", "#BDEFFF"),
    SUNSET_ORANGE("Orange Couchant", "theme-sunset.css", "#FFA66F", "#FFE0C2"),
    VIOLET_AURORA("Violet Aurore", "theme-violet.css", "#A970FF", "#E6D6FF"),
    EMERALD_GREEN("Vert Émeraude", "theme-emerald.css", "#2FD38A", "#C2F7DD"),
    SAKURA_PINK("Rose Sakura", "theme-sakura.css", "#FF7EB3", "#FFDCEB"),
    RUBY_RED("Rouge Rubis", "theme-ruby.css", "#F0465A", "#FFC9CF"),
    MIDNIGHT_GOLD("Or Minuit", "theme-gold.css", "#E8B64C", "#FBEBC4"),
    GRAPHITE("Graphite", "theme-graphite.css", "#B8C2CC", "#EEF2F5");

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

    /** Classe CSS de la vignette dans les paramètres (ex. « theme-ice »). */
    public String swatchClass() { return stylesheet.replace(".css", ""); }

    /** Thème suivant (bouton de la barre du haut, changement automatique). */
    public Theme next() {
        Theme[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
