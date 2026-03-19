# Guide d'integration bbmodel (ModelEngine)

Ce document explique comment integrer correctement un modele `.bbmodel` avec DanseAvecLaStare.

## 1. Prerequis

- Paper/Spigot 1.21.x
- ModelEngine 4.0.9 installe et actif
- DanseAvecLaStare deploye
- Resource pack ModelEngine fonctionnel cote client

## 2. Emplacement des modeles

Placer les blueprints dans:

`plugins/ModelEngine/blueprints/`

Exemples:

- `plugins/ModelEngine/blueprints/danseur.bbmodel`
- `plugins/ModelEngine/blueprints/visible-test.bbmodel`

## 3. Configuration du plugin

Fichier a editer sur le serveur:

`plugins/DanseAvecLaStare/config.yml`

Configuration minimale:

```yml
useModelEngine: true

modelEngine:
  defaultModelId: danseur
  styleModels:
    twist: danseur
    spin: visible-test
```

Regle importante:

- `defaultModelId` sert de fallback global.
- `styleModels.<style>` permet de mapper chaque danse vers un blueprint specifique.
- L'ancienne cle `modelEngine.modelId` reste supportee en fallback.

## 4. Exigences du blueprint

- Le blueprint doit etre charge par ModelEngine.
- Le blueprint doit contenir une animation nommee exactement `dance`.
- Si objectif skin dynamique joueur:
  - le blueprint doit etre prepare pour Player Skin Mapping
  - UV et materiaux doivent etre compatibles avec le rendu skin attendu

## 5. Cycle de test

1. Redemarrer le serveur (recommande) ou `/meg reload`.
2. Lancer `/danse debug`.
3. Verifier:
   - ModelEngine actif
   - `useModelEngine=true`
  - `defaultModelId` correct
  - mapping `styleModels` correct
  - blueprint(s) present(s)
4. Lancer `/danse twist`.
5. Verifier:
   - modele visible
   - animation `dance` active

## 6. Erreurs frequentes

### 6.1 Rien ne s'affiche

Verifier:

- `useModelEngine` active dans le bon config serveur
- `modelId` correct
- plugin ModelEngine actif
- blueprint present

### 6.2 Cube rose/noir (magenta/noir)

Cause probable: texture manquante ou resource pack non applique.

Actions:

- verifier les chemins de textures dans le blueprint
- regenerer/recharger le pack ModelEngine
- verifier que le client accepte le pack

### 6.3 Le modele apparait mais n'anime pas

Cause probable: animation `dance` absente ou nom differente.

Action: renommer/ajouter l'animation `dance` dans le `.bbmodel`.

### 6.4 Le skin joueur ne s'applique pas

Cause probable: blueprint non prepare pour Player Skin Mapping.

Action: adapter le modele (UV/materials) a un workflow skin joueur.

## 7. Commandes utiles

- `/danse debug`
- `/danse list`
- `/danse twist`
- `/danse stop`
- `/meg reload`

## 8. Rappel implementation code

- Le choix Citizens/ModelEngine est gere dans `DanceManager`.
- Le rendu bbmodel est gere dans `ModelEngineDancer`.
- Le modele utilise est configurable via `modelEngine.modelId`.
