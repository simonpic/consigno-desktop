# Nouvelle fonctionnalité — ConsignO Desktop

Implémente la fonctionnalité suivante : $ARGUMENTS

## Processus à suivre

1. **Analyser** la demande et identifier les modules concernés
   - UI uniquement → `consigno-app`
   - PDF/signature → `consigno-pdf` + potentiellement `consigno-app`
   - Crypto → `consigno-crypto`
   - Backend → `consigno-backend-client`

2. **Créer une branche** feature/[nom-court-kebab-case]

3. **Implémenter dans l'ordre** :
   - Modèles/DTOs dans `consigno-common` si nécessaire
   - Interface de service (si nouveau service)
   - Implémentation du service
   - Tests unitaires du service
   - Contrôleur FXML + ViewModel (si UI)
   - Fichier FXML (si nouvel écran)

4. **Vérifier** :
   - `mvn compile -q` sans erreur
   - `mvn test -q` sans régression
   - Thread safety pour tout code JavaFX
   - Unités de coordonnées si PDF impliqué

5. **Résumer** les fichiers créés/modifiés et les décisions d'architecture prises
