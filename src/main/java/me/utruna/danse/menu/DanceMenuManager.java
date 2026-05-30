package me.utruna.danse.menu;

import me.utruna.danse.DanseAvecLaStare;
import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.DanceStyle;
import me.utruna.danse.managers.PlaylistManager;
import me.utruna.danse.managers.SkinService;
import me.utruna.danse.managers.StaticDancerManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.util.*;

public class DanceMenuManager {

    static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final int MIN_RENDER_RADIUS = 1;
    private static final int MAX_RENDER_RADIUS = 256;
    private static final int DEFAULT_RENDER_RADIUS = 256;

    private final DanseAvecLaStare plugin;
    private final DanceManager dm;
    private final StaticDancerManager sdm;
    private final PlaylistManager pm;

    /** Head of deque = current menu descriptor (e.g. "staff_dancer:lobby_dj"). */
    private final Map<UUID, Deque<String>>              navStack          = new HashMap<>();
    /** Rebuilt on every open call. Key = viewer UUID. */
    private final Map<UUID, Map<Integer, ClickHandler>> slotHandlers      = new HashMap<>();
    /** Set when staff opens a playlist sub-menu; consumed by choreo confirm. */
    private final Map<UUID, String>                     playerPlaylistCtx = new HashMap<>();

    /** Set at the start of every open*() call; valid only on the main thread. */
    private UUID currentPlayer;

    public DanceMenuManager(DanseAvecLaStare plugin, DanceManager dm,
                            StaticDancerManager sdm, PlaylistManager pm) {
        this.plugin = plugin;
        this.dm     = dm;
        this.sdm    = sdm;
        this.pm     = pm;
    }

    // ── Package-private API used by MenuListener ──────────────────────────

    DanseAvecLaStare getPlugin() { return plugin; }

    /** Dispatch a click to the registered handler for this player/slot. */
    void dispatch(Player p, int slot, ClickType click) {
        Map<Integer, ClickHandler> h = slotHandlers.get(p.getUniqueId());
        if (h == null) return;
        ClickHandler ch = h.get(slot);
        if (ch != null) ch.onClick(p, click);
    }

    /** Clean all state for this player (called from MenuListener on close). */
    void clearPlayer(UUID id) {
        navStack.remove(id);
        slotHandlers.remove(id);
        playerPlaylistCtx.remove(id);
    }

    // ── Open helpers ──────────────────────────────────────────────────────

    /**
     * Initialises per-player state for a new menu and returns the fresh inventory.
     * Must be called once at the top of every open*() method.
     */
    private Inventory beginOpen(Player p, int size, String title, String navKey) {
        currentPlayer = p.getUniqueId();
        slotHandlers.put(currentPlayer, new HashMap<>());
        navStack.computeIfAbsent(currentPlayer, k -> new ArrayDeque<>()).push(navKey);
        SimpleMenu menu = new SimpleMenu(size, LEGACY.deserialize(title));
        return menu.getInventory();
    }

    /** Sets item in slot and stores handler in the current player's handler map. */
    private void register(Inventory inv, int slot, ItemStack item, ClickHandler handler) {
        inv.setItem(slot, item);
        if (handler != null) slotHandlers.get(currentPlayer).put(slot, handler);
    }

    /** Fills all null slots with gray glass. */
    private void fill(Inventory inv) {
        ItemStack g = makeGlass();
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, g.clone());
        }
    }

    // ── Public menus ──────────────────────────────────────────────────────

    public void openPlayerMain(Player p) {
        Inventory inv = beginOpen(p, 27, "§aDance — Menu", "player_main");

        register(inv, 0,
                makeIcon(Material.LIME_DYE, "§aDance", List.of("§7Choose a style")),
                (player, click) -> openPlayerStyles(player));

        if (dm.isDancing(p.getUniqueId())) {
            register(inv, 4,
                    makeIcon(Material.RED_DYE, "§cStop", List.of("§7Stop the current dance")),
                    (player, click) -> {
                        dm.stopDance(player.getUniqueId());
                        player.closeInventory();
                        player.sendMessage("§aDance stopped.");
                    });
        }

        String activePl = pm.getActivePlaylistForPlayer(p.getUniqueId());
        if (activePl != null) {
            register(inv, 8,
                    makeIcon(Material.PAPER, "§ePlaylist", List.of("§7Current: §f" + activePl)),
                    null); // info only
        }

        if (p.hasPermission("danse.staff")) {
            register(inv, 26,
                        makeIcon(Material.COMMAND_BLOCK, "§cStaff Menu",
                            List.of("§7Playlists, choreography, dancers")),
                    (player, click) -> openStaffMain(player));
        }

        fill(inv);
        p.openInventory(inv);
    }

    public void openPlayerSettings(Player p) {
        openPlayerSettings(p, false);
    }

    private void openPlayerSettings(Player p, boolean fromStaffMenu) {
        Inventory inv = beginOpen(p, 27, "§bDisplay Settings", "player_settings");

        int currentRadius = getConfiguredRenderRadius();

        register(inv, 11,
                    makeIcon(Material.RED_STAINED_GLASS_PANE, "§c−16",
                    List.of("§7Reduce render distance")),
                (player, click) -> updateRenderRadius(player, currentRadius - 16));

        register(inv, 12,
                    makeIcon(Material.RED_STAINED_GLASS_PANE, "§c−1",
                    List.of("§7Reduce render distance")),
                (player, click) -> updateRenderRadius(player, currentRadius - 1));

        register(inv, 13,
            makeIcon(Material.ENDER_EYE, "§fRender Distance",
                    List.of("§7Current: §f" + currentRadius,
                        "§7Buttons: §c−16 §7/ §c−1 §7/ §a+1 §7/ §a+16",
                        "§7Range: §f1§7 to §f256",
                        "§8Applied immediately")),
                null);

        register(inv, 14,
                makeIcon(Material.GREEN_STAINED_GLASS_PANE, "§a+1",
                List.of("§7Increase render distance")),
                (player, click) -> updateRenderRadius(player, currentRadius + 1));

        register(inv, 15,
                makeIcon(Material.GREEN_STAINED_GLASS_PANE, "§a+16",
                List.of("§7Increase render distance")),
                (player, click) -> updateRenderRadius(player, currentRadius + 16));

        register(inv, 22,
            makeIcon(Material.LIGHT_BLUE_DYE, "§bReset",
                List.of("§7Return to default: §f" + DEFAULT_RENDER_RADIUS)),
            (player, click) -> updateRenderRadius(player, DEFAULT_RENDER_RADIUS));

        register(inv, 26, makeBack(), (player, click) -> {
            if (fromStaffMenu) openStaffMain(player);
            else openPlayerMain(player);
        });
        fill(inv);
        p.openInventory(inv);
    }

        private int getConfiguredRenderRadius() {
        return Math.max(MIN_RENDER_RADIUS,
                    Math.min(MAX_RENDER_RADIUS, plugin.getConfig().getInt("modelEngine.renderRadius", DEFAULT_RENDER_RADIUS)));
        }

        private void updateRenderRadius(Player player, int requestedRadius) {
        int radius = Math.max(MIN_RENDER_RADIUS, Math.min(MAX_RENDER_RADIUS, requestedRadius));
            plugin.getConfig().set("modelEngine.renderRadius", radius);
        plugin.saveConfig();
        sdm.applyRenderRadiusToActiveDancers(radius);
        dm.applyRenderRadiusToActiveDances(radius);
        player.sendMessage("§aRender distance set to §f" + radius + "§a.");
        openPlayerSettings(player);
        }

    public void openPlayerStyles(Player p) {
        Inventory inv = beginOpen(p, 27, "§aDance Styles", "player_styles");

        List<String> styles = dm.getStyleNames().stream()
                .filter(s -> { String perm = dm.getPermission(s); return perm == null || p.hasPermission(perm); })
                .toList();

        for (int i = 0; i < styles.size() && i < 26; i++) {
            final String style = styles.get(i);
            register(inv, i, makeDisc(style), (player, click) -> {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    player.closeInventory();
                    player.sendMessage("§eSkin username:"); // TODO: chat prompt
                    return;
                }
                DanceStyle ds = dm.parseStyle(style);
                if (ds != null) {
                    dm.startDance(player, ds, true, null);
                    player.closeInventory();
                }
            });
        }

        register(inv, 26, makeBack(), (player, click) -> openPlayerMain(player));
        fill(inv);
        p.openInventory(inv);
    }

    public void openStaffMain(Player p) {
        if (!p.hasPermission("danse.staff")) {
            p.sendMessage("§cYou do not have the danse.staff permission.");
            return;
        }
        Inventory inv = beginOpen(p, 54, "§cStaff Menu", "staff_main");

        register(inv, 0,
                makeIcon(Material.CHEST, "§bStatic Dancers",
                    List.of("§7" + sdm.getDancerIds().size() + " active")),
                (player, click) -> openStaffDancerList(player));

        register(inv, 1,
                makeIcon(Material.MUSIC_DISC_CAT, "§5Choreography",
                    List.of("§7" + sdm.getChoreographyGroupIds().size() + " group(s)")),
                (player, click) -> openStaffChoreoList(player));

        register(inv, 2,
                makeIcon(Material.BOOK, "§6Playlists",
                        List.of("§7" + pm.getPlaylistIds().size() + " playlist(s)")),
                (player, click) -> openStaffPlaylistList(player));

        register(inv, 8,
                makeIcon(Material.PLAYER_HEAD, "§eOnline Players",
                    List.of("§7" + Bukkit.getOnlinePlayers().size() + " online")),
                null); // info only

        register(inv, 7,
            makeIcon(Material.COMPASS, "§bDisplay Settings",
                List.of("§7Dancer render distance",
                    "§7Current value: §f" + getConfiguredRenderRadius())),
            (player, click) -> openPlayerSettings(player, true));

        // Row 1 (slots 9–17): one head per online player
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < online.size() && i < 9; i++) {
            final Player target = online.get(i);
            register(inv, 9 + i, makePlayerHead(target),
                    (player, click) -> openStaffPlayer(player, target));
        }

        register(inv, 53,
                makeIcon(Material.EMERALD_BLOCK, "§a+ Create dancer here",
                    List.of("§7You will be prompted for the ID")),
                (player, click) -> {
                    Location spawnLoc = player.getLocation();
                    @SuppressWarnings("deprecation")
                    PlayerProfile spawnProfile = player.getPlayerProfile();
                    String spawnSkin = player.getName();
                    player.closeInventory();
                    player.sendMessage("§eType the new dancer ID in chat:");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotSpawn(player.getUniqueId(), spawnLoc, spawnProfile, spawnSkin), plugin);
                });

        fill(inv);
        p.openInventory(inv);
    }

    public void openStaffDancer(Player viewer, String dancerId) {
        Inventory inv = beginOpen(viewer, 54, "§cDancer: §f" + dancerId, "staff_dancer:" + dancerId);

        // ── Row 0 — info (nodim) ──────────────────────────────────────────
        Location loc = sdm.getDancerLocation(dancerId);
        String posStr = loc != null
                ? "§7" + (loc.getWorld() != null ? loc.getWorld().getName() : "?")
                  + " §f" + (int) loc.getX() + "§7, §f" + (int) loc.getY() + "§7, §f" + (int) loc.getZ()
                : "§8N/A";
        PlayerProfile dancerProfile = sdm.getDancerProfile(dancerId);
        register(inv, 0, makeHeadFromProfile(dancerProfile, "§fPosition", List.of(posStr)),
            (player, click) -> {
                boolean ok = sdm.playDancerAnimation(dancerId);
            player.sendMessage(ok ? "§aAnimation started." : "§cDancer not found or no animation.");
            });

        String skinStr = sdm.getDancerSkin(dancerId);
        register(inv, 1, makeHeadFromProfile(dancerProfile, "§fSkin",
            List.of(skinStr != null ? "§7" + skinStr : "§8none")), null);

        String styleStr = sdm.getDancerStyle(dancerId);
        register(inv, 2, makeIcon(Material.MUSIC_DISC_13, "§fStyle",
                List.of(styleStr != null ? "§7" + styleStr : "§8N/A")), null);

        double scale = sdm.getDancerScale(dancerId);
        register(inv, 3,
                makeIcon(Material.AMETHYST_SHARD, "§fTaille",
                        List.of("§7Actuelle: §f" + formatScale(scale),
                                "§7left/right click: §c-0.1 §7/ §a+0.1",
                                "§7shift+click: §c-0.5 §7/ §a+0.5")),
                (player, click) -> {
                    double delta = switch (click) {
                        case LEFT -> -0.1;
                        case RIGHT -> 0.1;
                        case SHIFT_LEFT -> -0.5;
                        case SHIFT_RIGHT -> 0.5;
                        default -> 0.0;
                    };
                    if (delta == 0.0) return;
                    double next = Math.max(0.1, Math.min(20.0, Math.round((sdm.getDancerScale(dancerId) + delta) * 10.0) / 10.0));
                    if (sdm.setScale(dancerId, next)) {
                player.sendMessage("§aDancer scale set to §f" + formatScale(next) + "§a.");
                        openStaffDancer(player, dancerId);
                    } else {
                player.sendMessage("§cDancer not found.");
                    }
                });

        // ── Row 1 — actions ───────────────────────────────────────────────
        register(inv, 9,
                makeIcon(Material.SPECTRAL_ARROW, "§aChange style", List.of()),
                (player, click) -> openStylePickerForDancer(player, dancerId));

        register(inv, 10,
                makeIcon(Material.ENDER_PEARL, "§aMove here", List.of()),
                (player, click) -> {
                    boolean ok = sdm.moveStaticDancer(dancerId, player.getLocation());
                player.sendMessage(ok ? "§aDancer moved." : "§cDancer not found.");
                });

        register(inv, 11,
                makeIcon(Material.BOOK, "§aAssign playlist", List.of()),
                (player, click) -> openStaffPlaylistPicker(player, "dancer", dancerId));

        register(inv, 12,
                makeIcon(Material.BLAZE_ROD, "§aAdd to group", List.of()),
                (player, click) -> openStaffGroupPicker(player, dancerId));

        register(inv, 13,
                makeIcon(Material.PLAYER_HEAD, "§aChange skin",
                        List.of("§7New skin username")),
                (player, click) -> {
                    player.closeInventory();
                player.sendMessage("§eType the skin username in chat:");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotSkinChanger(player.getUniqueId(), dancerId), plugin);
                });

        register(inv, 14,
                makeIcon(Material.NAME_TAG, "§aRename",
                    List.of("§7Type the new ID in chat")),
                (player, click) -> {
                    player.closeInventory();
                player.sendMessage("§eType the new dancer ID in chat:");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotRenamer(player.getUniqueId(), dancerId), plugin);
                });

        register(inv, 52, makeBack(), (player, click) -> openStaffDancerList(player));

        register(inv, 53,
                makeIcon(Material.BARRIER, "§cDelete", List.of("§7Shift+click to confirm")),
                (player, click) -> {
                    if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
                    sdm.removeStaticDancer(dancerId);
                    openStaffDancerList(player);
                player.sendMessage("§aDancer §f'" + dancerId + "§a' deleted.");
                });

        fill(inv);
        viewer.openInventory(inv);
    }

    public void openStaffPlaylist(Player viewer, String playlistId) {
        playerPlaylistCtx.put(viewer.getUniqueId(), playlistId);
        Inventory inv = beginOpen(viewer, 54, "§6Playlist: §f" + playlistId, "staff_playlist:" + playlistId);

        // Row 0 (slots 0–8): current tracks
        PlaylistManager.Playlist playlist = pm.getPlaylists().get(playlistId);
        if (playlist != null) {
            for (int i = 0; i < playlist.tracks.size() && i < 9; i++) {
                final int idx = i;
                PlaylistManager.Track t = playlist.tracks.get(i);
                register(inv, i,
                        makeIcon(Material.PAPER,
                                "§f#" + i + " §7— §f" + t.styleName(),
                    List.of("§7×" + t.repetitions() + " reps", "§cleft click -> remove")),
                        (player, click) -> {
                            pm.removeTrack(playlistId, idx);
                            openStaffPlaylist(player, playlistId);
                        });
            }
        }

        register(inv, 45,
                makeIcon(Material.RED_DYE, "§cDelete playlist",
                    List.of("§7Shift+click to confirm")),
                (player, click) -> {
                    if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
                    pm.deletePlaylist(playlistId);
                    playerPlaylistCtx.remove(player.getUniqueId());
                    openStaffMain(player);
                player.sendMessage("§aPlaylist §f'" + playlistId + "§a' deleted.");
                });

        register(inv, 46,
                makeIcon(Material.PLAYER_HEAD, "§aLaunch -> player", List.of()),
                (player, click) -> openTargetPicker(player, playlistId, "player"));

        register(inv, 47,
                makeIcon(Material.ARMOR_STAND, "§aLaunch -> dancer", List.of()),
                (player, click) -> openTargetPicker(player, playlistId, "dancer"));

        register(inv, 48,
                makeIcon(Material.BLAZE_ROD, "§aLaunch -> group", List.of()),
                (player, click) -> openTargetPicker(player, playlistId, "group"));

        register(inv, 49,
                makeIcon(Material.LIME_DYE, "§aAdd track", List.of("§7Choose style + repetitions")),
                (player, click) -> openPlaylistTrackPicker(player, playlistId));

        register(inv, 53, makeBack(), (player, click) -> openStaffMain(player));

        fill(inv);
        viewer.openInventory(inv);
    }

    public void openChoreoSelectStyle(Player viewer, String groupId) {
        Inventory inv = beginOpen(viewer, 54,
                "§5Choreo · " + groupId + " — Style selection",
                "choreo_select:" + groupId);

        List<String> styles = dm.getStyleNames();
        for (int i = 0; i < styles.size() && i < 53; i++) {
            final String style = styles.get(i);
            register(inv, i, makeDisc(style),
                    (player, click) -> openChoreoConfigTrack(player, groupId, style, 1));
        }

        register(inv, 53, makeBack(), (player, click) -> openStaffMain(player));
        fill(inv);
        viewer.openInventory(inv);
    }

    public void openChoreoConfigTrack(Player viewer, String groupId, String styleName, int repetitions) {
        int reps = Math.max(1, Math.min(64, repetitions));
        Inventory inv = beginOpen(viewer, 27,
                "§5Choreo · " + styleName + " — Repetitions",
                "choreo_config:" + groupId + ":" + styleName);

        // Row 0
        inv.setItem(0, makeDisc(styleName)); // [nodim]

        // Row 1 — −5 / −1 / counter / +1 / +5
        register(inv, 9,
                makeIcon(Material.RED_STAINED_GLASS_PANE, "§c−5", List.of()),
                (player, click) -> openChoreoConfigTrack(player, groupId, styleName, Math.max(1, reps - 5)));

        register(inv, 10,
                makeIcon(Material.RED_STAINED_GLASS_PANE, "§c−1", List.of()),
                (player, click) -> openChoreoConfigTrack(player, groupId, styleName, Math.max(1, reps - 1)));

        ItemStack counter = new ItemStack(Material.DIAMOND, reps);
        ItemMeta cm = counter.getItemMeta();
        if (cm != null) {
            cm.displayName(LEGACY.deserialize("§f" + reps + " repetition" + (reps > 1 ? "s" : "")));
            cm.lore(List.of(LEGACY.deserialize("§7L:+1 §8R:−1 §7shiftL:+5 §8shiftR:−5")));
            counter.setItemMeta(cm);
        }
        register(inv, 12, counter, (player, click) -> {
            int delta = switch (click) {
                case LEFT        ->  1;
                case RIGHT       -> -1;
                case SHIFT_LEFT  ->  5;
                case SHIFT_RIGHT -> -5;
                default          ->  0;
            };
            openChoreoConfigTrack(player, groupId, styleName, Math.max(1, Math.min(64, reps + delta)));
        });

        register(inv, 14,
                makeIcon(Material.GREEN_STAINED_GLASS_PANE, "§a+1", List.of()),
                (player, click) -> openChoreoConfigTrack(player, groupId, styleName, Math.min(64, reps + 1)));

        register(inv, 15,
                makeIcon(Material.GREEN_STAINED_GLASS_PANE, "§a+5", List.of()),
                (player, click) -> openChoreoConfigTrack(player, groupId, styleName, Math.min(64, reps + 5)));

        // Row 2
        register(inv, 18, makeBack(),
                (player, click) -> openChoreoSelectStyle(player, groupId));

        register(inv, 26,
                makeIcon(Material.EMERALD, "§aConfirmer",
                        List.of("§7" + styleName + " §8×§f " + reps + " reps")),
                (player, click) -> {
                    String pid = playerPlaylistCtx.get(player.getUniqueId());
                    if (pid == null) { player.sendMessage("§cNo playlist selected."); return; }
                    pm.addTrack(pid, styleName, reps);
                    openStaffMain(player);
                    player.sendMessage("§aTrack added.");
                });

        fill(inv);
        viewer.openInventory(inv);
    }

    // ── Private style-picker (for dancer style change) ────────────────────

    private void openStylePickerForDancer(Player viewer, String dancerId) {
        Inventory inv = beginOpen(viewer, 54,
                "§aChange style — §f" + dancerId,
                "style_picker:" + dancerId);

        List<String> styles = dm.getStyleNames();
        for (int i = 0; i < styles.size() && i < 53; i++) {
            final String style = styles.get(i);
            register(inv, i, makeDisc(style), (player, click) -> {
                sdm.changeAnimation(dancerId, style);
                openStaffDancer(player, dancerId);
                player.sendMessage("§aStyle changed: §f" + style);
            });
        }

        register(inv, 53, makeBack(), (player, click) -> openStaffDancer(player, dancerId));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §1 Dancer list ────────────────────────────────────────────────────

    private void openStaffDancerList(Player viewer) {
        Inventory inv = beginOpen(viewer, 54, "§bStatic Dancers", "staff_dancer_list");
        List<String> ids = new ArrayList<>(sdm.getDancerIds());
        Collections.sort(ids);
        if (ids.isEmpty()) {
            register(inv, 0, makeIcon(Material.BARRIER, "§cNo active dancer", List.of()), null);
        } else {
            int show = Math.min(ids.size(), 52);
            for (int i = 0; i < show; i++) {
                final String id = ids.get(i);
                DancerSnapshot snap = snapshotDancer(id);
                register(inv, i,
                    makeHeadFromProfile(snap.skinProfile(), "§f" + id,
                        List.of("§7style: §f" + snap.styleName(),
                            "§7skin: §f" + snap.skinName())),
                    (player, click) -> openStaffDancer(player, id));
            }
            if (ids.size() > 52)
                register(inv, 51, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                        "§8... and " + (ids.size() - 51) + " more", List.of()), null);
        }
        register(inv, 53, makeBack(), (player, click) -> openStaffMain(player));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §2 Playlist list ──────────────────────────────────────────────────

    private void openStaffPlaylistList(Player viewer) {
        Inventory inv = beginOpen(viewer, 54, "§6Playlists", "staff_playlist_list");
        List<String> ids = new ArrayList<>(pm.getPlaylistIds());
        Collections.sort(ids);

        int show = Math.min(ids.size(), 51); // reserve slot 51 for NETHER_STAR
        for (int i = 0; i < show; i++) {
            final String pid = ids.get(i);
            PlaylistManager.Playlist pl = pm.getPlaylists().get(pid);
            int tracks = pl != null ? pl.tracks.size() : 0;
            boolean loop = pl != null && pl.loop;
            register(inv, i,
                    makeIcon(Material.BOOK, "§f" + pid,
                            List.of("§7" + tracks + " piste(s)",
                                    "§7" + (loop ? "loop" : "once"))),
                    (player, click) -> openStaffPlaylist(player, pid));
        }

        // Slot 51 — always available: create new playlist
        List<String> starLore = new ArrayList<>();
        starLore.add("§7Type the ID in chat");
        if (ids.size() > 51) starLore.add("§8(and " + (ids.size() - 51) + " hidden playlist(s))");
        register(inv, 51,
                makeIcon(Material.NETHER_STAR, "§eNew playlist...", starLore),
                (player, click) -> {
                    player.closeInventory();
                    player.sendMessage("§eType the new playlist ID in chat:");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotPlaylistCreator(player.getUniqueId()), plugin);
                });

        register(inv, 53, makeBack(), (player, click) -> openStaffMain(player));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §3 Choreo list ────────────────────────────────────────────────────

    private void openStaffChoreoList(Player viewer) {
        Inventory inv = beginOpen(viewer, 54, "§5Choreographies", "staff_choreo_list");
        List<String> ids = new ArrayList<>(sdm.getChoreographyGroupIds());
        Collections.sort(ids);

        Map<String, Set<String>> groups = sdm.getChoreographyGroups();
        int show = Math.min(ids.size(), 51); // reserve slot 51 for NETHER_STAR
        for (int i = 0; i < show; i++) {
            final String gid = ids.get(i);
            Set<String> members = groups.getOrDefault(gid, Set.of());
            String raw = String.join(", ", new TreeSet<>(members));
            String memberStr = raw.length() > 40 ? raw.substring(0, 37) + "..." : raw;
            register(inv, i,
                    makeIcon(Material.BLAZE_ROD, "§5" + gid,
                            List.of("§7" + members.size() + " membre(s)", "§8" + memberStr)),
                    (player, click) -> openStaffChoreoGroup(player, gid));
        }

        // Slot 51 — always available: create new group
        List<String> starLore = new ArrayList<>();
        starLore.add("§7Type the ID in chat");
        if (ids.size() > 51) starLore.add("§8(and " + (ids.size() - 51) + " hidden group(s))");
        register(inv, 51,
                makeIcon(Material.NETHER_STAR, "§eNew group...", starLore),
                (player, click) -> {
                    player.closeInventory();
                    player.sendMessage("§eType the new group ID in chat:");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotChoreoCreator(player.getUniqueId()), plugin);
                });

        register(inv, 53, makeBack(), (player, click) -> openStaffMain(player));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §4 Choreo group ───────────────────────────────────────────────────

    private void openStaffChoreoGroup(Player viewer, String groupId) {
        Inventory inv = beginOpen(viewer, 54, "§5Group: " + groupId, "staff_choreo_group:" + groupId);
        List<String> members = new ArrayList<>(
                sdm.getChoreographyGroups().getOrDefault(groupId, Set.of()));
        Collections.sort(members);

        // Slots 0–7 — members; shift+click to remove
        for (int i = 0; i < members.size() && i < 8; i++) {
            final String mid = members.get(i);
            String pl = pm.getActivePlaylistForDancer(mid);
            DancerSnapshot snap = snapshotDancer(mid);
            register(inv, i,
                makeHeadFromProfile(snap.skinProfile(), "§f" + mid,
                    List.of("§7" + (pl != null ? "playlist: " + pl : "solo"),
                        "§cshift+click → remove")),
                (player, click) -> {
                if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
                sdm.removeFromChoreography(groupId, mid);
                player.sendMessage("§a" + mid + " §aremoved from group §f" + groupId + "§a.");
                openStaffChoreoGroup(player, groupId);
                });
        }
        if (members.size() > 8)
            register(inv, 7, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                    "§8... and " + (members.size() - 7) + " more", List.of()), null);

        register(inv, 45,
                makeIcon(Material.EXPERIENCE_BOTTLE, "§aSynchronize", List.of()),
                (player, click) -> {
                    sdm.syncChoreography(groupId);
                    player.sendMessage("§aSynchronized.");
                });
        register(inv, 46,
                makeIcon(Material.BOOK, "§aAssign playlist", List.of()),
                (player, click) -> openStaffPlaylistPicker(player, "group", groupId));
        register(inv, 47,
                makeIcon(Material.LIME_DYE, "§aAdd member", List.of()),
                (player, click) -> openStaffChoreoMemberPicker(player, groupId));
        register(inv, 48,
                makeIcon(Material.BARRIER, "§cDisband", List.of("§7Shift+click to confirm")),
                (player, click) -> {
                    if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
                    sdm.deleteChoreography(groupId);
                    openStaffChoreoList(player);
                    player.sendMessage("§aGroup §f'" + groupId + "§a' disbanded.");
                });
        register(inv, 53, makeBack(), (player, click) -> openStaffChoreoList(player));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §5 Playlist picker (assign to target) ─────────────────────────────

    private void openStaffPlaylistPicker(Player viewer, String targetType, String targetId) {
        Inventory inv = beginOpen(viewer, 27,
                "§6Playlist → " + targetType + ":" + targetId,
                "pl_picker:" + targetType + ":" + targetId);
        List<String> ids = new ArrayList<>(pm.getPlaylistIds());
        Collections.sort(ids);
        if (ids.isEmpty()) {
            register(inv, 0, makeIcon(Material.BARRIER, "§cNo playlist defined", List.of()), null);
        } else {
            int show = Math.min(ids.size(), 25);
            for (int i = 0; i < show; i++) {
                final String pid = ids.get(i);
                boolean active = isPlaylistActiveOn(targetType, targetId, pid);
                register(inv, i,
                        makeIcon(Material.BOOK, "§f" + pid,
                                List.of(active ? "§a▶ active" : "§7click → launch")),
                        (player, click) -> {
                            launchPlaylistOn(targetType, targetId, pid);
                            player.sendMessage("§aPlaylist launched.");
                            player.closeInventory();
                        });
            }
            if (ids.size() > 25)
                register(inv, 24, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                        "§8... and " + (ids.size() - 24) + " more", List.of()), null);
        }
        register(inv, 26, makeBack(), (player, click) -> {
            switch (targetType) {
                case "dancer" -> openStaffDancer(player, targetId);
                case "group"  -> openStaffChoreoGroup(player, targetId);
                default       -> { // "player"
                    try {
                        Player t = Bukkit.getPlayer(UUID.fromString(targetId));
                        if (t != null) openStaffPlayer(player, t);
                        else openStaffMain(player);
                    } catch (IllegalArgumentException ignored) { openStaffMain(player); }
                }
            }
        });
        fill(inv);
        viewer.openInventory(inv);
    }

    private boolean isPlaylistActiveOn(String targetType, String targetId, String pid) {
        String active = switch (targetType) {
            case "dancer" -> pm.getActivePlaylistForDancer(targetId);
            case "group"  -> pm.getActivePlaylistForGroup(targetId);
            default       -> { // "player"
                try { yield pm.getActivePlaylistForPlayer(UUID.fromString(targetId)); }
                catch (IllegalArgumentException e) { yield null; }
            }
        };
        return pid.equals(active);
    }

    private void launchPlaylistOn(String targetType, String targetId, String pid) {
        switch (targetType) {
            case "dancer" -> pm.playForDancer(targetId, pid);
            case "group"  -> pm.playForGroup(targetId, pid);
            default       -> { // "player"
                try { pm.playForPlayer(UUID.fromString(targetId), pid); }
                catch (IllegalArgumentException ignored) {}
            }
        }
    }

    // ── §6 Target picker (launch playlist on target) ──────────────────────

    private void openTargetPicker(Player viewer, String playlistId, String targetType) {
        Inventory inv = beginOpen(viewer, 27,
                "§6Cible → " + targetType,
                "target_picker:" + targetType + ":" + playlistId);
        boolean anyItem = false;
        switch (targetType) {
            case "player" -> {
                List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
                if (online.isEmpty()) break;
                anyItem = true;
                int show = Math.min(online.size(), 25);
                for (int i = 0; i < show; i++) {
                    final Player t = online.get(i);
                    register(inv, i, makePlayerHead(t), (player, click) -> {
                        pm.playForPlayer(t.getUniqueId(), playlistId);
                        player.sendMessage("§aPlaylist launched on §f" + t.getName() + "§a.");
                        player.closeInventory();
                    });
                }
                if (online.size() > 25)
                    register(inv, 24, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                            "§8... and " + (online.size() - 24) + " more", List.of()), null);
            }
            case "dancer" -> {
                List<String> ids = new ArrayList<>(sdm.getDancerIds());
                Collections.sort(ids);
                if (ids.isEmpty()) break;
                anyItem = true;
                int show = Math.min(ids.size(), 25);
                for (int i = 0; i < show; i++) {
                    final String did = ids.get(i);
                    String pl = pm.getActivePlaylistForDancer(did);
                    register(inv, i,
                            makeIcon(Material.PLAYER_HEAD, "§f" + did,
                                List.of(pl != null ? "§a▶ " + pl : "§7no playlist")),
                            (player, click) -> {
                                pm.playForDancer(did, playlistId);
                                player.sendMessage("§aPlaylist launched on §f" + did + "§a.");
                                player.closeInventory();
                            });
                }
                if (ids.size() > 25)
                    register(inv, 24, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                            "§8... and " + (ids.size() - 24) + " more", List.of()), null);
            }
            case "group" -> {
                List<String> ids = new ArrayList<>(sdm.getChoreographyGroupIds());
                Collections.sort(ids);
                if (ids.isEmpty()) break;
                anyItem = true;
                int show = Math.min(ids.size(), 25);
                for (int i = 0; i < show; i++) {
                    final String gid = ids.get(i);
                    String pl = pm.getActivePlaylistForGroup(gid);
                    register(inv, i,
                            makeIcon(Material.BLAZE_ROD, "§5" + gid,
                                List.of(pl != null ? "§a▶ " + pl : "§7no playlist")),
                            (player, click) -> {
                                pm.playForGroup(gid, playlistId);
                                player.sendMessage("§aPlaylist launched on §f" + gid + "§a.");
                                player.closeInventory();
                            });
                }
                if (ids.size() > 25)
                    register(inv, 24, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                            "§8... et " + (ids.size() - 24) + " de plus", List.of()), null);
            }
        }
        if (!anyItem)
            register(inv, 0, makeIcon(Material.BARRIER, "§cNo target available", List.of()), null);
        register(inv, 26, makeBack(), (player, click) -> openStaffPlaylist(player, playlistId));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §7 Staff player sub-menu ──────────────────────────────────────────

    private void openStaffPlayer(Player viewer, Player target) {
        if (target == null || !target.isOnline()) { openStaffMain(viewer); return; }
        Inventory inv = beginOpen(viewer, 27,
                "§ePlayer: " + target.getName(),
                "staff_player:" + target.getUniqueId());
        String activeStyle = dm.getActiveDanceStyle(target.getUniqueId());
        String activePl = pm.getActivePlaylistForPlayer(target.getUniqueId());

        ItemStack head = makePlayerHead(target);
        ItemMeta hm = head.getItemMeta();
        if (hm != null) {
            hm.lore(List.of(
                    LEGACY.deserialize("§7dance: §f" + (activeStyle != null ? activeStyle : "none")),
                    LEGACY.deserialize("§7playlist: §f" + (activePl != null ? activePl : "none"))));
            head.setItemMeta(hm);
        }
        register(inv, 0, head, null); // [nodim]

        register(inv, 9,
                makeIcon(Material.MUSIC_DISC_CAT, "§aForce style", List.of()),
                (player, click) -> openPlayerStylePicker(player, target));

        register(inv, 10,
                makeIcon(Material.BARRIER, "§cStop dance", List.of()),
                (player, click) -> {
                    dm.stopDance(target.getUniqueId());
                    pm.stopForPlayer(target.getUniqueId());
                player.sendMessage("§aDance stopped for §f" + target.getName() + "§a.");
                    Player fresh = Bukkit.getPlayer(target.getUniqueId());
                    if (fresh != null) openStaffPlayer(player, fresh);
                    else openStaffMain(player);
                });

        register(inv, 12,
                makeIcon(Material.LIME_DYE, "§aRestore visibility", List.of("§7Force visibility if blocked")),
                (player, click) -> {
                    if (!player.hasPermission("danse.staff") && !player.isOp()) {
                        player.sendMessage("§cYou do not have the danse.staff permission.");
                        return;
                    }
                    dm.restoreVisibility(target.getUniqueId());
                    player.sendMessage("§aVisibility restored for §f" + target.getName() + "§a.");
                    Player fresh = Bukkit.getPlayer(target.getUniqueId());
                    if (fresh != null) openStaffPlayer(player, fresh);
                    else openStaffMain(player);
                });

        register(inv, 11,
        makeIcon(Material.BOOK, "§aLaunch playlist", List.of()),
                (player, click) -> openStaffPlaylistPicker(player, "player",
                        target.getUniqueId().toString()));

        register(inv, 26, makeBack(), (player, click) -> openStaffMain(player));
        fill(inv);
        viewer.openInventory(inv);
    }

    private void openPlayerStylePicker(Player viewer, Player target) {
        Inventory inv = beginOpen(viewer, 27,
                "§aStyle → " + target.getName(),
                "style_picker_player:" + target.getUniqueId());
        List<String> styles = dm.getStyleNames().stream()
                .filter(s -> { String p = dm.getPermission(s); return p == null || viewer.hasPermission(p); })
                .toList();
        for (int i = 0; i < styles.size() && i < 26; i++) {
            final String style = styles.get(i);
            register(inv, i, makeDisc(style), (player, click) -> {
                Player fresh = Bukkit.getPlayer(target.getUniqueId());
                if (fresh == null) { player.sendMessage("§c" + target.getName() + " est hors ligne."); openStaffMain(player); return; }
                DanceStyle ds = dm.parseStyle(style);
                if (ds != null) {
                    dm.startDance(fresh, ds, true, null);
                    player.sendMessage("§aStyle §f" + style + " §aforced on §f" + target.getName() + "§a.");
                    player.closeInventory();
                }
            });
        }
        register(inv, 26, makeBack(), (player, click) -> {
            Player fresh = Bukkit.getPlayer(target.getUniqueId());
            if (fresh != null) openStaffPlayer(player, fresh);
            else openStaffMain(player);
        });
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §8a Choreo member picker (add dancer to existing group) ──────────

    private void openStaffChoreoMemberPicker(Player viewer, String groupId) {
        Inventory inv = beginOpen(viewer, 54,
                "§5Add member -> " + groupId,
                "choreo_member_picker:" + groupId);

        Set<String> members = sdm.getChoreographyGroups().getOrDefault(groupId, Set.of());
        List<String> candidates = new ArrayList<>(sdm.getDancerIds());
        candidates.removeAll(members);
        Collections.sort(candidates);

        if (candidates.isEmpty()) {
            register(inv, 0,
                    makeIcon(Material.BARRIER, "§cAll dancers are already members", List.of()), null);
        } else {
            int show = Math.min(candidates.size(), 52);
            for (int i = 0; i < show; i++) {
                final String did = candidates.get(i);
                DancerSnapshot snap = snapshotDancer(did);
                register(inv, i,
                    makeHeadFromProfile(snap.skinProfile(), "§f" + did,
                                List.of("§7style: §f" + snap.styleName(),
                                        "§aclick -> add to group")),
                        (player, click) -> {
                            sdm.addToChoreography(groupId, did);
                            player.sendMessage("§a" + did + " §aadded to group §f" + groupId + "§a.");
                            openStaffChoreoMemberPicker(player, groupId);
                        });
            }
            if (candidates.size() > 52)
                register(inv, 51, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                        "§8... et " + (candidates.size() - 51) + " de plus", List.of()), null);
        }
        register(inv, 53, makeBack(), (player, click) -> openStaffChoreoGroup(player, groupId));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §8b Playlist track picker & config ────────────────────────────────

    private void openPlaylistTrackPicker(Player viewer, String playlistId) {
        playerPlaylistCtx.put(viewer.getUniqueId(), playlistId);
        Inventory inv = beginOpen(viewer, 54,
                "§6Add track -> " + playlistId,
                "pl_track_picker:" + playlistId);

        List<String> styles = dm.getStyleNames();
        for (int i = 0; i < styles.size() && i < 53; i++) {
            final String style = styles.get(i);
            register(inv, i, makeDisc(style),
                    (player, click) -> openPlaylistTrackConfig(player, playlistId, style, 1));
        }
        register(inv, 53, makeBack(), (player, click) -> openStaffPlaylist(player, playlistId));
        fill(inv);
        viewer.openInventory(inv);
    }

    private void openPlaylistTrackConfig(Player viewer, String playlistId, String styleName, int repetitions) {
        int reps = Math.max(1, Math.min(64, repetitions));
        Inventory inv = beginOpen(viewer, 27,
                "§6Track · " + styleName + " — Repetitions",
                "pl_track_config:" + playlistId + ":" + styleName);

        inv.setItem(0, makeDisc(styleName)); // [nodim]

        register(inv, 9,
            makeIcon(Material.RED_STAINED_GLASS_PANE, "§c−5", List.of()),
            (player, click) -> openPlaylistTrackConfig(player, playlistId, styleName, Math.max(1, reps - 5)));
        register(inv, 10,
                makeIcon(Material.RED_STAINED_GLASS_PANE, "§c−1", List.of()),
                (player, click) -> openPlaylistTrackConfig(player, playlistId, styleName, Math.max(1, reps - 1)));

        ItemStack counter = new ItemStack(Material.DIAMOND, reps);
        ItemMeta cm = counter.getItemMeta();
        if (cm != null) {
            cm.displayName(LEGACY.deserialize("§f" + reps + " repetition" + (reps > 1 ? "s" : "")));
            cm.lore(List.of(LEGACY.deserialize("§7G:+1 §8D:−1 §7shiftG:+5 §8shiftD:−5")));
            counter.setItemMeta(cm);
        }
        register(inv, 12, counter, (player, click) -> {
            int delta = switch (click) {
                case LEFT        ->  1;
                case RIGHT       -> -1;
                case SHIFT_LEFT  ->  5;
                case SHIFT_RIGHT -> -5;
                default          ->  0;
            };
            openPlaylistTrackConfig(player, playlistId, styleName, Math.max(1, Math.min(64, reps + delta)));
        });

        register(inv, 14,
                makeIcon(Material.GREEN_STAINED_GLASS_PANE, "§a+1", List.of()),
                (player, click) -> openPlaylistTrackConfig(player, playlistId, styleName, Math.min(64, reps + 1)));
        register(inv, 15,
                makeIcon(Material.GREEN_STAINED_GLASS_PANE, "§a+5", List.of()),
                (player, click) -> openPlaylistTrackConfig(player, playlistId, styleName, Math.min(64, reps + 5)));

        register(inv, 18, makeBack(),
                (player, click) -> openPlaylistTrackPicker(player, playlistId));

        register(inv, 26,
                makeIcon(Material.EMERALD, "§aConfirmer",
                        List.of("§7" + styleName + " §8×§f " + reps + " reps")),
                (player, click) -> {
                    pm.addTrack(playlistId, styleName, reps);
                    openStaffPlaylist(player, playlistId);
                    player.sendMessage("§aTrack §f" + styleName + " §a(×" + reps + ") added.");
                });

        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §8 Group picker (add dancer to a choreo group) ────────────────────

    private void openStaffGroupPicker(Player viewer, String dancerId) {
        Inventory inv = beginOpen(viewer, 27, "§5Add to group", "group_picker:" + dancerId);

        List<String> groupIds = new ArrayList<>(sdm.getChoreographyGroupIds());
        Collections.sort(groupIds);
        Map<String, Set<String>> groups = sdm.getChoreographyGroups();

        // One BLAZE_ROD per existing group (slots 0–23 max)
        int shown = Math.min(groupIds.size(), 24);
        for (int i = 0; i < shown; i++) {
            final String gid = groupIds.get(i);
            boolean already = groups.getOrDefault(gid, Set.of()).contains(dancerId);
            register(inv, i,
                    makeIcon(Material.BLAZE_ROD, "§5" + gid,
                            List.of(already ? "§calready in this group" : "§aclick -> add")),
                    already ? null : (player, click) -> {
                        sdm.addToChoreography(gid, dancerId);
                        player.sendMessage("§a" + dancerId + " §aadded to group §f" + gid + "§a.");
                        openStaffDancer(player, dancerId);
                    });
        }

        // NETHER_STAR for new group — always at slot min(size, 24)
        int starSlot = Math.min(groupIds.size(), 24);
        register(inv, starSlot,
                makeIcon(Material.NETHER_STAR, "§eNew group...",
                    List.of("§7Type the ID in chat")),
                (player, click) -> {
                    player.closeInventory();
                    player.sendMessage("§eType the new group ID in chat:");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotGroupCreator(player.getUniqueId(), dancerId), plugin);
                });

        register(inv, 26, makeBack(), (player, click) -> openStaffDancer(player, dancerId));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── Snapshot helper ───────────────────────────────────────────────────

    record DancerSnapshot(String styleName, String skinName, PlayerProfile skinProfile) {}

    private String formatScale(double scale) {
        return String.format(Locale.ROOT, "%.1f", scale);
    }

    private DancerSnapshot snapshotDancer(String id) {
        String style = sdm.getDancerStyle(id);
        String skin  = sdm.getDancerSkin(id);
        PlayerProfile profile = sdm.getDancerProfile(id);
        return new DancerSnapshot(style != null ? style : "?", skin != null ? skin : "?", profile);
    }

    // ── Item factories ────────────────────────────────────────────────────

    /** Reads dances.<style>.discItem from config; fallback MUSIC_DISC_13. */
    ItemStack makeDisc(String styleName) {
        String key = plugin.getConfig().getString("dances." + styleName + ".discItem", null);
        Material mat = Material.MUSIC_DISC_13;
        if (key != null) { Material m = Material.matchMaterial(key); if (m != null) mat = m; }
        String mv = plugin.getConfig().getString("dances." + styleName + ".movementType", "");

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY.deserialize("§a" + styleName));
            meta.lore(List.of(LEGACY.deserialize(mv), LEGACY.deserialize("§7clic gauche → lancer")));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeGlass() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(Component.empty()); item.setItemMeta(meta); }
        return item;
    }

    @SuppressWarnings("deprecation")
    private ItemStack makeHeadFromProfile(PlayerProfile profile, String name, List<String> lore) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            if (profile != null) meta.setOwnerProfile(profile);
            meta.displayName(LEGACY.deserialize(name));
            meta.lore(lore.stream().map(LEGACY::deserialize).toList());
            skull.setItemMeta(meta);
        }
        return skull;
    }

    ItemStack makeBack() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) { meta.displayName(LEGACY.deserialize("§6← Retour")); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack makeIcon(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(LEGACY.deserialize(name));
            meta.lore(lore.stream().map(LEGACY::deserialize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    @SuppressWarnings("deprecation")
    private ItemStack makePlayerHead(Player target) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        if (meta != null) {
            meta.setOwnerProfile(target.getPlayerProfile());
            String activeStyle = dm.getActiveDanceStyle(target.getUniqueId());
            String status = activeStyle != null ? "§7" + activeStyle : "§7no dance";
            meta.displayName(LEGACY.deserialize("§e" + target.getName()));
            meta.lore(List.of(LEGACY.deserialize(status)));
            skull.setItemMeta(meta);
        }
        return skull;
    }

    // ── One-shot chat listeners ───────────────────────────────────────────

    /** Asks for a dancer ID in chat, then spawns the NPC at the captured location. */
    private class OneShotSpawn implements Listener {
        private final UUID     viewerId;
        private final Location spawnLoc;
        @SuppressWarnings("deprecation")
        private final PlayerProfile skinProfile;
        private final String        skinName;
        private volatile boolean fired = false;

        @SuppressWarnings("deprecation")
        OneShotSpawn(UUID viewerId, Location spawnLoc, PlayerProfile skinProfile, String skinName) {
            this.viewerId    = viewerId;
            this.spawnLoc    = spawnLoc;
            this.skinProfile = skinProfile;
            this.skinName    = skinName;
        }

        @EventHandler
        public void onChat(AsyncChatEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            e.setCancelled(true);
            HandlerList.unregisterAll(this);
            String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(viewerId);
                if (player == null) return;
                String id = sanitizeGroupId(raw);
                if (id.isEmpty()) {
                    player.sendMessage("§cID invalide.");
                    openStaffMain(player);
                    return;
                }
                String style = dm.getStyleNames().isEmpty() ? "twist" : dm.getStyleNames().get(0);
                boolean ok = sdm.spawnStaticDancer(id, spawnLoc, style, skinProfile, skinName);
                if (ok) {
                    player.sendMessage("§aDancer §f'" + id + "§a' created (style: §f" + style + "§a).");
                    openStaffDancer(player, id);
                } else {
                    player.sendMessage("§cSpawn failed (ID already taken or ModelEngine inactive?)");
                    openStaffMain(player);
                }
            });
        }

        @EventHandler
        public void onInventoryOpen(InventoryOpenEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            HandlerList.unregisterAll(this);
        }
    }

    /** Asks for a player name in chat, fetches its skin, and applies it to the dancer. */
    private class OneShotSkinChanger implements Listener {
        private final UUID   viewerId;
        private final String dancerId;
        private volatile boolean fired = false;

        OneShotSkinChanger(UUID viewerId, String dancerId) {
            this.viewerId = viewerId;
            this.dancerId = dancerId;
        }

        @EventHandler
        public void onChat(AsyncChatEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            e.setCancelled(true);
            HandlerList.unregisterAll(this);
            String skinName = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
            if (skinName.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(viewerId);
                    if (p != null) { p.sendMessage("§cNom invalide."); openStaffDancer(p, dancerId); }
                });
                return;
            }
            // SkinService callback runs on an async thread → schedule main-thread work
            SkinService.fetchSkin(plugin, skinName, profile ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        Player p = Bukkit.getPlayer(viewerId);
                        if (p == null) return;
                        if (profile == null) {
                            p.sendMessage("§cSkin not found for §f" + skinName + "§c.");
                        } else {
                            boolean ok = sdm.changeSkin(dancerId, profile, skinName);
                            p.sendMessage(ok ? "§aSkin changed to §f" + skinName + "§a."
                                            : "§cDancer §f" + dancerId + " §cnot found.");
                        }
                        openStaffDancer(p, dancerId);
                    }));
        }

        @EventHandler
        public void onInventoryOpen(InventoryOpenEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            HandlerList.unregisterAll(this);
        }
    }

    /** Asks for a new ID in chat and renames the dancer. */
    private class OneShotRenamer implements Listener {
        private final UUID   viewerId;
        private final String dancerId;
        private volatile boolean fired = false;

        OneShotRenamer(UUID viewerId, String dancerId) {
            this.viewerId = viewerId;
            this.dancerId = dancerId;
        }

        @EventHandler
        public void onChat(AsyncChatEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            e.setCancelled(true);
            HandlerList.unregisterAll(this);
            String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(viewerId);
                if (p == null) return;
                String newId = sanitizeGroupId(raw);
                if (newId.isEmpty()) {
                    p.sendMessage("§cID invalide.");
                    openStaffDancer(p, dancerId);
                    return;
                }
                boolean ok = sdm.renameDancer(dancerId, newId);
                if (ok) {
                    p.sendMessage("§aDancer renamed: §f" + dancerId + " §a-> §f" + newId);
                    openStaffDancer(p, newId);
                } else {
                    p.sendMessage("§cRename failed (ID §f" + newId + " §calready taken?)");
                    openStaffDancer(p, dancerId);
                }
            });
        }

        @EventHandler
        public void onInventoryOpen(InventoryOpenEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            HandlerList.unregisterAll(this);
        }
    }

    /** Asks for a group ID in chat, then opens its management menu (creates on first member add). */
    private class OneShotChoreoCreator implements Listener {
        private final UUID viewerId;
        private volatile boolean fired = false;

        OneShotChoreoCreator(UUID viewerId) { this.viewerId = viewerId; }

        @EventHandler
        public void onChat(AsyncChatEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            e.setCancelled(true);
            HandlerList.unregisterAll(this);
            String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(viewerId);
                if (p == null) return;
                String groupId = sanitizeGroupId(raw);
                if (groupId.isEmpty()) {
                    p.sendMessage("§cInvalid group ID.");
                    openStaffChoreoList(p);
                    return;
                }
                if (sdm.getChoreographyGroupIds().contains(groupId)) {
                    p.sendMessage("§eGroup §f'" + groupId + "§e' already exists — opening.");
                } else {
                    p.sendMessage("§aGroup §f'" + groupId + "§a' ready. Add at least one member:");
                }
                openStaffChoreoGroup(p, groupId);
            });
        }

        @EventHandler
        public void onInventoryOpen(InventoryOpenEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            HandlerList.unregisterAll(this);
        }
    }

    /** Asks for a playlist ID in chat, creates it (loop by default), then opens its editor. */
    private class OneShotPlaylistCreator implements Listener {
        private final UUID viewerId;
        private volatile boolean fired = false;

        OneShotPlaylistCreator(UUID viewerId) { this.viewerId = viewerId; }

        @EventHandler
        public void onChat(AsyncChatEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            e.setCancelled(true);
            HandlerList.unregisterAll(this);
            String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(viewerId);
                if (p == null) return;
                String pid = sanitizeGroupId(raw);
                if (pid.isEmpty()) {
                    p.sendMessage("§cID de playlist invalide.");
                    openStaffPlaylistList(p);
                    return;
                }
                boolean ok = pm.createPlaylist(pid, true); // loop = true by default
                if (ok) {
                    p.sendMessage("§aPlaylist §f'" + pid + "§a' created (loop). Add tracks:");
                } else {
                    p.sendMessage("§ePlaylist §f'" + pid + "§e' already exists — opening.");
                }
                openStaffPlaylist(p, pid);
            });
        }

        @EventHandler
        public void onInventoryOpen(InventoryOpenEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            HandlerList.unregisterAll(this);
        }
    }

    // ── One-shot chat listener for new group creation (from dancer picker) ─

    private static String sanitizeGroupId(String input) {
        String s = input.trim().toLowerCase().replaceAll("[^a-z0-9_]", "_");
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    private class OneShotGroupCreator implements Listener {

        private final UUID viewerId;
        private final String dancerId;
        private volatile boolean fired = false;

        OneShotGroupCreator(UUID viewerId, String dancerId) {
            this.viewerId = viewerId;
            this.dancerId = dancerId;
        }

        @EventHandler
        public void onChat(AsyncChatEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            e.setCancelled(true);
            HandlerList.unregisterAll(this);

            String raw = PlainTextComponentSerializer.plainText().serialize(e.message());
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(viewerId);
                if (player == null) return;
                String groupId = sanitizeGroupId(raw);
                if (groupId.isEmpty()) {
                    player.sendMessage("§cInvalid group ID.");
                    openStaffDancer(player, dancerId);
                    return;
                }
                // Try to create a new group; if it already exists, add to it instead
                boolean created = sdm.createChoreography(groupId, List.of(dancerId));
                if (created) {
                    player.sendMessage("§aGroup §f'" + groupId + "§a' created with §f" + dancerId + "§a.");
                } else {
                    boolean added = sdm.addToChoreography(groupId, dancerId);
                    player.sendMessage(added
                            ? "§a" + dancerId + " §aadded to group §f'" + groupId + "§a'."
                            : "§cFailed to add to group §f'" + groupId + "§c'.");
                }
                openStaffDancer(player, dancerId);
            });
        }

        /** Cancel guard: player opened a new inventory → abandon the prompt. */
        @EventHandler
        public void onInventoryOpen(InventoryOpenEvent e) {
            if (!e.getPlayer().getUniqueId().equals(viewerId)) return;
            if (fired) return;
            fired = true;
            HandlerList.unregisterAll(this);
        }
    }

    // ── Inner DanceMenu holder ────────────────────────────────────────────

    private class SimpleMenu implements DanceMenu {
        private final Inventory inventory;

        SimpleMenu(int size, Component title) {
            this.inventory = Bukkit.createInventory(this, size, title);
        }

        @Override public Inventory getInventory() { return inventory; }

        @Override
        public void handleClick(InventoryClickEvent e, Player p) {
            dispatch(p, e.getSlot(), e.getClick());
        }
    }
}
