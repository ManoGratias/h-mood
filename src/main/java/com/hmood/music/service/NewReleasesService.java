package com.hmood.music.service;

import com.hmood.catalog.CatalogException;
import com.hmood.music.ChartResult;

/** Nouveautés : sorties récentes réelles (playlist éditoriale Deezer « Les nouveautés du vendredi »). */
public final class NewReleasesService {

    private final TrendingService trending;

    public NewReleasesService(TrendingService trending) {
        this.trending = trending;
    }

    public ChartResult latest(boolean force) throws CatalogException {
        return trending.chart("new", force);
    }

    public ChartResult trendingAlbums(boolean force) throws CatalogException {
        return trending.chart("albums", force);
    }
}
