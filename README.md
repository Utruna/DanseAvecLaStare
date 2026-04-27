README.md
# DanseAvecLaStare

Plugin Minecraft Paper/Spigot 1.21.x permettant aux joueurs de lancer des danses visibles en jeu.

Le plugin supporte deux moteurs via un pattern Strategy :

- **Citizens** (NPC joueur classique)
- **ModelEngine 4** (Entité modelée à partir d'un fichier `.bbmodel` qui s'adapte au skin du joueur)

## Fonctionnalités

- Commande unique `/danse` avec autocomplétion.
- Styles disponibles : `twist`, `spin`, `disco`, `moonwalk`, `wave`.
- Bascule Citizens/ModelEngine via configuration.
- Commande de diagnostic `/danse debug`.
- Nettoyage automatique des danses à la déconnexion.

## Commandes

- `/danse` : lance/arrête la danse par défaut (`twist`).
- `/danse list` : liste des styles.
- `/danse stop` : arrêt de la danse courante.
- `/danse debug` : état runtime (plugins détectés, config, fichiers trouvés).
- `/danse <style> [visible|off|false]` : lance un style ; l'argument optionnel agit surtout avec Citizens.

Exemples :
- `/danse twist`
- `/danse twist visible`
- `/danse debug`

## Configuration

Fichier serveur : `plugins/DanseAvecLaStare/config.yml`

Exemple optimisé :
```yaml
useModelEngine: true

modelEngine:
  defaultModelId: twist
  styleModels:
    twist: twist
    spin: twist
    disco: disco
    moonwalk: twist
    wave: twist
```

**Règles :**
- `useModelEngine: true` active la stratégie ModelEngine (si le plugin ModelEngine est présent).
- `modelEngine.defaultModelId` est le modèle utilisé par défaut si le style n'est pas précisé.
- `modelEngine.styleModels.<style>` permet d'associer une commande (ex: `twist`) à un nom de fichier `.bbmodel` précis (ex: `twist.bbmodel`).

## Intégration bbmodel (ModelEngine 4)

Pour que ModelEngine génère correctement le danseur et applique le skin du joueur, suivez ces règles dans **Blockbench** :

1.  **Nom du modèle** : Le fichier doit s'appeler exactement comme dans la configuration (ex: `twist.bbmodel`).
2.  **Skin Dynamique** : Dans Blockbench, l'**ID de la texture** de votre modèle DOIT commencer par `p_` (ex: `p_skin`). C'est ce qui indique à ModelEngine de remplacer la texture par le skin du joueur.
3.  **Animation** : Le fichier doit contenir une animation nommée EXACTEMENT `dance` (tout en minuscules).
4.  **Emplacement** : Placez le fichier `.bbmodel` dans `plugins/ModelEngine/models/`.
5.  **Génération** : En jeu, tapez `/meg reload models`.
6.  **Ressource Pack** : Récupérez le `.zip` généré dans `plugins/ModelEngine/resource pack/` et appliquez-le sur votre client Minecraft.

## Troubleshooting

- **"Unknown model"** : Le nom dans `config.yml` ne correspond pas au nom du fichier `.bbmodel` dans `/models/`.
- **Cube violet/noir** : Le ressource pack n'est pas activé sur votre Minecraft.
- **Personnage bleu** : L'ID de la texture dans Blockbench ne commence pas par `p_`.
- **Personnage immobile** : L'animation ne s'appelle pas `dance`.

## Auteur
Utruna


