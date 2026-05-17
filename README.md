# DanseAvecLaStare

Plugin Paper (1.21.x) qui affiche des danseurs 3D animés via ModelEngine 4.

---

## Prérequis

- Paper 1.21.x
- Java 21+
- ModelEngine 4.0.9

## Installation

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

**Danseurs statiques**

| Commande | Description |
|---|---|
| `/danse here <id> <style> [pseudo]` | Pose un danseur à ta position |
| `/danse move <id>` | Déplace un danseur à ta position |
| `/danse delete <id>` | Supprime un danseur |
| `/danse listID` | Liste les danseurs actifs |

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
Voir [`docs/static_dancers.md`](docs/static_dancers.md) pour les danseurs statiques et les chorégraphies, [`docs/playlists.md`](docs/playlists.md) pour le système de playlists, et [`docs/BBMODEL_INTEGRATION.md`](docs/BBMODEL_INTEGRATION.md) pour l'intégration des modèles.
