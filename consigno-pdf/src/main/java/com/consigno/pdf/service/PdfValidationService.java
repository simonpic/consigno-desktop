package com.consigno.pdf.service;

import com.consigno.common.exception.PdfValidationException;
import com.consigno.common.model.ValidationResult;

import java.nio.file.Path;

/**
 * Service de validation des signatures d'un document PDF.
 *
 * <p>Vérifie l'intégrité des signatures et la validité des certificats.
 */
public interface PdfValidationService {

    /**
     * Valide toutes les signatures d'un document PDF.
     *
     * @param pdfPath  chemin vers le document PDF
     * @return         résultat de validation global avec détail par signature
     * @throws PdfValidationException si le document ne peut pas être lu ou analysé
     */
    ValidationResult validate(Path pdfPath) throws PdfValidationException;

    /**
     * Valide une signature spécifique dans un document PDF.
     *
     * @param pdfPath        chemin vers le document PDF
     * @param signatureName  nom du champ de signature à valider
     * @return               résultat de validation pour cette signature
     * @throws PdfValidationException si la signature n'existe pas ou ne peut pas être validée
     */
    ValidationResult validateSignature(Path pdfPath, String signatureName) throws PdfValidationException;
}
