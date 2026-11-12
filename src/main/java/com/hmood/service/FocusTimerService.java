package com.hmood.service;

import com.hmood.player.PlayerService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyLongProperty;
import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.util.Duration;

import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Minuteur de concentration type Pomodoro : travail / pause, cycles, compteur du jour.
 * Indépendant de la page affichée. Peut mettre la musique en pause pendant les pauses.
 */
public final class FocusTimerService {

    public enum Phase {
        IDLE("Prêt"), WORK("Concentration"), BREAK("Pause");
        public final String label;
        Phase(String label) { this.label = label; }
    }

    private final PlayerService player;
    private final SettingsService settings;
    private final IntegerProperty workMinutes = new SimpleIntegerProperty(25);
    private final IntegerProperty breakMinutes = new SimpleIntegerProperty(5);
    private final ObjectProperty<Phase> phase = new SimpleObjectProperty<>(Phase.IDLE);
    private final ReadOnlyLongWrapper remaining = new ReadOnlyLongWrapper(25 * 60);
    private final ReadOnlyLongWrapper total = new ReadOnlyLongWrapper(25 * 60);
    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper();
    private final IntegerProperty completedToday;
    private final Timeline ticker;
    private boolean pauseMusicDuringBreaks = true;
    private Consumer<String> notifier = s -> { };

    public FocusTimerService(PlayerService player, SettingsService settings) {
        this.player = player;
        this.settings = settings;
        completedToday = new SimpleIntegerProperty(settings.focusSessions(LocalDate.now().toString()));
        ticker = new Timeline(new KeyFrame(Duration.seconds(1), e -> tick()));
        ticker.setCycleCount(Timeline.INDEFINITE);
        workMinutes.addListener((o, a, v) -> {
            if (phase.get() == Phase.IDLE) reset();
        });
    }

    public void setNotifier(Consumer<String> notifier) {
        this.notifier = notifier;
    }

    public void setPauseMusicDuringBreaks(boolean pause) {
        this.pauseMusicDuringBreaks = pause;
    }

    public void startOrPause() {
        if (running.get()) {
            ticker.pause();
            running.set(false);
            return;
        }
        if (phase.get() == Phase.IDLE) {
            begin(Phase.WORK);
        }
        ticker.play();
        running.set(true);
    }

    public void reset() {
        ticker.stop();
        running.set(false);
        phase.set(Phase.IDLE);
        total.set(workMinutes.get() * 60L);
        remaining.set(total.get());
    }

    public void skip() {
        if (phase.get() == Phase.IDLE) return;
        endPhase();
    }

    private void begin(Phase p) {
        phase.set(p);
        long seconds = (p == Phase.BREAK ? breakMinutes.get() : workMinutes.get()) * 60L;
        total.set(seconds);
        remaining.set(seconds);
    }

    private void tick() {
        remaining.set(remaining.get() - 1);
        if (remaining.get() <= 0) {
            endPhase();
        }
    }

    private void endPhase() {
        if (phase.get() == Phase.WORK) {
            String day = LocalDate.now().toString();
            settings.addFocusSession(day);
            completedToday.set(settings.focusSessions(day));
            notifier.accept("Session de concentration terminée ✔ — " + breakMinutes.get() + " min de pause.");
            if (pauseMusicDuringBreaks) player.pause();
            begin(Phase.BREAK);
        } else {
            notifier.accept("La pause est finie : on s'y remet !");
            if (pauseMusicDuringBreaks && player.getCurrent() != null && !player.isPlaying()) player.togglePlay();
            begin(Phase.WORK);
        }
    }

    public IntegerProperty workMinutesProperty() { return workMinutes; }
    public IntegerProperty breakMinutesProperty() { return breakMinutes; }
    public ObjectProperty<Phase> phaseProperty() { return phase; }
    public ReadOnlyLongProperty remainingProperty() { return remaining.getReadOnlyProperty(); }
    public ReadOnlyLongProperty totalProperty() { return total.getReadOnlyProperty(); }
    public ReadOnlyBooleanProperty runningProperty() { return running.getReadOnlyProperty(); }
    public IntegerProperty completedTodayProperty() { return completedToday; }
}
