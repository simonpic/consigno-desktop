package com.consigno.common.model;

/**
 * Position et dimensions d'une signature sur une page PDF.
 *
 * <p>Toutes les coordonnées sont en points typographiques (1 pt = 1/72 pouce),
 * avec l'origine en bas à gauche (convention PDF / PDFBox).
 *
 * <p>Note : {@code pageNumber} est 1-based
 * PDFBox utilise un index 0-based — penser à faire {@code pageNumber - 1} lors des appels PDFBox.
 *
 * <p>La conversion vers {@code PDRectangle} PDFBox est effectuée dans {@code consigno-pdf}
 * pour respecter la séparation des modules.
 */
public record SignaturePosition(
        int pageNumber,
        double x,
        double y,
        double width,
        double height
) {
    public SignaturePosition {
        if (pageNumber < 1) throw new IllegalArgumentException("pageNumber doit être >= 1");
        if (width <= 0) throw new IllegalArgumentException("width doit être > 0");
        if (height <= 0) throw new IllegalArgumentException("height doit être > 0");
    }
}
