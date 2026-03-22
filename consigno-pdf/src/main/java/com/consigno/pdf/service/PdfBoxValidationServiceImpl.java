package com.consigno.pdf.service;

import com.consigno.common.exception.PdfValidationException;
import com.consigno.common.model.ValidationResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Implémentation PDFBox 3 + Bouncy Castle de la validation des signatures PDF.
 *
 * <p>Vérifie :
 * <ul>
 *   <li>L'intégrité de la signature CMS (hash + clé)</li>
 *   <li>La période de validité du certificat signataire</li>
 * </ul>
 *
 * <p>Note : la validation de la chaîne de certification complète (OCSP, CRL)
 * n'est pas implémentée ici — TODO pour une révision future.
 */
public class PdfBoxValidationServiceImpl implements PdfValidationService {

    private static final Logger log = LoggerFactory.getLogger(PdfBoxValidationServiceImpl.class);

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public ValidationResult validate(Path pdfPath) throws PdfValidationException {
        log.debug("Validation de : {}", pdfPath);
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {

            List<ValidationResult.SignatureValidationInfo> sigInfos = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            boolean allValid = true;

            for (PDSignatureField field : doc.getSignatureFields()) {
                PDSignature sig = field.getSignature();
                if (sig == null) continue;

                String fieldName = field.getFullyQualifiedName();
                try {
                    ValidationResult.SignatureValidationInfo info = validateSignatureField(pdfPath, sig, fieldName);
                    sigInfos.add(info);
                    if (!info.signatureIntact() || !info.certificateValid()) {
                        allValid = false;
                    }
                } catch (Exception e) {
                    log.warn("Erreur lors de la validation de '{}' : {}", fieldName, e.getMessage());
                    errors.add("Signature '" + fieldName + "' : " + e.getMessage());
                    allValid = false;
                    sigInfos.add(new ValidationResult.SignatureValidationInfo(
                            fieldName, false, false, sig.getName(), sig.getReason(), sig.getLocation()
                    ));
                }
            }

            return new ValidationResult(allValid, sigInfos, List.of(), errors);

        } catch (IOException e) {
            throw new PdfValidationException("Impossible de lire le PDF : " + pdfPath, e);
        }
    }

    @Override
    public ValidationResult validateSignature(Path pdfPath, String signatureName) throws PdfValidationException {
        log.debug("Validation de la signature '{}' dans {}", signatureName, pdfPath);
        try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {

            for (PDSignatureField field : doc.getSignatureFields()) {
                if (signatureName.equals(field.getFullyQualifiedName())) {
                    PDSignature sig = field.getSignature();
                    if (sig == null) {
                        return new ValidationResult(false, List.of(), List.of(),
                                List.of("Le champ '" + signatureName + "' ne contient pas de signature"));
                    }
                    ValidationResult.SignatureValidationInfo info;
                    try {
                        info = validateSignatureField(pdfPath, sig, signatureName);
                    } catch (Exception ex) {
                        throw new PdfValidationException("Erreur validation signature '" + signatureName + "'", ex);
                    }
                    boolean valid = info.signatureIntact() && info.certificateValid();
                    return new ValidationResult(valid, List.of(info), List.of(), List.of());
                }
            }

            return new ValidationResult(false, List.of(), List.of(),
                    List.of("Signature introuvable : " + signatureName));

        } catch (PdfValidationException e) {
            throw e;
        } catch (IOException e) {
            throw new PdfValidationException("Impossible de lire le PDF : " + pdfPath, e);
        }
    }

    private ValidationResult.SignatureValidationInfo validateSignatureField(
            Path pdfPath, PDSignature sig, String fieldName) throws Exception {

        byte[] cmsBytes = sig.getContents();
        byte[] signedContent;
        try (InputStream is = Files.newInputStream(pdfPath)) {
            signedContent = sig.getSignedContent(is);
        }

        boolean intact = false;
        boolean certValid = false;
        String signerName = sig.getName();
        String reason = sig.getReason();
        String location = sig.getLocation();

        try {
            CMSSignedData signedData = new CMSSignedData(cmsBytes);
            CMSSignedData dataWithContent = new CMSSignedData(
                    new org.bouncycastle.cms.CMSProcessableByteArray(signedContent), cmsBytes
            );

            for (SignerInformation signerInfo : dataWithContent.getSignerInfos()) {
                Collection<X509CertificateHolder> certs =
                        dataWithContent.getCertificates().getMatches(signerInfo.getSID());

                if (certs.isEmpty()) {
                    log.warn("Aucun certificat trouvé pour le signataire dans '{}'", fieldName);
                    continue;
                }

                X509CertificateHolder certHolder = certs.iterator().next();
                intact = signerInfo.verify(
                        new JcaSimpleSignerInfoVerifierBuilder()
                                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                .build(certHolder)
                );

                // Vérification de la période de validité du certificat
                java.util.Date now = new java.util.Date();
                certValid = certHolder.isValidOn(now);
            }
        } catch (Exception e) {
            log.warn("Vérification CMS échouée pour '{}' : {}", fieldName, e.getMessage());
        }

        log.info("Signature '{}' — intact={}, certValid={}", fieldName, intact, certValid);
        return new ValidationResult.SignatureValidationInfo(
                fieldName, intact, certValid, signerName, reason, location
        );
    }
}
