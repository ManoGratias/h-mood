package com.hmood.controller;

/** Navigation entre les pages de la zone centrale. */
public interface Navigator {

    default void navigate(Page page) {
        navigate(page, null);
    }

    /** @param argument donnée transmise à la page (texte de recherche, id de playlist…) */
    void navigate(Page page, Object argument);
}
