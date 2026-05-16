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

---

## Configuration

Les styles de danse se définissent dans `config.yml` sans recompiler.
Voir [`docs/static_dancers.md`](docs/static_dancers.md) pour la persistance des danseurs statiques et [`docs/BBMODEL_INTEGRATION.md`](docs/BBMODEL_INTEGRATION.md) pour l'intégration des modèles et le pipeline technique.
