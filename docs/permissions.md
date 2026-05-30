# Permission System — ModelDancer

## Purpose
This document describes the plugin permission hierarchy and how to use it (LuckPerms, role-based assignment, style-specific permissions).

## General Rules
- The plugin uses Bukkit permissions declared in `plugin.yml`.
- Commands validate permissions on the server before executing.
- Each style can define its own permission in `config.yml` (the `permission` field).

## Recommended Roles
- Player: base access (`danse.player`) - default: `true`.
- DJ: access to DJ styles (for example `danse.style.dj`) and player commands.
- Staff: access to management commands (`danse.static`, `danse.choreo`, `danse.playlist`) and the staff menu (`danse.staff`).
- Admin: full access (`danse.*`) and debug (`danse.debug`).

## Main Permissions
- `danse.player` - base access: `list`, `stop`, and starting personal dances.
- `danse.skin` - allow using another player's skin.
- `danse.static` - create / move / delete static dancers (`here`, `move`, `delete`, `listID`).
- `danse.choreo` - manage choreography groups.
- `danse.playlist` - create / modify / delete playlists.
- `danse.playlist.play` - start a public playlist on yourself.
- `danse.staff` - access the staff menu and dancer display settings.
- `danse.debug` - enable technical logs.
- `danse.style` - parent node for style permissions (`danse.style.twist`, `danse.style.dj`, etc.).
- `danse.style.<name>` - permission specific to one style (defined in `config.yml`).
- `danse.*` - global access (includes the others).

## Define a Style Permission (excerpt from `config.yml`)

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

-- If a style has no `permission`, it is accessible by default (no style check).
-- The plugin reads this field via `DanceManager.getPermission(styleName)`.

## Command to Permission Mapping (summary)
- `/danse <style>` : `danse.style.<style>` (if defined) or `danse.player` otherwise
- `/danse <style> <pseudo>` : `danse.skin` + permission du style
- `/danse list` / `/danse stop` : `danse.player`
- `/danse here|move|delete|listID|highlight` : `danse.static`
- `/danse choreo ...` : `danse.choreo`
- `/danse playlist ...` : `danse.playlist` (except public `play` = `danse.playlist.play`)
- Staff menu / display settings : `danse.staff`
- `/danse debug` : `danse.debug`

## `/danse help` Behavior
- Shows only the sections for which the player has permission.
- Commands the player cannot use are shown in gray (informational display).
- The console sees all sections without filtering.

## Example Assignment (LuckPerms)
- Player: `lp group player permission set danse.player true`
- DJ: `lp group dj permission set danse.style.dj true`
- Staff: `lp group staff permission set danse.staff true`, then `danse.static`, `danse.choreo`, `danse.playlist`, etc.
- Admin: `lp group admin permission set danse.* true`

## Implementation Notes
- Checks are performed in `DanseAvecLaStare.onCommand(...)` before each sensitive action.
-- Styles are loaded from `config.yml`, and `DanceManager` exposes `getPermission(styleName)`.
-- `plugin.yml` contains the declaration of the main permissions (see `src/main/resources/plugin.yml`).

## Best Practices
- Define `permission` per style only when restricted access is desired.
- Prefer `danse.style.<name>` over overly broad global rules for fine-grained control.
- Use permission groups in LuckPerms to simplify role management.
