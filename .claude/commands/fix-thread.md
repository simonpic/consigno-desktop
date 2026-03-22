# Diagnostic thread safety — ConsignO Desktop

Analyse et corrige les problèmes de thread safety JavaFX dans : $ARGUMENTS

## Ce que tu cherches

### Violations directes
- Appels à des méthodes JavaFX (`.setText()`, `.setVisible()`, `.getChildren()`, etc.) hors du FX thread
- `engine.executeScript()` hors `Platform.runLater()`
- Accès à des propriétés JavaFX (`Property`, `ObservableList`) depuis un thread background

### Anti-patterns
- `Platform.runLater()` wrappant des opérations longues (I/O, réseau, SDK) → utiliser `Task<T>`
- `Thread.sleep()` dans le FX thread
- Boucles d'attente dans le FX thread

## Format de sortie

Pour chaque problème trouvé :
- **Fichier + ligne**
- **Problème** : description précise
- **Risque** : crash / comportement aléatoire / gel UI
- **Correction** : code corrigé complet
