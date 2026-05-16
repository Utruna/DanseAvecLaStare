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
