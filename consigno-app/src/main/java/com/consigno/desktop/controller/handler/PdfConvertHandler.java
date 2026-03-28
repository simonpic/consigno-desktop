package com.consigno.desktop.controller.handler;

import com.consigno.desktop.service.NotificationService;
import com.consigno.pdf.service.PdfConversionService;
import javafx.concurrent.Task;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Gère la conversion PDF → PDF/A (tâche asynchrone).
 */
public final class PdfConvertHandler {

    private final PdfConversionService pdfConversionService;
    private final NotificationService notificationService;
    private final UiControls ui;
    private final Executor executor;

    public PdfConvertHandler(PdfConversionService pdfConversionService,
                             NotificationService notificationService,
                             UiControls ui,
                             Executor executor) {
        this.pdfConversionService = pdfConversionService;
        this.notificationService = notificationService;
        this.ui = ui;
        this.executor = executor;
    }

    public void handle(Path pdf) {
        String name = pdf.getFileName().toString();
        String outName = name.endsWith(".pdf")
                ? name.substring(0, name.length() - 4) + "_pdfa.pdf"
                : name + "_pdfa.pdf";
        Path output = pdf.getParent().resolve(outName);

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                pdfConversionService.convertToPdfA(pdf, output);
                return null;
            }
        };

        task.setOnSucceeded(e -> {
            ui.refreshFileBrowser();
            notificationService.showSuccess("Converti en PDF/A : " + outName);
        });

        task.setOnFailed(e -> notificationService.showError(
                "Échec de la conversion PDF/A", task.getException()));

        executor.execute(task);
    }
}
