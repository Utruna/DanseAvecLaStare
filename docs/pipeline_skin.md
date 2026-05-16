# Pipeline d'affichage du skin sur modèle 3D

## 🎯 Vue d'ensemble

Affiche le skin du joueur sur un modèle 3D (bbmodel) animé via ModelEngine 4.0.9, avec les bones `PlayerLimb` qui rendent automatiquement la texture du joueur.

---

## 1️⃣ Récupération du profil du joueur

**File** : `DanceManager.java` + `SkinService.java`

### Deux approches :
- **Skin d'un autre joueur** : `SkinService.fetchSkin(targetName)` → récupère async le profil Mojang avec texture
- **Skin du joueur actuel** : `player.getPlayerProfile()` → accès direct au profil du joueur qui lance la danse

```java
// Cas 1 : Joueur actuel
PlayerProfile currentProfile = player.getPlayerProfile();
Dancer dancer = new ModelEngineDancer(plugin, modelId, animationName, currentProfile);

// Cas 2 : Autre joueur (asynchrone)
SkinService.fetchSkin(plugin, targetName, (profile) -> {
    Dancer dancer = new ModelEngineDancer(plugin, modelId, animationName, profile);
});
```

---

## 2️⃣ Création de l'entité visible (ArmorStand)

**File** : `ModelEngineDancer.java` → `spawn(Location, Player)`

### Étapes :

```java
// 1. Spawn une ArmorStand RÉELLE dans le monde
armorStand = location.getWorld().spawn(location, ArmorStand.class, stand -> {
    stand.setGravity(false);        // Pas de gravité
    stand.setInvisible(true);        // Invisible, seul le modèle s'affiche
    stand.setCanMove(true);
    stand.setArms(true);             // Avec bras
    stand.setBasePlate(false);       // Sans socle
});

// 2. Créer un BukkitPlayer pour accéder aux données du joueur
bukkitPlayer = new BukkitPlayer(player);
```

**Pourquoi ArmorStand ?**
- C'est une vraie entité Bukkit visible en jeu
- ModelEngine peut l'envelopper et lui attacher des modèles
- Elle supporte les rotations et animations

---

## 3️⃣ Enveloppe dans ModelEngine

**File** : `ModelEngineDancer.java` → `spawn()`

### Enregistrement de l'entité :

```java
// 1. Wrapper l'ArmorStand avec ModelEngine
modeledEntity = ModelEngineAPI.createModeledEntity(armorStand);

// 2. Enregistrer l'entité auprès de ModelEngine
modeledEntity.registerSelf();  // ou .register() selon la version
```

**Résultat** : L'ArmorStand est maintenant gérée par ModelEngine et peut recevoir des modèles.

---

## 4️⃣ Chargement et attachement du modèle 3D

**File** : `ModelEngineDancer.java` → `spawn()`

### Processus :

```java
// 1. Charger le blueprint bbmodel depuis les ressources
activeModel = ModelEngineAPI.createActiveModel(modelId);

// 2. Attacher le modèle à l'ArmorStand
modeledEntity.addModel(activeModel, true);

// 3. Vérifier que les modèles sont bien attachés
Map<String, ActiveModel> models = modeledEntity.getModels();
plugin.getLogger().info("Modèles attachés: " + models.size());
```

**Configuration requise** :
- `modelId` doit exister dans le dossier ModelEngine (`plugins/ModelEngine/models/`)
- Le fichier `.bbmodel` doit contenir des bones `PlayerLimb`

---

## 5️⃣ Détection des PlayerLimb bones

**File** : `ModelEngineDancer.java` → `logModelStructure()`

### Vérification des bones :

```java
Map<String, Bone> bones = activeModel.getBones();
for (Bone bone : bones.values()) {
    List<BoneBehavior> behaviors = bone.getImmutableBoneBehaviors();
    // Si le behavior est "PlayerLimb" → affichera le skin
    for (BoneBehavior behavior : behaviors) {
        if (behavior instanceof PlayerLimb) {
            System.out.println("✓ " + bone.getName() + " affichera le skin");
        }
    }
}
```

**Bones attendus** (affichent automatiquement le skin du joueur) :
- `head` (PlayerLimb)
- `body` (PlayerLimb)
- `left_arm` (PlayerLimb)
- `right_arm` (PlayerLimb)
- `left_leg` (PlayerLimb)
- `right_leg` (PlayerLimb)

---

## 6️⃣ Application du skin (AUTOMATIQUE)

**Comment ça marche** :
- Les bones de type `PlayerLimb` appliquent **automatiquement** la texture du joueur
- **Pas d'appel manuel** `setTexture()` nécessaire
- ModelEngine gère tout via le `BukkitPlayer` créé

**Note** : Si une méthode `setTexture(Player)` est disponible sur le modèle, elle est utilisée. Sinon, les PlayerLimb bones suffisent.

---

## 7️⃣ Boucle d'animation (Tick Loop)

**File** : `DanceManager.java` → `finishDanceSetup()` + `ModelEngineDancer.java` → `tick()`

### Architecture :

```java
// DanceManager : Créer une tâche Bukkit qui appelle tick() chaque tick
running.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
    // Incrémenter le tick counter
    running.tickCounter++;
    
    // Appeler tick() avec le compteur
    dancer.tick(running.tickCounter, style);
}, 0L, 1L);  // 0 délai initial, 1 tick d'intervalle = 20x/seconde
```

### Dans ModelEngineDancer.tick() :

```java
public void tick(int tick, DanceStyle style) {
    // 1. Calculer la nouvelle position basée sur le DanceStyle
    Location danceLocation = style.computeLocation(owner.getLocation(), tick);
    
    // 2. Téléporter l'ArmorStand à la nouvelle position
    armorStand.teleport(danceLocation);
    
    // 3. Mettre à jour la rotation (head pose)
    EulerAngle angle = new EulerAngle(
        Math.toRadians(danceLocation.getPitch()),
        Math.toRadians(danceLocation.getYaw()),
        0
    );
    armorStand.setHeadPose(angle);
    
    // 4. Jouer l'animation
    if (!animHandler.isPlayingAnimation(animationName)) {
        animHandler.playAnimation(animationName, 0.1, 0.1, 1.0, true);
    }
}
```

**Styles de danse disponibles** :
- `twist`, `spin`, `disco`, `moonwalk`, `wave`, `dj`
- Chaque style implémente `DanceStyle.computeLocation()` pour calculer la position à chaque tick

---

## 8️⃣ Arrêt et nettoyage

**File** : `ModelEngineDancer.java` → `stop()` + `DanceManager.java` → `stopDance()`

### Processus d'arrêt :

```java
// 1. Arrêter la tâche Bukkit
running.task.cancel();

// 2. Désenregistrer le modèle de ModelEngine
modeledEntity.unregisterSelf();  // ou .unregister() / .destroy()

// 3. Supprimer l'ArmorStand du monde
armorStand.remove();

// 4. Restaurer la visibilité du joueur
player.setInvisible(false);
```

---

## 📊 État final

| Composant | État |
|-----------|------|
| ArmorStand | ✅ Spawn, téléportée chaque tick, supprimée à l'arrêt |
| ModeledEntity | ✅ Enregistrée, modèles attachés, désenregistrée à l'arrêt |
| ActiveModel | ✅ Bones PlayerLimb détectés, animations jouées |
| Skin du joueur | ✅ Affiché via les PlayerLimb bones |
| Animation | ✅ Joue en boucle, mise à jour chaque tick |
| Position | ✅ Suit le style de danse via computeLocation() |

---

## 🐛 Diagnostic disponible

### À chaque spawn :
```
[DEBUG] ArmorStand UUID: ...
[DEBUG] ArmorStand ID: ...
[DEBUG] ArmorStand isInvisible: true
[DEBUG] ArmorStand isDead: false
[DEBUG] Modèles attachés à l'entité: 1
```

### À chaque tick (tous les 20 ticks) :
```
[DEBUG] tick() appelé avec tick=20
[DEBUG] ArmorStand alive: true | InvisibleButVisible: true
[DEBUG] Modèles actuels: 1
```

Ces logs confirment que le pipeline est actif et fonctionne correctement.
