# Playlists

Le système de playlists permet de programmer des séquences d'animations avec répétitions sur un joueur, un danseur statique ou un groupe chorégraphique.

---

## Concepts

- **Playlist** : une liste ordonnée de pistes, jouée en boucle (`loop`) ou une seule fois (`once`).
- **Piste (track)** : un style de danse + un nombre de répétitions. La durée d'une répétition est calculée automatiquement depuis la longueur de l'animation dans ModelEngine.
- **Cible** : `player`, `dancer` (danseur statique) ou `group` (groupe chorégraphique).

---

## Commandes

### Gestion des playlists

```
/danse playlist create <id> [loop|once]     Crée une playlist (en boucle par défaut)
/danse playlist add <id> <style> <rép>      Ajoute une piste (style × N répétitions)
/danse playlist remove <id> <index>         Supprime la piste à l'index donné (commence à 0)
/danse playlist delete <id>                 Supprime la playlist et arrête toutes ses lectures actives
/danse playlist info <id>                   Affiche les pistes avec leur index
/danse playlist list                        Liste toutes les playlists définies
```

### Lecture

```
/danse playlist play <id> player [pseudo]   Lance sur un joueur (soi-même si pseudo omis)
/danse playlist play <id> dancer <dancerId> Lance sur un danseur statique
/danse playlist play <id> group  <groupId>  Lance sur un groupe chorégraphique
```

### Arrêt

```
/danse playlist stop player [pseudo]        Arrête la playlist d'un joueur
/danse playlist stop dancer <dancerId>      Arrête la playlist d'un danseur
/danse playlist stop group  <groupId>       Arrête la playlist d'un groupe
```

### Supervision

```
/danse playlist active                      Affiche toutes les lectures en cours (joueurs, danseurs, groupes)
/danse playlist debug                       Active/désactive les logs de diagnostic playlist dans la console
```

- Toutes ces commandes sont utilisables depuis la console.
- La complétion par Tab fonctionne sur les IDs de playlists, styles, joueurs, danseurs et groupes.

---

## Exemple d'utilisation

```
# Créer une playlist en boucle
/danse playlist create show loop

# Ajouter des pistes
/danse playlist add show twist 2
/danse playlist add show dj 1
/danse playlist add show salsa 3

# Vérifier le contenu
/danse playlist info show
#   #0 → twist [×2 rép.]
#   #1 → dj    [×1 rép.]
#   #2 → salsa [×3 rép.]

# Lancer sur un groupe
/danse playlist play show group scene_principale

# Arrêter
/danse playlist stop group scene_principale
```

---

## Persistance

Les playlists sont sauvegardées dans `plugins/DanseAvecLaStare/playlists.yml`.

```yaml
playlists:
  show:
    loop: true
    tracks:
      - style: twist
        repetitions: 2
      - style: dj
        repetitions: 1
      - style: salsa
        repetitions: 3
```

- Les playlists sont restaurées au redémarrage mais les lectures actives ne le sont pas (à relancer manuellement).
- `deletePlaylist` arrête immédiatement toutes les lectures actives liées à cette playlist.

---

## Comportement technique

- La durée d'une répétition est calculée via `BlueprintAnimation.getLength()` (retourne des secondes) × 20 → ticks.
- Le `PlaylistRunner` enchaîne les pistes avec `BukkitScheduler.runTaskLater()` ; chaque transition planifie la piste suivante à la fin de la durée courante.
- Pour les groupes, `changeGroupAnimation()` appelle `playAnimation()` sur tous les membres dans le même tick → synchronisation maintenue entre pistes.
- Les tâches d'animation individuelle/groupe restent actives pendant la playlist (ME4 ne boucle pas nativement les animations — la tâche est nécessaire pour relancer l'anim si elle s'arrête entre deux checks).
- Si une playlist en `loop` se termine, elle repart automatiquement depuis la première piste.
- `/danse stop` arrête à la fois la playlist du joueur et sa danse ME4.
