package com.consigno.pdf.service;

import com.consigno.common.exception.PdfException;
import com.consigno.common.exception.PdfSignatureException;
import com.consigno.common.model.SignatureRequest;
import com.consigno.common.model.SignatureResult;
import com.consigno.pdf.model.SignatureInfo;

import java.nio.file.Path;
import java.util.List;

/**
 * Service de signature électronique de documents PDF.
 *
 * <p>Les PDFs sont transmis par {@link Path}. Le mot de passe du certificat
 * est passé séparément et effacé par le service après usage.
 */
public interface PdfSignatureService {

    /**
     * Signe un document PDF selon les paramètres fournis.
     *
     * @param request   paramètres de la signature (chemins, certificat, position)
     * @param password  mot de passe du certificat — sera effacé par {@code CryptoService}
     * @return          résultat de la signature avec le chemin du PDF signé
     * @throws PdfSignatureException si la signature échoue
     */
    SignatureResult sign(SignatureRequest request, char[] password) throws PdfSignatureException;

    /**
     * Retourne la liste des signatures présentes dans un document PDF.
     *
     * @param pdfPath  chemin vers le document PDF
     * @return         liste des signatures (peut être vide)
     * @throws PdfException si le document ne peut pas être lu
     */
    List<SignatureInfo> getSignatures(Path pdfPath) throws PdfException;
}
