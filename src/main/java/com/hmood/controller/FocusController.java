package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.music.service.AmbienceService.Ambience;
import com.hmood.service.FocusTimerService;
import com.hmood.service.FocusTimerService.Phase;
import com.hmood.ui.Ui;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.StrokeLineCap;

import java.util.List;

/** Mode Concentration : minuteur Pomodoro + ambiances. */
public final class FocusController implements PageController {

    @FXML private StackPane ringBox;
    @FXML private Label phaseLabel;
    @FXML private FlowPane workChips;
    @FXML private FlowPane breakChips;
    @FXML private Button startButton;
    @FXML private CheckBox pauseMusic;
    @FXML private Label countLabel;
    @FXML private FlowPane sources;
    @FXML private VBox musicBox;

    private final AppContext ctx;
    private final Arc progress = new Arc(0, 0, 110, 110, 90, 0);
    private final Label time = Ui.label("", "focus-time");
    private final ChangeListener<Object> refresh = (o, a, b) -> update();

    public FocusController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        FocusTimerService timer = ctx.focusTimer();
        Circle track = new Circle(110);
        track.getStyleClass().add("focus-track");
        progress.setType(ArcType.OPEN);
        progress.getStyleClass().add("focus-arc");
        progress.setStrokeLineCap(StrokeLineCap.ROUND);
        ringBox.getChildren().addAll(track, progress, time);

        chips(workChips, List.of(15, 25, 45, 50), timer.workMinutesProperty().get(), m -> timer.workMinutesProperty().set(m));
        chips(breakChips, List.of(5, 10, 15), timer.breakMinutesProperty().get(), m -> timer.breakMinutesProperty().set(m));
        pauseMusic.selectedProperty().addListener((o, a, v) -> timer.setPauseMusicDuringBreaks(v));

        WeakChangeListener<Object> weak = new WeakChangeListener<>(refresh);
        timer.remainingProperty().addListener(weak);
        timer.phaseProperty().addListener(weak);
        timer.runningProperty().addListener(weak);
        timer.completedTodayProperty().addListener(weak);
        update();

        new AmbienceLoader(ctx, musicBox).fill(sources,
                List.of(Ambience.LOFI, Ambience.AMBIENT, Ambience.PIANO, Ambience.INSTRUMENTAL, Ambience.RAIN),
                Mood.FOCUSED, "Ma bibliothèque · Concentré",
                new String[]{"#45BFEF", "#1D4E89", "#8E54E9", "#3A8D99", "#5B7BB4"});
    }

    private static void chips(FlowPane pane, List<Integer> values, int selected, java.util.function.IntConsumer onSelect) {
        ToggleGroup group = new ToggleGroup();
        for (int v : values) {
            ToggleButton chip = new ToggleButton(v + " min");
            chip.getStyleClass().add("chip");
            chip.setToggleGroup(group);
            chip.setSelected(v == selected);
            chip.setOnAction(e -> {
                if (!chip.isSelected()) chip.setSelected(true);
                onSelect.accept(v);
            });
            pane.getChildren().add(chip);
        }
    }

    private void update() {
        FocusTimerService timer = ctx.focusTimer();
        long left = timer.remainingProperty().get();
        long total = Math.max(1, timer.totalProperty().get());
        time.setText(String.format("%02d:%02d", left / 60, left % 60));
        progress.setLength(-360.0 * (total - left) / total);
        Phase phase = timer.phaseProperty().get();
        phaseLabel.setText(phase == Phase.IDLE ? "Prêt à te concentrer ?" : phase.label + (timer.runningProperty().get() ? "" : " · en pause"));
        progress.getStyleClass().removeAll("focus-arc-break");
        if (phase == Phase.BREAK) progress.getStyleClass().add("focus-arc-break");
        startButton.setText(timer.runningProperty().get() ? "Pause" : phase == Phase.IDLE ? "Démarrer" : "Reprendre");
        countLabel.setText(timer.completedTodayProperty().get() + " session(s) de concentration terminée(s) aujourd'hui");
    }

    @FXML private void onStart() { ctx.focusTimer().startOrPause(); }
    @FXML private void onSkip() { ctx.focusTimer().skip(); }
    @FXML private void onReset() { ctx.focusTimer().reset(); }
}
