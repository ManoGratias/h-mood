package com.hmood.music.provider;

import com.hmood.catalog.CatalogException;
import com.hmood.music.CatalogEntry;

import java.util.List;

/** Fournisseur de classements réels (référence propre à chaque fournisseur). Appels bloquants. */
public interface MusicChartProvider {

    String id();

    String displayName();

    List<CatalogEntry> chart(String chartRef) throws CatalogException;
}
