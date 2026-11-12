package com.hmood.controller;

/** Contrôleur d'une page de la zone centrale. */
public interface PageController {

    /** Appelé après le chargement FXML, avec l'argument de navigation éventuel. */
    default void onShow(Object argument) {
    }
}
