package com.hmood.music.provider;

import com.hmood.catalog.AudiusCatalog;
import com.hmood.catalog.CatalogException;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.music.CatalogEntry;
import com.hmood.music.CatalogEntry.Kind;
import com.hmood.music.PlaybackMode;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptateur Audius : artistes indépendants, lecture intégrale autorisée par les artistes.
 * Référence de classement : {@code trending} ou {@code trending:<genre>}.
 */
public final class AudiusProvider implements MusicCatalogProvider, MusicChartProvider, MusicPlaybackProvider {

    public static final String ID = "audius";
    private final AudiusCatalog catalog;

    public AudiusProvider(AudiusCatalog catalog) {
        this.catalog = catalog;
    }

    @Override public String id() { return ID; }
    @Override public String displayName() { return "Audius"; }

    @Override
    public List<CatalogEntry> search(String query) throws CatalogException {
        return convert(catalog.search(query), false);
    }

    @Override
    public List<CatalogEntry> chart(String ref) throws CatalogException {
        String[] p = ref.split(":", 2);
        return convert(catalog.trending(p.length > 1 ? p[1] : null), true);
    }

    public List<CatalogEntry> forMood(Mood mood) throws CatalogException {
        return convert(catalog.forMood(mood), false);
    }

    @Override
    public boolean supports(CatalogEntry entry) {
        return ID.equals(entry.provider());
    }

    @Override
    public String resolve(CatalogEntry entry) throws CatalogException {
        return catalog.resolveStream(toTrack(entry));
    }

    private static Track toTrack(CatalogEntry e) {
        return new Track(-1, AudiusCatalog.PREFIX + e.externalId(), e.title(), e.artist(), null, e.genre(), null,
                e.durationMs(), e.mood(), e.coverUrl(), 0, false);
    }

    static List<CatalogEntry> convert(List<Track> tracks, boolean ranked) {
        List<CatalogEntry> out = new ArrayList<>();
        int i = 0;
        for (Track t : tracks) {
            i++;
            String id = t.filePath().substring(AudiusCatalog.PREFIX.length());
            out.add(new CatalogEntry(Kind.TRACK, ID, id, t.title(), t.artist(), null, null, null, t.coverPath(),
                    t.genre(), t.year() == null ? null : t.year().toString(), t.durationMs(), ranked ? i : null,
                    (long) t.playCount(), null, "https://audius.co/tracks/" + id, PlaybackMode.FULL, t.mood()));
        }
        return out;
    }
}
