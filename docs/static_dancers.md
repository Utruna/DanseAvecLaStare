# Static Dancers

Static dancers are ModelEngine entities independent from players, placed at a fixed position in the world.

---

## Commands

All NPC commands go through `/danse npc <subcommand>`.

```
/danse npc spawn <id> <style>           Spawn an NPC at your position with your skin
/danse npc spawn <id> <style> <player>  Same, but using another player's skin (Mojang async)
/danse npc move <id>                    Move the NPC to your current position
/danse npc delete <id>                  Delete the NPC and remove it from persistence
/danse npc list                         List all active IDs
/danse npc highlight <id> [seconds]     Highlight an NPC with particles (default: 3s)
/danse npc resize <id> <value>          Resize the NPC (0.1 - 20.0, default: 1.0)
/danse npc style <id> <style>           Change the dance style of an existing NPC
```

- `spawn` and `move` are player-only because they rely on player position.
- `delete`, `list`, `resize`, `style`, and `highlight` can be used from the console.
- Tab completion works on active IDs and available styles.

### Highlight

Shows a particle column (`TOTEM_OF_UNDYING` + `CRIT`) above the NPC for the specified duration. Useful for finding an NPC quickly in a busy area.

```
/danse npc highlight lobby_dj        -> particles for 3 seconds (default)
/danse npc highlight lobby_dj 10     -> particles for 10 seconds
```

- Duration is optional; default is 3 seconds.
- The particles are visible to all players in range.
- The task automatically cancels if the dancer is removed before it finishes.

---

## Persistence

Dancers are saved in `plugins/ModelDancer/static_dancers.yml`.

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
  lobby_dancer:
    world: world
    x: 50.0
    y: 64.0
    z: 50.0
    yaw: 180.0
    style: twist
    skin: Notch
    scale: 1.0
```

- `skin`: player name whose skin is used. Null = default skin (Steve/Alex).
- `scale`: model scale factor (default: `1.0`, max: `20.0`). Persisted in YAML and restored on restart.
- The file is managed automatically. Do not edit it manually unless you need to fix an entry.
- On **restart**, dancers are restored with a 3 second delay (60 ticks) to give ModelEngine time to load its blueprints.
- On **onDisable**, entities are destroyed but the file is preserved.

---

## Technical Pipeline

1. `spawnStaticDancer()` creates a `Dummy<PlayerProfile>` with the orientation (`setYBodyRot` / `setYHeadRot`) applied immediately.
2. The `ActiveModel` is loaded through `createActiveModel(blueprintId)` while respecting `useFallbackMode`.
3. If a skin is provided, `applySkinToModel()` applies the texture through reflection on the compatible bones. The discovered `setTexture` methods for each behavior class are cached statically in `ModelEngineDancer` to avoid repeated `getMethods()` calls.
4. If `scale != 1.0`, `activeModel.setScale(scale)` is called after spawn.
5. **The dancer is driven by the global tick** - no individual `BukkitTask` is created per dancer. A single `globalTask` (1 tick/cycle, started in the `StaticDancerManager` constructor) iterates over all active dancers and all groups every tick and restarts stopped animations.
6. `saveDancer()` writes the position, style, player name, and scale to YAML (`synchronized` to protect concurrent writes during async Mojang fetches).
7. `moveStaticDancer()` updates `dummy.setLocation()` plus `setYBodyRot` / `setYHeadRot` and overwrites the saved entry.
8. `setScale()` calls `activeModel.setScale()` and persists the value; scale is also reapplied in `swapModel()` so it survives animation changes.
9. `changeDancerStyle()` temporarily pauses the animation through `pausedDancers` / `pausedGroups` (see below), calls `changeAnimation()`, then removes the pause entry and persists the new style in YAML.

## Error Handling

- Blueprint not found -> warning in logs, spawn canceled, file unchanged.
- World not loaded on restart -> warning, entry ignored (kept in the file).
- `destroy()` failure on delete -> warning logged, entry still removed from the file and map.

---

## Choreography

Choreography groups synchronize animations for multiple static dancers on the same tick.

### Commands

```
/danse choreo create <groupId> <id1> [id2...]   Create a group and start animations simultaneously
/danse choreo add <groupId> <id>               Add a dancer and resynchronize
/danse choreo remove <groupId> <id>            Remove a dancer (returns to solo mode)
/danse choreo sync <groupId>                   Force resynchronization without dissolving the group
/danse choreo delete <groupId>                 Dissolve the group (dancers return to solo mode)
/danse choreo list                             List all groups and their members
```

- All of these commands can be used from the console.
- Tab completion works on group and dancer IDs.

### Persistence

Groups are saved in `plugins/ModelDancer/choreography.yml`.

```yaml
groups:
  main_scene:
    - lobby_dj
    - lobby_dancer
  background:
    - left_dancer
    - right_dancer
```

- On restart, groups are restored with an additional 40 tick delay after the dancers (to allow async skin fetches to complete).
- If a dancer ID is missing during loading, it is ignored with a warning.
- When a dancer belongs to a group, its individual animation task is paused; the group's shared task drives all its members.

### Technical Behavior

1. `createChoreography()` records the members in `choreographyGroups`, removes each dancer from other individual tasks (`dancerToGroup`), and calls `syncAnimations()`.
2. `syncAnimations()` stops all group animations on the same tick and restarts them simultaneously (`lerpIn=0`, `lerpOut=0`). This is the only place where synchronization is explicitly forced.
3. The `globalTask` monitors every member of every active group and restarts its animation if it stops - there is no separate shared task per group.

### Pause Mechanism (interaction with PlaylistManager)

To allow `PlaylistManager` to change a dancer or group animation without conflicting with the global tick, two pause sets are used:

| Set | Managed by | Effect on the global tick |
|---|---|---|
| `pausedDancers` | `pauseAnimationTask(id)` / `resumeAnimationTask(id)` | The global tick ignores solo dancers listed in this set |
| `pausedGroups` | `pauseGroupTask(groupId)` / `resumeGroupTask(groupId)` | The global tick ignores all members of the groups listed in this set |

Typical sequence in `PlaylistManager`:
1. `pauseAnimationTask(id)` -> adds `id` to `pausedDancers`
2. `changeAnimation(id, styleName)` -> changes the animation (no conflict with the tick)
3. `resumeAnimationTask(id)` -> removes `id` from `pausedDancers`, and the global tick resumes control
