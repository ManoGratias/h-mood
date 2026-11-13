package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.projet4.Projet4Service;
import com.hmood.projet4.catalogue.Catalogue;
import com.hmood.projet4.modele.Admin;
import com.hmood.projet4.modele.Morceau;
import com.hmood.ui.Async;
import com.hmood.ui.Dialogs;
import com.hmood.ui.Ui;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;

/** Page « Administration » de l'Admin : charger le catalogue depuis Jamendo, ajouter et supprimer des morceaux. */
public final class AdminController implements PageController {

    private static final String TOUS = "Tous les genres";

    @FXML private VBox contenu;

    private final AppContext ctx;
    private final Projet4Service p4;
    private final VBox liste = new VBox(6);
    private final ComboBox<String> genreBox = new ComboBox<>();
    private final TextField filtre = new TextField();
    private final Label message = new Label();
    private Label sousTitre;

    public AdminController(AppContext ctx) {
        this.ctx = ctx;
        this.p4 = ctx.projet4();
    }

    @FXML
    private void initialize() {
        sousTitre = Ui.label("", "page-subtitle");
        contenu.getChildren().add(new VBox(2, Ui.label("Administration du catalogue", "page-title"), sousTitre));
        if (!(p4.utilisateur() instanceof Admin)) {
            contenu.getChildren().add(Ui.label("Cette page est réservée à l'Admin.", "muted"));
            return;
        }
        message.getStyleClass().add("card-text");
        message.setWrapText(true);
        contenu.getChildren().addAll(carteJamendo(), carteAjout(), carteCatalogue());
        rafraichir();
    }

    private Catalogue catalogue() {
        return p4.catalogue();
    }

    private void rafraichir() {
        sousTitre.setText(catalogue().taille() + " morceaux dans " + catalogue().genres().size()
                + " genres · sauvegardé dans " + p4.fichierSauvegarde().getFileName());
        String choix = genreBox.getValue();
        genreBox.getItems().setAll(TOUS);
        genreBox.getItems().addAll(catalogue().genres());
        genreBox.setValue(choix != null && genreBox.getItems().contains(choix) ? choix : TOUS);
        afficherListe();
    }

    // ------------------------------------------------------------------ Jamendo

    private VBox carteJamendo() {
        Button charger = new Button("Charger / compléter depuis Jamendo");
        charger.getStyleClass().add("primary-button");
        charger.setMnemonicParsing(false);
        charger.setOnAction(e -> {
            if (!p4.jamendoDisponible()) {
                message.setText("Jamendo n'est pas configuré : renseigne ton client_id dans Paramètres.");
                return;
            }
            charger.setDisable(true);
            message.setText("Chargement depuis Jamendo…");
            Async.run(p4::chargerDepuisJamendo, (ajoutes, erreur) -> {
                charger.setDisable(false);
                message.setText(erreur != null ? "Chargement impossible : " + erreur
                        : ajoutes + " morceau(x) ajouté(s) depuis Jamendo.");
                rafraichir();
            });
        });
        VBox carte = new VBox(12, Ui.label("Catalogue Jamendo", "card-title"),
                Ui.label("Ajoute les morceaux populaires de " + com.hmood.projet4.catalogue.ChargeurJamendo.GENRES.size()
                        + " genres (musique libre, écoutable en entier). Les morceaux déjà présents sont ignorés.", "card-subtitle"),
                charger, message);
        carte.getStyleClass().add("card");
        return carte;
    }

    // ------------------------------------------------------------------ ajout manuel

    private VBox carteAjout() {
        TextField titre = champ("Titre");
        TextField artiste = champ("Artiste");
        ComboBox<String> genre = new ComboBox<>();
        genre.setEditable(true);
        genre.setPromptText("Genre");
        genre.getItems().setAll(com.hmood.projet4.catalogue.ChargeurJamendo.GENRES);
        Button ajouter = new Button("Ajouter");
        ajouter.getStyleClass().add("primary-button");
        ajouter.setMnemonicParsing(false);
        ajouter.setOnAction(e -> {
            try {
                Morceau m = p4.ajouterMorceau(titre.getText(), artiste.getText(), genre.getEditor().getText());
                message.setText("Ajouté : " + m);
                titre.clear();
                artiste.clear();
                rafraichir();
            } catch (IllegalArgumentException ex) {
                message.setText(ex.getMessage());
            } catch (IOException ex) {
                message.setText("Ajouté mais non sauvegardé : " + ex.getMessage());
            }
        });
        HBox ligne = new HBox(10, titre, artiste, genre, ajouter);
        ligne.setAlignment(Pos.CENTER_LEFT);
        VBox carte = new VBox(12, Ui.label("Ajouter un morceau", "card-title"),
                Ui.label("Un morceau ajouté à la main n'a pas de fichier audio : il sert au catalogue et aux playlists.", "card-subtitle"),
                ligne);
        carte.getStyleClass().add("card");
        return carte;
    }

    private static TextField champ(String invite) {
        TextField f = new TextField();
        f.setPromptText(invite);
        f.getStyleClass().add("search-field");
        HBox.setHgrow(f, Priority.ALWAYS);
        return f;
    }

    // ------------------------------------------------------------------ liste et suppression

    private VBox carteCatalogue() {
        filtre.setPromptText("Filtrer par titre ou artiste…");
        filtre.getStyleClass().add("search-field");
        HBox.setHgrow(filtre, Priority.ALWAYS);
        filtre.textProperty().addListener((o, a, b) -> afficherListe());
        genreBox.valueProperty().addListener((o, a, b) -> afficherListe());
        HBox outils = new HBox(10, genreBox, filtre);
        outils.setAlignment(Pos.CENTER_LEFT);
        VBox carte = new VBox(12, Ui.label("Morceaux du catalogue", "card-title"), outils, liste);
        carte.getStyleClass().add("card");
        return carte;
    }

    private void afficherListe() {
        liste.getChildren().clear();
        if (catalogue().estVide()) {
            liste.getChildren().add(Ui.label("Catalogue vide : charge-le depuis Jamendo ou ajoute des morceaux.", "muted"));
            return;
        }
        String genre = genreBox.getValue();
        List<Morceau> morceaux = genre == null || TOUS.equals(genre) ? catalogue().tous() : catalogue().morceauxDuGenre(genre);
        String f = filtre.getText() == null ? "" : filtre.getText().trim().toLowerCase();
        List<Morceau> visibles = morceaux.stream()
                .filter(m -> f.isEmpty() || m.getTitre().toLowerCase().contains(f) || m.getArtiste().toLowerCase().contains(f))
                .toList();
        for (Morceau m : visibles) {
            Button supprimer = Projet4Ui.petitBouton("Supprimer", "Retirer ce morceau du catalogue", () -> supprimer(m));
            supprimer.getStyleClass().add("danger-button");
            liste.getChildren().add(Projet4Ui.ligne(ctx, m, visibles, supprimer));
        }
        if (visibles.isEmpty()) liste.getChildren().add(Ui.label("Aucun morceau ne correspond.", "muted"));
    }

    private void supprimer(Morceau m) {
        if (!Dialogs.confirm(ctx, "Supprimer « " + m.getTitre() + " » du catalogue ?",
                "Il disparaîtra aussi des playlists proposées. Les favoris qui le visent seront ignorés.")) {
            return;
        }
        try {
            p4.supprimerMorceau(m.getId());
            message.setText("Supprimé : " + m);
        } catch (IOException e) {
            message.setText("Supprimé mais non sauvegardé : " + e.getMessage());
        }
        rafraichir();
    }
}
