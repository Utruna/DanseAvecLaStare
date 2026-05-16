# Diagnostic et correctifs rapides pour le rendu du skin

Contexte : le code applique désormais `setTexture(...)` sur chaque `PlayerLimb` détecté (logs `✓ Skin applied`). Si malgré cela certains membres restent invisibles, suivre cette checklist.

Checklist de diagnostic
1. Logs serveur : vérifier `Bones found: N` et `✓ Skin applied` pour chaque limb.
2. `animationName` : vérifier qu'il correspond exactement au nom de l'animation dans le `.bbmodel`.
3. Géométrie : dans Blockbench, s'assurer que chaque limb a des cubes visibles et n'est pas placé dans un groupe masqué.
4. Hiérarchie : chaque limb doit être indépendant (pas parenté indûment à la tête).
5. Resource pack client : recharger le pack ModelEngine côté client, tester sans autres packs si nécessaire.

Points techniques importants
- `HeadForcedImpl` est un comportement interne ajouté par ModelEngine ; ne pas tenter de le supprimer depuis le plugin.
- Les appels `setTexture(PlayerProfile)` sont la bonne approche ; le fallback `setTexture(Player)` est aussi implémenté au cas où.

Procédure d'isolation rapide
- Créer une copie du `.bbmodel` et supprimer tous les limbs sauf `phead_` → tester.
- Réintroduire `pbody_` → tester.
- Réintroduire bras puis jambes.

Si après ces étapes le problème persiste, joindre le `.bbmodel` et les logs `/danse debug` dans une issue pour analyse approfondie.
