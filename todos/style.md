# ConsignO Desktop — Guide de style

## Concept : « Ciel Ouvert »

L'application de signature est un outil de confiance. Elle doit inspirer **clarté, fiabilité et légèreté** — le bleu ciel évoque l'air pur, la précision, l'horizon dégagé. L'esthétique vise un équilibre entre **logiciel professionnel** (pas de couleurs criardes, pas de gadgets) et **outil moderne agréable** (contrastes vifs, typographie expressive, hiérarchie claire).

L'identité visuelle repose sur trois piliers :
- **Bleu profond** comme couleur signature — solide, clair, professionnel
- **Navy profond** pour les zones structurantes (toolbar) — ancrage et sérieux
- **Blanc cassé bleuté** pour les fonds — respiration, légèreté

---

## Palette de couleurs

### Couleurs primaires

| Rôle | Nom | Hex | Usage |
|------|-----|-----|-------|
| Primaire | Royal Blue | `#2D72B8` | Bouton principal, fantôme signature, focus rings, accents |
| Primaire foncé | Deep Blue | `#1A5A9A` | Hover sur bouton primaire, liens actifs |
| Primaire clair | Mist | `#9AB8D8` | Highlights, états sélectionnés |
| Primaire subtil | Frost | `#EEF3FA` | Fond du panneau latéral, arrière-plans de sections |

### Couleurs structurelles

| Rôle | Nom | Hex | Usage |
|------|-----|-----|-------|
| Toolbar | Deep Navy | `#1A3650` | Fond de la barre d'outils |
| Fond principal | White | `#FFFFFF` | Zone centrale, panneaux, cartes |
| Fond viewer | Slate Blue | `#D8E8F2` | Fond du visualiseur PDF (remplace le gris neutre) |
| Texte principal | Ink | `#1C2E3D` | Texte courant, labels |
| Texte secondaire | Muted | `#5A7A8A` | Labels de champs, aide, statut |
| Bordure | Ice | `#C4D5E8` | Séparateurs, bordures de champs |

### Couleurs sémantiques

| Rôle | Hex | Usage |
|------|-----|-------|
| Succès | `#27AE60` | Bouton "Ouvrir le signé", marqueur signature placée |
| Erreur | `#E05252` | Messages d'erreur |
| Avertissement | `#F0A030` | Notifications d'attention |

---

## Typographie

### Polices recommandées

**Titres et labels (UI)** — [Outfit](https://fonts.google.com/specimen/Outfit)
- Géométrique, moderne, très lisible en petite taille
- Poids utilisés : 400 (Regular), 500 (Medium), 600 (SemiBold)
- Intégration JavaFX : charger via `Font.loadFont()` au démarrage

**Corps de texte** — Outfit Regular (cohérence)

**Champs techniques** (chemins, noms de fichiers) — [IBM Plex Mono](https://fonts.google.com/specimen/IBM+Plex+Mono)
- Distingue visuellement les données brutes des labels UI
- Poids : 400 Regular

**Fallback système** : `-fx-font-family: 'Segoe UI', 'Helvetica Neue', sans-serif`

### Hiérarchie typographique

| Élément | Police | Taille | Poids | Couleur |
|---------|--------|--------|-------|---------|
| Titre section (TitledPane) | Outfit | 12px | SemiBold | `#1C2E3D` |
| Label de champ | Outfit | 11px | Medium | `#5A7A8A` |
| Valeur de champ | Outfit | 12px | Regular | `#1C2E3D` |
| Chemin fichier | IBM Plex Mono | 11px | Regular | `#1C2E3D` |
| Barre de statut | Outfit | 11px | Regular | `#5A7A8A` |
| Bouton principal | Outfit | 13px | SemiBold | `#FFFFFF` |
| Bouton secondaire | Outfit | 12px | Medium | `#1E8DC8` |

---

## Composants

### Barre d'outils (Toolbar)

- **Fond** : `#1A3650` (navy profond)
- **Hauteur** : 44px
- **Boutons** : style texte sur fond sombre — texte blanc `rgba(255,255,255,0.72)`, fond transparent, hover texte `#2D72B8`
- **Séparateurs** : `rgba(255,255,255,0.13)`

### Panneau latéral (Sidebar)

- **Fond** : `#EEF3FA` (Frost — très légèrement bleuté)
- **Largeur min/max** : 240–360px
- **Bordure droite** : 1px solid `#C4D5E8`

#### TitledPanes (sections)
- **Header fond** : `#EEF3FA`
- **Titre** : Outfit 11px, `#5A7A8A`
- **Contenu fond** : `#EEF3FA`
- **Séparateur bas** : 1px `#C4D5E8`
- **Flèche** : masquée (sections toujours visibles)

#### Champs texte (TextField, PasswordField)
- **Fond** : `#FFFFFF`
- **Bordure** : 1px `#C4D5E8`, radius 5px
- **Bordure focus** : 2px `#2D72B8`
- **Texte** : `#1C2E3D`
- **Placeholder** : `#8AAEC8`

#### Labels de champ
- **Couleur** : `#5A7A8A`
- **Taille** : 11px, Outfit Medium
- **Espacement bas** : 2px entre label et champ

### Bouton principal — "Signer le document"

- **Fond** : gradient linéaire `#2D72B8` → `#1A5A9A` (gauche → droite)
- **Fond hover** : `#1A5A9A` → `#145080`
- **Texte** : Outfit SemiBold 13px, `#FFFFFF`
- **Radius** : 0px (pleine largeur, ancré en bas — pas de radius)
- **Padding** : 12px vertical
- **Shadow** : `dropshadow(gaussian, rgba(30,141,200,0.4), 6, 0, 0, 2)`
- **Transition** : couleur + shadow en 150ms

### Bouton succès — "Ouvrir le document signé"

- **Fond** : `#27AE60`
- **Fond hover** : `#219150`
- **Texte** : Outfit SemiBold 13px, `#FFFFFF`
- Même géométrie que le bouton principal

### Boutons secondaires (toolbar)

- **Fond** : transparent
- **Texte** : `rgba(255,255,255,0.72)` (sur navy toolbar)
- **Hover** : texte `#2D72B8`
- **Radius** : 0px

### Visualiseur PDF

- **Fond** : `#D8E8F2` (Slate Blue — fond bleuté qui met en valeur les pages blanches)
- **Pages** : fond blanc `#FFFFFF`, shadow `dropshadow(gaussian, rgba(26,54,80,0.2), 12, 0, 0, 4)`
- **Label page** : Outfit 10px, `#7A9CAE`
- **Espacement entre pages** : 20px

#### Fantôme de signature (Ghost)
- **Contour** : `#2D72B8` tirets, 2px (cohérent avec la couleur primaire)
- **Remplissage** : `rgba(45, 114, 184, 0.12)`

#### Marqueur de signature placée
- **Contour** : `#27AE60` solide, 2px
- **Remplissage** : `rgba(39, 174, 96, 0.15)`

### Barre de statut

- **Fond** : `#FFFFFF`
- **Bordure haute** : 1px `#C8DFF0`
- **Texte** : Outfit 11px, `#5A7A8A`
- **Indicateur actif** : pastille ronde 6px `#2D72B8` à gauche du texte quand activité en cours
- **Padding** : 5px 14px

### Popup mot de passe

- **Fond** : `#FFFFFF`
- **Header titre** : Outfit SemiBold 14px, `#1C2E3D`
- **Sous-titre** (nom du cert) : IBM Plex Mono 11px, `#5A7A8A`
- **Champ mot de passe** : style identique aux champs de la sidebar
- **Bouton "Signer"** : même style que le bouton principal (gradient bleu profond)
- **Bouton "Annuler"** : ghost, bordure `#C4D5E8`, texte `#5A7A8A`
- **Shadow de la dialog** : `dropshadow(gaussian, rgba(26,54,80,0.25), 24, 0, 0, 8)`

---

## Implémentation JavaFX

### Fichiers à créer

```
consigno-app/src/main/resources/
├── css/
│   └── consigno.css       ← feuille de style principale
└── fonts/
    ├── Outfit-Regular.ttf
    ├── Outfit-Medium.ttf
    ├── Outfit-SemiBold.ttf
    └── IBMPlexMono-Regular.ttf
```

### Chargement dans ConsignoApplication.java

```java
// Après le thème AtlantaFX
scene.getStylesheets().add(
    getClass().getResource("/css/consigno.css").toExternalForm()
);

// Polices (avant création de la Scene)
Font.loadFont(getClass().getResourceAsStream("/fonts/Outfit-Regular.ttf"), 12);
Font.loadFont(getClass().getResourceAsStream("/fonts/Outfit-Medium.ttf"), 12);
Font.loadFont(getClass().getResourceAsStream("/fonts/Outfit-SemiBold.ttf"), 12);
Font.loadFont(getClass().getResourceAsStream("/fonts/IBMPlexMono-Regular.ttf"), 12);
```

### Variables CSS à redéfinir (AtlantaFX tokens)

```css
/* consigno.css */
.root {
    -color-accent-fg:         #2D72B8;
    -color-accent-emphasis:   #1A5A9A;
    -color-accent-muted:      #9AB8D8;
    -color-accent-subtle:     #EEF3FA;
    -color-bg-default:        #FFFFFF;
    -color-bg-subtle:         #EEF3FA;
    -color-border-default:    #C4D5E8;
    -color-border-muted:      #CCDAEC;
    -color-fg-default:        #1C2E3D;
    -color-fg-muted:          #5A7A8A;
    -fx-font-family:          'Outfit';
}
```

### Sélecteurs CSS clés à styler

```css
/* Toolbar navy */
.tool-bar { -fx-background-color: #1A3650; }

/* Sidebar */
.sidebar { -fx-background-color: #EEF3FA; }

/* TitledPane — intégré */
.titled-pane > .title {
    -fx-background-color: #EEF3FA;
    -fx-border-color: transparent transparent #C4D5E8 transparent;
    -fx-border-width: 0 0 1 0;
}

/* Bouton principal gradient */
.button.accent {
    -fx-background-color: linear-gradient(to right, #2D72B8, #1A5A9A);
    -fx-text-fill: white;
    -fx-font-family: 'Outfit';
    -fx-font-weight: 600;
}
.button.accent:hover {
    -fx-background-color: linear-gradient(to right, #1A5A9A, #145080);
}

/* Champ p12 — police mono */
#p12PathField { -fx-font-family: 'IBM Plex Mono'; }
```

---

## Ordre d'implémentation suggéré

1. Télécharger et intégrer les polices (Outfit + IBM Plex Mono depuis Google Fonts)
2. Créer `consigno.css` avec les variables de couleur AtlantaFX
3. Styler la toolbar (navy + boutons ghost)
4. Styler les TitledPanes (accent bleu + fond frost)
5. Styler le bouton principal (gradient)
6. Styler le viewer PDF (fond Slate Blue)
7. Styler la barre de statut
8. Affiner la popup mot de passe
