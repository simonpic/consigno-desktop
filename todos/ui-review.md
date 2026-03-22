# Revue UI/UX — ConsignO Desktop

**Contexte** : app desktop de signature PDF, utilisateurs techniques, thème AtlantaFX PrimerLight, aucun CSS custom.

---

## 🔴 Critique — Bloquant

### 1. `NotificationService` est un stub silencieux
Toutes les erreurs, succès et avertissements sont **swallowed dans des logs**. L'utilisateur ne voit jamais rien quand :
- la signature échoue
- le certificat est invalide
- le mot de passe est mauvais

C'est le problème numéro un. L'app peut sembler gelée ou défaillante sans aucun retour.

---

## 🟠 Haute priorité — Friction forte

### 3. Aucun feedback pendant le rendu PDF
Quand l'utilisateur ouvre un PDF, rien ne se passe visuellement jusqu'à l'apparition de la première page. Pas de spinner, pas de message "Chargement…". Pour un PDF de 50 pages, l'app semble gelée.

*Fix* : afficher un `ProgressIndicator` centré dans le viewer dès que `loadPdf()` est appelé.

### 5. Pas de zoom
Le viewer est fixé à 96 DPI. Sur un PDF A4 standard (595 pt), ça donne ~793 px de largeur — acceptable pour un écran 1920px mais illisible sur 1280px avec le panneau latéral. Aucun moyen de zoomer.

### 6. Parcours certificat peu découvrable
Le flow actuel :
1. Cliquer "…" → sélectionner le fichier
2. Taper le mot de passe dans le champ
3. Appuyer sur **Entrée** (non documenté)

Le `promptText` du PasswordField dit seulement "Mot de passe du certificat" — rien n'indique qu'il faut appuyer sur Entrée. Le message contextuel n'apparaît que si le mot de passe est vide lors de la sélection.

---

## 🟡 Priorité moyenne — Polissage

### 7. Incohérence visuelle dark/light
Le viewer PDF utilise `#404040` (gris foncé) hardcodé alors que l'UI applique AtlantaFX PrimerLight. Ce contraste est voulu pour mettre en valeur les pages, mais crée une rupture visuelle au niveau du `SplitPane`.

### 8. Le titre de fenêtre ne change pas
`primaryStage.setTitle("ConsignO Desktop")` est statique. Après ouverture d'un PDF, le titre devrait afficher le nom du fichier (`ConsignO — facture.pdf`).

### 9. `p12PathField` affiche le chemin complet
`/Users/simon/Documents/Certificats/ConsignO/mon-certificat-pro-2024.p12` dépasse largement le champ. Il faudrait afficher seulement le nom du fichier et mettre le chemin complet en tooltip.

### 10. Émoji ⬛ dans le bouton "Signer"
Le carré noir (`⬛`) est un émoji Unicode qui s'affiche mal selon les polices/OS. Utiliser une icône AtlantaFX ou supprimer l'émoji.

### 11. Curseur inchangé sur le viewer PDF
Quand l'utilisateur survole une page pour placer une signature, le curseur reste une flèche standard. Un `Cursor.CROSSHAIR` signalerait l'interactivité.

---

## 🟢 Nice-to-have

### 12. Pas de raccourcis clavier
- `Ctrl+O` → Ouvrir PDF
- `Ctrl+Shift+S` → Signer

### 13. Pas de navigation par page
Sur un document long, l'utilisateur scroll manuellement. Un champ "Page X / N" avec des boutons précédent/suivant serait utile.

### 14. Le champ "Signataire" dans Apparence
Il est pré-rempli avec le CN du certificat après chargement — bonne idée — mais la section "Apparence" est visible et vide dès l'ouverture de l'app, avant qu'il y ait quoi que ce soit à configurer.

---

## Récapitulatif priorisé

| Priorité | Problème | Effort estimé |
|----------|----------|---------------|
| 🔴 | Notifications réelles (stub → toast AtlantaFX) | Moyen |
| ✅ | ~~Double bouton Signer~~ | Faible |
| 🟠 | Feedback chargement PDF | Faible |
| ✅ | ~~Ghost désactivé sans certificat~~ | Faible |
| 🟠 | Zoom viewer | Moyen |
| 🟠 | UX chargement certificat | Faible |
| 🟡 | Titre fenêtre dynamique | Faible |
| 🟡 | Chemin → nom fichier + tooltip | Faible |
| 🟡 | Curseur crosshair | Trivial |
| 🟡 | Émoji bouton | Trivial |
