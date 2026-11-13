package com.hmood.app;

import com.hmood.controller.MainController;
import com.hmood.ui.ConnexionView;
import com.hmood.ui.Logo;
import com.hmood.util.AppPaths;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Point d'entrée JavaFX de H MOOD. */
public class HMoodApp extends Application {

    private AppContext context;
    private ConnexionView connexion;
    private Parent main;
    private MainController mainController;

    @Override
    public void start(Stage stage) {
        try {
            context = new AppContext(AppPaths.dataDir());
            context.setPrimaryStage(stage);
            context.setHostServices(getHostServices());

            // Écran de connexion d'abord ; la fenêtre principale est construite après la première connexion,
            // pour que son menu corresponde au type de l'utilisateur (Auditeur ou Admin).
            StackPane root = new StackPane();
            connexion = new ConnexionView(context.projet4(), u -> ouvrirSession(root));
            root.getChildren().add(connexion);
            context.setDeconnexion(() -> {
                if (main != null) main.setVisible(false);
                connexion.reinitialiser();
                connexion.setVisible(true);
                connexion.toFront();
            });

            Scene scene = new Scene(root, 1440, 880);
            context.themes().register(scene);

            stage.setTitle("H MOOD — Ressens ton humeur. Vis ta musique.");
            stage.getIcons().addAll(Logo.renderIcon(32), Logo.renderIcon(64), Logo.renderIcon(256));
            stage.setMinWidth(1180);
            stage.setMinHeight(720);
            stage.setScene(scene);
            stage.show();
        } catch (Exception e) {
            showFatal(e);
        }
    }

    /** Après connexion : construit la fenêtre principale la première fois, sinon recharge son menu. */
    private void ouvrirSession(StackPane root) {
        try {
            if (main == null) {
                FXMLLoader loader = new FXMLLoader(HMoodApp.class.getResource("/com/hmood/fxml/MainView.fxml"));
                loader.setControllerFactory(context.controllerFactory());
                main = loader.load();
                mainController = loader.getController();
                root.getChildren().add(0, main);
            } else {
                mainController.ouvrirSession();
            }
            main.setVisible(true);
            connexion.setVisible(false);
        } catch (Exception e) {
            showFatal(e);
        }
    }

    @Override
    public void stop() {
        if (context != null) {
            context.shutdown();
        }
    }

    private static void showFatal(Exception e) {
        e.printStackTrace();
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("H MOOD");
        alert.setHeaderText("H MOOD n'a pas pu démarrer");
        alert.setContentText(e.getMessage());
        TextArea details = new TextArea(sw.toString());
        details.setEditable(false);
        alert.getDialogPane().setExpandableContent(details);
        alert.showAndWait();
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
