package com.consigno.desktop.controller.handler;

import com.consigno.desktop.service.NotificationService;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gère la suppression définitive d'un fichier PDF depuis le filesystem.
 * Affiche une confirmation avant d'agir. Appelé depuis le FX thread (menu contextuel).
 */
public final class PdfDeleteHandler {

    private static final Logger log = LoggerFactory.getLogger(PdfDeleteHandler.class);

    private final NotificationService notificationService;
    private final PdfSessionState     sessionState;
    private final UiControls          ui;

    public PdfDeleteHandler(NotificationService notificationService,
                            PdfSessionState sessionState,
                            UiControls ui) {
        this.notificationService = notificationService;
        this.sessionState        = sessionState;
        this.ui                  = ui;
    }

    /**
     * Demande confirmation puis supprime {@code pdf} du filesystem.
     * Doit être appelé depuis le FX thread.
     */
    public void handle(Path pdf) {
        if (!confirm(pdf)) return;

        try {
            Files.deleteIfExists(pdf);
            log.info("Fichier supprimé : {}", pdf);

            if (pdf.equals(sessionState.getCurrentPdf().orElse(null))) {
                sessionState.setCurrentPdf(null);
                sessionState.clearPendingPositions();
            }

            ui.refreshFileBrowser();
            notificationService.showSuccess("Fichier supprimé : " + pdf.getFileName());

        } catch (IOException e) {
            log.error("Impossible de supprimer : {}", pdf, e);
            notificationService.showError("Impossible de supprimer le fichier", e);
        }
    }

    private static boolean confirm(Path pdf) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Confirmer la suppression");

        DialogPane pane = dialog.getDialogPane();
        pane.setContentText(
                "Supprimer définitivement \"" + pdf.getFileName() + "\" ?\n\nCette action est irréversible.");
        pane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) pane.lookupButton(ButtonType.OK);
        okBtn.setText("Supprimer");

        return dialog.showAndWait()
                .filter(bt -> bt == ButtonType.OK)
                .isPresent();
    }
}
