package com.hmood.service;

import com.hmood.model.TrackMetadata;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit les tags d'un fichier audio (titre, artiste, album, genre, année, durée, pochette).
 * En cas de tags absents ou illisibles, se rabat sur le nom du fichier.
 */
public final class MetadataReader {

    private static final Pattern YEAR = Pattern.compile("(19|20)\\d{2}");
    private static final Pattern ID3_GENRE_CODE = Pattern.compile("^\\(\\d+\\)\\s*");

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    private final Path coversDir;

    public MetadataReader(Path coversDir) {
        this.coversDir = coversDir;
    }

    public TrackMetadata read(Path file) {
        TrackMetadata fallback = fromFileName(file);
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            AudioHeader header = audioFile.getAudioHeader();
            long duration = header == null ? 0 : Math.round(header.getPreciseTrackLength() * 1000);
            Tag tag = audioFile.getTag();
            if (tag == null) {
                return fallback.withDuration(duration);
            }
            String genre = field(tag, FieldKey.GENRE);
            if (genre != null) {
                genre = ID3_GENRE_CODE.matcher(genre).replaceFirst("").trim();
            }
            return new TrackMetadata(
                    fallback.filePath(),
                    firstNonBlank(field(tag, FieldKey.TITLE), fallback.title()),
                    firstNonBlank(field(tag, FieldKey.ARTIST), field(tag, FieldKey.ALBUM_ARTIST), fallback.artist()),
                    field(tag, FieldKey.ALBUM),
                    blankToNull(genre),
                    parseYear(field(tag, FieldKey.YEAR)),
                    duration,
                    extractCover(tag, fallback.filePath()));
        } catch (Exception | LinkageError e) {
            return fallback;
        }
    }

    /** « Artiste - Titre.mp3 » → artiste + titre ; sinon le nom du fichier sert de titre. */
    public static TrackMetadata fromFileName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0 ? name.substring(0, dot) : name).replace('_', ' ').trim();
        String artist = null;
        String title = base;
        int sep = base.indexOf(" - ");
        if (sep > 0 && sep < base.length() - 3) {
            artist = base.substring(0, sep).trim();
            title = base.substring(sep + 3).trim();
        }
        return new TrackMetadata(file.toAbsolutePath().toString(), title, artist, null, null, null, 0, null);
    }

    public static Integer parseYear(String raw) {
        if (raw == null) {
            return null;
        }
        Matcher m = YEAR.matcher(raw);
        return m.find() ? Integer.parseInt(m.group()) : null;
    }

    private String extractCover(Tag tag, String filePath) {
        try {
            Artwork artwork = tag.getFirstArtwork();
            if (artwork == null || artwork.getBinaryData() == null || artwork.getBinaryData().length == 0) {
                return null;
            }
            String mime = artwork.getMimeType() == null ? "" : artwork.getMimeType().toLowerCase();
            String ext = mime.contains("png") ? "png" : "jpg";
            Files.createDirectories(coversDir);
            Path target = coversDir.resolve(sha1(filePath) + "." + ext);
            Files.write(target, artwork.getBinaryData());
            return target.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String field(Tag tag, FieldKey key) {
        try {
            return blankToNull(tag.getFirst(key));
        } catch (Exception e) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String sha1(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
