BBMODEL_INTEGRATION.md
# Guide d'intégration bbmodel pour ModelEngine 4

Ce document est destiné aux **animateurs et modélisateurs** pour préparer les fichiers `.bbmodel` pour le plugin DanseAvecLaStare.

## 1. Emplacement des fichiers
Les fichiers `.bbmodel` doivent être placés dans :
`plugins/ModelEngine/models/`

*Note : Ne pas les mettre dans le dossier blueprints/.*

## 2. Règles Blockbench (VITAL)

### A. ID de la Texture (Le Skin)
Pour que le skin du joueur s'affiche sur le modèle :
- Dans Blockbench, allez dans l'onglet **Textures**.
- Faites un clic droit sur votre texture > **Propriétés**.
- L'**ID** de la texture doit commencer par `p_` (ex: `p_skin`, `p_steve`, `p_player`).
- **Sans ce préfixe, le modèle restera bleu.**

### B. Nom de l'Animation
Le plugin déclenche une animation précise.
- Dans l'onglet **Animations**, votre animation de danse doit s'appeler : **`dance`**.
- Attention : respectez la casse (minuscules uniquement).

### C. Exportation
- Enregistrez simplement votre projet en tant que fichier **`.bbmodel`**.
- Utilisez des noms de fichiers simples, sans espaces ni majuscules (ex: `twist.bbmodel`).

## 3. Workflow de test
1. Placer le fichier dans `plugins/ModelEngine/models/`.
2. Taper `/meg reload models` sur le serveur.
3. **Récupérer le Resource Pack** : ModelEngine génère un ZIP dans `plugins/ModelEngine/resource pack/`. Installez ce pack sur votre Minecraft.
4. Tester la danse avec `/danse <style>`.

## 4. Résolution de problèmes
- **Modèle invisible** : Vérifiez le nom du fichier et faites `/meg reload models`.
- **Modèle figé** : L'animation ne s'appelle pas `dance`.
- **Modèle bleu** : L'ID de texture ne commence pas par `p_`.
- **Cube violet/noir** : Chargez le resource pack généré par le serveur.


