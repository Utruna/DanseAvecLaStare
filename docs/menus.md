# Menus — DanseAvecLaStare

Cette page documente les menus d'inventaire fournis par le plugin et l'API minimale pour les ouvrir/étendre.

## Vue d'ensemble

- Les menus sont des inventaires Bukkit avec un holder `DanceMenu`.
- Les clics sont interceptés par `MenuListener` et dispatchés via `DanceMenuManager`.
- Les handlers de clics sont stockés par-slot pour chaque joueur (map reconstruite à l'ouverture).
- Navigation: `DanceMenuManager` maintient une pile par-joueur (`navStack`) et fournit des boutons « ← Retour ».
- Plusieurs menus sont « one-shot » et ouvrent des prompts chat (création, renommage, changement de skin).

## Menus publics (résumé)

- Player Main (`openPlayerMain`) — permet de danser, arrêter, voir la playlist active, accéder au menu staff si permission `danse.staff`.
- Player Styles (`openPlayerStyles`) — liste des styles disponibles pour le joueur (clic gauche = lancer, clic droit = chat pour pseudo skin).

- Staff Main (`openStaffMain`) — central pour le staff: accès aux danseurs statiques, chorégraphies, playlists, joueurs en ligne, paramètres d'affichage, créer danseur, et cache de skins (slot 3 « Skins en cache »).
- Staff Dancer (`openStaffDancer`) — gestion d'un danseur statique : changer style, déplacer, assigner playlist/groupe, changer skin, renommer, supprimer. Slot 15 « Skin depuis cache » (visible uniquement si le cache contient au moins un alias) ouvre `openSkinCachePicker` et affiche l'alias courant s'il en a un.
- Staff Playlist (`openStaffPlaylist`) — éditeur d'une playlist : voir pistes, retirer piste (clic), lancer sur cible, ajouter piste.
- Staff Playlist List (`openStaffPlaylistList`) — liste des playlists, création d'une nouvelle playlist (one-shot chat).
- Staff Choreo (list / group) — gestion des groupes de chorégraphie, création, ajout/suppression, sync.
- Staff Skin Cache (`openStaffSkinCache`) — liste paginée des alias enregistrés dans `skin_cache.yml`. Clic gauche sur un alias = choisir un NPC cible (`openSkinCachePicker`). Shift+clic = supprimer l'alias. Slot 45 « + » = créer un nouvel alias via prompt chat (`OneShotSkinSaver`).
- Skin Cache Picker (`openSkinCachePicker`) — deux variantes : choisir un alias pour un NPC donné, ou choisir un NPC pour un alias donné. Accessible depuis `openStaffDancer` (slot 15 « Skin depuis cache ») ou depuis `openStaffSkinCache`.
- Pickers (playlist picker, target picker, group picker, player picker) — sous-menus pour sélectionner cibles ou objets.
- Choreo config / Playlist track config — écrans de configuration de répétitions avec boutons +/- et confirmation.

> Pour la liste complète et le comportement détaillé, voir les méthodes `open*` dans `src/main/java/me/utruna/danse/menu/DanceMenuManager.java`.

## Interactions et contrôles importants

- Retour: la méthode `makeBack()` place un item `ARROW` en slot dédié qui invoque la page précédente.
- Confirmation de suppression: la plupart des actions destructrices exigent `Shift+clic`.
- Changement via chat: opérations créant/renommant/changer skin s'inscrivent comme listeners temporaires (`OneShot*`) qui lisent le prochain message du joueur.
- Les slots vides sont remplis par des vitres grises (`makeGlass()`) pour éviter clics invalides.

## API minimale (référence rapide)

- Interface `DanceMenu` (holder):
  - `Inventory getInventory()` (hérité d'InventoryHolder)
  - `void handleClick(InventoryClickEvent e, Player player)` — dispatch interne proposé.

- Interface fonctionnelle `ClickHandler`:
  - `void onClick(Player player, ClickType clickType)` — handler pour un slot.

- `DanceMenuManager` (exposé via constructeur dans le plugin): méthodes publiques utiles :
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
  - `openStaffSkinCache(Player viewer, int page)` — liste paginée du cache de skins
  - `openSkinCachePicker(Player viewer, String dancerId)` — choisir un alias pour un NPC
  - `openSkinCachePicker(Player viewer, String dancerIdOrNull, String fixedAlias)` — choisir un NPC pour un alias fixé

  Remarques d'implémentation :
  - `beginOpen(...)` initialise l'état par-joueur et pousse une clef de nav (`navStack`).
  - `register(Inventory inv, int slot, ItemStack item, ClickHandler handler)` place un item et enregistre le handler pour le slot courant.
  - `dispatch(Player p, int slot, ClickType click)` appelle le handler enregistré pour ce joueur/slot.

- `MenuListener` : écoute `InventoryClickEvent` et `InventoryCloseEvent` et délègue aux `DanceMenu`.

- `MenuType` : énumère quelques types logiques utilisés par l'UI : `PLAYER_MAIN`, `PLAYER_STYLES`, `STAFF_MAIN`, `STAFF_DANCER`, `STAFF_PLAYLIST`, `STAFF_CHOREOGRAPHY`, `CHOREO_SELECT_STYLE`, `CHOREO_CONFIG_TRACK`, `STAFF_SKIN_CACHE`.

  Note: le menu Paramètres existe bien dans le code, mais il n'est pas encore reflété dans `MenuType`.

## Exemples d'utilisation

- Ouvrir le menu joueur depuis une commande / event handler :

```java
// injection via votre instance de DanceMenuManager
menuManager.openPlayerMain(player);
```

- Forcer ouverture du menu staff pour un joueur (vérifier permission `danse.staff` avant) :

```java
if (player.hasPermission("danse.staff")) menuManager.openStaffMain(player);
```

## Notes pour contributeurs

- Les handlers de clics sont valables uniquement pour l'ouverture courante; ils sont nettoyés au `InventoryClose` (après délai 1 tick pour permettre navigation interne).
- Si vous ajoutez un nouveau menu `openXXX`, utilisez `beginOpen(...)`, `register(...)`, `fill(...)` et `p.openInventory(inv)` pour rester compatible.
- Pour créer un one-shot chat prompt, suivez le pattern `OneShot*` (enregistrer le listener, annuler le chat, HandlerList.unregisterAll(this) après réception). Exemple : `OneShotSkinSaver` attend un message `"alias pseudo"`, fetch le skin via `SkinService` et appelle `SkinCacheManager.saveSkin()`.

---

Fichiers source à consulter :
- [DanceMenuManager](src/main/java/me/utruna/danse/menu/DanceMenuManager.java)
- [MenuListener](src/main/java/me/utruna/danse/menu/MenuListener.java)
- [ClickHandler](src/main/java/me/utruna/danse/menu/ClickHandler.java)
- [DanceMenu](src/main/java/me/utruna/danse/menu/DanceMenu.java)
- [MenuType](src/main/java/me/utruna/danse/menu/MenuType.java)


Si vous voulez, j'ajoute un lien depuis le README vers cette page ou j'étends la doc avec des captures d'écran et exemples plus détaillés.