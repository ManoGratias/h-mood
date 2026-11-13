package com.hmood.ui;

import com.hmood.projet4.Projet4Service;
import com.hmood.projet4.compte.ComptesService;
import com.hmood.projet4.modele.Utilisateur;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Écran affiché au lancement : connexion à un compte existant ou inscription.
 * Il n'affiche que des messages ; toutes les vérifications sont faites par le Projet4Service.
 */
public final class ConnexionView extends StackPane {

    private final Projet4Service service;
    private final Consumer<Utilisateur> onConnecte;
    private final ToggleButton connexionTab = new ToggleButton("Connexion");
    private final ToggleButton inscriptionTab = new ToggleButton("Inscription");
    private final TextField identifiant = new TextField();
    private final PasswordField motDePasse = new PasswordField();
    private final PasswordField confirmation = new PasswordField();
    private final Label erreur = new Label();
    private final Label info = new Label();
    private final Button valider = new Button();

    public ConnexionView(Projet4Service service, Consumer<Utilisateur> onConnecte) {
        this.service = service;
        this.onConnecte = onConnecte;
        getStyleClass().add("connexion-root");

        Label titre = Ui.label("H MOOD", "connexion-title");
        Label slogan = Ui.label("Ressens ton humeur. Vis ta musique.", "brand-slogan");
        VBox marque = new VBox(10, Logo.create(64), titre, slogan);
        marque.setAlignment(Pos.CENTER);

        ToggleGroup onglets = new ToggleGroup();
        for (ToggleButton t : new ToggleButton[]{connexionTab, inscriptionTab}) {
            t.setToggleGroup(onglets);
            t.getStyleClass().add("chip");
            t.setMnemonicParsing(false);
        }
        HBox ongletsBox = new HBox(8, connexionTab, inscriptionTab);
        ongletsBox.setAlignment(Pos.CENTER);
        onglets.selectedToggleProperty().addListener((o, a, b) -> {
            if (b == null) {
                a.setSelected(true);
                return;
            }
            mettreAJour();
        });

        identifiant.setPromptText("Identifiant");
        motDePasse.setPromptText("Mot de passe");
        confirmation.setPromptText("Confirme le mot de passe");
        for (TextField f : new TextField[]{identifiant, motDePasse, confirmation}) {
            f.getStyleClass().add("search-field");
            f.setOnAction(e -> valider());
        }
        erreur.getStyleClass().add("connexion-error");
        erreur.setWrapText(true);
        info.getStyleClass().add("muted");
        info.setWrapText(true);
        valider.getStyleClass().add("primary-button");
        valider.setMaxWidth(Double.MAX_VALUE);
        valider.setMnemonicParsing(false);
        valider.setOnAction(e -> valider());

        VBox carte = new VBox(14, marque, ongletsBox, identifiant, motDePasse, confirmation, erreur, valider, info);
        carte.getStyleClass().addAll("card", "connexion-card");
        carte.setMaxWidth(420);
        carte.setMaxHeight(USE_PREF_SIZE);
        carte.setPadding(new Insets(34, 34, 28, 34));
        getChildren().add(carte);

        if (service.getChargementEchoue() != null) {
            erreur.setText(service.getChargementEchoue());
        }
        (service.aucunCompte() ? inscriptionTab : connexionTab).setSelected(true);
        mettreAJour();
        Platform.runLater(identifiant::requestFocus);
    }

    private boolean inscription() {
        return inscriptionTab.isSelected();
    }

    private void mettreAJour() {
        boolean inscr = inscription();
        confirmation.setVisible(inscr);
        confirmation.setManaged(inscr);
        valider.setText(inscr ? "Créer mon compte" : "Se connecter");
        if (!inscr) {
            info.setText("");
        } else if (service.aucunCompte()) {
            info.setText("Aucun compte pour l'instant : le premier compte créé sera l'Admin (gestion du catalogue). "
                    + "Les comptes suivants seront des Auditeurs.");
        } else {
            info.setText("Ton compte sera un compte Auditeur. Mot de passe : au moins "
                    + ComptesService.LONGUEUR_MIN_MOT_DE_PASSE + " caractères.");
        }
        if (service.getChargementEchoue() == null) erreur.setText("");
    }

    private void valider() {
        erreur.setText("");
        try {
            Utilisateur u = inscription()
                    ? service.inscrire(identifiant.getText(), motDePasse.getText(), confirmation.getText())
                    : service.connecter(identifiant.getText(), motDePasse.getText());
            motDePasse.clear();
            confirmation.clear();
            onConnecte.accept(u);
        } catch (IllegalArgumentException e) {
            erreur.setText(e.getMessage());
        } catch (IOException e) {
            erreur.setText("Compte créé mais non sauvegardé : " + e.getMessage());
        }
    }

    /** Remet l'écran à zéro (après une déconnexion). */
    public void reinitialiser() {
        identifiant.clear();
        motDePasse.clear();
        confirmation.clear();
        erreur.setText("");
        connexionTab.setSelected(true);
        mettreAJour();
        Platform.runLater(identifiant::requestFocus);
    }
}
