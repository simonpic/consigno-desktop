package com.consigno.desktop.controller.handler;

import com.consigno.desktop.view.pdf.PdfViewerPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * Gère l'ouverture d'un PDF dans le viewer (double-clic ou action "Ouvrir").
 */
public final class PdfSelectionHandler {

    private static final Logger log = LoggerFactory.getLogger(PdfSelectionHandler.class);

    private final PdfSessionState session;
    private final PdfViewerPane pdfViewerPane;
    private final UiControls ui;

    public PdfSelectionHandler(PdfSessionState session, PdfViewerPane pdfViewerPane, UiControls ui) {
        this.session = session;
        this.pdfViewerPane = pdfViewerPane;
        this.ui = ui;
    }

    public void handle(Path pdfPath) {
        session.setCurrentPdf(pdfPath);
        session.clearPendingPositions();
        ui.setSignButtonVisible(true);
        pdfViewerPane.loadPdf(pdfPath);
        ui.setStatus("PDF ouvert : " + pdfPath.getFileName());
        log.info("PDF ouvert : {}", pdfPath);
    }
}
