package com.consigno.desktop.controller.handler;

import com.consigno.common.model.SignaturePosition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * État mutable partagé entre les handlers du MainController.
 * Instancié une seule fois dans initialize(), jamais géré par Guice.
 */
public final class PdfSessionState {

    private Path currentPdf;
    private final List<SignaturePosition> pendingPositions = new ArrayList<>();

    public Optional<Path> getCurrentPdf() {
        return Optional.ofNullable(currentPdf);
    }

    public void setCurrentPdf(Path pdf) {
        this.currentPdf = pdf;
    }

    public List<SignaturePosition> getPendingPositions() {
        return pendingPositions;
    }

    public void clearPendingPositions() {
        pendingPositions.clear();
    }
}
