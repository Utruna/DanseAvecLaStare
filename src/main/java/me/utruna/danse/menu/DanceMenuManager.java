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
        Inventory inv = beginOpen(p, 27, "§aDanse — Menu", "player_main");

        register(inv, 0,
                makeIcon(Material.LIME_DYE, "§aDanser", List.of("§7Choisir un style")),
                (player, click) -> openPlayerStyles(player));

        if (dm.isDancing(p.getUniqueId())) {
            register(inv, 4,
                    makeIcon(Material.RED_DYE, "§cArrêter", List.of("§7Stoppe la danse en cours")),
                    (player, click) -> {
                        dm.stopDance(player.getUniqueId());
                        player.closeInventory();
                        player.sendMessage("§aDanse arrêtée.");
                    });
        }

        String activePl = pm.getActivePlaylistForPlayer(p.getUniqueId());
        if (activePl != null) {
            register(inv, 8,
                    makeIcon(Material.PAPER, "§ePlaylist", List.of("§7En cours: §f" + activePl)),
                    null); // info only
        }

        if (p.hasPermission("danse.staff")) {
            register(inv, 26,
                    makeIcon(Material.COMMAND_BLOCK, "§cMenu Staff",
                            List.of("§7Playlists, chorégraphies, danseurs")),
                    (player, click) -> openStaffMain(player));
        }

        fill(inv);
        p.openInventory(inv);
    }

    public void openPlayerSettings(Player p) {
        openPlayerSettings(p, false);
    }

    private void openPlayerSettings(Player p, boolean fromStaffMenu) {
        Inventory inv = beginOpen(p, 27, "§bParamètres d'affichage", "player_settings");

        int currentRadius = getConfiguredRenderRadius();

        register(inv, 11,
                makeIcon(Material.RED_STAINED_GLASS_PANE, "§c−16",
                    List.of("§7Réduit la distance d'affichage")),
                (player, click) -> updateRenderRadius(player, currentRadius - 16));

        register(inv, 12,
                makeIcon(Material.RED_STAINED_GLASS_PANE, "§c−1",
                    List.of("§7Réduit la distance d'affichage")),
                (player, click) -> updateRenderRadius(player, currentRadius - 1));

        register(inv, 13,
            makeIcon(Material.ENDER_EYE, "§fDistance d'affichage",
                    List.of("§7Actuelle: §f" + currentRadius,
                        "§7Boutons: §c−16 §7/ §c−1 §7/ §a+1 §7/ §a+16",
                        "§7Plage: §f1§7 à §f256",
                        "§8Valeur appliquée immédiatement")),
                null);

        register(inv, 14,
                makeIcon(Material.GREEN_STAINED_GLASS_PANE, "§a+1",
                List.of("§7Augmente la distance d'affichage")),
                (player, click) -> updateRenderRadius(player, currentRadius + 1));

        register(inv, 15,
                makeIcon(Material.GREEN_STAINED_GLASS_PANE, "§a+16",
                List.of("§7Augmente la distance d'affichage")),
                (player, click) -> updateRenderRadius(player, currentRadius + 16));

        register(inv, 22,
            makeIcon(Material.LIGHT_BLUE_DYE, "§bRéinitialiser",
                List.of("§7Revient à la valeur par défaut: §f" + DEFAULT_RENDER_RADIUS)),
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
        player.sendMessage("§aDistance d'affichage réglée à §f" + radius + "§a.");
        openPlayerSettings(player);
        }

    public void openPlayerStyles(Player p) {
        Inventory inv = beginOpen(p, 27, "§aStyles de danse", "player_styles");

        List<String> styles = dm.getStyleNames().stream()
                .filter(s -> { String perm = dm.getPermission(s); return perm == null || p.hasPermission(perm); })
                .toList();

        for (int i = 0; i < styles.size() && i < 26; i++) {
            final String style = styles.get(i);
            register(inv, i, makeDisc(style), (player, click) -> {
                if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                    player.closeInventory();
                    player.sendMessage("§ePseudo du skin :"); // TODO: chat prompt
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
            p.sendMessage("§cVous n'avez pas la permission danse.staff.");
            return;
        }
        Inventory inv = beginOpen(p, 54, "§cMenu Staff", "staff_main");

        register(inv, 0,
                makeIcon(Material.CHEST, "§bDanseurs statiques",
                        List.of("§7" + sdm.getDancerIds().size() + " actif(s)")),
                (player, click) -> openStaffDancerList(player));

        register(inv, 1,
                makeIcon(Material.MUSIC_DISC_CAT, "§5Chorégraphies",
                        List.of("§7" + sdm.getChoreographyGroupIds().size() + " groupe(s)")),
                (player, click) -> openStaffChoreoList(player));

        register(inv, 2,
                makeIcon(Material.BOOK, "§6Playlists",
                        List.of("§7" + pm.getPlaylistIds().size() + " playlist(s)")),
                (player, click) -> openStaffPlaylistList(player));

        register(inv, 8,
                makeIcon(Material.PLAYER_HEAD, "§eJoueurs en ligne",
                        List.of("§7" + Bukkit.getOnlinePlayers().size() + " connecté(s)")),
                null); // info only

        register(inv, 7,
            makeIcon(Material.COMPASS, "§bParamètres d'affichage",
                List.of("§7Distance d'affichage des danseurs",
                    "§7Valeur actuelle: §f" + getConfiguredRenderRadius())),
            (player, click) -> openPlayerSettings(player, true));

        // Row 1 (slots 9–17): one head per online player
        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        for (int i = 0; i < online.size() && i < 9; i++) {
            final Player target = online.get(i);
            register(inv, 9 + i, makePlayerHead(target),
                    (player, click) -> openStaffPlayer(player, target));
        }

        register(inv, 53,
                makeIcon(Material.EMERALD_BLOCK, "§a+ Créer danseur ici",
                        List.of("§7Vous serez invité à saisir l'ID")),
                (player, click) -> {
                    Location spawnLoc = player.getLocation();
                    @SuppressWarnings("deprecation")
                    PlayerProfile spawnProfile = player.getPlayerProfile();
                    String spawnSkin = player.getName();
                    player.closeInventory();
                    player.sendMessage("§eTapez l'ID du nouveau danseur dans le chat :");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotSpawn(player.getUniqueId(), spawnLoc, spawnProfile, spawnSkin), plugin);
                });

        fill(inv);
        p.openInventory(inv);
    }

    public void openStaffDancer(Player viewer, String dancerId) {
        Inventory inv = beginOpen(viewer, 54, "§cDanseur: §f" + dancerId, "staff_dancer:" + dancerId);

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
                player.sendMessage(ok ? "§aAnimation lancée." : "§cDanseur introuvable ou pas d'animation.");
            });

        String skinStr = sdm.getDancerSkin(dancerId);
        register(inv, 1, makeHeadFromProfile(dancerProfile, "§fSkin",
            List.of(skinStr != null ? "§7" + skinStr : "§8aucun")), null);

        String styleStr = sdm.getDancerStyle(dancerId);
        register(inv, 2, makeIcon(Material.MUSIC_DISC_13, "§fStyle",
                List.of(styleStr != null ? "§7" + styleStr : "§8N/A")), null);

        double scale = sdm.getDancerScale(dancerId);
        register(inv, 3,
                makeIcon(Material.AMETHYST_SHARD, "§fTaille",
                        List.of("§7Actuelle: §f" + formatScale(scale),
                                "§7clic gauche/droit: §c-0.1 §7/ §a+0.1",
                                "§7shift+clic: §c-0.5 §7/ §a+0.5")),
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
                        player.sendMessage("§aTaille du danseur ajustée à §f" + formatScale(next) + "§a.");
                        openStaffDancer(player, dancerId);
                    } else {
                        player.sendMessage("§cDanseur introuvable.");
                    }
                });

        // ── Row 1 — actions ───────────────────────────────────────────────
        register(inv, 9,
                makeIcon(Material.SPECTRAL_ARROW, "§aChanger style", List.of()),
                (player, click) -> openStylePickerForDancer(player, dancerId));

        register(inv, 10,
                makeIcon(Material.ENDER_PEARL, "§aDéplacer ici", List.of()),
                (player, click) -> {
                    boolean ok = sdm.moveStaticDancer(dancerId, player.getLocation());
                    player.sendMessage(ok ? "§aDanseur déplacé." : "§cDanseur introuvable.");
                });

        register(inv, 11,
                makeIcon(Material.BOOK, "§aAssigner playlist", List.of()),
                (player, click) -> openStaffPlaylistPicker(player, "dancer", dancerId));

        register(inv, 12,
                makeIcon(Material.BLAZE_ROD, "§aAjouter au groupe", List.of()),
                (player, click) -> openStaffGroupPicker(player, dancerId));

        register(inv, 13,
                makeIcon(Material.PLAYER_HEAD, "§aChanger skin",
                        List.of("§7Pseudo du nouveau skin")),
                (player, click) -> {
                    player.closeInventory();
                    player.sendMessage("§eTapez le pseudo du skin dans le chat :");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotSkinChanger(player.getUniqueId(), dancerId), plugin);
                });

        register(inv, 14,
                makeIcon(Material.NAME_TAG, "§aRenommer",
                        List.of("§7Tapez le nouvel ID dans le chat")),
                (player, click) -> {
                    player.closeInventory();
                    player.sendMessage("§eTapez le nouvel ID du danseur dans le chat :");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotRenamer(player.getUniqueId(), dancerId), plugin);
                });

        register(inv, 52, makeBack(), (player, click) -> openStaffDancerList(player));

        register(inv, 53,
                makeIcon(Material.BARRIER, "§cSupprimer", List.of("§7Shift+clic pour confirmer")),
                (player, click) -> {
                    if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
                    sdm.removeStaticDancer(dancerId);
                    openStaffDancerList(player);
                    player.sendMessage("§aDanseur §f'" + dancerId + "§a' supprimé.");
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
                                List.of("§7×" + t.repetitions() + " rép.", "§cclic gauche → retirer")),
                        (player, click) -> {
                            pm.removeTrack(playlistId, idx);
                            openStaffPlaylist(player, playlistId);
                        });
            }
        }

        register(inv, 45,
                makeIcon(Material.RED_DYE, "§cSupprimer playlist",
                        List.of("§7Shift+clic pour confirmer")),
                (player, click) -> {
                    if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
                    pm.deletePlaylist(playlistId);
                    playerPlaylistCtx.remove(player.getUniqueId());
                    openStaffMain(player);
                    player.sendMessage("§aPlaylist §f'" + playlistId + "§a' supprimée.");
                });

        register(inv, 46,
                makeIcon(Material.PLAYER_HEAD, "§aLancer → joueur", List.of()),
                (player, click) -> openTargetPicker(player, playlistId, "player"));

        register(inv, 47,
                makeIcon(Material.ARMOR_STAND, "§aLancer → danseur", List.of()),
                (player, click) -> openTargetPicker(player, playlistId, "dancer"));

        register(inv, 48,
                makeIcon(Material.BLAZE_ROD, "§aLancer → groupe", List.of()),
                (player, click) -> openTargetPicker(player, playlistId, "group"));

        register(inv, 49,
                makeIcon(Material.LIME_DYE, "§aAjouter une piste", List.of("§7Choisir style + répétitions")),
                (player, click) -> openPlaylistTrackPicker(player, playlistId));

        register(inv, 53, makeBack(), (player, click) -> openStaffMain(player));

        fill(inv);
        viewer.openInventory(inv);
    }

    public void openChoreoSelectStyle(Player viewer, String groupId) {
        Inventory inv = beginOpen(viewer, 54,
                "§5Chorée · " + groupId + " — Choix du style",
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
                "§5Chorée · " + styleName + " — Répétitions",
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
            cm.displayName(LEGACY.deserialize("§f" + reps + " répétition" + (reps > 1 ? "s" : "")));
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
                        List.of("§7" + styleName + " §8×§f " + reps + " rép.")),
                (player, click) -> {
                    String pid = playerPlaylistCtx.get(player.getUniqueId());
                    if (pid == null) { player.sendMessage("§cAucune playlist sélectionnée."); return; }
                    pm.addTrack(pid, styleName, reps);
                    openStaffMain(player);
                    player.sendMessage("§aPiste ajoutée.");
                });

        fill(inv);
        viewer.openInventory(inv);
    }

    // ── Private style-picker (for dancer style change) ────────────────────

    private void openStylePickerForDancer(Player viewer, String dancerId) {
        Inventory inv = beginOpen(viewer, 54,
                "§aChanger style — §f" + dancerId,
                "style_picker:" + dancerId);

        List<String> styles = dm.getStyleNames();
        for (int i = 0; i < styles.size() && i < 53; i++) {
            final String style = styles.get(i);
            register(inv, i, makeDisc(style), (player, click) -> {
                sdm.changeAnimation(dancerId, style);
                openStaffDancer(player, dancerId);
                player.sendMessage("§aStyle changé: §f" + style);
            });
        }

        register(inv, 53, makeBack(), (player, click) -> openStaffDancer(player, dancerId));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §1 Dancer list ────────────────────────────────────────────────────

    private void openStaffDancerList(Player viewer) {
        Inventory inv = beginOpen(viewer, 54, "§bDanseurs statiques", "staff_dancer_list");
        List<String> ids = new ArrayList<>(sdm.getDancerIds());
        Collections.sort(ids);
        if (ids.isEmpty()) {
            register(inv, 0, makeIcon(Material.BARRIER, "§cAucun danseur actif", List.of()), null);
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
                        "§8... et " + (ids.size() - 51) + " de plus", List.of()), null);
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
                                    "§7" + (loop ? "boucle" : "une fois"))),
                    (player, click) -> openStaffPlaylist(player, pid));
        }

        // Slot 51 — always available: create new playlist
        List<String> starLore = new ArrayList<>();
        starLore.add("§7Tapez l'ID dans le chat");
        if (ids.size() > 51) starLore.add("§8(et " + (ids.size() - 51) + " playlist(s) non affichées)");
        register(inv, 51,
                makeIcon(Material.NETHER_STAR, "§eNouvelle playlist...", starLore),
                (player, click) -> {
                    player.closeInventory();
                    player.sendMessage("§eTapez l'ID de la nouvelle playlist dans le chat :");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotPlaylistCreator(player.getUniqueId()), plugin);
                });

        register(inv, 53, makeBack(), (player, click) -> openStaffMain(player));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §3 Choreo list ────────────────────────────────────────────────────

    private void openStaffChoreoList(Player viewer) {
        Inventory inv = beginOpen(viewer, 54, "§5Chorégraphies", "staff_choreo_list");
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
        starLore.add("§7Tapez l'ID dans le chat");
        if (ids.size() > 51) starLore.add("§8(et " + (ids.size() - 51) + " groupe(s) non affichés)");
        register(inv, 51,
                makeIcon(Material.NETHER_STAR, "§eNouveau groupe...", starLore),
                (player, click) -> {
                    player.closeInventory();
                    player.sendMessage("§eTapez l'ID du nouveau groupe dans le chat :");
                    Bukkit.getPluginManager().registerEvents(
                            new OneShotChoreoCreator(player.getUniqueId()), plugin);
                });

        register(inv, 53, makeBack(), (player, click) -> openStaffMain(player));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §4 Choreo group ───────────────────────────────────────────────────

    private void openStaffChoreoGroup(Player viewer, String groupId) {
        Inventory inv = beginOpen(viewer, 54, "§5Groupe : " + groupId, "staff_choreo_group:" + groupId);
        List<String> members = new ArrayList<>(
                sdm.getChoreographyGroups().getOrDefault(groupId, Set.of()));
        Collections.sort(members);

        // Slots 0–7 — members; shift+clic to remove
        for (int i = 0; i < members.size() && i < 8; i++) {
            final String mid = members.get(i);
            String pl = pm.getActivePlaylistForDancer(mid);
            DancerSnapshot snap = snapshotDancer(mid);
            register(inv, i,
                makeHeadFromProfile(snap.skinProfile(), "§f" + mid,
                    List.of("§7" + (pl != null ? "playlist: " + pl : "solo"),
                        "§cshift+clic → retirer")),
                (player, click) -> {
                if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
                sdm.removeFromChoreography(groupId, mid);
                player.sendMessage("§a" + mid + " §aretired du groupe §f" + groupId + "§a.");
                openStaffChoreoGroup(player, groupId);
                });
        }
        if (members.size() > 8)
            register(inv, 7, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                    "§8... et " + (members.size() - 7) + " de plus", List.of()), null);

        register(inv, 45,
                makeIcon(Material.EXPERIENCE_BOTTLE, "§aSync", List.of()),
                (player, click) -> {
                    sdm.syncChoreography(groupId);
                    player.sendMessage("§aSynchronisé.");
                });
        register(inv, 46,
                makeIcon(Material.BOOK, "§aAssigner playlist", List.of()),
                (player, click) -> openStaffPlaylistPicker(player, "group", groupId));
        register(inv, 47,
                makeIcon(Material.LIME_DYE, "§aAjouter un membre", List.of()),
                (player, click) -> openStaffChoreoMemberPicker(player, groupId));
        register(inv, 48,
                makeIcon(Material.BARRIER, "§cDissoudre", List.of("§7Shift+clic pour confirmer")),
                (player, click) -> {
                    if (click != ClickType.SHIFT_LEFT && click != ClickType.SHIFT_RIGHT) return;
                    sdm.deleteChoreography(groupId);
                    openStaffChoreoList(player);
                    player.sendMessage("§aGroupe §f'" + groupId + "§a' dissout.");
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
            register(inv, 0, makeIcon(Material.BARRIER, "§cAucune playlist définie", List.of()), null);
        } else {
            int show = Math.min(ids.size(), 25);
            for (int i = 0; i < show; i++) {
                final String pid = ids.get(i);
                boolean active = isPlaylistActiveOn(targetType, targetId, pid);
                register(inv, i,
                        makeIcon(Material.BOOK, "§f" + pid,
                                List.of(active ? "§a▶ en cours" : "§7clic → lancer")),
                        (player, click) -> {
                            launchPlaylistOn(targetType, targetId, pid);
                            player.sendMessage("§aPlaylist lancée.");
                            player.closeInventory();
                        });
            }
            if (ids.size() > 25)
                register(inv, 24, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                        "§8... et " + (ids.size() - 24) + " de plus", List.of()), null);
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
                        player.sendMessage("§aPlaylist lancée sur §f" + t.getName() + "§a.");
                        player.closeInventory();
                    });
                }
                if (online.size() > 25)
                    register(inv, 24, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                            "§8... et " + (online.size() - 24) + " de plus", List.of()), null);
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
                                    List.of(pl != null ? "§a▶ " + pl : "§7sans playlist")),
                            (player, click) -> {
                                pm.playForDancer(did, playlistId);
                                player.sendMessage("§aPlaylist lancée sur §f" + did + "§a.");
                                player.closeInventory();
                            });
                }
                if (ids.size() > 25)
                    register(inv, 24, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                            "§8... et " + (ids.size() - 24) + " de plus", List.of()), null);
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
                                    List.of(pl != null ? "§a▶ " + pl : "§7sans playlist")),
                            (player, click) -> {
                                pm.playForGroup(gid, playlistId);
                                player.sendMessage("§aPlaylist lancée sur §f" + gid + "§a.");
                                player.closeInventory();
                            });
                }
                if (ids.size() > 25)
                    register(inv, 24, makeIcon(Material.GRAY_STAINED_GLASS_PANE,
                            "§8... et " + (ids.size() - 24) + " de plus", List.of()), null);
            }
        }
        if (!anyItem)
            register(inv, 0, makeIcon(Material.BARRIER, "§cAucune cible disponible", List.of()), null);
        register(inv, 26, makeBack(), (player, click) -> openStaffPlaylist(player, playlistId));
        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §7 Staff player sub-menu ──────────────────────────────────────────

    private void openStaffPlayer(Player viewer, Player target) {
        if (target == null || !target.isOnline()) { openStaffMain(viewer); return; }
        Inventory inv = beginOpen(viewer, 27,
                "§eJoueur : " + target.getName(),
                "staff_player:" + target.getUniqueId());
        String activeStyle = dm.getActiveDanceStyle(target.getUniqueId());
        String activePl = pm.getActivePlaylistForPlayer(target.getUniqueId());

        ItemStack head = makePlayerHead(target);
        ItemMeta hm = head.getItemMeta();
        if (hm != null) {
            hm.lore(List.of(
                    LEGACY.deserialize("§7danse: §f" + (activeStyle != null ? activeStyle : "aucune")),
                    LEGACY.deserialize("§7playlist: §f" + (activePl != null ? activePl : "aucune"))));
            head.setItemMeta(hm);
        }
        register(inv, 0, head, null); // [nodim]

        register(inv, 9,
                makeIcon(Material.MUSIC_DISC_CAT, "§aForcer un style", List.of()),
                (player, click) -> openPlayerStylePicker(player, target));

        register(inv, 10,
                makeIcon(Material.BARRIER, "§cArrêter danse", List.of()),
                (player, click) -> {
                    dm.stopDance(target.getUniqueId());
                    pm.stopForPlayer(target.getUniqueId());
                    player.sendMessage("§aDanse arrêtée pour §f" + target.getName() + "§a.");
                    Player fresh = Bukkit.getPlayer(target.getUniqueId());
                    if (fresh != null) openStaffPlayer(player, fresh);
                    else openStaffMain(player);
                });

        register(inv, 12,
                makeIcon(Material.LIME_DYE, "§aRendre visible", List.of("§7Force la visibilité si bloquée")),
                (player, click) -> {
                    if (!player.hasPermission("danse.staff") && !player.isOp()) {
                        player.sendMessage("§cVous n'avez pas la permission danse.staff.");
                        return;
                    }
                    dm.restoreVisibility(target.getUniqueId());
                    player.sendMessage("§aVisibilité rétablie pour §f" + target.getName() + "§a.");
                    Player fresh = Bukkit.getPlayer(target.getUniqueId());
                    if (fresh != null) openStaffPlayer(player, fresh);
                    else openStaffMain(player);
                });

        register(inv, 11,
                makeIcon(Material.BOOK, "§aLancer playlist", List.of()),
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
                    player.sendMessage("§aStyle §f" + style + " §aforcé sur §f" + target.getName() + "§a.");
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
                "§5Ajouter membre → " + groupId,
                "choreo_member_picker:" + groupId);

        Set<String> members = sdm.getChoreographyGroups().getOrDefault(groupId, Set.of());
        List<String> candidates = new ArrayList<>(sdm.getDancerIds());
        candidates.removeAll(members);
        Collections.sort(candidates);

        if (candidates.isEmpty()) {
            register(inv, 0,
                    makeIcon(Material.BARRIER, "§cTous les danseurs sont déjà membres", List.of()), null);
        } else {
            int show = Math.min(candidates.size(), 52);
            for (int i = 0; i < show; i++) {
                final String did = candidates.get(i);
                DancerSnapshot snap = snapshotDancer(did);
                register(inv, i,
                    makeHeadFromProfile(snap.skinProfile(), "§f" + did,
                                List.of("§7style: §f" + snap.styleName(),
                                        "§aclic → ajouter au groupe")),
                        (player, click) -> {
                            sdm.addToChoreography(groupId, did);
                            player.sendMessage("§a" + did + " §aajouté au groupe §f" + groupId + "§a.");
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
                "§6Ajouter piste — " + playlistId,
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
                "§6Piste · " + styleName + " — Répétitions",
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
            cm.displayName(LEGACY.deserialize("§f" + reps + " répétition" + (reps > 1 ? "s" : "")));
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
                        List.of("§7" + styleName + " §8×§f " + reps + " rép.")),
                (player, click) -> {
                    pm.addTrack(playlistId, styleName, reps);
                    openStaffPlaylist(player, playlistId);
                    player.sendMessage("§aPiste §f" + styleName + " §a(×" + reps + ") ajoutée.");
                });

        fill(inv);
        viewer.openInventory(inv);
    }

    // ── §8 Group picker (add dancer to a choreo group) ────────────────────

    private void openStaffGroupPicker(Player viewer, String dancerId) {
        Inventory inv = beginOpen(viewer, 27, "§5Ajouter au groupe", "group_picker:" + dancerId);

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
                            List.of(already ? "§cdéjà dans ce groupe" : "§aclic → ajouter")),
                    already ? null : (player, click) -> {
                        sdm.addToChoreography(gid, dancerId);
                        player.sendMessage("§a" + dancerId + " §aajouté au groupe §f" + gid + "§a.");
                        openStaffDancer(player, dancerId);
                    });
        }

        // NETHER_STAR for new group — always at slot min(size, 24)
        int starSlot = Math.min(groupIds.size(), 24);
        register(inv, starSlot,
                makeIcon(Material.NETHER_STAR, "§eNouveau groupe...",
                        List.of("§7Tapez l'ID dans le chat")),
                (player, click) -> {
                    player.closeInventory();
                    player.sendMessage("§eTapez l'ID du nouveau groupe dans le chat :");
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
            String status = activeStyle != null ? "§7" + activeStyle : "§7aucune danse";
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
                    player.sendMessage("§aDanseur §f'" + id + "§a' créé (style: §f" + style + "§a).");
                    openStaffDancer(player, id);
                } else {
                    player.sendMessage("§cÉchec du spawn (ID déjà pris ou ModelEngine inactif ?)");
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
                            p.sendMessage("§cSkin introuvable pour §f" + skinName + "§c.");
                        } else {
                            boolean ok = sdm.changeSkin(dancerId, profile, skinName);
                            p.sendMessage(ok ? "§aSkin changé en §f" + skinName + "§a."
                                            : "§cDanseur §f" + dancerId + " §cintrouvable.");
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
                    p.sendMessage("§aDanseur renommé : §f" + dancerId + " §a→ §f" + newId);
                    openStaffDancer(p, newId);
                } else {
                    p.sendMessage("§cÉchec du renommage (ID §f" + newId + " §cdéjà pris ?)");
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
                    p.sendMessage("§cID de groupe invalide.");
                    openStaffChoreoList(p);
                    return;
                }
                if (sdm.getChoreographyGroupIds().contains(groupId)) {
                    p.sendMessage("§eGroupe §f'" + groupId + "§e' déjà existant — ouverture.");
                } else {
                    p.sendMessage("§aGroupe §f'" + groupId + "§a' prêt. Ajoutez au moins un membre :");
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
                boolean ok = pm.createPlaylist(pid, true); // loop = true par défaut
                if (ok) {
                    p.sendMessage("§aPlaylist §f'" + pid + "§a' créée (boucle). Ajoutez des pistes :");
                } else {
                    p.sendMessage("§ePlaylist §f'" + pid + "§e' déjà existante — ouverture.");
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
                    player.sendMessage("§cID de groupe invalide.");
                    openStaffDancer(player, dancerId);
                    return;
                }
                // Try to create a new group; if it already exists, add to it instead
                boolean created = sdm.createChoreography(groupId, List.of(dancerId));
                if (created) {
                    player.sendMessage("§aGroupe §f'" + groupId + "§a' créé avec §f" + dancerId + "§a.");
                } else {
                    boolean added = sdm.addToChoreography(groupId, dancerId);
                    player.sendMessage(added
                            ? "§a" + dancerId + " §aajouté au groupe §f'" + groupId + "§a'."
                            : "§cÉchec de l'ajout au groupe §f'" + groupId + "§c'.");
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
