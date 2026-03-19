# DanseAvecLaStare

Plugin Minecraft Paper/Spigot 1.21.x permettant aux joueurs de lancer des danses visibles en jeu.

Le plugin supporte deux moteurs via un pattern Strategy:

- Citizens (NPC joueur classique)
- ModelEngine (entité modelée à partir d'un blueprint `.bbmodel`)

## Fonctionnalités

- Commande unique `/danse` avec autocomplétion.
- Styles disponibles: `twist`, `spin`, `disco`, `moonwalk`, `wave`.
- Bascule Citizens/ModelEngine via configuration.
- Commande de diagnostic `/danse debug`.
- Nettoyage automatique des danses à la déconnexion.

## Commandes

- `/danse` : lance/arrête la danse par défaut (`twist`).
- `/danse list` : liste des styles.
- `/danse stop` : arrêt de la danse courante.
- `/danse debug` : état runtime (plugins détectés, config, blueprint).
- `/danse <style> [visible|off|false]` : lance un style ; l'argument optionnel agit surtout avec Citizens.

Exemples:

- `/danse twist`
- `/danse twist visible`
- `/danse debug`

## Configuration

Fichier serveur: `plugins/DanseAvecLaStare/config.yml`

Exemple:

```yml
useModelEngine: true

modelEngine:
  defaultModelId: danseur
  styleModels:
    twist: danseur
    spin: visible-test
    disco: disco-model
    moonwalk: moonwalk-model
    wave: wave-model
```

Règles:

- `useModelEngine: true` active la stratégie ModelEngine (si le plugin ModelEngine est présent).
- `modelEngine.defaultModelId` est le modèle utilisé par défaut.
- `modelEngine.styleModels.<style>` permet d'utiliser un modèle différent par danse.
- Compatibilité maintenue: l'ancienne clé `modelEngine.modelId` est encore lue en fallback.

## Intégration bbmodel (ModelEngine)

1. Placer le fichier `.bbmodel` dans:

  `plugins/ModelEngine/blueprints/`

2. Vérifier le nom/ID:

  si `defaultModelId: danseur`, le blueprint attendu est `danseur`.
  si `styleModels.spin: visible-test`, alors `/danse spin` utilisera `visible-test`.

3. Le blueprint doit contenir une animation nommée exactement:

  `dance`

4. Recharger ModelEngine:

  `/meg reload` (ou redémarrage serveur)

5. Vérifier avec:

  `/danse debug`

Important:

- Le modèle magenta/noir indique en général un problème de textures/resource pack, pas forcément de logique plugin.
- Le skin joueur dynamique nécessite un blueprint préparé pour le Player Skin Mapping.

## Installation

Prérequis serveur:

- Java 21
- Paper/Spigot 1.21.x
- Citizens (optionnel)
- ModelEngine 4.0.9 (optionnel, requis pour la stratégie bbmodel)

Build local:

```bash
mvn clean package
```

Déploiement:

1. Copier `target/DanseAvecLaStare-1.0.0-SNAPSHOT.jar` dans `plugins/`.
2. Vérifier `plugins/DanseAvecLaStare/config.yml`.
3. Redémarrer le serveur.

## Dépendance ModelEngine (développement)

ModelEngine étant souvent distribué hors dépôt Maven public, installer le jar localement en `.m2`:

```bash
mvn install:install-file -Dfile=libs/ModelEngine-4.0.9.jar -DgroupId=com.ticxo.modelengine -DartifactId=ModelEngine-API -Dversion=4.0.9 -Dpackaging=jar -DgeneratePom=true
```

Puis compiler normalement.

## Structure du code

- `src/main/java/me/utruna/danse/DanseAvecLaStare.java`: point d'entrée, commande, debug.
- `src/main/java/me/utruna/danse/managers/DanceManager.java`: orchestration de la stratégie active.
- `src/main/java/me/utruna/danse/managers/Dancer.java`: contrat Strategy.
- `src/main/java/me/utruna/danse/managers/CitizensDancer.java`: implémentation Citizens.
- `src/main/java/me/utruna/danse/managers/ModelEngineDancer.java`: implémentation ModelEngine.
- `src/main/java/me/utruna/danse/listeners/PlayerListener.java`: cleanup joueur.

## Troubleshooting rapide

- Rien ne s'affiche: vérifier `/danse debug`, `useModelEngine`, `modelId`, présence blueprint.
- Erreur d'attachement modèle: vérifier l'ID blueprint et la compatibilité du `.bbmodel`.
- Cube magenta/noir: corriger textures/materials et resource pack ModelEngine.
- Pas d'animation: vérifier que `dance` existe dans le blueprint.

## Documentation complémentaire

Voir `docs/BBMODEL_INTEGRATION.md` pour le guide détaillé d'implémentation bbmodel.

## Auteur

Utruna
