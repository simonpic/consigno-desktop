package com.consigno.pdf.service;

import com.consigno.common.exception.PdfException;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Service de rendu des pages d'un document PDF en images matricielles.
 *
 * <p>Conçu pour alimenter un composant JavaFX sans exposer de types PDFBox
 * hors du module {@code consigno-pdf}.
 */
public interface PdfRenderService {

    /**
     * Ouvre le PDF, rend chaque page à la résolution donnée, et invoque le
     * consumer pour chaque page rendue. Le document est fermé après rendu de
     * toutes les pages.
     *
     * <p>Le consumer est appelé séquentiellement dans le thread appelant,
     * de la page 0 à la dernière.
     *
     * @param pdfPath     chemin vers le fichier PDF
     * @param dpi         résolution de rendu en points par pouce (ex. 96, 150)
     * @param onPageReady appelé pour chaque page — {@code (pageIndex 0-based, RenderedPage)}
     * @throws PdfException si le fichier ne peut pas être lu ou rendu
     */
    void renderDocument(Path pdfPath, float dpi, BiConsumer<Integer, RenderedPage> onPageReady)
            throws PdfException;

    /**
     * Données d'une page rendue.
     *
     * @param image     image matricielle produite par le moteur de rendu
     * @param widthPts  largeur de la page en points PDF (1 pt = 1/72 pouce)
     * @param heightPts hauteur de la page en points PDF
     */
    record RenderedPage(BufferedImage image, float widthPts, float heightPts) {}
}
