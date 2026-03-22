package com.consigno.common.model;

import java.util.List;

/**
 * Résultat d'une opération de validation de signatures PDF.
 */
public record ValidationResult(
        boolean valid,
        List<SignatureValidationInfo> signatures,
        List<String> warnings,
        List<String> errors
) {
    public ValidationResult {
        signatures = List.copyOf(signatures);
        warnings = List.copyOf(warnings);
        errors = List.copyOf(errors);
    }

    /** Indique si le document est valide et ne contient aucune erreur. */
    public boolean isFullyValid() {
        return valid && errors.isEmpty();
    }

    /**
     * Informations de validation pour une signature individuelle.
     */
    public record SignatureValidationInfo(
            String name,
            boolean signatureIntact,
            boolean certificateValid,
            String signerName,
            String reason,
            String location
    ) {}
}
