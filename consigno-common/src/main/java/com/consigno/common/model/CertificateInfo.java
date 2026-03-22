package com.consigno.common.model;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Informations extraites d'un certificat X.509 (.p12 / .pfx).
 *
 * <p>Tous les champs sont des types Java standard — aucune dépendance vers Bouncy Castle
 * ni java.security dans cette classe. La conversion est faite dans {@code consigno-crypto}.
 */
public record CertificateInfo(
        String commonName,
        String issuer,
        String serialNumber,
        LocalDate validFrom,
        LocalDate validTo,
        Path p12Path
) {
    /**
     * Retourne le chemin vers le fichier .p12/.pfx si disponible.
     */
    public Optional<Path> p12PathOptional() {
        return Optional.ofNullable(p12Path);
    }

    /**
     * Indique si le certificat est actuellement dans sa période de validité.
     */
    public boolean isCurrentlyValid() {
        LocalDate today = LocalDate.now();
        return !today.isBefore(validFrom) && !today.isAfter(validTo);
    }
}
