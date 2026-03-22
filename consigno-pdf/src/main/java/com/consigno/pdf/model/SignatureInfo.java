package com.consigno.pdf.model;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Informations sur une signature existante dans un document PDF.
 */
public record SignatureInfo(
        String name,
        String signerName,
        String reason,
        String location,
        LocalDateTime signingTime,
        boolean coverageComplete
) {
    public Optional<String> reasonOptional() {
        return Optional.ofNullable(reason);
    }

    public Optional<String> locationOptional() {
        return Optional.ofNullable(location);
    }
}
