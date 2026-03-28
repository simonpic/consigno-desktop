package com.consigno.desktop.controller.handler;

import com.consigno.common.model.ValidationResult;
import com.consigno.desktop.service.NotificationService;
import com.consigno.pdf.service.PdfValidationService;
import javafx.concurrent.Task;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Gère la validation des signatures d'un PDF (tâche asynchrone).
 */
public final class PdfValidateHandler {

    private final PdfSessionState session;
    private final PdfValidationService pdfValidationService;
    private final NotificationService notificationService;
    private final UiControls ui;
    private final Executor executor;

    public PdfValidateHandler(PdfSessionState session,
                              PdfValidationService pdfValidationService,
                              NotificationService notificationService,
                              UiControls ui,
                              Executor executor) {
        this.session = session;
        this.pdfValidationService = pdfValidationService;
        this.notificationService = notificationService;
        this.ui = ui;
        this.executor = executor;
    }

    public void handle() {
        if (session.getCurrentPdf().isEmpty()) {
            notificationService.showWarning("Ouvrez un document PDF à valider");
            return;
        }

        Path pdfToValidate = session.getCurrentPdf().get();
        ui.setStatus("Validation des signatures…");
        ui.setBusy(true);

        Task<ValidationResult> task = new Task<>() {
            @Override
            protected ValidationResult call() throws Exception {
                return pdfValidationService.validate(pdfToValidate);
            }
        };

        task.setOnSucceeded(e -> {
            ValidationResult result = task.getValue();
            if (result.isFullyValid()) {
                notificationService.showSuccess(
                        "Toutes les signatures sont valides (" +
                        result.signatures().size() + " signature(s))");
            } else {
                notificationService.showWarning(
                        result.signatures().size() + " signature(s) — " +
                        result.errors().size() + " erreur(s)");
            }
            ui.setStatus("Validation terminée — " + result.signatures().size() + " signature(s)");
            ui.setBusy(false);
        });

        task.setOnFailed(e -> {
            notificationService.showError("Erreur lors de la validation", task.getException());
            ui.setStatus("Erreur lors de la validation");
            ui.setBusy(false);
        });

        executor.execute(task);
    }
}
