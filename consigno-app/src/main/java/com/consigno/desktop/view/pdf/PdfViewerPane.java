package com.consigno.desktop.view.pdf;

import com.consigno.common.model.SignaturePosition;
import com.consigno.desktop.service.NotificationService;
import com.consigno.pdf.service.PdfRenderService;
import com.consigno.pdf.service.SignatureAppearanceRenderer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Composant JavaFX d'affichage PDF et de placement de signature.
 *
 * <p>Le fantôme et le marqueur de signature affichent l'image d'apparence réelle
 * (générée par {@link SignatureAppearanceRenderer}), identique à ce qui sera
 * apposé sur le PDF signé.
 */
@Singleton
public class PdfViewerPane extends StackPane {

    private static final Logger log = LoggerFactory.getLogger(PdfViewerPane.class);

    private static final float  BASE_DPI   = 96.0f;
    private static final double GHOST_W_PT = 150.0;
    private static final double GHOST_H_PT = 50.0;

    private final PdfRenderService    pdfRenderService;
    private final NotificationService notificationService;
    private final Executor            executor;

    private final ScrollPane scrollPane;
    private final VBox       pageContainer;

    /** Image de l'apparence de signature — partagée entre ghost et marqueurs. */
    private final Image appearanceFxImage;

    private Path currentPdfPath;

    /** Marqueur actuellement sélectionné, ou null. */
    private SignatureMarkerNode selectedMarker;

    private Consumer<SignaturePosition>                       onSignaturePositionSelected;
    private Consumer<SignaturePosition>                       onSignatureRemoved;
    private BiConsumer<SignaturePosition, SignaturePosition>  onSignaturePositionUpdated;
    private Consumer<Integer>                                 onDocumentLoaded;

    @Inject
    public PdfViewerPane(PdfRenderService pdfRenderService, NotificationService notificationService) {
        this.pdfRenderService    = pdfRenderService;
        this.notificationService = notificationService;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "pdf-render");
            t.setDaemon(true);
            return t;
        });

        // Génération de l'image d'apparence aux dimensions du ghost en pixels
        int imgW = (int) (GHOST_W_PT * BASE_DPI / 72.0);
        int imgH = (int) (GHOST_H_PT * BASE_DPI / 72.0);
        appearanceFxImage = SwingFXUtils.toFXImage(
                SignatureAppearanceRenderer.render(imgW, imgH, LocalDate.now()), null);

        pageContainer = new VBox(16);
        pageContainer.setAlignment(Pos.TOP_CENTER);
        pageContainer.setPadding(new Insets(16));
        pageContainer.setStyle("-fx-background-color: #404040;");

        scrollPane = new ScrollPane(pageContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #404040; -fx-background: #404040;");
        scrollPane.prefWidthProperty().bind(widthProperty());
        scrollPane.prefHeightProperty().bind(heightProperty());

        getChildren().add(scrollPane);
        showPlaceholder();

        // Suppression clavier du marqueur sélectionné
        setFocusTraversable(true);
        setOnKeyPressed(e -> {
            if ((e.getCode() == KeyCode.DELETE || e.getCode() == KeyCode.BACK_SPACE)
                    && selectedMarker != null) {
                selectedMarker.deleteFromOverlay();
                e.consume();
            }
        });
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    public void loadPdf(Path path) {
        this.currentPdfPath = path;
        pageContainer.getChildren().clear();

        float dpi = BASE_DPI;

        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                int[] count = {0};
                pdfRenderService.renderDocument(path, dpi, (pageIndex, rendered) -> {
                    count[0]++;
                    Platform.runLater(() -> addPageNode(pageIndex, rendered, dpi));
                });
                return count[0];
            }
        };

        task.setOnSucceeded(e -> {
            log.info("PDF rendu — {} page(s)", task.getValue());
            if (onDocumentLoaded != null) onDocumentLoaded.accept(task.getValue());
        });
        task.setOnFailed(e ->
                notificationService.showError("Erreur de rendu PDF", task.getException())
        );

        executor.execute(task);
    }

    public void setOnSignaturePositionSelected(Consumer<SignaturePosition> callback) {
        this.onSignaturePositionSelected = callback;
    }

    public void setOnSignatureRemoved(Consumer<SignaturePosition> callback) {
        this.onSignatureRemoved = callback;
    }

    public void setOnSignaturePositionUpdated(BiConsumer<SignaturePosition, SignaturePosition> callback) {
        this.onSignaturePositionUpdated = callback;
    }

    public void setOnDocumentLoaded(Consumer<Integer> callback) {
        this.onDocumentLoaded = callback;
    }

    public Image getAppearanceFxImage() {
        return appearanceFxImage;
    }

    public Path getCurrentPdfPath() {
        return currentPdfPath;
    }

    // -------------------------------------------------------------------------
    // Construction des nœuds de page
    // -------------------------------------------------------------------------

    private void addPageNode(int pageIndex, PdfRenderService.RenderedPage rendered, float dpi) {
        WritableImage fxImage = SwingFXUtils.toFXImage(rendered.image(), null);

        ImageView imageView = new ImageView(fxImage);
        imageView.setSmooth(true);
        imageView.setCache(true);

        Pane overlay = new Pane();
        overlay.setPrefSize(fxImage.getWidth(), fxImage.getHeight());
        overlay.setMinSize(fxImage.getWidth(), fxImage.getHeight());
        overlay.setMaxSize(fxImage.getWidth(), fxImage.getHeight());

        double ghostWPx = GHOST_W_PT * dpi / 72.0;
        double ghostHPx = GHOST_H_PT * dpi / 72.0;

        // Fantôme — image d'apparence semi-transparente qui suit le curseur
        ImageView ghost = makeAppearanceView(ghostWPx, ghostHPx, 0.72);
        ghost.setVisible(false);
        overlay.getChildren().add(ghost);

        overlay.setOnDragOver(e -> {
            if (e.getDragboard().hasString()
                    && "SIGNATURE".equals(e.getDragboard().getString())) {
                e.acceptTransferModes(TransferMode.COPY);
                ghost.setVisible(true);
                ghost.setLayoutX(e.getX() - ghostWPx / 2);
                ghost.setLayoutY(e.getY() - ghostHPx / 2);
                e.consume();
            }
        });
        overlay.setOnDragExited(e -> ghost.setVisible(false));
        overlay.setOnDragDropped(e -> {
            if (e.getDragboard().hasString()
                    && "SIGNATURE".equals(e.getDragboard().getString())) {
                ghost.setVisible(false);
                handleSignatureClick(e.getX(), e.getY(), pageIndex + 1, rendered, dpi,
                        overlay, ghostWPx, ghostHPx);
                e.setDropCompleted(true);
                e.consume();
            }
        });

        // Clic sur le fond de l'overlay → désélectionner
        overlay.setOnMousePressed(e -> {
            selectMarker(null);
            requestFocus();
        });

        StackPane pageStack = new StackPane(imageView, overlay);
        pageStack.setStyle("-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 4);");

        Label pageLabel = new Label("Page " + (pageIndex + 1));
        pageLabel.setStyle("-fx-text-fill: #999; -fx-font-size: 11px;");

        VBox pageWrapper = new VBox(4, pageLabel, pageStack);
        pageWrapper.setAlignment(Pos.TOP_CENTER);

        pageContainer.getChildren().add(pageWrapper);
    }

    // -------------------------------------------------------------------------
    // Placement de la signature
    // -------------------------------------------------------------------------

    private void handleSignatureClick(double mouseX, double mouseY, int pageNumber,
                                      PdfRenderService.RenderedPage rendered, float dpi,
                                      Pane overlay, double ghostWPx, double ghostHPx) {
        double pxToPt = 72.0 / dpi;
        double pdfX   = (mouseX - ghostWPx / 2) * pxToPt;
        double pdfY   = rendered.heightPts() - (mouseY + ghostHPx / 2) * pxToPt;

        log.debug("Signature placée — page={}, pdfX={:.1f}, pdfY={:.1f}", pageNumber, pdfX, pdfY);

        SignaturePosition position = new SignaturePosition(
                pageNumber, pdfX, pdfY, GHOST_W_PT, GHOST_H_PT);

        SignatureMarkerNode marker = new SignatureMarkerNode(
                appearanceFxImage, ghostWPx, ghostHPx,
                position, rendered.heightPts(), dpi);
        marker.setLayoutX(mouseX - ghostWPx / 2);
        marker.setLayoutY(mouseY - ghostHPx / 2);
        overlay.getChildren().add(marker);

        selectMarker(marker);
        requestFocus();

        if (onSignaturePositionSelected != null) {
            onSignaturePositionSelected.accept(position);
        }
    }

    // -------------------------------------------------------------------------
    // Sélection
    // -------------------------------------------------------------------------

    private void selectMarker(SignatureMarkerNode marker) {
        if (selectedMarker != null && selectedMarker != marker) {
            selectedMarker.setIdle();
        }
        selectedMarker = marker;
        if (marker != null) marker.setSelected();
    }

    // -------------------------------------------------------------------------
    // Utilitaires UI
    // -------------------------------------------------------------------------

    private void showPlaceholder() {
        Label label = new Label("Ouvrez un document PDF pour commencer.");
        label.setStyle("-fx-text-fill: #888; -fx-font-size: 14px;");
        pageContainer.getChildren().setAll(label);
    }

    private ImageView makeAppearanceView(double w, double h, double opacity) {
        ImageView iv = new ImageView(appearanceFxImage);
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(false);
        iv.setSmooth(true);
        iv.setMouseTransparent(true);
        iv.setOpacity(opacity);
        return iv;
    }

    // -------------------------------------------------------------------------
    // Marqueur de signature interactif
    // -------------------------------------------------------------------------

    private final class SignatureMarkerNode extends Pane {

        private static final String STYLE_IDLE =
                "-fx-border-color: rgba(66,133,244,0.6); -fx-border-width: 1.5; -fx-background-color: transparent;";
        private static final String STYLE_HOVER =
                "-fx-border-color: #4285f4; -fx-border-width: 2; -fx-background-color: transparent;";
        private static final String STYLE_SELECTED =
                "-fx-border-color: #4285f4; -fx-border-width: 2; -fx-background-color: transparent;";

        private SignaturePosition position;
        private final double pageHeightPts;
        private final double dpi;
        private final double w;
        private final double h;

        private final Button deleteBtn;

        private double dragStartSceneX;
        private double dragStartSceneY;
        private double dragStartLayoutX;
        private double dragStartLayoutY;
        private boolean dragged;

        SignatureMarkerNode(Image image, double w, double h,
                            SignaturePosition position,
                            double pageHeightPts, double dpi) {
            this.position      = position;
            this.pageHeightPts = pageHeightPts;
            this.dpi           = dpi;
            this.w             = w;
            this.h             = h;

            setPrefSize(w, h);

            ImageView iv = new ImageView(image);
            iv.setFitWidth(w);
            iv.setFitHeight(h);
            iv.setPreserveRatio(false);
            iv.setSmooth(true);
            iv.setMouseTransparent(true);

            deleteBtn = new Button("×");
            deleteBtn.setStyle(
                    "-fx-background-color: rgba(220,53,69,0.9); -fx-text-fill: white;"
                    + " -fx-background-radius: 0; -fx-border-radius: 0;"
                    + " -fx-font-size: 10px; -fx-padding: 1 4 1 4; -fx-cursor: hand;");
            deleteBtn.setLayoutX(w - 20);
            deleteBtn.setLayoutY(0);
            deleteBtn.setVisible(false);
            deleteBtn.setOnAction(e -> {
                deleteFromOverlay();
                e.consume();
            });

            getChildren().addAll(iv, deleteBtn);
            setStyle(STYLE_IDLE);
            setCursor(Cursor.HAND);

            setOnMouseEntered(e -> {
                if (this != selectedMarker) setStyle(STYLE_HOVER);
                deleteBtn.setVisible(true);
            });
            setOnMouseExited(e -> {
                if (this != selectedMarker) {
                    setStyle(STYLE_IDLE);
                    deleteBtn.setVisible(false);
                }
            });
            setOnMousePressed(e -> {
                if (e.getTarget() == deleteBtn) return; // géré par le bouton lui-même
                selectMarker(this);
                PdfViewerPane.this.requestFocus();
                dragStartSceneX  = e.getSceneX();
                dragStartSceneY  = e.getSceneY();
                dragStartLayoutX = getLayoutX();
                dragStartLayoutY = getLayoutY();
                dragged = false;
                setCursor(Cursor.CLOSED_HAND);
                e.consume();
            });
            setOnMouseDragged(e -> {
                dragged = true;
                double newX = dragStartLayoutX + (e.getSceneX() - dragStartSceneX);
                double newY = dragStartLayoutY + (e.getSceneY() - dragStartSceneY);
                Pane overlay = (Pane) getParent();
                setLayoutX(Math.max(0, Math.min(newX, overlay.getWidth()  - w)));
                setLayoutY(Math.max(0, Math.min(newY, overlay.getHeight() - h)));
                e.consume();
            });
            setOnMouseReleased(e -> {
                setCursor(Cursor.HAND);
                if (dragged && onSignaturePositionUpdated != null) {
                    double pxToPt             = 72.0 / dpi;
                    double newPdfX            = getLayoutX() * pxToPt;
                    double newPdfY            = pageHeightPts - (getLayoutY() + h) * pxToPt;
                    SignaturePosition oldPos   = this.position;
                    this.position             = new SignaturePosition(
                            oldPos.pageNumber(), newPdfX, newPdfY,
                            oldPos.width(), oldPos.height());
                    onSignaturePositionUpdated.accept(oldPos, this.position);
                }
                dragged = false;
                e.consume();
            });
        }

        void setIdle() {
            setStyle(STYLE_IDLE);
            deleteBtn.setVisible(false);
            setEffect(null);
        }

        void setSelected() {
            setStyle(STYLE_SELECTED);
            deleteBtn.setVisible(true);
            setEffect(new DropShadow(8, Color.rgb(66, 133, 244, 0.6)));
        }

        void deleteFromOverlay() {
            if (getParent() instanceof Pane parent) {
                parent.getChildren().remove(this);
            }
            if (selectedMarker == this) selectedMarker = null;
            if (onSignatureRemoved != null) onSignatureRemoved.accept(position);
        }
    }
}
