package com.consigno.pdf.service;

import com.consigno.common.exception.PdfException;
import com.consigno.pdf.model.FormField;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Service de manipulation des formulaires PDF (AcroForm).
 *
 * <p>Permet d'extraire et de remplir les champs d'un formulaire PDF.
 * Le remplissage produit un nouveau fichier PDF — le fichier source n'est jamais modifié.
 */
public interface PdfFormService {

    /**
     * Extrait tous les champs de formulaire d'un document PDF.
     *
     * @param pdfPath  chemin vers le document PDF
     * @return         liste des champs (vide si le PDF n'a pas d'AcroForm)
     * @throws PdfException si le document ne peut pas être lu
     */
    List<FormField> extractFields(Path pdfPath) throws PdfException;

    /**
     * Remplit les champs de formulaire et écrit le résultat dans un nouveau fichier.
     *
     * @param inputPdf   chemin vers le PDF source
     * @param outputPdf  chemin vers le PDF de sortie (sera créé ou écrasé)
     * @param values     map nom-de-champ → valeur à remplir
     * @throws PdfException si le remplissage échoue
     */
    void fillFields(Path inputPdf, Path outputPdf, Map<String, String> values) throws PdfException;
}
