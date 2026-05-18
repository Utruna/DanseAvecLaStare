# Système de permissions — DanseAvecLaStare

## Objectif
Ce document décrit la hiérarchie de permissions du plugin et comment l'utiliser (LuckPerms, assignation par rôle, définition par style).

## Principe général
- Le plugin utilise des permissions Bukkit déclarées dans `plugin.yml`.
- Les commandes vérifient les permissions côté serveur avant exécution.
- Chaque style peut déclarer sa propre permission dans `config.yml` (champ `permission`).

## Rôles recommandés
- Joueur: accès de base (`danse.player`) — default: `true`.
- DJ: accès aux styles DJ (ex. `danse.style.dj`) et aux commandes joueur.
- Staff: accès aux commandes de gestion (`danse.static`, `danse.choreo`, `danse.playlist`).
- Admin: accès total (`danse.*`) et debug (`danse.debug`).

## Permissions principales
- `danse.player` — accès de base: `list`, `stop`, lancer ses danses.
- `danse.skin` — autoriser l'utilisation du skin d'un autre joueur.
- `danse.static` — créer / déplacer / supprimer des danseurs statiques (`here`, `move`, `delete`, `listID`).
- `danse.choreo` — gérer les chorégraphies.
- `danse.playlist` — créer/modifier/supprimer des playlists.
- `danse.playlist.play` — lancer une playlist publique sur soi.
- `danse.debug` — activer les logs techniques.
- `danse.style.<name>` — permission spécifique à un style (définie par `config.yml`).
- `danse.*` — accès global (regroupe les autres).

## Définir une permission par style (extrait `config.yml`)

```yaml
dances:
  twist:
    displayName: "Twist"
    modelId: danseur
    animationName: dance
    movementType: dynamic
    permission: danse.style.twist

  dj:
    displayName: "DJ"
    modelId: dj_animation1
    animationName: dance
    movementType: dynamic
    permission: danse.style.dj
```

- Si un style n'a pas de `permission`, il est accessible par défaut (aucune vérification de style).
- Le plugin lit ce champ via `DanceManager.getPermission(styleName)`.

## Cartographie commande → permission (résumé)
- `/danse <style>` : `danse.style.<style>` (si défini) ou `danse.player` sinon
- `/danse <style> <pseudo>` : `danse.skin` + permission du style
- `/danse list` / `/danse stop` : `danse.player`
- `/danse here|move|delete|listID|highlight` : `danse.static`
- `/danse choreo ...` : `danse.choreo`
- `/danse playlist ...` : `danse.playlist` (sauf `play` public = `danse.playlist.play`)
- `/danse debug` : `danse.debug`

## Comportement de `/danse help`
- Affiche uniquement les sections pour lesquelles le joueur a la permission.
- Les commandes auxquelles le joueur n'a pas accès sont affichées en grisé (ex.: affichage informatif).
- La console voit toutes les sections sans filtrage.

## Exemple d'assignation (LuckPerms)
- Joueur: `lp group joueur permission set danse.player true`
- DJ: `lp group dj permission set danse.style.dj true`
- Staff: `lp group staff permission set danse.static true` puis `danse.choreo`, `danse.playlist` etc.
- Admin: `lp group admin permission set danse.* true`

## Notes d'implémentation
- Les vérifications sont effectuées dans `DanseAvecLaStare.onCommand(...)` avant chaque action sensible.
- Les styles sont chargés depuis `config.yml` et `DanceManager` expose `getPermission(styleName)`.
- `plugin.yml` contient la déclaration des permissions principales (voir `src/main/resources/plugin.yml`).

## Bonnes pratiques
- Définir `permission` par style uniquement si accès restreint souhaité.
- Préférer `danse.style.<name>` plutôt que des règles globales trop larges pour un contrôle fin.
- Utiliser des groupes de permissions dans LuckPerms pour simplifier la gestion des rôles.
