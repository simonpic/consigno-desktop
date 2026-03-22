package com.consigno.pdf.service;

import com.consigno.common.exception.PdfException;
import com.consigno.pdf.model.FormField;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDListBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Implémentation PDFBox 3 du service de manipulation des formulaires PDF.
 *
 * <p>Extrait et remplit les champs AcroForm.
 * Le remplissage utilise {@code doc.save()} (correct pour les formulaires —
 * contrairement à la signature qui exige {@code saveIncremental()}).
 */
public class PdfBoxFormServiceImpl implements PdfFormService {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxFormServiceImpl.class);

    @Override
    public List<FormField> extractFields(Path pdfPath) throws PdfException {
        log.debug("Extraction des champs de formulaire de : {}", pdfPath);
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {

            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                log.debug("Aucun AcroForm trouvé dans {}", pdfPath.getFileName());
                return List.of();
            }

            List<FormField> fields = collectFields(acroForm.getFields());
            log.debug("{} champ(s) extrait(s) de {}", fields.size(), pdfPath.getFileName());
            return List.copyOf(fields);

        } catch (IOException e) {
            throw new PdfException("Erreur lors de l'extraction des champs de : " + pdfPath, e);
        }
    }

    @Override
    public void fillFields(Path inputPdf, Path outputPdf, Map<String, String> values) throws PdfException {
        log.info("Remplissage de {} champ(s) dans {}", values.size(), inputPdf.getFileName());

        try (PDDocument doc = Loader.loadPDF(inputPdf.toFile())) {

            PDAcroForm acroForm = doc.getDocumentCatalog().getAcroForm();
            if (acroForm == null) {
                throw new PdfException("Le PDF ne contient pas de formulaire AcroForm : " + inputPdf);
            }

            List<String> notFound = new ArrayList<>();
            for (Map.Entry<String, String> entry : values.entrySet()) {
                PDField field = acroForm.getField(entry.getKey());
                if (field == null) {
                    notFound.add(entry.getKey());
                    continue;
                }
                field.setValue(entry.getValue());
                log.debug("Champ '{}' rempli avec '{}'", entry.getKey(),
                        field instanceof PDSignatureField ? "[signature]" : entry.getValue());
            }

            if (!notFound.isEmpty()) {
                log.warn("Champs introuvables dans le formulaire : {}", notFound);
            }

            // save() est correct ici (remplissage de formulaire, pas signature)
            doc.save(outputPdf.toFile());
            log.info("Formulaire rempli enregistré : {}", outputPdf);

        } catch (PdfException e) {
            throw e;
        } catch (IOException e) {
            throw new PdfException("Erreur lors du remplissage du formulaire de : " + inputPdf, e);
        }
    }

    /** Parcourt récursivement la hiérarchie des champs AcroForm. */
    private List<FormField> collectFields(List<PDField> fields) {
        List<FormField> result = new ArrayList<>();
        if (fields == null) return result;

        for (PDField field : fields) {
            if (field instanceof PDNonTerminalField nonTerminal) {
                result.addAll(collectFields(nonTerminal.getChildren()));
            } else {
                result.add(convertField(field));
            }
        }
        return result;
    }

    private FormField convertField(PDField field) {
        FormField.FieldType type = switch (field) {
            case PDTextField      ignored -> FormField.FieldType.TEXT;
            case PDCheckBox       ignored -> FormField.FieldType.CHECKBOX;
            case PDRadioButton    ignored -> FormField.FieldType.RADIO;
            case PDComboBox       ignored -> FormField.FieldType.COMBO_BOX;
            case PDListBox        ignored -> FormField.FieldType.LIST_BOX;
            case PDSignatureField ignored -> FormField.FieldType.SIGNATURE;
            case PDPushButton     ignored -> FormField.FieldType.BUTTON;
            default                       -> FormField.FieldType.UNKNOWN;
        };

        List<String> options = (field instanceof PDListBox lb) ? lb.getOptions() :
                               (field instanceof PDComboBox cb) ? cb.getOptions() :
                               List.of();

        return new FormField(
                field.getFullyQualifiedName(),
                type,
                field.getValueAsString(),
                field.isReadOnly(),
                field.isRequired(),
                options
        );
    }
}
