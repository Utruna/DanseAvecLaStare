# Pipeline d'affichage du skin (version concise)

But : appliquer le skin du joueur sur un modèle Blockbench (.bbmodel) animé par ModelEngine 4.0.9.

Flux principal
- Récupérer le `PlayerProfile` (sync ou via `SkinService` asynchrone).
- Créer un `Dummy<PlayerProfile>` et l'enregistrer auprès de ModelEngine (`createModeledEntity`).
- Charger l'`ActiveModel` correspondant au `modelId` et l'attacher via `addModel(...)`.
- Parcourir `activeModel.getBones()` et pour chaque `PlayerLimb` appeler `setTexture(profile)`.
- Lancer l'animation via `activeModel.getAnimationHandler().playAnimation(animationName, ...)`.

Points essentiels
- `Dummy<PlayerProfile>` est la source de vérité du skin côté serveur.
- `PlayerLimb` est le comportement ModelEngine attendu sur les bones (préfixes `phead_`, `pbody_`, `prarm_`, `plarm_`, `prleg_`, `plleg_`).
- Si les logs indiquent `✓ Skin applied` pour chaque limb mais que des membres restent invisibles, cela indique un problème côté `.bbmodel` ou resource pack client, pas côté Java.

Vérifications rapides (serveur)
- `Bones found: N` et `✓ Skin applied` pour chaque limb
- `activeModel != null` et `activeModel.getBones()` non vide

Vérifications rapides (modèle / client)
- `animationName` = nom exact dans le `.bbmodel`
- les cubes des limbs existent et sont visibles dans Blockbench
- le resource pack ModelEngine est chargé correctement par le client

Conseil pratique : tester un `.bbmodel` minimal (phead_ seul, puis ajouter pbody_, puis les bras/jambes) pour isoler le problème.
