package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.model.Theme;
import com.hmood.ui.Dialogs;
import com.hmood.util.AudioFormats;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;

import java.io.IOException;

/** Page Settings. */
public final class SettingsController implements PageController {

    @FXML private ToggleButton iceButton;
    @FXML private ToggleButton sunsetButton;
    @FXML private TextField nameField;
    @FXML private Label libraryInfo;
    @FXML private Slider volumeSlider;
    @FXML private Label volumeLabel;
    @FXML private Label formatsLabel;
    @FXML private Label dataInfo;
    @FXML private Label aiStatus;
    @FXML private Label aboutLabel;
    @FXML private TextField jamendoField;
    @FXML private javafx.scene.control.CheckBox mediaKeysBox;
    @FXML private Label mediaKeysStatus;
    @FXML private Label jamendoStatus;

    private final AppContext ctx;

    public SettingsController(AppContext ctx) {
        this.ctx = ctx;
    }

    @FXML
    private void initialize() {
        ToggleGroup themes = new ToggleGroup();
        iceButton.setToggleGroup(themes);
        sunsetButton.setToggleGroup(themes);
        iceButton.setUserData(Theme.ICE_BLUE);
        sunsetButton.setUserData(Theme.SUNSET_ORANGE);
        (ctx.themes().theme() == Theme.ICE_BLUE ? iceButton : sunsetButton).setSelected(true);
        themes.selectedToggleProperty().addListener((o, old, selected) -> {
            if (selected == null) {
                old.setSelected(true); // une option reste toujours sélectionnée
                return;
            }
            Theme theme = (Theme) selected.getUserData();
            ctx.themes().setTheme(theme);
            ctx.settings().setTheme(theme);
        });

        nameField.setText(ctx.settings().displayName());

        volumeSlider.valueProperty().bindBidirectional(ctx.player().volumeProperty());
        volumeLabel.textProperty().bind(volumeSlider.valueProperty().multiply(100).asString("%.0f %%"));
        volumeSlider.setOnMouseReleased(e -> ctx.settings().setVolume(volumeSlider.getValue()));
        formatsLabel.setText("Formats lus : " + AudioFormats.DESCRIPTION
                + ". FLAC et OGG ne sont pas pris en charge par le moteur audio JavaFX : ils sont ignorés à l'import.");

        refreshInfo();
        jamendoField.setText(ctx.settings().jamendoClientId());
        mediaKeysBox.setSelected(ctx.settings().globalMediaKeys() && ctx.mediaKeys().isRunning());
        mediaKeysBox.setDisable(!com.hmood.player.GlobalMediaKeys.supported());
        mediaKeysStatus.setText(!ctx.settings().globalMediaKeys() ? "Touches multimédia : seulement quand la fenêtre H MOOD est active."
                : ctx.mediaKeys().isRunning() && ctx.mediaKeys().failures().isEmpty()
                ? "Actives, même quand H MOOD est en arrière-plan. Le navigateur ne les reçoit plus tant que H MOOD est ouvert."
                : ctx.mediaKeys().failures().isEmpty() ? "Touches multimédia globales indisponibles."
                : "Actives sauf : " + String.join(", ", ctx.mediaKeys().failures()) + " (déjà utilisées par un autre programme).");
        mediaKeysBox.selectedProperty().addListener((o, a, on) -> mediaKeysStatus.setText(ctx.setGlobalMediaKeys(on)));
        jamendoStatus.setText(ctx.jamendo().configured()
                ? "Client ID enregistré. Clique sur « Enregistrer et tester » pour vérifier la connexion."
                : "Non configuré : Jamendo est ignoré (aucun morceau Jamendo n'est proposé).");
        aiStatus.setText(ctx.assistant().statusText());
        aboutLabel.setText("H MOOD 0.1.0 (MVP) — Ressens ton humeur. Vis ta musique.\n"
                + "Java " + System.getProperty("java.version") + " · JavaFX " + System.getProperty("javafx.runtime.version", "?")
                + " · SQLite local");
    }

    @FXML
    private void onSaveJamendo() {
        ctx.settings().setJamendoClientId(jamendoField.getText());
        if (!ctx.jamendo().configured()) {
            jamendoStatus.setText("Client ID effacé : Jamendo est désactivé.");
            return;
        }
        jamendoStatus.setText("Test de connexion à Jamendo…");
        com.hmood.ui.Async.run(() -> ctx.jamendo().chart("popular"), (list, error) -> {
            if (error != null) {
                jamendoStatus.setText("Échec : " + error);
            } else {
                jamendoStatus.setText("Connecté à Jamendo : " + list.size() + " morceaux complets reçus (ex. « "
                        + (list.isEmpty() ? "—" : list.get(0).title() + " » de " + list.get(0).artist())
                        + "). Jamendo est maintenant utilisé dans Découvrir, les humeurs, Concentration et Sommeil.");
            }
        });
    }

    @FXML
    private void onOpenJamendoPortal() {
        ctx.openLink(com.hmood.music.provider.JamendoProvider.DEV_PORTAL);
    }

    private void refreshInfo() {
        libraryInfo.setText(ctx.library().count() + " morceaux dans la bibliothèque. Retirer des morceaux ne supprime jamais les fichiers du disque.");
        dataInfo.setText("Base de données : " + ctx.database().file() + "\nPochettes : " + ctx.dataDir().resolve("covers"));
    }

    @FXML
    private void onSaveName() {
        try {
            ctx.settings().setDisplayName(nameField.getText());
            ctx.notify("Nom enregistré");
        } catch (RuntimeException e) {
            ctx.notify(e.getMessage());
        }
    }

    @FXML
    private void onOpenLibrary() {
        ctx.navigator().navigate(Page.MY_MUSIC);
    }

    @FXML
    private void onClearLibrary() {
        if (Dialogs.confirm(ctx, "Vider toute la bibliothèque ?",
                "Tous les morceaux seront retirés de H MOOD (ainsi que leur place dans les playlists, les favoris et l'historique). "
                        + "Les fichiers audio sur le disque ne sont pas touchés.")) {
            ctx.player().stop();
            ctx.library().clear();
            ctx.bumpLibrary();
            refreshInfo();
            ctx.notify("Bibliothèque vidée");
        }
    }

    @FXML
    private void onOpenDataFolder() {
        try {
            new ProcessBuilder("explorer.exe", ctx.dataDir().toString()).start();
        } catch (IOException e) {
            ctx.notify("Impossible d'ouvrir le dossier : " + e.getMessage());
        }
    }

    @FXML
    private void onClearHistory() {
        if (Dialogs.confirm(ctx, "Effacer l'historique ?",
                "L'historique d'écoute, les compteurs d'écoutes et l'historique des humeurs seront supprimés.")) {
            ctx.history().clear();
            ctx.setCurrentMood(null);
            ctx.bumpLibrary();
            ctx.notify("Historique effacé");
        }
    }
}
