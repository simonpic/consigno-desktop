package com.consigno.common.model;

import java.nio.file.Path;
import java.util.List;

/**
 * Paramètres d'une demande de signature PDF.
 *
 * <p>Les PDFs sont transmis par {@link Path} (chemin fichier).
 * Pas de surcharge {@code byte[]} ou {@code InputStream} dans l'API publique.
 *
 * <p>Une signature est unique (une opération cryptographique) mais peut avoir
 * plusieurs positions d'apparence visuelle sur le document.
 */
public record SignatureRequest(
        Path inputPdf,
        Path outputPdf,
        CertificateInfo certificate,
        List<SignaturePosition> positions,
        SignatureAppearance appearance
) {
    public SignatureRequest {
        if (inputPdf == null)    throw new IllegalArgumentException("inputPdf ne peut pas être null");
        if (outputPdf == null)   throw new IllegalArgumentException("outputPdf ne peut pas être null");
        if (certificate == null) throw new IllegalArgumentException("certificate ne peut pas être null");
        if (positions == null || positions.isEmpty())
            throw new IllegalArgumentException("positions ne peut pas être null ou vide");
        positions = List.copyOf(positions); // copie défensive immuable
    }

    /**
     * Apparence visuelle optionnelle de la signature.
     *
     * @param signerName   nom affiché sur le widget de signature
     * @param reason       raison de la signature (optionnelle)
     * @param location     lieu de signature (optionnel)
     * @param contactInfo  contact du signataire (optionnel)
     */
    public record SignatureAppearance(
            String signerName,
            String reason,
            String location,
            String contactInfo
    ) {}
}
