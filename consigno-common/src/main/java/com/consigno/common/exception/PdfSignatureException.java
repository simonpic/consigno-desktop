package com.consigno.common.exception;

/**
 * Exception levée lors d'une erreur dans le processus de signature PDF.
 */
public class PdfSignatureException extends PdfException {

    public PdfSignatureException(String message) {
        super(message);
    }

    public PdfSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
