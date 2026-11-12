package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.music.service.AmbienceService.Ambience;
import com.hmood.player.SleepTimerService;
import com.hmood.ui.Ui;
import javafx.animation.AnimationTimer;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.Random;

/** Mode Sommeil : ciel étoilé animé, minuteur d'arrêt, ambiances douces. */
public final class SleepController implements PageController {

    @FXML private StackPane nightBox;
    @FXML private VBox nightContent;
    @FXML private Label countdown;
    @FXML private Label countdownHint;
    @FXML private FlowPane durations;
    @FXML private Button timerButton;
    @FXML private CheckBox gradual;
    @FXML private FlowPane sources;
    @FXML private VBox musicBox;

    private final AppContext ctx;
    private final ToggleGroup durationGroup = new ToggleGroup();
    private final ChangeListener<Number> refresh = (o, a, b) -> update();

    public SleepController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        Canvas sky = starrySky();
        nightBox.getChildren().add(0, sky);
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.setArcWidth(48);
        clip.setArcHeight(48);
        clip.widthProperty().bind(nightBox.widthProperty());
        clip.heightProperty().bind(nightBox.heightProperty());
        nightBox.setClip(clip);
        for (int m : List.of(15, 30, 45, 60, 90)) {
            ToggleButton chip = new ToggleButton(m + " min");
            chip.getStyleClass().addAll("chip", "night-chip");
            chip.setUserData(m);
            chip.setToggleGroup(durationGroup);
            chip.setSelected(m == 30);
            durations.getChildren().add(chip);
        }
        durationGroup.selectedToggleProperty().addListener((o, a, t) -> {
            if (t == null) a.setSelected(true);
        });
        ctx.sleepTimer().remainingSecondsProperty().addListener(new WeakChangeListener<>(refresh));
        update();
        new AmbienceLoader(ctx, musicBox).fill(sources,
                List.of(Ambience.SOFT, Ambience.RAIN, Ambience.NIGHT, Ambience.SLEEP_PIANO),
                Mood.DREAMY, "Ma bibliothèque · Rêveur",
                new String[]{"#1D2B53", "#5B7BB4", "#3A4556", "#7F8EA3"});
    }

    @FXML
    private void onTimer() {
        SleepTimerService timer = ctx.sleepTimer();
        if (timer.isRunning()) {
            timer.cancel();
            ctx.notify("Minuteur de sommeil annulé");
        } else {
            int minutes = (Integer) durationGroup.getSelectedToggle().getUserData();
            timer.start(minutes, gradual.isSelected());
            ctx.notify("Bonne nuit 🌙 La musique s'arrêtera dans " + minutes + " min.");
        }
        update();
    }

    private void update() {
        SleepTimerService timer = ctx.sleepTimer();
        long s = timer.remainingSecondsProperty().get();
        if (timer.isRunning()) {
            countdown.setText(String.format("%d:%02d", s / 60, s % 60));
            countdownHint.setText("avant l'arrêt automatique de la musique");
            timerButton.setText("Annuler le minuteur");
        } else {
            countdown.setText("☾");
            countdownHint.setText("Choisis une durée : la musique s'arrêtera toute seule, en douceur.");
            timerButton.setText("Démarrer le minuteur");
        }
    }

    /** Ciel nocturne : étoiles scintillantes et lune. */
    private Canvas starrySky() {
        Canvas canvas = new Canvas(10, 10);
        canvas.setManaged(false); // dessiné en fond, sans influencer la taille du cadre
        nightBox.widthProperty().addListener((o, a, w) -> canvas.setWidth(Math.max(10, Math.min(4000, w.doubleValue()))));
        nightBox.heightProperty().addListener((o, a, h) -> canvas.setHeight(Math.max(10, Math.min(2000, h.doubleValue()))));
        canvas.setMouseTransparent(true);
        Random random = new Random(7);
        int n = 140;
        double[] xs = new double[n], ys = new double[n], phases = new double[n], sizes = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = random.nextDouble();
            ys[i] = random.nextDouble();
            phases[i] = random.nextDouble() * Math.PI * 2;
            sizes[i] = 0.6 + random.nextDouble() * 1.8;
        }
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                double t = now / 1e9;
                GraphicsContext g = canvas.getGraphicsContext2D();
                double w = canvas.getWidth(), h = canvas.getHeight();
                if (w < 20 || h < 20) return;
                g.clearRect(0, 0, w, h);
                for (int i = 0; i < n; i++) {
                    double a = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(t * 1.3 + phases[i]));
                    g.setFill(Color.web("#DDF8FF", a));
                    g.fillOval(xs[i] * w, ys[i] * h, sizes[i], sizes[i]);
                }
                double mx = w * 0.85, my = h * 0.28;
                g.setFill(Color.web("#FFF3E8", 0.10));
                g.fillOval(mx - 60, my - 60, 120, 120);
                g.setFill(Color.web("#FFF3E8", 0.92));
                g.fillOval(mx - 30, my - 30, 60, 60);
                g.setFill(Color.web("#101F32"));
                g.fillOval(mx - 16, my - 36, 60, 60);
            }
        };
        canvas.sceneProperty().addListener((o, a, scene) -> {
            if (scene == null) timer.stop();
            else timer.start();
        });
        return canvas;
    }
}
