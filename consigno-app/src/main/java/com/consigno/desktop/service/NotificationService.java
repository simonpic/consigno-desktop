package com.consigno.desktop.service;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service centralisé de notifications utilisateur via AtlantaFX.
 *
 * <p>Toute erreur métier remontée à l'utilisateur passe par ce service —
 * jamais par une {@code Alert} JavaFX standard ni par {@code System.out}.
 *
 * <p>Toutes les méthodes sont thread-safe : elles wrappent l'affichage
 * dans {@code Platform.runLater()} si nécessaire.
 *
 * <p>Appeler {@link #setOwner(StackPane)} dans {@code MainController.initialize()}
 * avant toute notification.
 */
@Singleton
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final Duration ENTER_DURATION   = Duration.millis(220);
    private static final Duration DISMISS_DURATION = Duration.millis(180);
    private static final Duration AUTO_DISMISS     = Duration.seconds(4);

    private StackPane ownerPane;

    @Inject
    public NotificationService() {}

    /**
     * Définit le StackPane racine dans lequel les toasts sont affichés.
     * Doit être appelé avant toute notification.
     */
    public void setOwner(StackPane ownerPane) {
        this.ownerPane = ownerPane;
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    public void showSuccess(String message) {
        log.debug("Notification succès : {}", message);
        show(message, Styles.SUCCESS, "\u2713");
    }

    public void showError(String message, Throwable cause) {
        log.error(message, cause);
        String detail = cause != null && cause.getMessage() != null
                ? message + "\n" + cause.getMessage()
                : message;
        show(detail, Styles.DANGER, "\u2715");
    }

    public void showWarning(String message) {
        log.warn("Notification avertissement : {}", message);
        show(message, Styles.WARNING, "\u26a0");
    }

    // -------------------------------------------------------------------------
    // Affichage interne
    // -------------------------------------------------------------------------

    private void show(String message, String styleClass, String iconChar) {
        Platform.runLater(() -> {
            if (ownerPane == null) {
                log.warn("[UI] NotificationService : owner non défini — {}", message);
                return;
            }

            Label icon = new Label(iconChar);
            icon.setStyle("-fx-font-size: 14px;");

            Notification notification = new Notification(message, icon);
            notification.getStyleClass().addAll(styleClass, Styles.ELEVATED_1);
            notification.setPrefWidth(320);
            notification.setMaxWidth(320);
            notification.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

            StackPane.setAlignment(notification, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(notification, new Insets(0, 16, 16, 0));

            ownerPane.getChildren().add(notification);
            playEnter(notification);

            PauseTransition pause = new PauseTransition(AUTO_DISMISS);
            pause.setOnFinished(e -> dismiss(notification));
            pause.play();

            notification.setOnClose(e -> {
                pause.stop();
                dismiss(notification);
            });
        });
    }

    private void playEnter(Notification notification) {
        notification.setOpacity(0);
        notification.setTranslateY(16);

        FadeTransition fade = new FadeTransition(ENTER_DURATION, notification);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(ENTER_DURATION, notification);
        slide.setToY(0);

        new ParallelTransition(fade, slide).playFromStart();
    }

    private void dismiss(Notification notification) {
        FadeTransition fade = new FadeTransition(DISMISS_DURATION, notification);
        fade.setToValue(0);

        TranslateTransition slide = new TranslateTransition(DISMISS_DURATION, notification);
        slide.setToY(16);

        ParallelTransition out = new ParallelTransition(fade, slide);
        out.setOnFinished(e -> ownerPane.getChildren().remove(notification));
        out.playFromStart();
    }
}
