---
name: javafx-consigno
description: >
  Expert JavaFX 21 pour ConsignO Desktop. Utilise ce skill dès que tu touches
  au module consigno-app : contrôleurs FXML, composants custom, handlers, thread
  safety, Guice DI, AtlantaFX, bindings, ou tout code UI. Active-le aussi quand
  tu dois ajouter un nouveau service, un nouveau FXML, un composant Pane/VBox/Control,
  ou que tu rencontres un problème lié aux threads JavaFX. Ne jamais écrire de code
  consigno-app sans ce skill.
---

# JavaFX ConsignO — Guide de développement UI

Ce skill te permet de produire du code JavaFX de qualité production pour ConsignO Desktop.
Lis les sections pertinentes à ta tâche avant de générer du code.

---

## 1. Thread safety — règle absolue

JavaFX est single-threaded. Toute violation est un crash silencieux ou une
`IllegalStateException` aléatoire.

```java
// ✅ Modification UI depuis n'importe quel thread
Platform.runLater(() -> statusLabel.setText("Terminé"));

// ✅ Opération longue → résultat sur FX thread
Task<SignatureResult> task = new Task<>() {
    @Override protected SignatureResult call() throws Exception {
        return pdfSignatureService.sign(request); // worker thread OK
    }
};
task.setOnSucceeded(e -> {
    // FX thread — modifier l'UI ici
    notificationService.showSuccess("Signature réussie");
    fileBrowser.refresh();
});
task.setOnFailed(e -> {
    // FX thread — toujours nettoyer les ressources sensibles ici
    Arrays.fill(password, '\0');
    notificationService.showError("Échec signature", task.getException());
});
executor.execute(task);

// ❌ Ne jamais faire ça
executor.submit(() -> label.setText("...")); // CRASH ou comportement indéfini
```

### Generation tracking (stale callbacks)

Quand une opération async peut être annulée/remplacée (ex : chargement PDF),
utiliser un compteur de génération pour ignorer les callbacks obsolètes :

```java
private int loadGeneration = 0;

public void loadPdf(Path path) {
    final int gen = ++loadGeneration;
    Task<Integer> task = new Task<>() {
        @Override protected Integer call() throws Exception {
            renderService.render(path, (pageIdx, img) -> {
                Platform.runLater(() -> {
                    if (gen == loadGeneration) addPage(pageIdx, img); // ignorer si dépassé
                });
            });
            return pageCount;
        }
    };
    executor.execute(task);
}
```

### Executor standard du projet

```java
// Dans MainController et les handlers — utiliser ce pattern
private final Executor executor = Executors.newCachedThreadPool(r -> {
    Thread t = new Thread(r, "consigno-worker");
    t.setDaemon(true); // s'arrête avec l'app
    return t;
});
```

---

## 2. Guice + FXML

### Injection constructeur (toujours)

```java
// ✅ Compatible Guice + testable
public class MonController {
    private final PdfSignatureService signatureService;
    private final NotificationService notificationService;

    @Inject
    public MonController(PdfSignatureService sig, NotificationService notif) {
        this.signatureService = sig;
        this.notificationService = notif;
    }
}

// ❌ Injection champ — silencieusement ignorée par Guice/FXML
@FXML private PdfSignatureService signatureService;
```

### Charger un FXML avec Guice

```java
FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mon-ecran.fxml"));
loader.setControllerFactory(injector::getInstance); // Guice injecte les dépendances
Parent root = loader.load();
```

Sans `setControllerFactory`, JavaFX utilise le constructeur no-arg → injection silencieusement ignorée.

### Ajouter un binding Guice (AppModule)

```java
// AppModule.java
@Provides @Singleton
MonService provideMonService(AutreDep dep) {
    return new MonServiceImpl(dep);
}
```

### Modules et ordre de création

```
CryptoModule → PdfModule (dépend de CryptoService) → AppModule (dépend des deux)
```

---

## 3. Architecture handler

Le projet utilise une architecture handler (pas ViewModel). Chaque opération métier
est dans un handler dédié dans `controller/handler/`.

```
controller/
├── MainController.java          ← orchestrateur, UI uniquement
└── handler/
    ├── PdfSignHandler.java      ← Task<SignatureResult>
    ├── PdfValidateHandler.java  ← Task<ValidationResult>
    ├── PdfConvertHandler.java   ← Task<Void>
    ├── PdfSelectionHandler.java ← chargement PDF depuis FileBrowser
    ├── AdobeOpenHandler.java    ← lancement Adobe
    ├── PdfSessionState.java     ← état partagé entre handlers (currentPdf, pendingPositions)
    ├── UiControls.java          ← interface d'abstraction UI (boutons, labels, progress)
    └── CertAndPassword.java     ← record(Path certPath, char[] password)
```

### Créer un nouveau handler

```java
public class MonHandler {
    private final MonService service;
    private final NotificationService notificationService;
    private final PdfSessionState sessionState;

    public MonHandler(MonService service, NotificationService notif, PdfSessionState state) {
        this.service = service;
        this.notificationService = notif;
        this.sessionState = state;
    }

    public void handle(Executor executor, UiControls ui) {
        sessionState.getCurrentPdf().ifPresentOrElse(pdfPath -> {
            ui.setProgress(true);
            Task<ResultType> task = new Task<>() {
                @Override protected ResultType call() throws Exception {
                    return service.doWork(pdfPath);
                }
            };
            task.setOnSucceeded(e -> {
                ui.setProgress(false);
                notificationService.showSuccess("Opération réussie");
            });
            task.setOnFailed(e -> {
                ui.setProgress(false);
                notificationService.showError("Échec", task.getException());
            });
            executor.execute(task);
        }, () -> notificationService.showWarning("Aucun PDF ouvert"));
    }
}
```

---

## 4. NotificationService

**Point d'entrée unique pour toutes les erreurs et confirmations utilisateur.**
Jamais d'`Alert` JavaFX standard, jamais de `System.out`.

```java
notificationService.showSuccess("Signature ajoutée avec succès");
notificationService.showError("Impossible de signer", exception); // logue aussi via SLF4J
notificationService.showWarning("Certificat expiré dans 7 jours");
```

Pré-requis : `notificationService.setOwner(rootPane)` appelé dans `MainController.initialize()`.
Déjà thread-safe (wrappé en `Platform.runLater()` en interne).

---

## 5. Composants custom existants

### PdfViewerPane (Singleton Guice)

Composant `StackPane` injecté dans `MainController`. Gère le rendu PDF asynchrone
et le placement de markers de signature.

```java
// Charger un PDF
pdfViewerPane.loadPdf(path);

// Écouter les positions de signature sélectionnées
pdfViewerPane.setOnSignaturePositionSelected(position -> {
    sessionState.getPendingPositions().add(position);
});

// Écouter les positions supprimées (touche DELETE ou bouton marker)
pdfViewerPane.setOnSignatureRemoved(position -> {
    sessionState.getPendingPositions().remove(position);
});
```

Le composant gère lui-même le thread rendering via son propre executor `pdf-render`.

### FileBrowserPane (instanciation manuelle)

```java
FileBrowserPane fileBrowser = new FileBrowserPane();
fileBrowser.setOnPdfSelected(path -> pdfSelectionHandler.handle(path, executor, ui));
fileBrowser.setOnSignRequested(path -> signHandler.handle(path, executor, ui));
fileBrowser.setOnValidateRequested(path -> validateHandler.handle(path, executor, ui));
fileBrowser.setOnConvertRequested(path -> convertHandler.handle(path, executor, ui));
fileBrowser.refresh(); // appeler après une opération qui modifie des fichiers
```

### Créer un composant custom

```java
// Pattern standard — étendre un layout JavaFX
public class MonComposant extends VBox {

    @Inject // si géré par Guice
    public MonComposant(MonService service) {
        // Charger le FXML si besoin
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/mon-composant.fxml"));
        loader.setRoot(this);
        loader.setController(this);
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException("Impossible de charger mon-composant.fxml", e);
        }
    }
}
```

---

## 6. AtlantaFX et CSS

### Thème appliqué

```java
// ConsignoApplication.start()
Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
```

### Classes utilitaires AtlantaFX

```java
// Styles
button.getStyleClass().addAll(Styles.ACCENT);       // bouton principal bleu
button.getStyleClass().addAll(Styles.FLAT);          // bouton ghost
button.getStyleClass().addAll(Styles.SUCCESS);       // vert
button.getStyleClass().addAll(Styles.DANGER);        // rouge
button.getStyleClass().addAll(Styles.WARNING);       // orange
label.getStyleClass().addAll(Styles.TEXT_MUTED);     // texte grisé
label.getStyleClass().addAll(Styles.TEXT_BOLD);      // gras

// Tailles de texte
label.getStyleClass().addAll(Styles.TITLE_1);
label.getStyleClass().addAll(Styles.TEXT_SMALL);
```

### Tokens CSS du projet (consigno.css)

```css
/* Couleurs à utiliser dans les composants */
-color-accent-fg       /* #2D72B8 — bleu principal */
-color-fg-default      /* #1C2E3D — texte principal */
-color-fg-muted        /* #5A7A8A — texte secondaire */
-color-bg-subtle       /* #EEF3FA — fond sidebar/panels */
-color-border-default  /* #C4D5E8 — bordures */

/* Police */
-fx-font-family: 'Outfit';
```

### Ajouter un style dans consigno.css

```css
/* Cibler un composant par sa classe CSS */
.mon-composant {
    -fx-background-color: -color-bg-subtle;
    -fx-border-color: -color-border-default;
    -fx-border-radius: 6;
    -fx-background-radius: 6;
    -fx-padding: 12;
}
```

---

## 7. Contraintes modules — rappel critique

```
consigno-app  ✗  org.apache.pdfbox.*   → réservé à consigno-pdf
consigno-app  ✗  org.bouncycastle.*    → réservé à consigno-crypto
```

Dans `consigno-app`, appeler uniquement les interfaces des services :
`PdfSignatureService`, `PdfValidationService`, `PdfFormService`, `PdfRenderService`,
`PdfConversionService`, `CryptoService`.

---

## 8. Coordonnées PDF vs écran

L'axe Y est **inversé** entre PDF et écran. `SignaturePosition` stocke des coordonnées
en points PDF (origine bas-gauche). La conversion pixel→PDF est faite dans `PdfViewerPane`.

```java
// Ne jamais passer des coordonnées pixel à PdfSignatureService
// PdfViewerPane fournit directement des SignaturePosition en points PDF
pdfViewerPane.setOnSignaturePositionSelected(position -> {
    // position.x(), position.y() sont déjà en points PDF (72 DPI, origine bas-gauche)
    sessionState.getPendingPositions().add(position);
});
```

---

## 9. Checklist avant de soumettre du code consigno-app

- [ ] Toute modification UI est dans `Platform.runLater()` ou dans un callback `Task.setOnSucceeded()`
- [ ] Les services sont injectés par constructeur (pas par champ)
- [ ] Aucun import `org.apache.pdfbox.*` ou `org.bouncycastle.*`
- [ ] Les erreurs passent par `notificationService.showError()`, pas par `Alert` ni `System.out`
- [ ] Les opérations longues (I/O, signature, validation) sont dans un `Task<T>`
- [ ] Les mots de passe `char[]` sont effacés après usage (`Arrays.fill(pwd, '\0')`)
- [ ] `mvn compile -q` passe sans erreur
