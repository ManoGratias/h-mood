package com.hmood.ui;

import com.hmood.app.AppContext;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

/** Boîtes de dialogue stylées au thème courant. */
public final class Dialogs {

    private Dialogs() {
    }

    public static void prepare(Dialog<?> dialog, AppContext ctx) {
        ctx.themes().styleDialog(dialog);
        if (ctx.primaryStage() != null && dialog.getOwner() == null) {
            dialog.initOwner(ctx.primaryStage());
        }
    }

    public static boolean confirm(AppContext ctx, String header, String text) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, text, ButtonType.OK, ButtonType.CANCEL);
        alert.setTitle("H MOOD");
        alert.setHeaderText(header);
        prepare(alert, ctx);
        return alert.showAndWait().filter(ButtonType.OK::equals).isPresent();
    }

    public static Optional<String> askText(AppContext ctx, String header, String label, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial);
        dialog.setTitle("H MOOD");
        dialog.setHeaderText(header);
        dialog.setContentText(label);
        prepare(dialog, ctx);
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    /** Titre et artiste d'un morceau : renvoie {titre, artiste} (artiste vide = inconnu). */
    public static Optional<String[]> askTrackInfo(AppContext ctx, String title, String artist) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("H MOOD");
        dialog.setHeaderText("Modifier le titre et l'artiste");
        javafx.scene.control.TextField titleField = new javafx.scene.control.TextField(title);
        javafx.scene.control.TextField artistField = new javafx.scene.control.TextField(artist);
        artistField.setPromptText("Artiste inconnu");
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.addRow(0, new javafx.scene.control.Label("Titre :"), titleField);
        grid.addRow(1, new javafx.scene.control.Label("Artiste :"), artistField);
        javafx.scene.control.Label note = new javafx.scene.control.Label("Modifié dans H MOOD uniquement : le fichier n'est pas touché.");
        note.getStyleClass().add("muted");
        grid.add(note, 0, 2, 2, 1);
        titleField.setPrefColumnCount(28);
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        prepare(dialog, ctx);
        javafx.application.Platform.runLater(artistField::requestFocus);
        return dialog.showAndWait().filter(b -> b == ButtonType.OK)
                .filter(b -> !titleField.getText().isBlank())
                .map(b -> new String[]{titleField.getText().trim(), artistField.getText().trim()});
    }
}
