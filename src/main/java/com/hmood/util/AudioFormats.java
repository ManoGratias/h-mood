package com.hmood.util;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Formats audio lisibles par JavaFX Media. */
public final class AudioFormats {

    /** FLAC et OGG ne sont pas lus nativement par JavaFX Media : ils sont ignorés à l'import. */
    public static final Set<String> SUPPORTED = Set.of("mp3", "m4a", "aac", "wav", "aif", "aiff");

    public static final String DESCRIPTION = "MP3, M4A/AAC, WAV, AIFF";

    private AudioFormats() {
    }

    public static String extension(Path file) {
        String name = file.getFileName() == null ? "" : file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isSupported(Path file) {
        return SUPPORTED.contains(extension(file));
    }
}
