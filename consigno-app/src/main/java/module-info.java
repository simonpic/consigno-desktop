module consigno.desktop {

    // JavaFX
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;

    // AtlantaFX (thème UI)
    requires atlantafx.base;

    // Google Guice (DI) — fournit transitivement jakarta.inject
    requires com.google.guice;
    requires jakarta.inject;

    // Logging
    requires org.slf4j;

    // Modules internes
    requires consigno.common;
    requires consigno.crypto;
    requires consigno.pdf;

    // ─── Packages ouverts pour JavaFX (FXMLLoader) et Guice (réflexion) ───

    // Bootstrap de l'application
    opens com.consigno.desktop.app to javafx.graphics, javafx.fxml, com.google.guice;

    // Contrôleurs FXML : ouverts à FXMLLoader (injection @FXML) et Guice (construction)
    opens com.consigno.desktop.controller to javafx.fxml, com.google.guice;

    // Services UI (NotificationService, etc.)
    opens com.consigno.desktop.service to com.google.guice;

    // Composants vue PDF : ouverts à Guice (injection @Inject)
    opens com.consigno.desktop.view.pdf to com.google.guice;

    // Explorateur de fichiers PDF
    opens com.consigno.desktop.view.filesystem to com.google.guice, javafx.fxml;
}
