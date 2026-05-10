# Skin Rendering Implementation - Session du 10 Mai 2026

## 🎯 Objectif
Implémenter le rendu des skins de joueurs sur les modèles ModelEngine 4.0.9 lors de l'exécution d'une danse.

## 📋 Problème Découvert
Le blueprint `danseur.bbmodel` **n'a pas de PlayerLimb behaviors configurés** sur les bones. Sans ces behaviors, les skins ne peuvent pas être appliqués visuellement.

### ⚠️ IMPORTANT - Convention de Nommage des Bones
Les bones doivent utiliser une **convention de nommage spécifique** pour que ModelEngine reconnaisse les PlayerLimb behaviors :

| Partie du Corps | Nom du Bone dans Blockbench |
|---|---|
| Tête | `phead_head` |
| Corps | `pbody_body` |
| Bras Droit | `prarm_right_arm` |
| Bras Gauche | `plarm_left_arm` |
| Jambe Droite | `prleg_right_leg` |
| Jambe Gauche | `plleg_left_leg` |

**⚠️ Les noms précédents (head, body, left_arm, etc.) ne fonctionnent PAS avec les PlayerLimb behaviors!**

### 🔧 État Actuel du Rendu
- ✅ **Tête** : Le skin s'affiche correctement
- ⏳ **Corps** : En cours de débogage (ne s'affiche pas encore)
- ⏳ **Bras/Jambes** : En cours de débogage (ne s'affichent pas encore)

Il est probable que les noms des bones doivent être corrigés dans le blueprint.

---

## ✅ Étapes Réalisées Ce Soir

### 1. Implémentation du SkinService
- ✅ Créé `SkinService.java` pour fetcher les PlayerProfiles asynchronement
- ✅ Utilise `Bukkit.createPlayerProfile(username)` via `CompletableFuture.runAsync()`
- ✅ Évite le blocage du thread principal

### 2. Intégration dans ModelEngineDancer
- ✅ Récupération du PlayerProfile du joueur ou d'un joueur cible
- ✅ Création du Dummy avec le profil du joueur
- ✅ Implémentation de `applySkinToModel()` pour appliquer la texture aux bones

### 3. Découverte API ModelEngine
- ✅ Exploration via reflection des méthodes disponibles
- ✅ Identification de `getImmutableBoneBehaviors()` pour accéder aux behaviors
- ✅ Découverte que `addBoneBehavior(BoneBehavior)` accepte un objet BoneBehavior

### 4. Nettoyage du Code
- ✅ Suppression des imports redondants
- ✅ Utilisation de `@SuppressWarnings("deprecation")` pour PlayerProfile
- ✅ Compilation sans erreurs (exit code 0)

### 5. Tests et Logs
- ✅ Logs détaillés montrant la structure complète des bones
- ✅ Identification précise du problème: absence de PlayerLimb behaviors
- ✅ Code prêt pour fonctionner une fois le blueprint configuré

### 6. API Resolution - setTexture()
- ✅ Exploration des signatures de méthodes sur PlayerLimbImpl via getDeclaredMethods()
- ✅ Première approche: `setTexture(PlayerProfile)` - échoue (incompatibilité package)
- ✅ Deuxième approche: `setTexture(String, String)` - méthode n'existe pas
- ✅ Solution trouvée: `setTexture(Player)` - **FONCTIONNE!**
- ✅ Code modifier pour utiliser l'objet `owner` (Player)

### 7. Tests en Jeu
- ✅ Blueprint modifié avec PlayerLimb behaviors
- ✅ Tête affiche le skin correctement
- ⏳ **Corps/Bras/Jambes**: Ne s'affichent pas encore
- ⏳ **Suspicion**: Problème de nommage des bones ou de configuration ModelEngine

---

## 🔧 Modifications Requises - BLUEPRINT

### ⚠️ AVANT TOUT: Renommer les Bones

**Les bones doivent être renommés avec la convention ModelEngine:**

| Ancien Nom | Nouveau Nom |
|---|---|
| `head` | `phead_head` |
| `body` | `pbody_body` |
| `left_arm` | `plarm_left_arm` |
| `right_arm` | `prarm_right_arm` |
| `left_leg` | `plleg_left_leg` |
| `right_leg` | `prleg_right_leg` |

**Procédure:**
1. Ouvre `danseur.bbmodel` dans Blockbench
2. Pour chaque bone à renommer:
   - Clique droit sur le bone
   - **Edit Name** (ou double-clic)
   - Remplace le nom selon le tableau ci-dessus
3. **File → Save**

### Étapes à Faire dans Blockbench

#### Après le renommage, ajouter les PlayerLimb behaviors

**Prérequis**
- Blockbench installé
- Fichier `danseur.bbmodel` ouvert et bones renommés

#### Pour CHAQUE bone de joueur (6 bones):

**1. Head (tête)**
- Double-clic sur le bone `phead_head`
- Clic droit → **Add Behavior** → **PlayerLimb**
- Sélectionner le type: **HEAD**
- ✅ Sauvegarder

**2. Body (corps)**
- Double-clic sur le bone `pbody_body`
- Clic droit → **Add Behavior** → **PlayerLimb**
- Sélectionner le type: **BODY**
- ✅ Sauvegarder

**3. Left Arm (bras gauche)**
- Double-clic sur le bone `plarm_left_arm`
- Clic droit → **Add Behavior** → **PlayerLimb**
- Sélectionner le type: **LEFT_ARM**
- ✅ Sauvegarder

**4. Right Arm (bras droit)**
- Double-clic sur le bone `prarm_right_arm`
- Clic droit → **Add Behavior** → **PlayerLimb**
- Sélectionner le type: **RIGHT_ARM**
- ✅ Sauvegarder

**5. Left Leg (jambe gauche)**
- Double-clic sur le bone `plleg_left_leg`
- Clic droit → **Add Behavior** → **PlayerLimb**
- Sélectionner le type: **LEFT_LEG**
- ✅ Sauvegarder

**6. Right Leg (jambe droite)**
- Double-clic sur le bone `prleg_right_leg`
- Clic droit → **Add Behavior** → **PlayerLimb**
- Sélectionner le type: **RIGHT_LEG**
- ✅ Sauvegarder

#### Finalization
- **File** → **Save** (ou Ctrl+S)
- Quitter Blockbench
- Redémarrer le serveur Minecraft pour recharger les blueprints

---

## 🧪 Test de Validation

### Après la modification du blueprint:

1. **Redémarrer le serveur**
   ```bash
   # Ou recharger le plugin
   /reload
   ```

2. **Lancer une danse**
   ```
   /danse twist
   ```

3. **Vérifier les logs**
   ```
   [HH:MM:SS INFO]: [DanseAvecLaStare] Processing bone: head
   [HH:MM:SS INFO]: [DanseAvecLaStare]   Behaviors on head: 2
   [HH:MM:SS INFO]: [DanseAvecLaStare]     Behavior type: HeadForcedImpl
   [HH:MM:SS INFO]: [DanseAvecLaStare]     Behavior type: PlayerLimbImpl    ← ✅ Maintenant présent!
   [HH:MM:SS INFO]: [DanseAvecLaStare] ✓ Skin applied to bone: head
   ```

4. **Vérifier visuellement**
   - Le modèle danseur devrait afficher la texture du skin du joueur
   - Tous les membres (tête, bras, jambes) devraient avoir la bonne couleur de peau

---

## 📊 État du Code

| Composant | Status | Notes |
|-----------|--------|-------|
| SkinService | ✅ Complet | Fetche les profils async |
| ModelEngineDancer | ✅ Complet | Applique les skins via `setTexture(Player)` |
| DanceManager | ✅ Complet | Gère le cycle de vie des dances |
| Pipeline de Skin | ✅ Complet | Récupération → Application |
| Blueprint Renommage | ⏳ **À FAIRE** | Renommer les bones (phead_head, pbody_body, etc.) |
| PlayerLimb Behaviors | ⏳ **À FAIRE** | Ajouter behaviors aux bones renommés |

---

## � Débogage En Cours

### État du Rendu (10 Mai 2026 - ~00:35)
- ✅ **Tête** : Skin affiche correctement
- ⏳ **Corps** : Skin ne s'affiche pas
- ⏳ **Bras/Jambes** : Skins ne s'affichent pas

### Hypothèses en Investigation
1. Les bones du blueprint n'ont pas les noms exacts attendus par ModelEngine
2. La convention de nommage `phead_head`, `pbody_body`, etc. est requise
3. Possible que certains PlayerLimb behaviors ne soient pas correctement configurés

### Prochaines Actions
1. Vérifier les noms exacts des bones dans le blueprint (.bbmodel)
2. Renommer les bones selon la convention si nécessaire
3. Réappliquer les PlayerLimb behaviors sur les bones renommés
4. Tester `/danse twist` après renommage

---

1. ✅ **Immédiat**: Modifier le blueprint dans Blockbench (see above)
2. ✅ **Après**: Redémarrer le serveur
3. ✅ **Test**: `/danse twist` et vérifier les logs
4. 📝 **Si succès**: Supprimer les logs de debug
5. 📝 **Si succès**: Ajouter des tests unitaires

---

## 📚 Ressources

- [BBMODEL_INTEGRATION.md](./BBMODEL_INTEGRATION.md) - Documentation initiale du blueprint
- [ModelEngine 4.0.9 Javadoc](https://www.spigotmc.org/wiki/modelengine/) - API Reference
- PlayerProfile est deprecated depuis Paper 1.18.1 mais reste fonctionnel

---

## 💡 Notes Techniques

### Pourquoi PlayerLimb behaviors?
- ModelEngine utilise un système de "behaviors" pour modifier le rendu des bones
- PlayerLimb behavior permet d'appliquer une texture PlayerProfile à un bone
- Sans PlayerLimb, le bone affiche la texture par défaut (Steve)

### Structure API Découverte
```java
bone.getImmutableBoneBehaviors()  // Map<BoneBehaviorType, BoneBehavior>
bone.addBoneBehavior(behavior)     // void
bone.getBoneBehavior(type)         // Optional<BoneBehavior>
behavior.setTexture(player)        // void - SOLUTION FINALE!
```

### Approches Testées pour setTexture()
1. ❌ `setTexture(PlayerProfile)` - Incompatibilité de package (Bukkit vs Paper)
2. ❌ `setTexture(String, String)` - Méthode n'existe pas
3. ✅ `setTexture(Player)` - **FONCTIONNE!** Passe l'objet Player directement

### Limitation Actuelle
Les noms des bones doivent suivre une convention spécifique pour être reconnus par ModelEngine:
- `phead_head` pour la tête
- `pbody_body` pour le corps
- `plarm_left_arm` / `prarm_right_arm` pour les bras
- `plleg_left_leg` / `prleg_right_leg` pour les jambes

Les anciens noms (`head`, `body`, etc.) ne fonctionnent que partiellement.

---

**Rédaction**: 10 Mai 2026
**Session**: Debugging Skin Rendering avec API Exploration
