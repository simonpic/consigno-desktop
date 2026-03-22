package com.consigno.pdf.service;

import com.consigno.common.exception.PdfException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Implémentation PDFBox de {@link PdfRenderService}.
 *
 * <p>Ouvre le document une seule fois par appel à {@link #renderDocument},
 * rend toutes les pages séquentiellement, puis ferme le document.
 */
public class PdfBoxRenderServiceImpl implements PdfRenderService {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxRenderServiceImpl.class);

    @Override
    public void renderDocument(Path pdfPath, float dpi,
                               BiConsumer<Integer, RenderedPage> onPageReady) throws PdfException {
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
            PDFRenderer renderer = new PDFRenderer(doc);
            int pageCount = doc.getNumberOfPages();
            log.debug("Rendu de {} page(s) à {} DPI — {}", pageCount, dpi, pdfPath.getFileName());

            for (int i = 0; i < pageCount; i++) {
                PDPage page = doc.getPage(i);
                PDRectangle mediaBox = page.getMediaBox();
                BufferedImage image = renderer.renderImageWithDPI(i, dpi);
                onPageReady.accept(i, new RenderedPage(image, mediaBox.getWidth(), mediaBox.getHeight()));
                log.debug("Page {} rendue ({} x {} pts)", i + 1,
                        mediaBox.getWidth(), mediaBox.getHeight());
            }
        } catch (IOException e) {
            throw new PdfException("Impossible de rendre le PDF : " + pdfPath.getFileName(), e);
        }
    }
}
