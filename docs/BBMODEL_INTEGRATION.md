# Guide d'intégration BBMODEL (essentiel)

Objectif : préparer un `.bbmodel` compatible avec l'application du skin joueur via ModelEngine.

Règles de base
- Utiliser les préfixes ModelEngine pour les player limbs : `phead_`, `pbody_`, `prarm_`, `plarm_`, `prleg_`, `plleg_`.
- Chaque limb doit avoir une géométrie visible (cubes) et ne pas être masqué ni placé dans un groupe non visible.
- Donner un nom d'animation explicite et stable dans Blockbench : ce nom doit être copié dans `config.yml` comme `animationName`.

Étapes recommandées
1. Exporter le `.bbmodel` depuis Blockbench (conserver textures et animations).
2. Importer le blueprint dans ModelEngine (dossier `plugins/ModelEngine/blueprints/`).
3. Vérifier côté serveur : `createActiveModel(modelId)` retourne un `ActiveModel` non nul.
4. Lancer `/danse debug` et vérifier que les bones `PlayerLimb` sont détectés et loggés.

Tests minimaux
- Test A : modèle avec `phead_` uniquement → confirmer que la tête affiche le skin.
- Test B : ajouter `pbody_` → confirmer que le torse affiche le skin.
- Ajouter ensuite bras et jambes.

Remarques
- Si la tête fonctionne mais pas les autres membres, le problème est très probablement dans la géométrie du `.bbmodel` ou le resource pack côté client.
