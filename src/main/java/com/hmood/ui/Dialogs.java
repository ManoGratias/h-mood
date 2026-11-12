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
}
