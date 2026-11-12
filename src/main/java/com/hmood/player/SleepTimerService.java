package com.hmood.player;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.util.Duration;

/**
 * Minuteur d'arrêt automatique (Mode Sommeil) : à l'échéance, le volume baisse progressivement
 * puis la lecture s'arrête. Fonctionne quelle que soit la page affichée.
 */
public final class SleepTimerService {

    /** Durée du fondu final avant l'arrêt. */
    public static final int FADE_SECONDS = 30;

    private final PlayerService player;
    private final ReadOnlyLongWrapper remainingSeconds = new ReadOnlyLongWrapper(0);
    private Timeline ticker;
    private boolean gradual = true;
    private long totalSeconds;

    public SleepTimerService(PlayerService player) {
        this.player = player;
    }

    /** Démarre (ou redémarre) le minuteur. {@code gradual} : baisse le volume sur tout le dernier quart. */
    public void start(int minutes, boolean gradual) {
        cancel();
        this.gradual = gradual;
        totalSeconds = minutes * 60L;
        remainingSeconds.set(totalSeconds);
        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        ticker.play();
    }

    private void tick() {
        long left = remainingSeconds.get() - 1;
        remainingSeconds.set(Math.max(0, left));
        long fadeWindow = gradual ? Math.max(FADE_SECONDS, totalSeconds / 4) : FADE_SECONDS;
        if (left <= fadeWindow) {
            player.masterGainProperty().set(Math.max(0, (double) left / fadeWindow));
        }
        if (left <= 0) {
            player.stop();
            cancel();
        }
    }

    public void cancel() {
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
        remainingSeconds.set(0);
        player.masterGainProperty().set(1.0);
    }

    public boolean isRunning() {
        return ticker != null;
    }

    public ReadOnlyLongProperty remainingSecondsProperty() {
        return remainingSeconds.getReadOnlyProperty();
    }
}
