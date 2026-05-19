# Danseurs statiques

Les danseurs statiques sont des entités ModelEngine indépendantes des joueurs, placées à une position fixe dans le monde.

---

## Commandes

Toutes les commandes NPC passent par `/danse npc <sous-commande>`.

```
/danse npc spawn <id> <style>           Pose un NPC à ta position avec ton skin
/danse npc spawn <id> <style> <pseudo>  Idem avec le skin d'un autre joueur (Mojang async)
/danse npc move <id>                    Déplace le NPC à ta position actuelle
/danse npc delete <id>                  Supprime le NPC et l'efface de la sauvegarde
/danse npc list                         Liste tous les IDs actifs
/danse npc highlight <id> [secondes]    Signale un NPC avec des particules (défaut : 3s)
/danse npc resize <id> <valeur>         Redimensionne le NPC (0.1 – 20.0, défaut : 1.0)
/danse npc style <id> <style>           Change le style de danse d'un NPC existant
```

- `spawn` et `move` sont réservés aux joueurs (besoin de leur position).
- `delete`, `list`, `resize`, `style` et `highlight` sont utilisables depuis la console.
- La complétion par Tab fonctionne sur les IDs actifs et les styles disponibles.

### highlight

Fait apparaître une colonne de particules (`TOTEM_OF_UNDYING` + `CRIT`) au-dessus du NPC pendant la durée indiquée. Utile pour localiser rapidement un NPC dans une zone chargée.

```
/danse npc highlight lobby_dj        → particules pendant 3 secondes (défaut)
/danse npc highlight lobby_dj 10     → particules pendant 10 secondes
```

- La durée est optionnelle, défaut : 3 secondes.
- Les particules sont visibles par tous les joueurs à portée.
- La tâche s'annule automatiquement si le danseur est supprimé avant la fin.

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
    scale: 1.5
  danseur_accueil:
    world: world
    x: 50.0
    y: 64.0
    z: 50.0
    yaw: 180.0
    style: twist
    skin: Notch
    scale: 1.0
```

- `skin` : pseudo du joueur dont le skin est utilisé. Null = skin par défaut (Steve/Alex).
- `scale` : facteur d'échelle du modèle (défaut : `1.0`, max : `20.0`). Persisté dans le YAML et restauré au redémarrage.
- Le fichier est géré automatiquement. Ne pas modifier manuellement sauf pour corriger une entrée.
- Au **redémarrage**, les danseurs sont restaurés avec un délai de 3 secondes (60 ticks) pour laisser ModelEngine charger ses blueprints.
- Au **onDisable**, les entités sont détruites mais le fichier est conservé.

---

## Pipeline technique

1. `spawnStaticDancer()` crée un `Dummy<PlayerProfile>` avec orientation (`setYBodyRot` / `setYHeadRot`) appliquée immédiatement.
2. L'`ActiveModel` est chargé via `createActiveModel(blueprintId)` en respectant `useFallbackMode`.
3. Si un skin est fourni, `applySkinToModel()` applique la texture par réflexion sur les bones compatibles.
4. Si `scale != 1.0`, `activeModel.setScale(scale)` est appelé après le spawn.
5. Une `BukkitTask` (1 tick) relance l'animation en boucle si elle s'arrête.
6. `saveDancer()` écrit la position, le style, le pseudo et l'échelle dans le YAML (`synchronized` pour les écritures concurrentes lors des fetch Mojang async).
7. `moveStaticDancer()` met à jour `dummy.setLocation()` + `setYBodyRot/YHeadRot` et écrase l'entrée du fichier.
8. `setScale()` appelle `activeModel.setScale()` et persiste la valeur ; l'échelle est aussi réappliquée dans `swapModel()` pour survivre aux changements d'animation.
9. `changeDancerStyle()` met en pause la tâche d'animation (individuelle ou de groupe), appelle `changeAnimation()` puis relance la tâche et persiste le nouveau style dans le YAML.

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
