# DanseAvecLaStare

Plugin Minecraft Paper 1.21.1 permettant aux joueurs de lancer des animations de danse via des modèles 3D rendus par **ModelEngine 4**.

---

## Présentation

DanseAvecLaStare est un plugin entièrement **configuration-driven** qui exploite ModelEngine 4 (R4.0.9) pour afficher des danseurs 3D avec animations dynamiques. Les modèles sont positionnés selon des patterns de mouvement paramétrables (statique, rotations, vagues, orbites, etc.).

**Pas de code Java à modifier pour ajouter une danse.** Tout se configure en YAML.

---

## Système Dynamique

### Architecture

L'ensemble des styles de danse est défini dans `config.yml`, section `dances:`. À chaque démarrage, le plugin :

1. Charge la configuration YAML
2. Crée dynamiquement des instances `GenericDanceStyle` pour chaque style
3. Associe chaque style à un modèle ModelEngine et une animation

### Ajouter une Nouvelle Danse

Pour ajouter un style de danse **sans recompiler** :

1. **Créer le modèle** : Ajouter un fichier `.bbmodel` dans le dossier ModelEngine du serveur
2. **Configurer en YAML** : Ajouter une entrée dans la section `dances:` du `config.yml`
3. **Recharger** : Relancer le plugin ou redémarrer le serveur

Aucun redéploiement JAR requis.

---

## Guide de Configuration

Fichier : `plugins/DanseAvecLaStare/config.yml`

### Structure Globale

```yaml
useModelEngine: true

modelEngine:
  defaultModelId: danseur
  defaultAnimationName: dance

dances:
  # Chaque style ici
```

### Paramètres par Style

```yaml
dances:
  <styleName>:
    displayName: "Nom Affiché"
    modelId: <blueprintId>
    animationName: <animName>
    movementType: <STATIC|SPIN|ORBIT|WAVE|MOONWALK>
    rotationSpeed: <degres/tick>
    radius: <blocs>
```

#### Détail des Paramètres

| Paramètre | Type | Description |
|-----------|------|-------------|
| `displayName` | String | Nom affiché aux joueurs via `/danse list` |
| `modelId` | String | ID du blueprint ModelEngine (défini dans le fichier `.bbmodel`) |
| `animationName` | String | Nom de l'animation à jouer en boucle |
| `movementType` | Enum | Pattern de mouvement : `STATIC` (immobile), `DYNAMIC` (bouge) |
| `rotationSpeed` | Float | Vitesse en degrés/tick pour `SPIN` et `ORBIT` |
| `radius` | Float | Rayon en blocs pour `ORBIT` et `WAVE` |

#### Exemple Complet

```yaml
dances:
  twist:
    displayName: "Twist"
    modelId: danseur
    animationName: dance
    movementType: DYNAMIC
    rotationSpeed: 0.0
    radius: 0.0

  spin:
    displayName: "Spin Rapide"
    modelId: danseur
    animationName: dance
    movementType: DYNAMIC
    rotationSpeed: 5.0
    radius: 0.0
```

---

## Commandes

### Utilisation

```
/danse <style>              Lance le style de danse spécifié
/danse list                 Affiche les styles disponibles
/danse stop                 Arrête la danse courante
/danse debug                Affiche l'état du plugin (ModelEngine, config, blueprints)
```

### Exemples

```
/danse twist                # Lancer la danse "twist"
/danse list                 # Voir les styles
/danse stop                 # Arrêter
/danse debug                # Diagnostic
```

---

## Prérequis Graphiques

### ModelEngine et Blockbench

Le skin mapping (option pour styles futurs) **exige une nomenclature stricte des bones** dans Blockbench :

| Bone | Utilisation |
|------|-------------|
| `head` | Tête du joueur |
| `body` | Torse |
| `left_arm` | Bras gauche |
| `right_arm` | Bras droit |
| `left_leg` | Jambe gauche |
| `right_leg` | Jambe droite |

**État actuel** : Le skin mapping n'est pas encore fonctionnel. Si l'implémentation réussit, les blueprints devront respecter **obligatoirement** cette hiérarchie de bones pour que les skins se plaquent correctement sur le modèle 3D.

### Installation des Blueprints

1. Exporter le blueprint depuis Blockbench au format `.bbmodel`
2. Placer le fichier dans : `plugins/ModelEngine/blueprints/`
3. Redémarrer ModelEngine ou le serveur
4. Ajouter une entrée `dances:` dans `config.yml` pointant sur l'ID du blueprint

---

## Installation & Build

### Prérequis Serveur

- **Paper** 1.21.1 (ou compatible Bukkit API)
- **ModelEngine** 4.0.9+
- **Java** 21+

### Build Local

```bash
mvn clean package -DskipTests
```

JAR généré : `target/DanseAvecLaStare-VERSION.jar`

### Déploiement

1. Copier le JAR dans `plugins/`
2. Redémarrer le serveur
3. Vérifier la config dans `plugins/DanseAvecLaStare/config.yml`

---

## Architecture Technique

### Classes Clés

- **DanceManager** : Orchestration des danses, chargement config, gestion du cycle de vie
- **GenericDanceStyle** : Implémentation unifiée des styles de mouvement
- **ModelEngineDancer** : Intégration ModelEngine 4 — création Dummy, modèles, animations
- **SkinService** : Récupération asynchrone des profils joueur via Mojang API

### Pattern de Mouvement

Les styles sont paramétrés par `movementType` :

- **STATIC** : Position fixe
- **DYNAMIC** : mouvement basé sur le bbmodel


---

## Notes

- Les danses sont chargées **au démarrage du plugin**.
- Le skin mapping est en développement.
- Chaque danse rend le joueur invisible pour afficher le modèle 3D.
