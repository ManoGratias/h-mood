package com.hmood.controller;

import com.hmood.ai.AiAssistantService.AssistantReply;
import com.hmood.app.AppContext;
import com.hmood.model.Mood;
import com.hmood.model.Track;
import com.hmood.ui.Icons;
import com.hmood.ui.Ui;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Panneau de droite « Assistant IA » : suggestions selon l'humeur + échange avec le DJ. */
public final class AiPanelController {

    private final AppContext ctx;
    private final VBox suggestions = new VBox(2);
    private final Label moodLabel = Ui.wrapped("", "panel-mood");
    private final VBox conversation = new VBox(10);
    private final TextField input = new TextField();

    public AiPanelController(VBox root, AppContext ctx) {
        this.ctx = ctx;

        HBox header = new HBox(8, Icons.icon(Icons.SPARKLE, 18), Ui.label("Assistant IA", "panel-title"));
        header.getStyleClass().add("panel-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Label status = ctx.assistant().isExternalConfigured()
                ? Ui.pill(ctx.assistant().statusText())
                : Ui.warnPill(ctx.assistant().statusText());

        VBox moodSection = new VBox(8, Ui.label("POUR TON HUMEUR", "section-label"), moodLabel, suggestions);

        FlowPane examples = new FlowPane(6, 6);
        for (String example : List.of("J'ai besoin de motivation", "Un son pour un jour de pluie", "Surprends-moi")) {
            Button chip = new Button(example);
            chip.getStyleClass().add("chip-button");
            chip.setOnAction(e -> send(example));
            examples.getChildren().add(chip);
        }

        ScrollPane scroll = new ScrollPane(conversation);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("chat-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        conversation.heightProperty().addListener((o, a, b) -> scroll.setVvalue(1.0));

        input.setPromptText("Ex. j'ai besoin de motivation");
        input.getStyleClass().add("chat-input");
        input.setOnAction(e -> send(input.getText()));
        HBox.setHgrow(input, Priority.ALWAYS);
        Button sendButton = new Button(null, Icons.icon(Icons.SEND, 16));
        sendButton.getStyleClass().add("send-button");
        sendButton.setOnAction(e -> send(input.getText()));
        HBox inputRow = new HBox(8, input, sendButton);
        inputRow.setAlignment(Pos.CENTER);

        root.getChildren().setAll(header, status, moodSection, new Separator(),
                Ui.label("DEMANDE AU DJ", "section-label"), examples, scroll, inputRow);

        ctx.currentMoodProperty().addListener((o, a, m) -> refreshSuggestions());
        ctx.libraryRevisionProperty().addListener((o, a, b) -> refreshSuggestions());
        refreshSuggestions();
        addAssistantBubble("Salut ! Dis-moi ce que tu ressens ou ce dont tu as besoin : je pioche dans ta bibliothèque.", List.of());
    }

    private void refreshSuggestions() {
        Mood mood = ctx.getCurrentMood();
        suggestions.getChildren().clear();
        if (mood == null) {
            moodLabel.setText("Aucune humeur choisie aujourd'hui.");
            Button open = new Button("Ouvrir le Scanner d'humeur");
            open.getStyleClass().add("secondary-button");
            open.setOnAction(e -> ctx.navigator().navigate(Page.MOOD_SCANNER));
            suggestions.getChildren().add(open);
            return;
        }
        moodLabel.setText(mood.label() + " — " + mood.tagline());
        try {
            List<Track> tracks = ctx.reco().forMood(mood, 5);
            if (tracks.isEmpty()) {
                suggestions.getChildren().add(Ui.muted("Aucun morceau associé à cette humeur pour l'instant. "
                        + "Dans Ma musique : clic droit › Humeur."));
            }
            for (int i = 0; i < tracks.size(); i++) {
                int index = i;
                suggestions.getChildren().add(Ui.trackRow(tracks.get(i), () -> ctx.player().play(tracks, index), 36));
            }
        } catch (RuntimeException e) {
            suggestions.getChildren().add(Ui.muted("Suggestions indisponibles : " + e.getMessage()));
        }
    }

    private void send(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        input.clear();
        Label bubble = Ui.wrapped(text, "bubble-user");
        HBox line = new HBox(Ui.hSpacer(), bubble);
        conversation.getChildren().add(line);
        CompletableFuture.supplyAsync(() -> ctx.assistant().ask(text))
                .whenComplete((reply, error) -> Platform.runLater(() -> {
                    if (error != null) {
                        addAssistantBubble("Erreur : " + error.getMessage(), List.of());
                    } else {
                        showReply(reply);
                    }
                }));
    }

    private void showReply(AssistantReply reply) {
        addAssistantBubble(reply.message(), reply.tracks());
    }

    private void addAssistantBubble(String text, List<Track> tracks) {
        VBox bubble = new VBox(8, Ui.wrapped(text, "bubble-text"));
        bubble.getStyleClass().add("bubble-ai");
        for (int i = 0; i < Math.min(4, tracks.size()); i++) {
            int index = i;
            bubble.getChildren().add(Ui.trackRow(tracks.get(i), () -> ctx.player().play(tracks, index), 30));
        }
        if (!tracks.isEmpty()) {
            Button playAll = new Button("Tout lire (" + tracks.size() + ")");
            playAll.getStyleClass().add("primary-button");
            playAll.setOnAction(e -> ctx.player().play(tracks, 0));
            bubble.getChildren().add(playAll);
        }
        conversation.getChildren().add(bubble);
        Ui.animateIn(bubble);
    }
}
