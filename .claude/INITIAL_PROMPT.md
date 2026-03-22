# Prompt initial — ConsignO Desktop

## Contexte

Tu es Claude Code, tu travailles sur **ConsignO Desktop**, une application desktop JavaFX de signature électronique de PDF. La configuration complète du projet est dans `CLAUDE.md`.

Lis **CLAUDE.md en entier** avant de faire quoi que ce soit.

## Mission de démarrage

Crée la structure Maven multi-modules complète du projet selon l'architecture définie dans CLAUDE.md.

### Ce que tu dois générer

**1. Parent POM (`pom.xml` racine)**
- `groupId` : `com.consigno`
- `artifactId` : `consigno-desktop`
- `version` : `1.0.0-SNAPSHOT`
- Déclarer les 5 modules enfants
- Dépendances managées :
  - JavaFX 21 (`javafx-controls`, `javafx-fxml`, `javafx-web`, `javafx-swing`)
  - AtlantaFX 2.x
  - Apache PDFBox 3.x (`pdfbox`)
  - Bouncy Castle 1.78+ (`bcprov-jdk18on`, `bcpkix-jdk18on`)
  - JUnit 5, Mockito, TestFX 4.x
- Plugin `maven-compiler-plugin` configuré pour Java 21
- Plugin `javafx-maven-plugin` configuré dans `consigno-app`

**2. POM de chaque module**
- `consigno-common` : aucune dépendance interne, modèles partagés
- `consigno-crypto` : dépend de `consigno-common` + Bouncy Castle
- `consigno-pdf` : dépend de `consigno-crypto`, `consigno-common` + PDFBox 3
- `consigno-app` : dépend de tous les autres modules + JavaFX 21 + AtlantaFX
- `consigno-packaging` : module de packaging jpackage uniquement (pas de code Java)

**3. Classes de démarrage dans `consigno-app`**
- `ConsignoApplication.java` : point d'entrée `Application` JavaFX
- `MainView.java` : première scène (placeholder acceptable)
- `module-info.java` : déclaration du module Java avec les `requires` nécessaires (JavaFX, PDFBox, Bouncy Castle)

**4. Interfaces de service**
- `PdfSignatureService.java` dans `consigno-pdf`
- `PdfValidationService.java` dans `consigno-pdf`
- `PdfFormService.java` dans `consigno-pdf`
- `CryptoService.java` dans `consigno-crypto`

**5. Modèles dans `consigno-common`**
- `SignaturePosition.java` (record, avec méthode `toPDRectangle()`)
- `SignatureRequest.java` (record)
- `CertificateInfo.java` (record)
- `SignatureResult.java` (record)
- `ValidationResult.java` (record)
- Hiérarchie d'exceptions dans `exception/` : `PdfException`, `PdfSignatureException`, `PdfValidationException`, `CryptoException`


**7. Configuration de base**
- `consigno-app/src/main/resources/logback.xml`
- `.gitignore` adapté Java/Maven/IntelliJ
- `.editorconfig`

### Contraintes à respecter

- Respecter scrupuleusement la hiérarchie de dépendances de CLAUDE.md
- `module-info.java` dans `consigno-app` (Java module system)
- Ajouter les `--add-opens java.base/java.security=ALL-UNNAMED` nécessaires à PDFBox dans les options JVM du plugin javafx-maven-plugin
- Pas de dépendance Spring Boot — application 100% offline

### Une fois la structure créée

1. Vérifie que `mvn compile -q` passe sans erreur
2. Lance `mvn -pl consigno-app javafx:run` pour confirmer que l'application démarre
3. Donne un résumé des fichiers créés et des prochaines étapes suggérées

---

**Commence par lire CLAUDE.md, puis génère la structure.**
