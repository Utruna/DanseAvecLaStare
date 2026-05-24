# Guide d'intégration BBMODEL

Objectif : préparer un `.bbmodel` compatible avec l'application du skin joueur via ModelEngine 4.0.9.

---

## Pipeline technique

Flux d'exécution pour appliquer un skin sur un modèle animé :

1. Récupérer le `PlayerProfile` (sync depuis le joueur connecté, ou via `SkinService` async pour un pseudo tiers).
2. Créer un `Dummy<PlayerProfile>` et l'enregistrer auprès de ModelEngine (`createModeledEntity`).
3. Charger l'`ActiveModel` correspondant au `modelId` et l'attacher via `addModel(...)`.
4. Parcourir `activeModel.getBones()` ; pour chaque bone exposant un behavior `PlayerLimb`, appeler `setTexture(profile)`.
5. Lancer l'animation via `activeModel.getAnimationHandler().playAnimation(animationName, ...)`.

> `Dummy<PlayerProfile>` est la source de vérité du skin côté serveur.  
> Si `setTexture` est appelé correctement sur chaque limb mais que le rendu reste absent, le problème est dans le `.bbmodel` ou le resource pack client — pas dans le code Java.

---

## Préparation du .bbmodel

**Nommage des bones (obligatoire)**

Les bones de premier niveau doivent avoir exactement ces noms pour que ModelEngine détecte les `PlayerLimb` :

| Bone exact        | Membre         |
|-------------------|----------------|
| `phead_head`      | Tête           |
| `pbody_body`      | Torse          |
| `prarm_right_arm` | Bras droit     |
| `plarm_left_arm`  | Bras gauche    |
| `prleg_right_leg` | Jambe droite   |
| `plleg_left_leg`  | Jambe gauche   |

La hiérarchie attendue dans Blockbench est la suivante (seul le premier niveau compte pour la détection) :

```
waist
├── phead_head
│   ├── Head
│   └── Hat Layer
├── pbody_body
│   ├── Body
│   └── Body Layer
├── prarm_right_arm
│   ├── Right_arm
│   └── Right Arm Layer
├── plarm_left_arm
│   ├── Left_arm
│   └── Left Arm Layer
├── prleg_right_leg
│   ├── Right_leg
│   └── Right Leg Layer
└── plleg_left_leg
    ├── Left_leg
    └── Left Leg Layer
```

**Nom de l'animation (obligatoire)**

L'animation doit s'appeler exactement **`dance`** dans Blockbench. La config `animationName: dance` dans `config.yml` doit correspondre à ce nom.

**Règles de géométrie**

- Chaque limb doit avoir des cubes visibles et ne doit pas être placé dans un groupe masqué.
- Chaque limb doit être indépendant (pas parenté à la tête ou à un autre limb).

---

## Intégration dans ModelEngine

1. Exporter le `.bbmodel` depuis Blockbench (conserver textures et animations).
2. Copier le fichier dans `plugins/ModelEngine/blueprints/`.
3. Vérifier côté serveur : `createActiveModel(modelId)` retourne un `ActiveModel` non nul.
4. Lancer `/danse debug` et vérifier que les bones `PlayerLimb` sont détectés (`Bones found: N`) et que `✓ Skin applied` apparaît pour chaque limb attendu.
5. Vérifier que l'animation `dance` est bien présente dans le blueprint (visible dans les logs debug si le nom ne correspond pas).

---

## Vérification rapide

**Côté serveur (logs `/danse debug`)**
- `Bones found: N` — valeur non nulle
- `✓ Skin applied` pour chaque limb attendu
- `activeModel != null`

**Côté modèle / client**
- L'animation dans Blockbench s'appelle bien `dance` (et `animationName: dance` dans `config.yml`)
- Les cubes des limbs sont présents et visibles dans Blockbench
- Le resource pack ModelEngine est correctement chargé par le client

---

## Dépannage

**Procédure d'isolation recommandée**

Créer un `.bbmodel` minimal avec `phead_` uniquement, puis réintroduire les membres un à un et tester à chaque étape :

1. `phead_` → confirmer que la tête affiche le skin
2. `pbody_` → confirmer que le torse affiche le skin
3. `prarm_` / `plarm_` → bras droit puis gauche
4. `prleg_` / `plleg_` → jambes

**Points techniques**

- `HeadForcedImpl` est un behavior interne ajouté automatiquement par ModelEngine ; ne pas tenter de le supprimer depuis le plugin.
- Si la tête fonctionne mais pas les autres membres, le problème est dans la géométrie du `.bbmodel` ou le resource pack côté client, pas dans le code Java.
- `setTexture(PlayerProfile)` est l'approche principale ; `setTexture(Player)` est implémenté en fallback via réflexion.

**Si le problème persiste**

Joindre le `.bbmodel` et les logs de `/danse debug` dans une issue pour analyse.
