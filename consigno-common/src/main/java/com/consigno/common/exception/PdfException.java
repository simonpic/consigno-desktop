package com.consigno.common.exception;

/**
 * Exception racine pour toutes les erreurs liées aux opérations PDF.
 */
public class PdfException extends Exception {

    public PdfException(String message) {
        super(message);
    }

    public PdfException(String message, Throwable cause) {
        super(message, cause);
    }
}
