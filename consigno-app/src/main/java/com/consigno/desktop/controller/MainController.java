package com.consigno.desktop.controller;

import com.consigno.common.model.SignaturePosition;
import com.consigno.crypto.service.CryptoService;
import com.consigno.desktop.controller.handler.AdobeOpenHandler;
import com.consigno.desktop.controller.handler.CertAndPassword;
import com.consigno.desktop.controller.handler.PdfConvertHandler;
import com.consigno.desktop.controller.handler.PdfDeleteHandler;
import com.consigno.desktop.controller.handler.PdfSelectionHandler;
import com.consigno.desktop.controller.handler.PdfSessionState;
import com.consigno.desktop.controller.handler.PdfSignHandler;
import com.consigno.desktop.controller.handler.PdfValidateHandler;
import com.consigno.desktop.controller.handler.UiControls;
import com.consigno.desktop.service.NotificationService;
import com.consigno.desktop.service.SystemService;
import com.consigno.desktop.view.filesystem.FileBrowserPane;
import com.consigno.desktop.view.pdf.PdfViewerPane;
import com.consigno.pdf.service.PdfConversionService;
import com.consigno.pdf.service.PdfSignatureService;
import com.consigno.pdf.service.PdfValidationService;
import com.consigno.pdf.service.SignatureAppearanceRenderer;
import jakarta.inject.Inject;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Contrôleur de la fenêtre principale — orchestre les handlers et câble les Consumers.
 */
public class MainController {

    private final PdfSignatureService  pdfSignatureService;
    private final PdfValidationService pdfValidationService;
    private final PdfConversionService pdfConversionService;
    private final CryptoService        cryptoService;
    private final NotificationService  notificationService;
    private final SystemService        systemService;
    private final PdfViewerPane        pdfViewerPane;

    private final Executor executor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "consigno-worker");
        t.setDaemon(true);
        return t;
    });

    private FileBrowserPane fileBrowserPane;
    private PdfSignHandler     signHandler;
    private PdfValidateHandler validateHandler;

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

        PdfSessionState session = new PdfSessionState();
        UiControls ui = buildUiControls();

        PdfSelectionHandler selectionHandler = new PdfSelectionHandler(session, pdfViewerPane, ui);
        signHandler     = new PdfSignHandler(session, pdfSignatureService, cryptoService,
                notificationService, pdfViewerPane, ui, executor, this::askCertificateAndPassword);
        validateHandler = new PdfValidateHandler(session, pdfValidationService,
                notificationService, ui, executor);
        PdfConvertHandler convertHandler = new PdfConvertHandler(pdfConversionService,
                notificationService, ui, executor);
        PdfDeleteHandler deleteHandler = new PdfDeleteHandler(notificationService, session, ui);

        pdfViewerPane.setOnSignaturePositionSelected(pos -> {
            session.getPendingPositions().add(pos);
            int n = session.getPendingPositions().size();
            ui.setStatus(n + " signature" + (n > 1 ? "s" : "") + " placée" + (n > 1 ? "s" : ""));
        });
        pdfViewerPane.setOnDocumentLoaded(pageCount ->
                ui.setStatus("Document chargé — " + pageCount + " page(s)"));
        pdfViewerPane.setOnSignatureRemoved(pos -> {
            session.getPendingPositions().remove(pos);
            int n = session.getPendingPositions().size();
            ui.setStatus(n == 0 ? "Prêt" : n + " signature" + (n > 1 ? "s" : "") + " placée" + (n > 1 ? "s" : ""));
        });
        pdfViewerPane.setOnSignaturePositionUpdated((oldPos, newPos) -> {
            int idx = session.getPendingPositions().indexOf(oldPos);
            if (idx >= 0) session.getPendingPositions().set(idx, newPos);
        });

        fileBrowserPane = new FileBrowserPane();
        fileBrowserContainer.getChildren().add(fileBrowserPane);
        fileBrowserPane.setOnPdfSelected(selectionHandler::handle);
        fileBrowserPane.setOnSignRequested(path -> {
            selectionHandler.handle(path);
            signHandler.handle();
        });
        fileBrowserPane.setOnValidateRequested(path -> {
            selectionHandler.handle(path);
            validateHandler.handle();
        });
        fileBrowserPane.setOnConvertRequested(convertHandler::handle);
        fileBrowserPane.setOnDeleteRequested(deleteHandler::handle);
        if (systemService.getAdobeAcrobatPath().isPresent()) {
            AdobeOpenHandler adobeHandler = new AdobeOpenHandler(systemService, notificationService);
            fileBrowserPane.setAdobeAvailable(true);
            fileBrowserPane.setOnOpenWithAdobeRequested(adobeHandler::handle);
        }

        signaturePreviewImage.setImage(SwingFXUtils.toFXImage(
                SignatureAppearanceRenderer.render(216, 72, LocalDate.now()), null));

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

        signaturePreviewImage.setPickOnBounds(true);
        signaturePreviewImage.setOnDragDetected(e -> {
            var db = signaturePreviewImage.startDragAndDrop(TransferMode.COPY);
            var content = new ClipboardContent();
            content.putString("SIGNATURE");
            db.setContent(content);
            e.consume();
        });

        ui.setStatus("Prêt");
        progressIndicator.setVisible(false);
    }

    @FXML
    private void handleSign() {
        signHandler.handle();
    }

    @FXML
    private void handleValidate() {
        validateHandler.handle();
    }

    // -------------------------------------------------------------------------
    // Dialog certificat + mot de passe
    // -------------------------------------------------------------------------

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

        dialogPane.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) ctrl.getBrowseBtn().requestFocus();
        });

        return dialog.showAndWait();
    }

    // -------------------------------------------------------------------------
    // UiControls — implémentation sur les champs @FXML
    // -------------------------------------------------------------------------

    private UiControls buildUiControls() {
        return new UiControls() {
            @Override public void setStatus(String message)              { statusLabel.setText(message); }
            @Override public void setBusy(boolean busy)                  { progressIndicator.setVisible(busy); }
            @Override public void setSignButtonDisabled(boolean disabled) { signButton.setDisable(disabled); }
            @Override public void setSignButtonVisible(boolean visible)   {
                signButton.setVisible(visible);
                signButton.setManaged(visible);
            }
            @Override public void refreshFileBrowser() {
                if (fileBrowserPane != null) fileBrowserPane.refresh();
            }
        };
    }
}
