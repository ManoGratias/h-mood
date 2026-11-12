package com.hmood.model;

/** Une playlist avec ses agrégats (nombre de titres, durée totale). */
public record Playlist(long id, String name, String description, Mood mood, int trackCount, long totalDurationMs) {
}
