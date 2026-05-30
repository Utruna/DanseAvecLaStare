# Playlists

The playlist system lets you sequence dance animations with repeats on a player, a static dancer, or a choreography group.

---

## Concepts

- **Playlist**: an ordered list of tracks, played in a loop (`loop`) or once (`once`).
- **Track**: a dance style plus a repeat count. The duration of one repeat is computed automatically from the animation length in ModelEngine.
- **Target**: `player`, `dancer` (static dancer), or `group` (choreography group).

---

## Commands

### Playlist management

```
/danse playlist create <id> [loop|once]     Create a playlist (loops by default)
/danse playlist add <id> <style> <repetitions>  Add a track (style × N repeats)
/danse playlist remove <id> <index>         Remove the track at the given index (starts at 0)
/danse playlist delete <id>                 Delete the playlist and stop all active playback
/danse playlist info <id>                   Show the tracks with their indexes
/danse playlist list                        List all defined playlists
```

### Playback

```
/danse playlist set <id> player [playerName]   Start on a player (self if omitted)
/danse playlist set <id> dancer <dancerId>     Start on a static dancer
/danse playlist set <id> group <groupId>       Start on a choreography group
```

### Stop

```
/danse playlist stop player [playerName]       Stop a player's playlist
/danse playlist stop dancer <dancerId>          Stop a dancer's playlist
/danse playlist stop group <groupId>            Stop a group's playlist
```

### Monitoring

```
/danse playlist active                          Show all active playback (players, dancers, groups)
/danse playlist debug                           Enable or disable playlist debug logs in the console
```

- All of these commands can be used from the console.
- Tab completion works for playlist IDs, styles, players, dancers, and groups.

---

## Usage Example

```
# Create a looping playlist
/danse playlist create show loop

# Add tracks
/danse playlist add show twist 2
/danse playlist add show dj 1
/danse playlist add show salsa 3

# Verify the content
/danse playlist info show
#   #0 → twist [×2 reps]
#   #1 → dj    [×1 reps]
#   #2 → salsa [×3 reps]

# Start on a group
/danse playlist set show group main_scene

# Stop
/danse playlist stop group main_scene
```

---

## Persistence

Playlists are saved in `plugins/ModelDancer/playlists.yml`.

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

- Playlists are restored on restart, but active playback is not (it must be started again manually).
- `deletePlaylist` immediately stops all active playback linked to that playlist.

---

## Technical Behavior

- The duration of a repeat is calculated via `BlueprintAnimation.getLength()` (returns seconds) × 20 → ticks.
- `PlaylistRunner` chains tracks with `BukkitScheduler.runTaskLater()`; each transition schedules the next track at the end of the current duration.
- For groups, `changeGroupAnimation()` calls `playAnimation()` on all members in the same tick, keeping tracks synchronized.
- **Pause/resume with the global tick**: before each animation change, `PlaylistManager` calls `pauseAnimationTask(id)` or `pauseGroupTask(groupId)`. These methods no longer cancel any `BukkitTask`; they add the ID to a pause set (`pausedDancers` or `pausedGroups`) that `StaticDancerManager` checks on every tick through its `globalTask`. `resumeAnimationTask` / `resumeGroupTask` simply remove the ID from the set. This avoids repeated Bukkit task creation and cancellation during transitions.
- If a looping playlist finishes, it automatically starts again from the first track.
- `/danse stop` stops both the player's playlist and their ME4 dance.
