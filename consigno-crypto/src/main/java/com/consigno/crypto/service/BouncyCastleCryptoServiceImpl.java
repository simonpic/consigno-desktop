package com.consigno.crypto.service;

import com.consigno.common.exception.CryptoException;
import com.consigno.common.model.CertificateInfo;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

/**
 * Implémentation Bouncy Castle du service cryptographique.
 *
 * <p>Produit des signatures CMS detached (PKCS#7) compatibles PDFBox 3.
 *
 * <p>Règles de sécurité :
 * <ul>
 *   <li>Le mot de passe n'est jamais stocké ni loggué</li>
 *   <li>Le tableau {@code char[]} est effacé dans le bloc {@code finally}</li>
 * </ul>
 */
public class BouncyCastleCryptoServiceImpl implements CryptoService {

    private static final Logger log = LoggerFactory.getLogger(BouncyCastleCryptoServiceImpl.class);
    private static final String ALGO_SIGN = "SHA256withRSA";

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Override
    public byte[] signCms(byte[] contentToSign, CertificateInfo cert, char[] password) throws CryptoException {
        log.debug("Signature CMS pour le certificat : {}", cert.commonName());
        Path p12Path = cert.p12Path();
        if (p12Path == null) {
            throw new CryptoException("Le chemin du certificat .p12 est absent de CertificateInfo");
        }

        KeyStore ks = null;
        PrivateKey privateKey = null;
        X509Certificate signingCert = null;

        try {
            ks = KeyStore.getInstance("PKCS12");
            try (InputStream is = Files.newInputStream(p12Path)) {
                ks.load(is, password);
            }

            String alias = ks.aliases().nextElement();
            privateKey = (PrivateKey) ks.getKey(alias, password);
            signingCert = (X509Certificate) ks.getCertificate(alias);

            if (privateKey == null) {
                throw new CryptoException("Clé privée introuvable dans le KeyStore : " + p12Path);
            }

            // Construction du signataire CMS
            ContentSigner signer = new JcaContentSignerBuilder(ALGO_SIGN)
                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                    .build(privateKey);

            JcaCertStore certStore = new JcaCertStore(List.of(signingCert));

            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(
                    new JcaSignerInfoGeneratorBuilder(
                            new JcaDigestCalculatorProviderBuilder()
                                    .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                                    .build()
                    ).build(signer, signingCert)
            );
            generator.addCertificates(certStore);

            CMSSignedData signedData = generator.generate(
                    new CMSProcessableByteArray(contentToSign),
                    false // detached — PDFBox gère l'embedding dans le ByteRange
            );

            log.debug("Signature CMS générée ({} octets)", signedData.getEncoded().length);
            return signedData.getEncoded();

        } catch (CryptoException e) {
            throw e;
        } catch (Exception e) {
            throw new CryptoException("Erreur lors de la génération de la signature CMS", e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public CertificateInfo loadCertificate(Path p12Path, char[] password) throws CertificateException {
        log.debug("Chargement du certificat : {}", p12Path);
        try {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (InputStream is = Files.newInputStream(p12Path)) {
                ks.load(is, password);
            }

            String alias = ks.aliases().nextElement();
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

            if (cert == null) {
                throw new CertificateException("Aucun certificat trouvé dans : " + p12Path);
            }

            String commonName = extractCN(cert.getSubjectX500Principal().getName());
            String issuer = cert.getIssuerX500Principal().getName();
            String serialNumber = cert.getSerialNumber().toString(16).toUpperCase();

            LocalDate validFrom = cert.getNotBefore().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate validTo = cert.getNotAfter().toInstant()
                    .atZone(ZoneId.systemDefault()).toLocalDate();

            log.info("Certificat chargé : {} (valide jusqu'au {})", commonName, validTo);
            return new CertificateInfo(commonName, issuer, serialNumber, validFrom, validTo, p12Path);

        } catch (CertificateException e) {
            throw e;
        } catch (Exception e) {
            throw new CertificateException("Impossible de charger le certificat depuis : " + p12Path, e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    @Override
    public boolean isCertificateValid(CertificateInfo cert) {
        return cert.isCurrentlyValid();
    }

    /**
     * Extrait le Common Name (CN) depuis un distinguished name X.500.
     * Ex : "CN=Jean Dupont,O=Consigno,C=FR" → "Jean Dupont"
     */
    private String extractCN(String dn) {
        if (dn == null) return "Inconnu";
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=") || trimmed.startsWith("cn=")) {
                return trimmed.substring(3);
            }
        }
        return dn;
    }
}
