# Menus — ModelDancer

This page documents the inventory menus provided by the plugin and the minimal API used to open or extend them.

## Overview

- Menus are Bukkit inventories with a `DanceMenu` holder.
- Clicks are intercepted by `MenuListener` and dispatched through `DanceMenuManager`.
- Click handlers are stored per slot for each player (the map is rebuilt when the menu opens).
- Navigation: `DanceMenuManager` keeps a per-player stack (`navStack`) and provides a `← Back` button.
- Several menus are one-shot and open chat prompts for creation, renaming, or skin changes.

## Public Menus (Summary)

- Player Main (`openPlayerMain`) - lets the player dance, stop, see the active playlist, and access the staff menu if they have `danse.staff`.
- Player Styles (`openPlayerStyles`) - lists the styles available to the player (left click = start, right click = chat for skin name).

- Staff Main (`openStaffMain`) - central hub for staff: access static dancers, choreography, playlists, online players, display settings, and dancer creation.
- Staff Dancer (`openStaffDancer`) - manage a static dancer: change style, move, assign playlist/group, change skin, rename, delete.
- Staff Playlist (`openStaffPlaylist`) - playlist editor: view tracks, remove a track (click), start on a target, add a track.
- Staff Playlist List (`openStaffPlaylistList`) - list playlists, create a new playlist (one-shot chat).
- Staff Choreo (list / group) - manage choreography groups, create, add/remove, sync.
- Pickers (playlist picker, target picker, group picker, player picker) - submenus used to select targets or objects.
- Choreo config / Playlist track config - repetition configuration screens with +/- buttons and confirmation.

> For the full list and detailed behavior, see the `open*` methods in `src/main/java/me/utruna/danse/menu/DanceMenuManager.java`.

## Important Interactions and Controls

- Back: `makeBack()` places an `ARROW` item in a dedicated slot that opens the previous page.
- Delete confirmation: most destructive actions require `Shift+click`.
- Chat-based changes: operations that create, rename, or change skin register temporary listeners (`OneShot*`) that read the player's next chat message.
- Empty slots are filled with gray glass (`makeGlass()`) to avoid invalid clicks.

## Minimal API (Quick Reference)

- `DanceMenu` interface (holder):
  - `Inventory getInventory()` (inherited from `InventoryHolder`)
  - `void handleClick(InventoryClickEvent e, Player player)` - proposed internal dispatch.

- Functional interface `ClickHandler`:
  - `void onClick(Player player, ClickType clickType)` - slot handler.

- `DanceMenuManager` (exposed through the plugin constructor): useful public methods:
  - `openPlayerMain(Player p)`
  - `openPlayerSettings(Player p)`
  - `openPlayerStyles(Player p)`
  - `openStaffMain(Player p)`
  - `openStaffDancer(Player viewer, String dancerId)`
  - `openStaffPlaylist(Player viewer, String playlistId)`
  - `openChoreoSelectStyle(Player viewer, String groupId)`
  - `openChoreoConfigTrack(Player viewer, String groupId, String styleName, int repetitions)`
  - `openStaffPlaylistList(Player viewer)`
  - `openStaffChoreoList(Player viewer)`
  - `openStaffChoreoGroup(Player viewer, String groupId)`
  - `openStaffPlaylistPicker(Player viewer, String targetType, String targetId)`
  - `openTargetPicker(Player viewer, String playlistId, String targetType)`
  - `openStaffPlayer(Player viewer, Player target)`
  - `openPlayerStylePicker(Player viewer, Player target)`

  Implementation notes:
  - `beginOpen(...)` initializes per-player state and pushes a navigation key (`navStack`).
  - `register(Inventory inv, int slot, ItemStack item, ClickHandler handler)` places an item and registers the handler for the current slot.
  - `dispatch(Player p, int slot, ClickType click)` calls the handler registered for that player/slot.

- `MenuListener`: listens to `InventoryClickEvent` and `InventoryCloseEvent` and delegates to `DanceMenu`.

- `MenuType`: enumerates the logical UI types used by the interface: `PLAYER_MAIN`, `PLAYER_STYLES`, `STAFF_MAIN`, `STAFF_DANCER`, `STAFF_PLAYLIST`, `STAFF_CHOREOGRAPHY`, `CHOREO_SELECT_STYLE`, `CHOREO_CONFIG_TRACK`.

  Note: the Settings menu exists in the code, but it is not yet reflected in `MenuType`.

## Usage Examples

- Open the player menu from a command or event handler:

```java
// inject your DanceMenuManager instance
menuManager.openPlayerMain(player);
```

- Force open the staff menu for a player (check `danse.staff` first):

```java
if (player.hasPermission("danse.staff")) menuManager.openStaffMain(player);
```

## Contributor Notes

- Click handlers are only valid for the current opening; they are cleaned up on `InventoryClose` (after a 1 tick delay to allow internal navigation).
- If you add a new `openXXX` menu, use `beginOpen(...)`, `register(...)`, `fill(...)`, and `p.openInventory(inv)` to stay compatible.
- To create a one-shot chat prompt, follow the `OneShot*` pattern (register the listener, cancel chat, call `HandlerList.unregisterAll(this)` after receiving the message).

---

Source files to inspect:
- [DanceMenuManager](src/main/java/me/utruna/danse/menu/DanceMenuManager.java)
- [MenuListener](src/main/java/me/utruna/danse/menu/MenuListener.java)
- [ClickHandler](src/main/java/me/utruna/danse/menu/ClickHandler.java)
- [DanceMenu](src/main/java/me/utruna/danse/menu/DanceMenu.java)
- [MenuType](src/main/java/me/utruna/danse/menu/MenuType.java)

If you want, I can add a link from the README to this page or expand the doc with screenshots and more detailed examples.