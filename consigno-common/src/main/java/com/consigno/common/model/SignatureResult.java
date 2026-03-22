package com.consigno.common.model;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Résultat d'une opération de signature PDF.
 */
public record SignatureResult(
        Path outputPdf,
        boolean success,
        String errorMessage
) {
    /** Construit un résultat de succès. */
    public static SignatureResult success(Path outputPdf) {
        return new SignatureResult(outputPdf, true, null);
    }

    /** Construit un résultat d'échec. */
    public static SignatureResult failure(String errorMessage) {
        return new SignatureResult(null, false, errorMessage);
    }

    /**
     * Retourne le message d'erreur si présent.
     */
    public Optional<String> errorMessageOptional() {
        return Optional.ofNullable(errorMessage);
    }
}
