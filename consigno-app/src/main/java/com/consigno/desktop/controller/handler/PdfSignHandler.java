package com.consigno.desktop.controller.handler;

import com.consigno.common.model.CertificateInfo;
import com.consigno.common.model.SignaturePosition;
import com.consigno.common.model.SignatureRequest;
import com.consigno.common.model.SignatureResult;
import com.consigno.crypto.service.CryptoService;
import com.consigno.desktop.service.NotificationService;
import com.consigno.desktop.view.pdf.PdfViewerPane;
import com.consigno.pdf.service.PdfSignatureService;
import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Gère le workflow de signature : dialog certificat + tâche asynchrone de signature.
 */
public final class PdfSignHandler {

    private static final Logger log = LoggerFactory.getLogger(PdfSignHandler.class);

    private final PdfSessionState session;
    private final PdfSignatureService pdfSignatureService;
    private final CryptoService cryptoService;
    private final NotificationService notificationService;
    private final PdfViewerPane pdfViewerPane;
    private final UiControls ui;
    private final Executor executor;
    private final Supplier<Optional<CertAndPassword>> certDialogSupplier;

    public PdfSignHandler(PdfSessionState session,
                          PdfSignatureService pdfSignatureService,
                          CryptoService cryptoService,
                          NotificationService notificationService,
                          PdfViewerPane pdfViewerPane,
                          UiControls ui,
                          Executor executor,
                          Supplier<Optional<CertAndPassword>> certDialogSupplier) {
        this.session = session;
        this.pdfSignatureService = pdfSignatureService;
        this.cryptoService = cryptoService;
        this.notificationService = notificationService;
        this.pdfViewerPane = pdfViewerPane;
        this.ui = ui;
        this.executor = executor;
        this.certDialogSupplier = certDialogSupplier;
    }

    public void handle() {
        if (session.getCurrentPdf().isEmpty()) {
            notificationService.showWarning("Ouvrez un document PDF avant de signer");
            return;
        }
        if (session.getPendingPositions().isEmpty()) {
            notificationService.showWarning("Glissez l'apparence sur le PDF pour placer la signature");
            return;
        }

        Optional<CertAndPassword> certOpt = certDialogSupplier.get();
        if (certOpt.isEmpty()) return;

        char[] password = certOpt.get().password();
        Path p12Path    = certOpt.get().p12Path();
        Path pdfToSign  = session.getCurrentPdf().get();
        Path tempPath   = pdfToSign.resolveSibling("." + UUID.randomUUID() + ".tmp");
        List<SignaturePosition> positions = List.copyOf(session.getPendingPositions());

        ui.setSignButtonDisabled(true);
        ui.setStatus("Signature en cours…");
        ui.setBusy(true);

        Task<SignatureResult> task = new Task<>() {
            @Override
            protected SignatureResult call() throws Exception {
                CertificateInfo cert = cryptoService.loadCertificate(p12Path, password.clone());
                SignatureRequest.SignatureAppearance appearance =
                        new SignatureRequest.SignatureAppearance(cert.commonName(), null, null, null);
                return pdfSignatureService.sign(
                        new SignatureRequest(pdfToSign, tempPath, cert, positions, appearance),
                        password);
            }
        };

        task.setOnSucceeded(e -> {
            try {
                Files.move(tempPath, pdfToSign, StandardCopyOption.REPLACE_EXISTING);
                int n = positions.size();
                notificationService.showSuccess(
                        "Document signé avec " + n + " apparence" + (n > 1 ? "s" : "")
                        + " — " + pdfToSign.getFileName());
                ui.setStatus("Signé → " + pdfToSign.getFileName());
                session.clearPendingPositions();
                ui.refreshFileBrowser();
                pdfViewerPane.loadPdf(pdfToSign);
            } catch (IOException ioEx) {
                log.error("Move temp→original échoué", ioEx);
                try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
                notificationService.showError("Impossible de remplacer le fichier original", ioEx);
            } finally {
                Arrays.fill(password, '\0');
                ui.setSignButtonDisabled(false);
                ui.setBusy(false);
            }
        });

        task.setOnFailed(e -> {
            Arrays.fill(password, '\0');
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
            notificationService.showError("Échec de la signature", task.getException());
            ui.setStatus("Erreur lors de la signature");
            ui.setSignButtonDisabled(false);
            ui.setBusy(false);
        });

        executor.execute(task);
    }
}
