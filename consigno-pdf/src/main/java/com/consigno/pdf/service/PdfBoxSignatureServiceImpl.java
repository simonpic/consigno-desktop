package com.consigno.pdf.service;

import com.consigno.common.exception.PdfException;
import com.consigno.common.exception.PdfSignatureException;
import com.consigno.common.model.SignaturePosition;
import com.consigno.common.model.SignatureRequest;
import com.consigno.common.model.SignatureResult;
import com.consigno.crypto.service.CryptoService;
import com.consigno.pdf.model.SignatureInfo;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationRubberStamp;

import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceDictionary;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.ExternalSigningSupport;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureOptions;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * Implémentation PDFBox 3 du service de signature PDF.
 *
 * <p>Utilise l'API de signature externe de PDFBox 3 :
 * {@code saveIncrementalForExternalSigning()} → {@code ExternalSigningSupport}.
 * La signature CMS est produite par {@link CryptoService}.
 * L'apparence visuelle est générée via PDFBox content stream.
 */
public class PdfBoxSignatureServiceImpl implements PdfSignatureService {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxSignatureServiceImpl.class);

    private final CryptoService cryptoService;

    public PdfBoxSignatureServiceImpl(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public SignatureResult sign(SignatureRequest request, char[] password) throws PdfSignatureException {
        log.info("Signature de {} → {}", request.inputPdf().getFileName(), request.outputPdf().getFileName());

        try {
            Files.createDirectories(request.outputPdf().getParent());
        } catch (IOException e) {
            throw new PdfSignatureException("Impossible de créer le répertoire de sortie", e);
        }

        try (PDDocument doc = Loader.loadPDF(request.inputPdf().toFile())) {

            PDSignature signature = buildPdSignature(request);

            SignatureOptions opts = new SignatureOptions();
            opts.setPage(request.positions().get(0).pageNumber() - 1);

            doc.addSignature(signature, opts);

            // Apparence visuelle — après addSignature, avant saveIncremental
            addVisualAppearance(doc, request);

            try (FileOutputStream fos = new FileOutputStream(request.outputPdf().toFile())) {
                ExternalSigningSupport externalSigning = doc.saveIncrementalForExternalSigning(fos);

                byte[] contentToSign = externalSigning.getContent().readAllBytes();
                byte[] cmsSignature = cryptoService.signCms(contentToSign, request.certificate(), password);

                externalSigning.setSignature(cmsSignature);
            }

            log.info("Document signé avec succès : {}", request.outputPdf());
            return SignatureResult.success(request.outputPdf());

        } catch (Exception e) {
            throw new PdfSignatureException("Erreur lors de la signature PDF", e);
        }
    }

    @Override
    public List<SignatureInfo> getSignatures(Path pdfPath) throws PdfException {
        log.debug("Lecture des signatures de : {}", pdfPath);
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {

            List<SignatureInfo> result = new ArrayList<>();
            for (PDSignatureField field : doc.getSignatureFields()) {
                PDSignature sig = field.getSignature();
                if (sig != null) {
                    result.add(new SignatureInfo(
                            field.getFullyQualifiedName(),
                            sig.getName(),
                            sig.getReason(),
                            sig.getLocation(),
                            toLocalDateTime(sig.getSignDate()),
                            true
                    ));
                }
            }

            log.debug("{} signature(s) trouvée(s) dans {}", result.size(), pdfPath.getFileName());
            return List.copyOf(result);

        } catch (IOException e) {
            throw new PdfException("Erreur lors de la lecture des signatures de : " + pdfPath, e);
        }
    }

    // -------------------------------------------------------------------------
    // Apparence visuelle
    // -------------------------------------------------------------------------

    /**
     * Ajoute les apparences visuelles pour toutes les positions de la requête.
     *
     * <p>La première position est appliquée au widget du champ de signature créé par
     * {@code addSignature}. Les positions supplémentaires génèrent des annotations
     * tampon ({@code PDAnnotationRubberStamp}) avec une apparence visuelle identique
     * mais sans valeur cryptographique propre — elles font partie du contenu signé
     * car elles sont ajoutées avant {@code saveIncrementalForExternalSigning}.
     */
    private void addVisualAppearance(PDDocument doc, SignatureRequest request) throws IOException {
        List<PDSignatureField> fields = doc.getSignatureFields();
        if (fields.isEmpty()) {
            log.warn("Aucun champ de signature trouvé pour appliquer l'apparence visuelle");
            return;
        }

        PDSignatureField sigField = fields.get(fields.size() - 1);
        List<PDAnnotationWidget> widgets = sigField.getWidgets();
        if (widgets.isEmpty()) return;

        List<SignaturePosition> positions = request.positions();

        // Première position → widget principal du champ de signature
        applyAppearanceToWidget(doc, widgets.get(0), positions.get(0));

        // Positions supplémentaires → annotations tampon (même visuel, incluses dans le ByteRange)
        for (int i = 1; i < positions.size(); i++) {
            SignaturePosition pos = positions.get(i);
            PDAnnotationRubberStamp stamp = new PDAnnotationRubberStamp();
            stamp.setName(PDAnnotationRubberStamp.NAME_APPROVED);
            stamp.setRectangle(new PDRectangle(
                    (float) pos.x(), (float) pos.y(), (float) pos.width(), (float) pos.height()));
            stamp.setPage(doc.getPage(pos.pageNumber() - 1));
            stamp.setPrinted(true);

            PDAppearanceDictionary appearanceDict = new PDAppearanceDictionary();
            appearanceDict.setNormalAppearance(buildAppearanceStream(doc, pos));
            stamp.setAppearance(appearanceDict);

            doc.getPage(pos.pageNumber() - 1).getAnnotations().add(stamp);
            log.debug("Apparence tampon ajoutée — page={} pos=({},{}) taille={}x{} pts",
                    pos.pageNumber(), pos.x(), pos.y(), pos.width(), pos.height());
        }
    }

    /** Positionne le widget de signature sur la page et lui applique l'apparence visuelle. */
    private void applyAppearanceToWidget(PDDocument doc, PDAnnotationWidget widget,
                                         SignaturePosition pos) throws IOException {
        float x = (float) pos.x(), y = (float) pos.y();
        float w = (float) pos.width(), h = (float) pos.height();

        widget.setRectangle(new PDRectangle(x, y, w, h));
        widget.setPage(doc.getPage(pos.pageNumber() - 1));
        widget.setPrinted(true);

        var annotations = doc.getPage(pos.pageNumber() - 1).getAnnotations();
        if (!annotations.contains(widget)) annotations.add(widget);

        PDAppearanceDictionary appearanceDict = new PDAppearanceDictionary();
        appearanceDict.setNormalAppearance(buildAppearanceStream(doc, pos));
        widget.setAppearance(appearanceDict);

        log.debug("Apparence widget appliquée — page={} pos=({},{}) taille={}x{} pts",
                pos.pageNumber(), x, y, w, h);
    }

    /**
     * Construit l'appearance stream en embarquant l'image de {@link SignatureAppearanceRenderer}
     * comme XObject PNG.
     *
     * <p>On évite ainsi tout problème de clip sous-pixel lié aux opérateurs de tracé PDF (stroke `S`) :
     * PDFBox clippe les annotations au {@code floor()} de leur rect en pixels (Java2D exclusif côté
     * droit/bas), ce qui tronque les traits proches du bord du BBox. Une image rastérisée avec
     * {@code (int)} (= floor) est dimensionnée exactement pour ce clip — aucun pixel ne dépasse.
     *
     * <p>{@link PDPageContentStream#drawImage} applique automatiquement la correction Y-flip standard
     * PDF (CTM : [w 0 0 -h 0 h]), ce qui garantit un rendu correct dans tous les lecteurs.
     */
    private PDAppearanceStream buildAppearanceStream(PDDocument doc,
                                                     SignaturePosition pos) throws IOException {
        float w = (float) pos.width();
        float h = (float) pos.height();

        // Même DPI que le viewer (96). (int) = floor() = même arrondi que le clip PDFBox.
        int imgW = (int) (w * 96f / 72f);
        int imgH = (int) (h * 96f / 72f);
        BufferedImage img = SignatureAppearanceRenderer.render(imgW, imgH, LocalDate.now());

        PDAppearanceStream stream = new PDAppearanceStream(doc);
        stream.setBBox(new PDRectangle(w, h));
        stream.setResources(new org.apache.pdfbox.pdmodel.PDResources());

        PDImageXObject pdImage = LosslessFactory.createFromImage(doc, img);

        // PDPageContentStream gère le Y-flip (CTM [w 0 0 -h 0 h]) — rendu identique dans tous les lecteurs
        try (PDPageContentStream cs = new PDPageContentStream(doc, stream)) {
            cs.drawImage(pdImage, 0, 0, w, h);
        }

        return stream;
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    private PDSignature buildPdSignature(SignatureRequest request) {
        PDSignature signature = new PDSignature();
        signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
        signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
        signature.setName(request.certificate().commonName());
        signature.setSignDate(Calendar.getInstance());

        if (request.appearance() != null) {
            if (request.appearance().reason() != null) {
                signature.setReason(request.appearance().reason());
            }
            if (request.appearance().location() != null) {
                signature.setLocation(request.appearance().location());
            }
            if (request.appearance().contactInfo() != null) {
                signature.setContactInfo(request.appearance().contactInfo());
            }
        }
        return signature;
    }

    private LocalDateTime toLocalDateTime(Calendar calendar) {
        if (calendar == null) return null;
        return calendar.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }
}
