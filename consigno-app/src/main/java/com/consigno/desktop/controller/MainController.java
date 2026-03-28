package com.consigno.desktop.controller;

import com.consigno.common.model.CertificateInfo;
import com.consigno.common.model.SignaturePosition;
import com.consigno.common.model.SignatureRequest;
import com.consigno.common.model.SignatureResult;
import com.consigno.common.model.ValidationResult;
import com.consigno.crypto.service.CryptoService;
import com.consigno.desktop.service.NotificationService;
import com.consigno.desktop.service.SystemService;
import com.consigno.desktop.view.filesystem.FileBrowserPane;
import com.consigno.desktop.view.pdf.PdfViewerPane;
import com.consigno.pdf.service.PdfConversionService;
import com.consigno.pdf.service.PdfSignatureService;
import com.consigno.pdf.service.PdfValidationService;
import com.consigno.pdf.service.SignatureAppearanceRenderer;
import jakarta.inject.Inject;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList; // pour pendingPositions
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Contrôleur de la fenêtre principale.
 */
public class MainController {

    private static final Logger log = LoggerFactory.getLogger(MainController.class);

    private final PdfSignatureService   pdfSignatureService;
    private final PdfValidationService  pdfValidationService;
    private final PdfConversionService  pdfConversionService;
    private final CryptoService         cryptoService;
    private final NotificationService   notificationService;
    private final SystemService         systemService;
    private final PdfViewerPane         pdfViewerPane;

    private final Executor executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "consigno-worker");
        t.setDaemon(true);
        return t;
    });

    private Path currentPdf;
    private final List<SignaturePosition> pendingPositions = new ArrayList<>();

    private FileBrowserPane fileBrowserPane;

    @FXML private StackPane         pdfViewerContainer;
    @FXML private VBox              fileBrowserContainer;
    @FXML private ImageView         signaturePreviewImage;
    @FXML private Label             statusLabel;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Button            signButton;

    @Inject
    public MainController(PdfSignatureService pdfSignatureService,
                          PdfValidationService pdfValidationService,
                          PdfConversionService pdfConversionService,
                          CryptoService cryptoService,
                          NotificationService notificationService,
                          SystemService systemService,
                          PdfViewerPane pdfViewerPane) {
        this.pdfSignatureService  = pdfSignatureService;
        this.pdfValidationService = pdfValidationService;
        this.pdfConversionService = pdfConversionService;
        this.cryptoService        = cryptoService;
        this.notificationService  = notificationService;
        this.systemService        = systemService;
        this.pdfViewerPane        = pdfViewerPane;
    }

    @FXML
    private void initialize() {
        notificationService.setOwner(pdfViewerContainer);
        pdfViewerContainer.getChildren().add(pdfViewerPane);

        pdfViewerPane.setOnSignaturePositionSelected(this::handlePositionSelected);
        pdfViewerPane.setOnDocumentLoaded(pageCount ->
                setStatus("Document chargé — " + pageCount + " page(s)"));
        pdfViewerPane.setOnSignatureRemoved(pos -> {
            pendingPositions.remove(pos);
            int n = pendingPositions.size();
            setStatus(n == 0 ? "Prêt" : n + " signature" + (n > 1 ? "s" : "") + " placée" + (n > 1 ? "s" : ""));
        });
        pdfViewerPane.setOnSignaturePositionUpdated((oldPos, newPos) -> {
            int idx = pendingPositions.indexOf(oldPos);
            if (idx >= 0) pendingPositions.set(idx, newPos);
        });

        // Explorateur de fichiers PDF
        fileBrowserPane = new FileBrowserPane();
        fileBrowserContainer.getChildren().add(fileBrowserPane);
        fileBrowserPane.setOnPdfSelected(this::handlePdfSelected);
        fileBrowserPane.setOnSignRequested(path -> {
            handlePdfSelected(path);
            handleSign();
        });
        fileBrowserPane.setOnValidateRequested(path -> {
            handlePdfSelected(path);
            handleValidate();
        });
        fileBrowserPane.setOnConvertRequested(this::handleConvertToPdfA);
        if (systemService.getAdobeAcrobatPath().isPresent()) {
            fileBrowserPane.setAdobeAvailable(true);
            fileBrowserPane.setOnOpenWithAdobeRequested(path -> {
                try {
                    systemService.openWithAdobeAcrobat(path);
                } catch (IOException e) {
                    notificationService.showError("Impossible d'ouvrir avec Adobe Acrobat", e);
                }
            });
        }

        // Preview de l'apparence dans la sidebar (ratio 3:1, plus lisible)
        signaturePreviewImage.setImage(SwingFXUtils.toFXImage(
                SignatureAppearanceRenderer.render(216, 72, LocalDate.now()), null));

        // Supprimer le curseur interdit partout pendant un drag SIGNATURE
        pdfViewerContainer.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                scene.setOnDragOver(e -> {
                    if (e.getDragboard().hasString()
                            && "SIGNATURE".equals(e.getDragboard().getString())) {
                        e.acceptTransferModes(TransferMode.COPY);
                        e.consume();
                    }
                });
            }
        });

        // Drag depuis la sidebar vers le visualiseur PDF
        signaturePreviewImage.setPickOnBounds(true);
        signaturePreviewImage.setOnDragDetected(e -> {
            var db = signaturePreviewImage.startDragAndDrop(TransferMode.COPY);
            var content = new ClipboardContent();
            content.putString("SIGNATURE");
            db.setContent(content);
            e.consume();
        });

        setStatus("Prêt");
        progressIndicator.setVisible(false);
    }

    // -------------------------------------------------------------------------
    // Handlers FXML
    // -------------------------------------------------------------------------

    @FXML
    private void handleSign() {
        if (currentPdf == null) {
            notificationService.showWarning("Ouvrez un document PDF avant de signer");
            return;
        }
        if (pendingPositions.isEmpty()) {
            notificationService.showWarning("Glissez l'apparence sur le PDF pour placer la signature");
            return;
        }

        Optional<CertAndPassword> certOpt = askCertificateAndPassword();
        if (certOpt.isEmpty()) return;

        char[] password  = certOpt.get().password();
        Path   p12Path   = certOpt.get().p12Path();
        Path   pdfToSign = currentPdf;
        Path   tempPath  = currentPdf.resolveSibling("." + UUID.randomUUID() + ".tmp");
        List<SignaturePosition> positions = List.copyOf(pendingPositions);

        signButton.setDisable(true);
        setStatus("Signature en cours…");
        progressIndicator.setVisible(true);

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
                setStatus("Signé → " + pdfToSign.getFileName());
                pendingPositions.clear();
                fileBrowserPane.refresh();
                pdfViewerPane.loadPdf(pdfToSign);
            } catch (IOException ioEx) {
                notificationService.showError("Impossible de remplacer le fichier original", ioEx);
                log.error("Move temp→original échoué", ioEx);
            } finally {
                signButton.setDisable(false);
                progressIndicator.setVisible(false);
            }
        });

        task.setOnFailed(e -> {
            Arrays.fill(password, '\0');
            try { Files.deleteIfExists(tempPath); } catch (IOException ignored) {}
            notificationService.showError("Échec de la signature", task.getException());
            setStatus("Erreur lors de la signature");
            signButton.setDisable(false);
            progressIndicator.setVisible(false);
        });

        executor.execute(task);
    }

    @FXML
    private void handleValidate() {
        if (currentPdf == null) {
            notificationService.showWarning("Ouvrez un document PDF à valider");
            return;
        }

        setStatus("Validation des signatures…");
        progressIndicator.setVisible(true);

        Path pdfToValidate = currentPdf;
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
            setStatus("Validation terminée — " + result.signatures().size() + " signature(s)");
            progressIndicator.setVisible(false);
        });

        task.setOnFailed(e -> {
            notificationService.showError("Erreur lors de la validation", task.getException());
            setStatus("Erreur lors de la validation");
            progressIndicator.setVisible(false);
        });

        executor.execute(task);
    }

    // -------------------------------------------------------------------------
    // Callbacks internes
    // -------------------------------------------------------------------------

    private void handlePdfSelected(Path pdfPath) {
        currentPdf = pdfPath;
        pendingPositions.clear();
        signButton.setVisible(true);
        signButton.setManaged(true);
        pdfViewerPane.loadPdf(currentPdf);
        setStatus("PDF ouvert : " + pdfPath.getFileName());
        log.info("PDF ouvert : {}", currentPdf);
    }

    private void handlePositionSelected(SignaturePosition position) {
        pendingPositions.add(position);
        int n = pendingPositions.size();
        setStatus(n + " signature" + (n > 1 ? "s" : "") + " placée" + (n > 1 ? "s" : ""));
        log.debug("Signature ajoutée ({} total) : {}", n, position);
    }

    // -------------------------------------------------------------------------
    // Popup certificat + mot de passe
    // -------------------------------------------------------------------------

    private record CertAndPassword(Path p12Path, char[] password) {}

    private Optional<CertAndPassword> askCertificateAndPassword() {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/signature-dialog.fxml"));
        DialogPane dialogPane;
        try {
            dialogPane = loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de charger signature-dialog.fxml", e);
        }
        SignatureDialogController ctrl = loader.getController();

        ButtonType signButtonType = new ButtonType("Signer", ButtonBar.ButtonData.OK_DONE);
        dialogPane.getButtonTypes().addAll(signButtonType, ButtonType.CANCEL);

        Dialog<CertAndPassword> dialog = new Dialog<>();
        dialog.setTitle("Signature électronique");
        dialog.initOwner(pdfViewerContainer.getScene().getWindow());
        dialog.setDialogPane(dialogPane);

        // Activer "Signer" seulement si les deux champs sont remplis
        Node signBtn = dialogPane.lookupButton(signButtonType);
        signBtn.setDisable(true);
        Runnable updateBtn = () -> signBtn.setDisable(
                ctrl.getSelectedCertPath() == null || ctrl.getPwdField().getText().isEmpty());
        ctrl.getCertField().textProperty().addListener((obs, o, v) -> updateBtn.run());
        ctrl.getPwdField().textProperty().addListener((obs, o, v) -> updateBtn.run());

        dialog.setResultConverter(bt -> {
            if (bt != signButtonType || ctrl.getSelectedCertPath() == null) return null;
            return new CertAndPassword(ctrl.getSelectedCertPath(), ctrl.getPwdField().getText().toCharArray());
        });

        // Focus sur "Parcourir…" à l'ouverture — le certificat est l'étape 1
        dialogPane.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) ctrl.getBrowseBtn().requestFocus();
        });

        return dialog.showAndWait();
    }

    // -------------------------------------------------------------------------
    // Conversion PDF/A
    // -------------------------------------------------------------------------

    private void handleConvertToPdfA(Path pdf) {
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
            fileBrowserPane.refresh();
            notificationService.showSuccess("Converti en PDF/A : " + outName);
        });
        task.setOnFailed(e -> notificationService.showError(
                "Échec de la conversion PDF/A", task.getException()));
        executor.execute(task);
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    private void setStatus(String message) {
        statusLabel.setText(message);
    }
}
