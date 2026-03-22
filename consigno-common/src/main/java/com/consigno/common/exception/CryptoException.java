package com.consigno.common.exception;

/**
 * Exception levée lors d'une erreur cryptographique (chargement certificat, signature CMS, etc.).
 */
public class CryptoException extends Exception {

    public CryptoException(String message) {
        super(message);
    }

    public CryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
