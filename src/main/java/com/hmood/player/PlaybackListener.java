package com.hmood.player;

import com.hmood.model.Track;

/** Événements de lecture (appelés sur le thread JavaFX). */
public interface PlaybackListener {

    default void onStarted(Track track) {
    }

    /** @param listenedMs durée effectivement écoutée (approximation) */
    default void onFinished(Track track, long listenedMs, boolean completed) {
    }

    default void onError(Track track, String message) {
    }
}
