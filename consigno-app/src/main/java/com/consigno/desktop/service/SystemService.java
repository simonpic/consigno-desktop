package com.consigno.desktop.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Service d'intégration avec le système d'exploitation.
 *
 * <p>Détecte les applications tierces installées (Adobe Acrobat) au démarrage
 * et fournit des opérations pour les invoquer.
 */
public interface SystemService {

    /**
     * Retourne le chemin de l'exécutable Adobe Acrobat (ou Reader) détecté au démarrage,
     * ou {@link Optional#empty()} si Adobe n'est pas installé.
     */
    Optional<Path> getAdobeAcrobatPath();

    /**
     * Ouvre {@code pdf} avec Adobe Acrobat en utilisant le chemin détecté au démarrage.
     *
     * @throws IOException                   si le processus ne peut pas être démarré
     * @throws UnsupportedOperationException si Adobe n'est pas disponible
     */
    void openWithAdobeAcrobat(Path pdf) throws IOException;
}
