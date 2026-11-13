package com.hmood.controller;

import com.hmood.app.AppContext;
import com.hmood.projet4.Projet4Service;
import com.hmood.projet4.catalogue.Catalogue;
import com.hmood.projet4.ia.Playlist;
import com.hmood.projet4.ia.Recommandeur;
import com.hmood.projet4.modele.Auditeur;
import com.hmood.projet4.modele.Morceau;
import com.hmood.ui.Async;
import com.hmood.ui.Ui;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Page « Catalogue IA » de l'Auditeur : playlist selon l'humeur (Recommandeur), favoris et abonnements,
 * catalogue Jamendo par genre. Affichage uniquement : la logique est dans Projet4Service / Auditeur / Catalogue.
 */
public final class CatalogueIaController implements PageController {

    private static final int TAILLE_PLAYLIST = 10;
    private static final String TOUS = "Tous les genres";

    @FXML private VBox contenu;

    private final AppContext ctx;
    private final Projet4Service p4;
    private final VBox resultat = new VBox(8);
    private final VBox favoris = new VBox(10);
    private final VBox listeCatalogue = new VBox(6);
    private final ComboBox<String> genreBox = new ComboBox<>();
    private final TextField filtre = new TextField();
    private Label sousTitre;
    /** Dernière playlist affichée (redessinée quand un favori change). */
    private Playlist dernierePlaylist;
    private String derniereHumeur;

    public CatalogueIaController(AppContext ctx) {
        this.ctx = ctx;
        this.p4 = ctx.projet4();
    }

    @FXML
    private void initialize() {
        sousTitre = Ui.label("", "page-subtitle");
        contenu.getChildren().add(new VBox(2, Ui.label("Catalogue IA", "page-title"), sousTitre));
        if (!(p4.utilisateur() instanceof Auditeur)) {
            contenu.getChildren().add(Ui.label("Cette page est réservée aux comptes Auditeur.", "muted"));
            return;
        }
        contenu.getChildren().addAll(cartePlaylist(), carteFavoris(), carteCatalogue());
        rafraichir();
    }

    private Auditeur auditeur() {
        return (Auditeur) p4.utilisateur();
    }

    private Catalogue catalogue() {
        return p4.catalogue();
    }

    private void rafraichir() {
        sousTitre.setText(catalogue().taille() + " morceaux Jamendo dans " + catalogue().genres().size()
                + " genres · connecté en tant que " + auditeur().getIdentifiant());
        String choix = genreBox.getValue();
        genreBox.getItems().setAll(TOUS);
        genreBox.getItems().addAll(catalogue().genres());
        genreBox.setValue(choix != null && genreBox.getItems().contains(choix) ? choix : TOUS);
        afficherFavoris();
        afficherCatalogue();
    }

    // ------------------------------------------------------------------ playlist selon l'humeur

    private VBox cartePlaylist() {
        TextField humeur = new TextField();
        humeur.setPromptText("Ton humeur : triste, motivé pour courir, nostalgique, amoureux…");
        humeur.getStyleClass().add("search-field");
        HBox.setHgrow(humeur, javafx.scene.layout.Priority.ALWAYS);
        ComboBox<String> mode = new ComboBox<>();
        mode.getItems().addAll("IA (Groq)", "Sans réseau");
        mode.setValue(p4.iaDisponible() ? "IA (Groq)" : "Sans réseau");
        Button proposer = new Button("Proposer une playlist");
        proposer.getStyleClass().add("primary-button");
        proposer.setMnemonicParsing(false);
        Runnable lancer = () -> proposer(humeur.getText(), "IA (Groq)".equals(mode.getValue()), proposer);
        proposer.setOnAction(e -> lancer.run());
        humeur.setOnAction(e -> lancer.run());
        HBox saisie = new HBox(10, humeur, mode, proposer);
        saisie.setAlignment(Pos.CENTER_LEFT);
        Label aide = Ui.label(p4.iaDisponible()
                ? "L'IA choisit uniquement dans le catalogue ; ce qu'elle invente est rejeté et affiché."
                : "Clé IA absente (variable AI_API_KEY) : le mode « Sans réseau » est utilisé.", "card-subtitle");
        aide.setWrapText(true);
        VBox carte = new VBox(12, Ui.label("Playlist selon ton humeur", "card-title"), aide, saisie, resultat);
        carte.getStyleClass().add("card");
        return carte;
    }

    private void proposer(String humeur, boolean ia, Button bouton) {
        if (humeur == null || humeur.isBlank()) {
            resultat.getChildren().setAll(Ui.label("Écris d'abord ton humeur.", "p4-rejet"));
            return;
        }
        if (catalogue().estVide()) {
            resultat.getChildren().setAll(Ui.label("Le catalogue est vide : l'Admin doit le charger depuis Jamendo.", "p4-rejet"));
            return;
        }
        Recommandeur recommandeur;
        try {
            recommandeur = ia ? p4.recommandeurIa() : p4.recommandeurSansReseau();
        } catch (IllegalStateException e) {
            resultat.getChildren().setAll(Ui.label(e.getMessage(), "p4-rejet"));
            return;
        }
        bouton.setDisable(true);
        resultat.getChildren().setAll(Ui.label("Recherche en cours (" + recommandeur.nom() + ")…", "muted"));
        Async.run(() -> recommandeur.recommander(humeur, catalogue(), TAILLE_PLAYLIST), (playlist, erreur) -> {
            bouton.setDisable(false);
            if (erreur != null) {
                resultat.getChildren().setAll(Ui.label("Playlist impossible : " + erreur, "p4-rejet"));
                return;
            }
            afficherPlaylist(humeur, playlist);
        });
    }

    private void afficherPlaylist(String humeur, Playlist p) {
        dernierePlaylist = p;
        derniereHumeur = humeur;
        resultat.getChildren().clear();
        String entete = p.morceaux().size() + " morceau(x) pour « " + humeur.trim() + " » · " + p.source();
        Button toutLire = Projet4Ui.petitBouton("▶ Tout écouter", null, () -> Projet4Ui.lire(ctx, p.morceaux(), null));
        toutLire.setDisable(p.morceaux().isEmpty());
        HBox haut = new HBox(10, Ui.label(entete, "card-text"), Ui.hSpacer(), toutLire);
        haut.setAlignment(Pos.CENTER_LEFT);
        resultat.getChildren().add(haut);
        for (Morceau m : p.morceaux()) {
            resultat.getChildren().add(Projet4Ui.ligne(ctx, m, p.morceaux(), boutonFavori(m)));
        }
        if (!p.rejetes().isEmpty()) {
            Label rejet = Ui.label("Rejeté(s) car absent(s) du catalogue (" + p.rejetes().size() + ") : "
                    + String.join(" ; ", p.rejetes()), "p4-rejet");
            rejet.setWrapText(true);
            resultat.getChildren().add(rejet);
        }
        if (p.morceaux().isEmpty()) {
            resultat.getChildren().add(Ui.label("Aucun morceau du catalogue n'a été retenu.", "muted"));
        }
    }

    // ------------------------------------------------------------------ favoris et abonnements

    private VBox carteFavoris() {
        VBox carte = new VBox(12, Ui.label("Mes favoris et abonnements", "card-title"), favoris);
        carte.getStyleClass().add("card");
        return carte;
    }

    private void afficherFavoris() {
        favoris.getChildren().clear();
        Auditeur a = auditeur();
        List<Morceau> morceaux = new ArrayList<>();
        for (String id : a.getMorceauxFavoris()) catalogue().trouverParId(id).ifPresent(morceaux::add);
        favoris.getChildren().add(Ui.label("Morceaux favoris (" + morceaux.size() + ")", "card-subtitle"));
        if (morceaux.isEmpty()) favoris.getChildren().add(Ui.label("Clique sur ♡ à côté d'un morceau.", "muted"));
        for (Morceau m : morceaux) favoris.getChildren().add(Projet4Ui.ligne(ctx, m, morceaux, boutonFavori(m)));

        favoris.getChildren().add(Ui.label("Artistes favoris (" + a.getArtistesFavoris().size() + ")", "card-subtitle"));
        favoris.getChildren().add(puces(a.getArtistesFavoris(), artiste -> basculer(() -> a.basculerArtisteFavori(artiste))));

        favoris.getChildren().add(Ui.label("Abonnements (" + a.getAbonnements().size() + ")", "card-subtitle"));
        favoris.getChildren().add(puces(a.getAbonnements(), artiste -> basculer(() -> a.basculerAbonnement(artiste))));
        List<Morceau> fil = new ArrayList<>();
        for (String artiste : a.getAbonnements()) fil.addAll(catalogue().morceauxDe(artiste));
        if (!fil.isEmpty()) {
            favoris.getChildren().add(Ui.label("Dans le catalogue, de tes abonnements (" + fil.size() + ")", "card-subtitle"));
            for (Morceau m : fil) favoris.getChildren().add(Projet4Ui.ligne(ctx, m, fil, boutonFavori(m)));
        }
    }

    /** Une puce par artiste ; clic = retirer. */
    private FlowPane puces(java.util.Set<String> artistes, java.util.function.Consumer<String> retirer) {
        FlowPane box = new FlowPane(8, 8);
        if (artistes.isEmpty()) box.getChildren().add(Ui.label("Aucun pour l'instant.", "muted"));
        for (String artiste : artistes) {
            box.getChildren().add(Projet4Ui.petitBouton(artiste + "  ✕", "Retirer", () -> retirer.accept(artiste)));
        }
        return box;
    }

    // ------------------------------------------------------------------ catalogue par genre

    private VBox carteCatalogue() {
        filtre.setPromptText("Filtrer par titre ou artiste…");
        filtre.getStyleClass().add("search-field");
        HBox.setHgrow(filtre, javafx.scene.layout.Priority.ALWAYS);
        filtre.textProperty().addListener((o, a, b) -> afficherCatalogue());
        genreBox.valueProperty().addListener((o, a, b) -> afficherCatalogue());
        HBox outils = new HBox(10, genreBox, filtre);
        outils.setAlignment(Pos.CENTER_LEFT);
        VBox carte = new VBox(12, Ui.label("Catalogue par genre", "card-title"),
                Ui.label("♡ morceau favori · ★ artiste favori · + s'abonner à l'artiste", "card-subtitle"), outils, listeCatalogue);
        carte.getStyleClass().add("card");
        return carte;
    }

    private void afficherCatalogue() {
        listeCatalogue.getChildren().clear();
        if (catalogue().estVide()) {
            listeCatalogue.getChildren().add(Ui.label("Catalogue vide : l'Admin doit le charger depuis Jamendo (page Administration).", "muted"));
            return;
        }
        String genre = genreBox.getValue();
        List<Morceau> morceaux = genre == null || TOUS.equals(genre) ? catalogue().tous() : catalogue().morceauxDuGenre(genre);
        String f = filtre.getText() == null ? "" : filtre.getText().trim().toLowerCase();
        List<Morceau> visibles = morceaux.stream()
                .filter(m -> f.isEmpty() || m.getTitre().toLowerCase().contains(f) || m.getArtiste().toLowerCase().contains(f))
                .toList();
        for (Morceau m : visibles) {
            listeCatalogue.getChildren().add(Projet4Ui.ligne(ctx, m, visibles,
                    boutonFavori(m), boutonArtiste(m.getArtiste()), boutonAbonnement(m.getArtiste())));
        }
        if (visibles.isEmpty()) listeCatalogue.getChildren().add(Ui.label("Aucun morceau ne correspond.", "muted"));
    }

    // ------------------------------------------------------------------ boutons d'action

    private Button boutonFavori(Morceau m) {
        boolean favori = auditeur().aEnFavori(m);
        return Projet4Ui.petitBouton(favori ? "♥" : "♡", favori ? "Retirer des favoris" : "Ajouter aux favoris",
                () -> basculer(() -> auditeur().basculerMorceauFavori(m)));
    }

    private Button boutonArtiste(String artiste) {
        boolean favori = auditeur().aArtisteEnFavori(artiste);
        return Projet4Ui.petitBouton(favori ? "★" : "☆", favori ? "Retirer l'artiste des favoris" : "Artiste en favori",
                () -> basculer(() -> auditeur().basculerArtisteFavori(artiste)));
    }

    private Button boutonAbonnement(String artiste) {
        boolean abonne = auditeur().estAbonne(artiste);
        return Projet4Ui.petitBouton(abonne ? "Abonné" : "+ S'abonner", abonne ? "Se désabonner" : "S'abonner à " + artiste,
                () -> basculer(() -> auditeur().basculerAbonnement(artiste)));
    }

    /** Applique un changement de favoris/abonnements, sauvegarde, puis redessine. */
    private void basculer(Runnable changement) {
        changement.run();
        try {
            p4.sauvegarder();
        } catch (IOException e) {
            ctx.notify("Sauvegarde impossible : " + e.getMessage());
        }
        afficherFavoris();
        afficherCatalogue();
        if (dernierePlaylist != null) afficherPlaylist(derniereHumeur, dernierePlaylist);
    }
}
