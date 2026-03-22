package com.consigno.pdf.service;

import com.consigno.common.exception.PdfException;
import com.consigno.common.exception.PdfSignatureException;
import com.consigno.common.model.SignaturePosition;
import com.consigno.common.model.SignatureRequest;
import com.consigno.common.model.SignatureResult;
import com.consigno.crypto.service.CryptoService;
import com.consigno.pdf.model.SignatureInfo;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

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

    /** Construit le flux de contenu PDF pour l'apparence visuelle à une position donnée. */
    private PDAppearanceStream buildAppearanceStream(PDDocument doc,
                                                     SignaturePosition pos) throws IOException {
        float w = (float) pos.width();
        float h = (float) pos.height();

        PDAppearanceStream stream = new PDAppearanceStream(doc);
        stream.setBBox(new PDRectangle(w, h));

        PDResources resources = new PDResources();
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDType1Font fontReg  = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        COSName boldName = resources.add(fontBold);
        COSName regName  = resources.add(fontReg);
        stream.setResources(resources);

        String dateStr = LocalDate.now().format(DATE_FMT);
        String pdfContent = String.format(Locale.US,
            "q\n" +
            "1 1 1 rg 0 0 %.2f %.2f re f\n" +
            "%.3f %.3f %.3f rg 0 0 4 %.2f re f\n" +
            "%.3f %.3f %.3f RG 0.5 w 0.5 0.5 %.2f %.2f re S\n" +
            "BT /%s 8 Tf %.3f %.3f %.3f rg 8 %.2f Td (Signe avec ConsignO Desktop) Tj ET\n" +
            "BT /%s 7 Tf %.3f %.3f %.3f rg 8 %.2f Td (%s) Tj ET\n" +
            "Q",
            w, h,
            0x2D / 255f, 0x72 / 255f, 0xB8 / 255f, h,
            0x2D / 255f, 0x72 / 255f, 0xB8 / 255f, w - 1, h - 1,
            boldName.getName(), 0x1C / 255f, 0x2E / 255f, 0x3D / 255f, h - 16,
            regName.getName(),  0x5A / 255f, 0x7A / 255f, 0x8A / 255f, h - 28, dateStr
        );

        try (OutputStream os = stream.getCOSObject().createOutputStream()) {
            os.write(pdfContent.getBytes(StandardCharsets.ISO_8859_1));
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
