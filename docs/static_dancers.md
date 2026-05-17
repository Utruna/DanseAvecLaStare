# Danseurs statiques

Les danseurs statiques sont des entités ModelEngine indépendantes des joueurs, placées à une position fixe dans le monde.

---

## Commandes

```
/danse here <id> <style>           Pose un danseur à ta position avec ton skin
/danse here <id> <style> <pseudo>  Idem avec le skin d'un autre joueur (Mojang async)
/danse move <id>                   Déplace le danseur à ta position actuelle
/danse delete <id>                 Supprime le danseur et l'efface de la sauvegarde
/danse listID                      Liste tous les IDs actifs
```

- `delete` et `listID` sont utilisables depuis la console.
- La complétion par Tab fonctionne sur les IDs actifs pour `move` et `delete`.

---

## Persistance

Les danseurs sont sauvegardés dans `plugins/DanseAvecLaStare/static_dancers.yml`.

```yaml
dancers:
  lobby_dj:
    world: world
    x: 100.5
    y: 64.0
    z: 200.5
    yaw: 90.0
    style: dj
    skin: Utruna
  danseur_accueil:
    world: world
    x: 50.0
    y: 64.0
    z: 50.0
    yaw: 180.0
    style: twist
    skin: Notch
```

- `skin` : pseudo du joueur dont le skin est utilisé. Null = skin par défaut (Steve/Alex).
- Le fichier est géré automatiquement. Ne pas modifier manuellement sauf pour corriger une entrée.
- Au **redémarrage**, les danseurs sont restaurés avec un délai de 3 secondes (60 ticks) pour laisser ModelEngine charger ses blueprints.
- Au **onDisable**, les entités sont détruites mais le fichier est conservé.

---

## Pipeline technique

1. `spawnStaticDancer()` crée un `Dummy<PlayerProfile>` avec orientation (`setYBodyRot` / `setYHeadRot`) appliquée immédiatement.
2. L'`ActiveModel` est chargé via `createActiveModel(blueprintId)` en respectant `useFallbackMode`.
3. Si un skin est fourni, `applySkinToModel()` applique la texture par réflexion sur les bones compatibles.
4. Une `BukkitTask` (1 tick) relance l'animation en boucle si elle s'arrête.
5. `saveDancer()` écrit la position, le style et le pseudo dans le YAML (`synchronized` pour les écritures concurrentes lors des fetch Mojang async).
6. `moveStaticDancer()` met à jour `dummy.setLocation()` + `setYBodyRot/YHeadRot` et écrase l'entrée du fichier.

## Gestion des erreurs

- Blueprint introuvable → warning dans les logs, spawn annulé, fichier non modifié.
- Monde non chargé au restart → warning, entrée ignorée (conservée dans le fichier).
- Échec de `destroy()` au delete → warning logué, entrée retirée quand même du fichier et de la map.

---

## Chorégraphie

Les chorégraphies synchronisent les animations de plusieurs danseurs statiques sur le même tick.

### Commandes

```
/danse choreo create <groupId> <id1> [id2…]  Crée un groupe et lance les animations simultanément
/danse choreo add <groupId> <id>             Ajoute un danseur et re-synchronise
/danse choreo remove <groupId> <id>          Retire un danseur (reprend en mode solo)
/danse choreo sync <groupId>                 Force la re-synchronisation sans dissoudre le groupe
/danse choreo delete <groupId>               Dissout le groupe (les danseurs reprennent en solo)
/danse choreo list                           Liste tous les groupes et leurs membres
```

- Toutes ces commandes sont utilisables depuis la console.
- La complétion par Tab fonctionne sur les IDs de groupes et de danseurs.

### Persistance

Les groupes sont sauvegardés dans `plugins/DanseAvecLaStare/choreography.yml`.

```yaml
groups:
  scene_principale:
    - lobby_dj
    - danseur_accueil
  fond_de_scene:
    - danseur_gauche
    - danseur_droit
```

- Au redémarrage, les groupes sont restaurés avec un délai supplémentaire de 40 ticks après les danseurs (pour laisser le temps aux fetches de skin async).
- Si un ID de danseur est introuvable au chargement, il est ignoré avec un warning.
- Quand un danseur est dans un groupe, sa tâche d'animation individuelle est suspendue ; c'est la tâche partagée du groupe qui pilote tous ses membres.

### Comportement technique

1. `createChoreography()` annule les tâches individuelles des danseurs concernés et lance une tâche partagée (1 tick).
2. `syncAnimations()` arrête toutes les animations du groupe au même tick puis les redémarre simultanément (`lerpIn=0`, `lerpOut=0`).
3. La tâche partagée surveille chaque membre et relance son animation si elle s'est arrêtée (boucle native ME4 non gérée).
