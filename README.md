# DanseAvecLaStare

Plugin Paper (1.21.x) qui affiche des danseurs 3D animés via ModelEngine 4.

---

## Prérequis

- Paper 1.21.x
- Java 21+
- ModelEngine 4.0.9

## Mise en place

1. Compiler le projet ou récupérer le JAR déjà compilé.
2. Placer le JAR du plugin dans le dossier `plugins/` du serveur, avec ModelEngine 4.0.9 installé.
3. Démarrer le serveur une première fois pour générer les fichiers et dossiers nécessaires.
4. Copier les modèles `.bbmodel` conformes aux règles du guide dans `plugins/ModelEngine/blueprints/`.
5. Rafraîchir les modèles avec `/meg reload` ou redémarrer le serveur.
6. Télécharger le resource pack généré par ModelEngine et l’ajouter à la configuration de ton propre resource pack.
7. Lancer le serveur et profiter.

Pour les contraintes de nommage, d’animation et de structure des modèles, voir [`docs/BBMODEL_INTEGRATION.md`](docs/BBMODEL_INTEGRATION.md).

## Téléchargement

Télécharge le JAR pré-compilé depuis la [dernière release](https://github.com/Utruna/DanseAvecLaStare/releases/latest).

## Installation rapide (développement)

```bash
mvn clean package -DskipTests
```

Copier le JAR `target/DanseAvecLaStare-*.jar` dans `plugins/` puis redémarrer.

---

## Commandes

**Danses joueur**

| Commande | Description |
|---|---|
| `/danse <style>` | Lance une danse avec ton skin |
| `/danse <style> <pseudo>` | Lance une danse avec le skin d'un autre joueur |
| `/danse stop` | Arrête la danse |
| `/danse list` | Liste les styles disponibles |
| `/danse debug` | Active/désactive les logs de diagnostic |

**NPC (danseurs statiques)**

| Commande | Description |
|---|---|
| `/danse npc spawn <id> <style> [pseudo]` | Crée un NPC à ta position |
| `/danse npc move <id>` | Déplace un NPC à ta position |
| `/danse npc delete <id>` | Supprime un NPC |
| `/danse npc list` | Liste les NPCs actifs |
| `/danse npc style <id> <style>` | Change le style de danse d'un NPC |
| `/danse npc resize <id> <valeur>` | Redimensionne un NPC (0.1 – 20.0) |
| `/danse npc highlight <id> [secondes]` | Signale un NPC avec des particules |

Les danseurs statiques sont sauvegardés automatiquement et restaurés au redémarrage.

**Chorégraphie** — synchronisation de groupes de danseurs statiques

| Commande | Description |
|---|---|
| `/danse choreo create <groupId> <id1> [id2…]` | Crée un groupe et synchronise les animations |
| `/danse choreo add <groupId> <id>` | Ajoute un danseur au groupe |
| `/danse choreo remove <groupId> <id>` | Retire un danseur du groupe |
| `/danse choreo sync <groupId>` | Re-synchronise les animations du groupe |
| `/danse choreo delete <groupId>` | Dissout le groupe (les danseurs reprennent en solo) |
| `/danse choreo list` | Liste tous les groupes et leurs membres |

**Playlists** — séquences d'animations programmées

| Commande | Description |
|---|---|
| `/danse playlist create <id> [loop\|once]` | Crée une playlist (en boucle par défaut) |
| `/danse playlist add <id> <style> <rép>` | Ajoute une piste (style × N répétitions) |
| `/danse playlist remove <id> <index>` | Supprime une piste par index |
| `/danse playlist delete <id>` | Supprime la playlist |
| `/danse playlist info <id>` | Affiche les pistes de la playlist |
| `/danse playlist list` | Liste toutes les playlists |
| `/danse playlist play <id> player [pseudo]` | Lance la playlist sur un joueur |
| `/danse playlist play <id> dancer <dancerId>` | Lance la playlist sur un danseur statique |
| `/danse playlist play <id> group <groupId>` | Lance la playlist sur un groupe |
| `/danse playlist stop player [pseudo]` | Arrête la playlist d'un joueur |
| `/danse playlist stop dancer <dancerId>` | Arrête la playlist d'un danseur |
| `/danse playlist stop group <groupId>` | Arrête la playlist d'un groupe |
| `/danse playlist active` | Affiche toutes les playlists en cours |
| `/danse playlist debug` | Active/désactive les logs de diagnostic playlist |

---

## Configuration

Les styles de danse se définissent dans `config.yml` sans recompiler.
Voir [`docs/static_dancers.md`](docs/static_dancers.md) pour les danseurs statiques et les chorégraphies, [`docs/playlists.md`](docs/playlists.md) pour le système de playlists, [`docs/menus.md`](docs/menus.md) pour la documentation des menus, et [`docs/BBMODEL_INTEGRATION.md`](docs/BBMODEL_INTEGRATION.md) pour l'intégration des modèles.

---

## Performance & limites

### Danses concurrentes

| Clé config | Défaut | Effet |
|---|---|---|
| `dance.maxConcurrent` | `500` | Nombre maximal de danses joueur actives simultanément. Chaque danse alloue 1 `Dummy`, 1 `ModeledEntity` et 1 `ActiveModel` dans ModelEngine. Dépasser cette limite retourne `§cTrop de danses actives, réessaie dans un moment.` au joueur. Mettre à `0` pour désactiver la limite. |

### Récupération des skins (API Mojang)

Les skins des joueurs hors ligne sont récupérés via l'API Mojang sur un pool de threads dédié. Les profils sont mis en cache pour la session et ne déclenchent qu'un seul appel Mojang par pseudo.

| Clé config | Défaut | Effet |
|---|---|---|
| `skinFetcher.maxThreads` | `4` | Taille du pool de threads `DanseSkinFetcher-N`. Augmenter si de nombreux skins sont chargés simultanément au démarrage (danseurs statiques avec skin custom). |
| `skinFetcher.timeoutSeconds` | `5` | Délai maximal d'attente d'une réponse Mojang. En cas de timeout, le skin est ignoré et un warning est logué. |

### Danseurs statiques

Tous les danseurs statiques et tous les groupes de chorégraphie partagent **une seule `BukkitTask`** (global tick à 1 tick/cycle) au lieu d'une tâche individuelle par danseur. Le nombre de tâches planifiées dans Bukkit est donc constant quelle que soit la quantité de NPCs.
