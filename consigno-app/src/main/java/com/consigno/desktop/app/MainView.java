package com.consigno.desktop.app;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Vue principale de l'application — placeholder en attendant l'interface complète.
 *
 * <p>Sera remplacée par le chargement d'un fichier {@code main.fxml}
 * avec {@code FXMLLoader} et {@code setControllerFactory(injector::getInstance)}.
 */
@Singleton
public class MainView extends StackPane {

    @Inject
    public MainView() {
        VBox content = new VBox(16);
        content.setAlignment(Pos.CENTER);

        Label title = new Label("ConsignO Desktop");
        title.setStyle("-fx-font-size: 28px; -fx-font-weight: bold;");

        Label subtitle = new Label("Signature électronique de documents PDF");
        subtitle.setStyle("-fx-font-size: 14px; -fx-text-fill: -color-fg-muted;");

        Label placeholder = new Label("Interface principale en cours de développement…");
        placeholder.setStyle("-fx-font-size: 12px; -fx-text-fill: -color-fg-subtle;");

        content.getChildren().addAll(title, subtitle, placeholder);
        setAlignment(Pos.CENTER);
        getChildren().add(content);
    }
}
