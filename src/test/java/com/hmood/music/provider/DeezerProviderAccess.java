package com.hmood.music.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.hmood.music.CatalogEntry;

import java.util.List;

/** Accès de test au décodage JSON de DeezerProvider (méthode package-private). */
public final class DeezerProviderAccess {
    private DeezerProviderAccess() {
    }

    public static List<CatalogEntry> parse(JsonNode data) {
        return DeezerProvider.tracks(data, true);
    }
}
