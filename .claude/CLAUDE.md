# ConsignO Desktop — Claude Code Configuration

## Vue d'ensemble du projet

Application desktop multiplateforme de signature électronique de PDF.
- **Nom** : ConsignO Desktop
- **Stack** : JavaFX 21 + Apache PDFBox 3
- **Distribution** : installeurs natifs via `jpackage` (.exe / .dmg / .deb)
- **Utilisateurs** : professionnels techniques
- **Portabilité** : Windows, macOS, Linux — un seul codebase
- **Mode** : 100% local / offline — pas de backend

---

## Règles de workflow Git

- **Ne jamais `git push`** sans demande explicite — créer les commits localement uniquement

---

## Ce que Claude ne doit PAS générer

> Lire cette section avant toute génération de code.

- ❌ Imports `org.apache.pdfbox.*` dans `consigno-app` — PDFBox est réservé à `consigno-pdf`
- ❌ Imports `org.bouncycastle.*` dans `consigno-app` — Bouncy Castle est réservé à `consigno-crypto`
- ❌ Imports `javafx.*` dans `consigno-pdf` — ce module est headless, sans dépendance UI
- ❌ Imports `org.apache.pdfbox.*` dans `consigno-crypto` — la crypto ne dépend pas de PDFBox
- ❌ Injection de services par champ `@FXML` dans les contrôleurs — toujours par constructeur
- ❌ Appels UI (`label.setText`, `executeScript`, notifications) hors FX thread
- ❌ `PDDocument.load()` — API PDFBox 2, supprimée. Utiliser `Loader.loadPDF()`
- ❌ `doc.save()` après une signature — casse le ByteRange. Utiliser `doc.saveIncremental()`
- ❌ `System.out.println` — utiliser SLF4J
- ❌ Retourner `null` depuis une API publique — utiliser `Optional<T>`
- ❌ Stocker ou logger un mot de passe de certificat

---

## Structure des modules Maven

```
consigno-desktop/                  ← parent pom
├── consigno-app/                  ← module principal JavaFX (UI + DI)
├── consigno-pdf/                  ← Apache PDFBox, services signature/validation
├── consigno-crypto/               ← gestion certificats .p12/.pfx, KeyStore
├── consigno-common/               ← DTOs, modèles partagés, constantes
└── consigno-packaging/            ← configuration jpackage, ressources installeur
```

### Dépendances inter-modules autorisées

| Module           | Peut importer                                      |
|------------------|----------------------------------------------------|
| `consigno-app`   | `consigno-pdf`, `consigno-crypto`, `consigno-common` |
| `consigno-pdf`   | `consigno-crypto`, `consigno-common`               |
| `consigno-crypto`| `consigno-common` uniquement                      |
| `consigno-common`| aucun module interne                               |

### Interdictions d'imports explicites

```
consigno-app    ✗ org.apache.pdfbox.*
consigno-app    ✗ org.bouncycastle.*
consigno-pdf    ✗ javafx.*
consigno-crypto ✗ org.apache.pdfbox.*
```

---

## Commandes essentielles

```bash
# Compilation complète
mvn clean compile -q

# Lancer l'application en dev
mvn -pl consigno-app javafx:run

# Tests unitaires
mvn test -q

# Tests d'un module spécifique
mvn test -pl consigno-pdf -q

# Build complet avec installeur
mvn clean package -Ppackaging

# Vérification checkstyle
mvn checkstyle:check -q
```

---

## Stack technique et versions

| Technologie              | Version   | Usage                                      |
|--------------------------|-----------|--------------------------------------------|
| Java                     | 21 LTS    | Langage principal, records, sealed classes |
| JavaFX                   | 21        | UI desktop multiplateforme                 |
| AtlantaFX                | 2.x       | Thème moderne pour JavaFX                  |
| Apache PDFBox            | 3.x       | Signature, validation, extraction champs   |
| Bouncy Castle            | 1.78+     | Cryptographie PKI, CMS/CAdES pour PDFBox   |
| Guice                    | 7.x       | Injection de dépendances                   |
| jpackage                 | JDK 21    | Packaging natif multiplateforme            |
| Maven                    | 3.9+      | Build system, dépôt Nexus interne          |
| JUnit 5 + Mockito        | latest    | Tests unitaires                            |
| TestFX                   | 4.x       | Tests UI JavaFX                            |

---

## Règles absolues — LIRE AVANT TOUT

### Thread safety JavaFX (CRITIQUE)

JavaFX est single-threaded. Violations = crash ou comportement aléatoire.

```java
// ✅ Toujours : code UI depuis le FX thread
Platform.runLater(() -> label.setText("..."));

// ✅ Toujours : appels executeScript depuis le FX thread
Platform.runLater(() -> engine.executeScript("moveGhost(...)"));

// ❌ Jamais : modifier l'UI depuis un thread background
executor.submit(() -> label.setText("...")); // CRASH

// ✅ Tâches longues en background, résultat sur FX thread
Task<SignatureResult> task = new Task<>() {
    @Override protected SignatureResult call() throws Exception {
        return signatureService.sign(params); // background OK
    }
};
task.setOnSucceeded(e -> updateUI(task.getValue())); // FX thread OK
executor.submit(task);
```

### Coordonnées PDF (CRITIQUE)

L'axe Y PDF est **inversé** par rapport aux coordonnées écran.
- PDF : origine **en bas à gauche**, Y croît vers le haut
- Écran : origine **en haut à gauche**, Y croît vers le bas
- Unités PDFBox = **points typographiques** (1 pt = 1/72 pouce)

### Passage d'images Java → JS

Convertir en base64 PNG avant injection dans `executeScript` :

```java
// Dans PdfViewerPane
private String imageToBase64(Image fxImage) {
    BufferedImage bimg = SwingFXUtils.fromFXImage(fxImage, null);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(bimg, "png", baos);
    return Base64.getEncoder().encodeToString(baos.toByteArray());
}
```

---

## Architecture UI — JavaFX

### Bootstrap Guice (consigno-app)

Le DI est initialisé dans `ConsignoApplication.start()` via un `Injector` Guice.
Les contrôleurs FXML sont instanciés par une `GuidedControllerFactory` qui délègue à Guice.

**Règles Guice** :
- Les modules Guice sont définis dans `consigno-app/app/`
- Les services sont systématiquement liés en `Singleton` (thread-safety par conception)
- Ne jamais appeler `new MonService()` dans un contrôleur — toujours injecter

### Gestion des erreurs — Notifications AtlantaFX

**Toute erreur métier remontée à l'utilisateur passe par `NotificationService`**, jamais par une `Alert` JavaFX standard ni un `System.out`.

**Règles notifications** :
- `NotificationService` est le **seul** point d'entrée pour l'affichage d'erreurs
- Toujours loguer la cause avec SLF4J dans `showError` avant d'afficher la notification
- `Notifications.create()` doit être appelé sur le FX thread — `Platform.runLater()` obligatoire si appelé depuis un callback background
- Enregistrer le membre JS uniquement après `Worker.State.SUCCEEDED`, pas avant

### Structure des contrôleurs

Pattern : un contrôleur FXML par écran, injection des services par constructeur (pas par champ).

```java
// ✅ Injection constructeur — compatible Guice + testable
public class SignatureController {
    private final SignatureViewModel viewModel;
    private final NotificationService notificationService;

    @Inject
    public SignatureController(SignatureViewModel vm, NotificationService ns) {
        this.viewModel = vm;
        this.notificationService = ns;
    }
}

// ❌ Injection champ — impossible à tester, incompatible avec Guice/FXML
@FXML private SignatureService signatureService;
```

### Organisation des packages (module consigno-app)

```
com.consigno.desktop/
├── app/              ← Application, modules Guice, bootstrap
├── controller/       ← Contrôleurs FXML
├── view/             ← Composants JavaFX custom
│   └── pdf/          ← PdfViewerPane, SignatureOverlay
├── viewmodel/        ← ViewModels (bindings JavaFX Property)
├── service/          ← NotificationService et autres services UI
└── util/             ← Helpers UI, converters
```

---

## Module consigno-pdf — Apache PDFBox 3

### APIs de service exposées

Tous les services reçoivent les PDFs **par `Path`** (chemin fichier). Pas de surcharge `byte[]` ou `InputStream` dans les APIs publiques.

```java
public interface PdfSignatureService {
    SignatureResult sign(SignatureRequest request) throws PdfSignatureException;
    List<SignatureInfo> getSignatures(Path pdfPath) throws PdfException;
}

public interface PdfValidationService {
    ValidationResult validate(Path pdfPath) throws PdfValidationException;
    ValidationResult validateSignature(Path pdfPath, String signatureName) throws PdfValidationException;
}

public interface PdfFormService {
    List<FormField> extractFields(Path pdfPath) throws PdfException;
    void fillFields(Path inputPdf, Path outputPdf, Map<String, String> values) throws PdfException;
}
```

PDFBox est **thread-safe en lecture** mais pas en écriture. Chaque opération d'écriture (signature, remplissage) ouvre et ferme son propre `PDDocument`.

### Pattern PDFBox — toujours utiliser try-with-resources

```java
// ✅ Correct — fermeture garantie
try (PDDocument doc = Loader.loadPDF(pdfPath.toFile())) {
    // ... lecture / manipulation
}

// ❌ Fuite mémoire — PDFBox charge le PDF en mémoire
PDDocument doc = Loader.loadPDF(pdfPath.toFile());
// oubli de doc.close() = fuite

```

**Points critiques PDFBox 3** :
- `doc.saveIncremental()` est **obligatoire** pour les révisions incrémentales (ne pas écraser le PDF original)
- PDFBox est **0-based** pour les pages (`pageNumber - 1`)
- Le `ByteRange` est géré automatiquement par PDFBox — ne pas le manipuler manuellement
- Utiliser `Loader.loadPDF()` (PDFBox 3) et non `PDDocument.load()` (PDFBox 2, supprimé)

### SignaturePosition — modèle de position

```java
public record SignaturePosition(
    int pageNumber,      // 1-based (converti en 0-based pour PDFBox)
    double x,           // en points PDF, origine bas-gauche (PDFBox natif)
    double y,           // en points PDF, origine bas-gauche (PDFBox natif)
    double width,       // en points PDF
    double height       // en points PDF
) {
    // Conversion vers PDRectangle PDFBox
    public PDRectangle toPDRectangle() {
        return new PDRectangle((float) x, (float) y, (float) width, (float) height);
    }
}
```

---

## Module consigno-crypto

- Chargement `.p12` / `.pfx` via `java.security.KeyStore` — pas de lib tierce
- La signature CMS (bytes signés) est produite ici via **Bouncy Castle** et consommée par `consigno-pdf`
- Le mot de passe du certificat n'est **jamais** stocké sur disque ni dans les logs
- Effacer le `char[]` du mot de passe après usage (`Arrays.fill(password, '\0')`)
- Stocker le chemin du certificat dans les préférences utilisateur (`java.util.prefs.Preferences`)

```java
public interface CryptoService {
    // Produit une signature CMS detached (PKCS#7) pour PDFBox
    byte[] signCms(byte[] contentToSign, CertificateInfo cert) throws CryptoException;
    CertificateInfo loadCertificate(Path p12Path, char[] password) throws CertificateException;
    boolean isCertificateValid(CertificateInfo cert);
}
```

---

## Conventions de code

- Java 21 : utiliser `records`, `sealed classes`, `pattern matching instanceof`, `switch expressions`
- Pas de `null` exposé dans les APIs publiques — utiliser `Optional<T>`
- Exceptions métier : hiérarchie dans `consigno-common/exception/`
- Logs : SLF4J uniquement, jamais `System.out.println`
- Nommage FXML : `kebab-case.fxml` ; contrôleurs correspondants : `NomController.java`
- Pas de logique métier dans les contrôleurs FXML — déléguer aux ViewModels/Services

---

## Tests

```bash
# Conventions de nommage
*Test.java          → tests unitaires (JUnit 5 + Mockito)
*IT.java            → tests d'intégration
*UITest.java        → tests JavaFX (TestFX, headless via Monocle)
```

Tests JavaFX en headless (CI) :
```
-Djava.awt.headless=true
-Dtestfx.robot=glass
-Dtestfx.headless=true
-Dprism.order=sw
```

---

## Packaging jpackage

Commande de référence (exécutée par le module `consigno-packaging`) :

```bash
jpackage \
  --input target/libs \
  --main-jar consigno-app.jar \
  --main-class com.consigno.desktop.app.ConsignoApplication \
  --name "ConsignO Desktop" \
  --app-version ${project.version} \
  --icon src/main/resources/icons/consigno.icns \  # .ico sur Windows, .png sur Linux
  --java-options "--add-opens javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED" \
  --type dmg   # ou msi, deb, rpm selon l'OS cible
```

---

## Pitfalls connus

2. **executeScript thread** : doit être sur le FX thread — wraper dans `Platform.runLater`.
4. **jpackage macOS** : nécessite signature Apple Developer pour Gatekeeper. Prévoir codesign dans le pipeline CI.
5. **PDFBox module system** : PDFBox 3 et Bouncy Castle nécessitent des `--add-opens` dans les options JVM de jpackage. Ajouter dans le POM de `consigno-packaging` : `--add-opens java.base/java.security=ALL-UNNAMED`.
7. **PDFBox saveIncremental** : ne jamais utiliser `save()` après une signature — cela casse le ByteRange. Toujours `saveIncremental()` vers un flux de sortie distinct.
8. **PDFBox 0-based** Penser à faire `pageNumber - 1` à chaque appel PDFBox.
9. **JavaBridge + Guice** : `JavaBridge` est un singleton Guice, mais `engine.setMember()` doit être appelé **après** `Worker.State.SUCCEEDED` à chaque chargement d'un rechargement).
10. **Guice + FXML** : sans `setControllerFactory(injector::getInstance)`, JavaFX instancie les contrôleurs via le constructeur no-arg — l'injection Guice est silencieusement ignorée.
