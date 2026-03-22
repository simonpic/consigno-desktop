package com.consigno.crypto.service;

import com.consigno.common.exception.CryptoException;
import com.consigno.common.model.CertificateInfo;

import java.nio.file.Path;
import java.security.cert.CertificateException;

/**
 * Service de cryptographie PKI.
 *
 * <p>Responsabilités :
 * <ul>
 *   <li>Chargement des certificats .p12/.pfx via {@link java.security.KeyStore}</li>
 *   <li>Production de la signature CMS detached (PKCS#7) pour PDFBox</li>
 *   <li>Vérification de validité des certificats</li>
 * </ul>
 *
 * <p>Règles de sécurité :
 * <ul>
 *   <li>Le mot de passe n'est jamais stocké ni loggué</li>
 *   <li>Le tableau {@code char[]} du mot de passe est effacé après usage</li>
 * </ul>
 */
public interface CryptoService {

    /**
     * Produit une signature CMS detached (PKCS#7) pour PDFBox.
     *
     * @param contentToSign  contenu à signer (hors ByteRange, fourni par PDFBox)
     * @param cert           certificat à utiliser pour signer
     * @param password       mot de passe du certificat — sera effacé après usage
     * @return               bytes de la signature CMS
     * @throws CryptoException si la signature échoue
     */
    byte[] signCms(byte[] contentToSign, CertificateInfo cert, char[] password) throws CryptoException;

    /**
     * Charge et valide un certificat .p12/.pfx.
     *
     * @param p12Path  chemin vers le fichier .p12 ou .pfx
     * @param password mot de passe du certificat — sera effacé après usage
     * @return         informations du certificat
     * @throws CertificateException si le certificat est invalide ou inaccessible
     */
    CertificateInfo loadCertificate(Path p12Path, char[] password) throws CertificateException;

    /**
     * Vérifie si un certificat est dans sa période de validité.
     *
     * @param cert  informations du certificat à vérifier
     * @return      {@code true} si le certificat est valide aujourd'hui
     */
    boolean isCertificateValid(CertificateInfo cert);
}
