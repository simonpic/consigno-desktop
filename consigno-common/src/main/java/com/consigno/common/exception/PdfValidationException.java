package com.consigno.common.exception;

/**
 * Exception levée lors d'une erreur dans le processus de validation PDF.
 */
public class PdfValidationException extends PdfException {

    public PdfValidationException(String message) {
        super(message);
    }

    public PdfValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
