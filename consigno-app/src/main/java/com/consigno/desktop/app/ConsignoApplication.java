package com.consigno.desktop.app;

import atlantafx.base.theme.PrimerLight;
import com.google.inject.Guice;
import com.google.inject.Injector;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;

/**
 * Point d'entrée de ConsignO Desktop.
 *
 * <p>Initialise l'injecteur Guice dans l'ordre des dépendances :
 * {@link CryptoModule} → {@link PdfModule} → {@link AppModule}.
 *
 * <p>Le {@link FXMLLoader} utilise {@code injector::getInstance} comme
 * {@code ControllerFactory}, ce qui permet l'injection Guice dans tous
 * les contrôleurs FXML.
 */
public class ConsignoApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(ConsignoApplication.class);
    private static final String MAIN_FXML = "/fxml/main.fxml";

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        log.info("Démarrage de ConsignO Desktop");

        // 1. Polices — doivent être chargées avant la création des nœuds
        loadFonts();

        // 2. Thème AtlantaFX
        Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());

        // 3. Bootstrap Guice — ordre respecté : Crypto → PDF → App
        Injector injector = Guice.createInjector(
                new CryptoModule(),
                new PdfModule(),
                new AppModule()
        );

        // 4. Chargement du FXML principal avec factory Guice
        URL fxmlUrl = getClass().getResource(MAIN_FXML);
        if (fxmlUrl == null) {
            throw new IllegalStateException("FXML introuvable : " + MAIN_FXML);
        }

        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        loader.setControllerFactory(injector::getInstance);
        Parent root = loader.load();

        // 5. Scène + thème ConsignO par-dessus AtlantaFX
        Scene scene = new Scene(root, 1280, 800);
        URL cssUrl = getClass().getResource("/css/consigno.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        } else {
            log.warn("Feuille de style consigno.css introuvable");
        }

        primaryStage.setTitle("ConsignO Desktop");
        primaryStage.setMinWidth(900);
        primaryStage.setMinHeight(600);
        primaryStage.setScene(scene);
        primaryStage.show();

        log.info("ConsignO Desktop démarré");
    }

    private void loadFonts() {
        loadFont("/fonts/Outfit-Regular.ttf",   "Outfit Regular");
        loadFont("/fonts/Outfit-Medium.ttf",    "Outfit Medium");
        loadFont("/fonts/Outfit-SemiBold.ttf",  "Outfit SemiBold");
        loadFont("/fonts/IBMPlexMono-Regular.ttf", "IBM Plex Mono Regular");
    }

    private void loadFont(String resourcePath, String name) {
        var stream = getClass().getResourceAsStream(resourcePath);
        if (stream != null) {
            Font.loadFont(stream, 12);
            log.debug("Police chargée : {}", name);
        } else {
            log.warn("Police introuvable : {}", resourcePath);
        }
    }
}
