package com.consigno.common.exception;

/**
 * Exception levée lors d'une erreur de conversion PDF (ex : PDF → PDF/A).
 */
public class PdfConversionException extends PdfException {

    public PdfConversionException(String message) {
        super(message);
    }

    public PdfConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
