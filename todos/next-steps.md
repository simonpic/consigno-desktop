# ConsignO Desktop — Prochaines étapes

---

## 2. Apparence visuelle de la signature

Ajouter une vignette visible dans le PDF signé via `PDAppearanceStream` (PDFBox).
À implémenter dans `PdfBoxSignatureServiceImpl`, méthode `buildVisualSignature()`.

**Contenu typique de la vignette :**
- Nom du signataire
- Date et heure
- Raison / lieu
- Logo optionnel (image PNG embarquée)

```java
// Dans PdfBoxSignatureServiceImpl
private PDVisibleSignDesigner buildVisualSignature(SignatureRequest request) {
    // Utiliser PDVisibleSignDesigner + PDVisibleSigProperties de PDFBox
    // ou construire manuellement un PDAppearanceStream avec PDPageContentStream
}
```

Référence PDFBox : `examples/src/main/java/org/apache/pdfbox/examples/signature/`

---

## 3. Tests unitaires

### consigno-crypto
```
CryptoServiceTest.java
├── loadCertificate_validP12_returnsCertificateInfo()
├── loadCertificate_wrongPassword_throwsCertificateException()
├── signCms_returnsValidCmsBytes()
└── password_isErasedAfterUsage()
```

### consigno-pdf
```
PdfBoxSignatureServiceTest.java
├── sign_producesValidSignedPdf()
├── sign_outputFileExists()
└── getSignatures_returnsCorrectCount()

PdfBoxValidationServiceTest.java
├── validate_signedPdf_returnsValid()
└── validate_tamperedPdf_returnsInvalid()

PdfBoxFormServiceTest.java
├── extractFields_acroFormPdf_returnsFields()
└── fillFields_writesCorrectValues()
```

Ressources de test : placer des PDFs de test dans `src/test/resources/` de chaque module.

---

## 4. Sélecteur de fichier de sortie

Actuellement le PDF signé est nommé automatiquement `nom_signé.pdf`.
Ajouter un `FileChooser` dans `MainController.handleSign()` pour que l'utilisateur
choisisse le chemin de sortie avant de signer.

---

## 5. Préférences utilisateur

Persister le chemin du dernier certificat utilisé via `java.util.prefs.Preferences` :

```java
// Dans MainController ou un PreferencesService
Preferences prefs = Preferences.userNodeForPackage(ConsignoApplication.class);
prefs.put("lastP12Path", p12Path.toString());
// Au démarrage : p12PathField.setText(prefs.get("lastP12Path", ""));
```

---

## 6. Packaging installeur

Une fois l'application stable, générer les installeurs natifs :

```bash
# Windows (.msi)
mvn clean package -Ppackaging

# Ou manuellement :
jpackage \
  --input consigno-app/target/libs \
  --main-jar consigno-app-1.0.0-SNAPSHOT.jar \
  --main-class com.consigno.desktop.app.ConsignoApplication \
  --name "ConsignO Desktop" \
  --app-version 1.0.0 \
  --java-options "--add-opens java.base/java.security=ALL-UNNAMED" \
  --type msi
```

---

## Note architecture

Le rendu PDF utilise désormais **PDFBox `PDFRenderer`** (option B) :
- `PdfRenderService` / `PdfBoxRenderServiceImpl` dans `consigno-pdf`
- `PdfViewerPane` en JavaFX pur (ScrollPane + ImageView par page)
- Plus de WebView, plus de JS bridge, plus de PDF.js
- Les fichiers `pdfjs/` en resources ont été supprimés

## Ordre suggéré

```
[ ] 1. Notifications        — UX de base (priorité haute)
[ ] 2. Tests unitaires      — fiabilité (priorité haute)
[ ] 3. Apparence signature  — valeur métier (priorité moyenne)
[ ] 4. Sélecteur sortie     — UX (priorité moyenne)
[ ] 5. Préférences          — confort (priorité basse)
[ ] 6. Packaging            — distribution (dernière étape)
```
