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

## Les 2 Stratégies d'animation : Citizens vs ModelEngine

### 1. Stratégie Citizens (Mode par défaut sans pack)
- **Avantage** : Ne nécessite **aucun pack de ressources** pour les joueurs.
- **Inconvénient** : Les mouvements sont strictement limités aux animations de Minecraft Vanilla (lever le bras, s'accroupir, avancer). Il est techniquement impossible d'utiliser des fichiers `.bbmodel` ou des animations fluides complexes sur un vrai "faux joueur" de type Citizens.

### 2. Stratégie ModelEngine (Mode avancé)
- **Avantage** : Permet des danses ultra-fluides, complexes, et totalement personnalisées via Blockbench (`.bbmodel`).
- **Inconvénient** : **Nécessite obligatoirement un Pack de Ressources** (Resource Pack) généré par ModelEngine pour afficher les modèles aux joueurs. Sans ce pack, les joueurs verront un gros cube rose et noir (texture manquante).

## Intégration bbmodel (Blockbench -> ModelEngine)

Pour que ModelEngine accepte et charge correctement votre modèle de danseur, le fichier `.bbmodel` doit respecter **strictement** ces règles :

1. **Type de projet** : Le projet Blockbench doit être impérativement un **Modèle d'entité Bedrock (Bedrock Entity)**. Les formats "Generic Model" ou "Free Model" de BlockBench v5 vont faire crasher l'import.
2. **Nomenclature des Os (Bones)** : Les dossiers (qui représentent les os) doivent être en minuscules et **SANS AUCUN ESPACE**. (ex: `head`, `body`, `right_arm`, `left_arm`, `right_leg`, `left_leg`, `hitbox`).
3. **Animation** : Le plugin ciblera par défaut l'animation nommée `dance`. *(Nouveau : s'il ne la trouve pas, il jouera automatiquement la première animation disponible dans le fichier)*.
4. **Texture dynamique du Joueur (p_skin)** : Dans Blockbench, la texture doit commencer par le préfixe `p_` (ex: `p_skin` ou `p_steve`) pour indiquer à ModelEngine qu'il doit décalquer le vrai skin du joueur du serveur sur cette texture en temps réel.
5. Mettez le fichier `.bbmodel` dans `plugins/ModelEngine/models/` (ou `blueprints/` selon version).
6. Tapez `/meg reload models` sur le serveur. La console doit renvoyer un message d'importation sans erreurs !

## Pack de Ressources (Le cube Rose et Noir)

Si vous voyez un grand cube rose et noir à la place de votre personnage :
Cela signifie que le serveur et le plugin **fonctionnent parfaitement**, mais que votre jeu Minecraft (client) n'a pas téléchargé le pack de ressources.
- **Pour tester localement** : Récupérez le fichier `ModelEngine.zip` dans le dossier `plugins/ModelEngine/resource pack/` du serveur, mettez-le dans le dossier `resourcepacks` de votre Minecraft et activez-le.
- **Pour les joueurs du serveur** : Hébergez ce `.zip` en ligne et mettez le lien dans le `server.properties` (`resource-pack=...`).

## Installation

Prérequis serveur:

- Java 25 (LTS)
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
