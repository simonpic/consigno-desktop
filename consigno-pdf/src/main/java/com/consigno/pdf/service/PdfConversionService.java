package com.consigno.pdf.service;

import com.consigno.common.exception.PdfConversionException;

import java.nio.file.Path;

/**
 * Service de conversion de documents PDF vers des formats conformes à des normes d'archivage.
 */
public interface PdfConversionService {

    /**
     * Convertit {@code inputPdf} en PDF/A-1b et écrit le résultat dans {@code outputPdf}.
     *
     * @param inputPdf  chemin du PDF source
     * @param outputPdf chemin du fichier de destination (sera créé ou écrasé)
     * @throws PdfConversionException si la conversion échoue ou si le moteur de conversion est indisponible
     */
    void convertToPdfA(Path inputPdf, Path outputPdf) throws PdfConversionException;

    /**
     * Retourne {@code true} si le moteur de conversion (Ghostscript) est disponible sur ce système.
     */
    boolean isAvailable();
}
