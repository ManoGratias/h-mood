package com.hmood.controller;

import com.hmood.ui.Icons;
import com.hmood.ui.Ui;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Page d'un module prévu : décrit honnêtement ce qui arrivera, sans rien simuler. */
public final class PlaceholderController implements PageController {

    @FXML private StackPane iconBox;
    @FXML private Label titleLabel;
    @FXML private Label descriptionLabel;
    @FXML private VBox featuresBox;
    @FXML private Label hintLabel;

    @Override
    public void onShow(Object argument) {
        if (!(argument instanceof Page page)) {
            return;
        }
        iconBox.getChildren().setAll(Icons.icon(page.icon(), 30));
        titleLabel.setText(page.label());
        descriptionLabel.setText(page.description());
        for (String feature : page.plannedFeatures()) {
            featuresBox.getChildren().add(Ui.wrapped("•  " + feature, "card-text"));
        }
        hintLabel.setText(page.hint() == null ? "" : page.hint());
        hintLabel.setVisible(page.hint() != null);
        hintLabel.setManaged(page.hint() != null);
    }
}
