# Révision de code — ConsignO Desktop

Effectue une révision approfondie du code spécifié : $ARGUMENTS

## Checklist de révision

### Thread safety JavaFX
- [ ] Toute modification de nœud JavaFX est sur le FX thread
- [ ] Les tâches longues utilisent `Task<T>`, pas `Platform.runLater()` directement

### Sécurité crypto
- [ ] Aucun mot de passe en `String` ou dans les logs
- [ ] Les `char[]` de mots de passe sont effacés dans un `finally`
- [ ] Aucune clé privée sérialisée ou exposée

### Apache PDFBox 3
- [ ] `Loader.loadPDF()` utilisé (pas `PDDocument.load()` — API PDFBox 2 supprimée)
- [ ] Tous les `PDDocument` sont dans un `try-with-resources`
- [ ] Après signature : `saveIncremental()` utilisé, jamais `save()`
- [ ] `pageNumber - 1` appliqué à chaque appel PDFBox (0-based)

### Architecture
- [ ] Aucune logique métier dans les contrôleurs FXML
- [ ] PDFBox et Bouncy Castle appelés uniquement depuis `consigno-pdf` et `consigno-crypto`
- [ ] Les dépendances inter-modules respectent la hiérarchie définie dans CLAUDE.md
- [ ] Injection par constructeur, pas par champ

### Coordonnées PDF
- [ ] Les coordonnées transmises au SDK sont en points (pas en pixels)
- [ ] L'inversion de l'axe Y est correctement gérée

### Qualité générale
- [ ] Utilisation des features Java 21 (records, sealed, pattern matching)
- [ ] Pas de `null` exposé dans les APIs publiques (`Optional<T>`)
- [ ] Messages d'erreur utilisateur en français, logs techniques en anglais
- [ ] Pas de `System.out.println` — SLF4J uniquement

## Format de sortie

1. **Problèmes bloquants** (à corriger avant tout commit)
2. **Améliorations recommandées** (bonnes pratiques)
3. **Suggestions mineures** (style, lisibilité)
4. **Code corrigé** si des problèmes bloquants sont trouvés
