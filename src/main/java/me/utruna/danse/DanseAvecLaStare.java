package me.utruna.danse;

import me.utruna.danse.commands.ChoreoCommandHandler;
import me.utruna.danse.commands.DanseTabCompleter;
import me.utruna.danse.commands.PlaylistCommandHandler;
import me.utruna.danse.listeners.PlayerListener;
import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.DanceStyle;
import me.utruna.danse.managers.PlaylistManager;
import me.utruna.danse.managers.SkinService;
import me.utruna.danse.managers.StaticDancerManager;
import me.utruna.danse.menu.DanceMenuManager;
import me.utruna.danse.menu.MenuListener;
import me.utruna.danse.utils.DanseGuard;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.UUID;

/**
 * Main DanseAvecLaStare plugin.
 * Initializes {@link DanceManager} and {@link StaticDancerManager}, registers listeners,
 * handles {@code /danse} commands, and keeps {@code config.yml} up to date automatically.
 */
public class DanseAvecLaStare extends JavaPlugin {

    private DanceManager danceManager;
    private StaticDancerManager staticDancerManager;
    private PlaylistManager playlistManager;
    private DanceMenuManager menuManager;
    private PlaylistCommandHandler playlistCommandHandler;
    private ChoreoCommandHandler choreoCommandHandler;
    private final Set<UUID> debugPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfigIfNeeded();
        reloadConfig();
        SkinService.init(getConfig().getInt("skinFetcher.maxThreads", 4));

        danceManager = new DanceManager(this);
        staticDancerManager = new StaticDancerManager(this);
        playlistManager = new PlaylistManager(this, danceManager, staticDancerManager);
        staticDancerManager.setPlaylistManager(playlistManager);
        playlistManager.loadFromFile();

        getLogger().info("Option useModelEngine=" + getConfig().getBoolean("useModelEngine", false));

        if (getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            checkModelEngineBlueprints();
            // 60 tick delay (3s): ModelEngine loads blueprints asynchronously after onEnable.
            // Without the delay, createActiveModel() returns null and dancers do not respawn.
            // Add another 40 ticks to allow async skin fetches to finish before loading
            // choreography groups, which require dancers to be active.
            Bukkit.getScheduler().runTaskLater(this, () -> {
                staticDancerManager.loadFromFile();
                Bukkit.getScheduler().runTaskLater(this, staticDancerManager::loadChoreographyFromFile, 40L);
            }, 60L);
        }

        menuManager = new DanceMenuManager(this, danceManager, staticDancerManager, playlistManager);
        playlistCommandHandler = new PlaylistCommandHandler(danceManager, playlistManager, staticDancerManager);
        choreoCommandHandler = new ChoreoCommandHandler(staticDancerManager);
        getServer().getPluginManager().registerEvents(new PlayerListener(danceManager, playlistManager, staticDancerManager, this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(menuManager), this);

        if (getCommand("danse") != null) {
            getCommand("danse").setExecutor(this);
            getCommand("danse").setTabCompleter(new DanseTabCompleter(danceManager, staticDancerManager, playlistManager));
        }
        getLogger().info("ModelDancer is ready.");
    }

    /**
    * Automatically updates config.yml with new keys from the default file.
    * Existing values are preserved; only missing keys are added.
     */
    private void updateConfigIfNeeded() {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                return; // saveDefaultConfig() handles this
            }

            java.io.InputStream defaultInput = getResource("config.yml");
            if (defaultInput == null) {
                getLogger().warning("Could not load the default config.yml from the JAR");
                return;
            }

            org.bukkit.configuration.file.YamlConfiguration defaultConfig = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defaultInput, java.nio.charset.StandardCharsets.UTF_8));
            
            org.bukkit.configuration.file.YamlConfiguration currentConfig = 
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);

            String beforeUpdate = currentConfig.saveToString();

            currentConfig.setDefaults(defaultConfig);
            currentConfig.options().copyDefaults(true);

            String configVersion = currentConfig.getString("configVersion", "1.0");
            String defaultVersion = defaultConfig.getString("configVersion", "1.0");
            
            if (!configVersion.equals(defaultVersion)) {
                currentConfig.set("configVersion", defaultVersion);
                getLogger().info("Config updated from version " + configVersion + " to " + defaultVersion);
            }

            boolean needsSave = !beforeUpdate.equals(currentConfig.saveToString());

            if (needsSave) {
                String timestamp = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                File backup = new File(getDataFolder(), "config.yml." + timestamp);
                try {
                    java.nio.file.Files.copy(configFile.toPath(), backup.toPath());
                    getLogger().info("✓ Previous config backed up → " + backup.getName());
                } catch (Exception backupEx) {
                    getLogger().log(Level.WARNING, "Could not save the previous config", backupEx);
                }
                currentConfig.save(configFile);
                getLogger().info("✓ config.yml updated automatically (new keys added)");
            }

        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Error while updating config.yml", ex);
        }
    }

    @Override
    public void onDisable() {
        if (playlistManager != null) {
            playlistManager.stopAll();
        }
        if (staticDancerManager != null) {
            staticDancerManager.removeAll();
        }
        if (danceManager != null) {
            danceManager.stopAll();
        }
        SkinService.shutdown();
        getLogger().info("Stopping ModelDancer plugin.");
    }

    /** Verifies that the configured .bbmodel files exist in ModelEngine's blueprints folder. */
    private void checkModelEngineBlueprints() {
        File modelEngineFolder = new File(getDataFolder().getParentFile(), "ModelEngine");
        File blueprintsFolder = new File(modelEngineFolder, "blueprints");

        Set<String> modelIds = resolveConfiguredModelIds();
        for (String modelId : modelIds) {
            File modelFile = new File(blueprintsFolder, modelId + ".bbmodel");
            if (!modelFile.exists()) {
                getLogger().severe("[ModelDancer] WARNING: Model '" + modelId + ".bbmodel' not found in " + blueprintsFolder.getPath());
            } else {
                getLogger().info("[ModelDancer] Model '" + modelId + ".bbmodel' found.");
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("danse")) return false;
        if (args.length > 0 && args[0].equalsIgnoreCase("debug")) {
            if (sender instanceof Player p) {
                UUID id = p.getUniqueId();
                boolean now = togglePlayerDebug(id);
                p.sendMessage("§eDebug mode " + (now ? "enabled" : "disabled") + " for you.");
            }
            sendDebugStatus(sender);
            return true;
        }

        // --- Menu staff ---

        if (args.length > 0 && args[0].equalsIgnoreCase("staff")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cPlayers only.");
                return true;
            }
            if (!p.hasPermission("danse.staff")) {
                p.sendMessage("§cYou do not have the danse.staff permission.");
                return true;
            }
            menuManager.openStaffMain(p);
            return true;
        }

        // --- Reload configuration ---

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            String adminNode = getConfig().getString("permissions.useAdmin", "danse.admin");
            if (!DanseGuard.canUse(sender, adminNode, this)) {
                sender.sendMessage("§cYou do not have permission.");
                return true;
            }
            try {
                File configFile = new File(getDataFolder(), "config.yml");
                String backupName = "config.yml." + LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm"));
                if (configFile.exists()) {
                    Files.copy(configFile.toPath(), new File(getDataFolder(), backupName).toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                reloadConfig();
                updateConfigIfNeeded();
                reloadConfig();
                danceManager.reloadStyles();
                SkinService.clearCache();
                sender.sendMessage("§aConfiguration reloaded. §7Backup: §f" + backupName);
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "Error while reloading configuration", ex);
                sender.sendMessage("§cAn error occurred while reloading the configuration.");
            }
            return true;
        }

        // --- NPC : gestion des danseurs statiques ---

        if (args.length > 0 && args[0].equalsIgnoreCase("npc")) {
            if (sender instanceof Player p && !p.hasPermission("danse.static")) {
                sender.sendMessage("§cYou do not have the danse.static permission.");
                return true;
            }
            return handleNpcCommand(sender, args);
        }

        // --- Choreography and playlists ---

        if (args.length > 0 && args[0].equalsIgnoreCase("choreo")) {
            String adminNode = getConfig().getString("permissions.useAdmin", "danse.admin");
            if (!DanseGuard.canUse(sender, adminNode, this)) {
                sender.sendMessage("§cYou do not have permission.");
                return true;
            }
            return choreoCommandHandler.handle(sender, args);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("playlist")) {
            String playlistNode = getConfig().getString("permissions.usePlaylist", "danse.playlist");
            if (!DanseGuard.canUse(sender, playlistNode, this)) {
                sender.sendMessage("§cYou do not have permission.");
                return true;
            }
            return playlistCommandHandler.handle(sender, args);
        }

        // --- Fix stuck visibility (admin / console) ---
        if (args.length > 0 && args[0].equalsIgnoreCase("fixvisible")) {
            // Usage: /danse fixvisible [player]
            if (args.length == 1) {
                if (sender instanceof Player p) {
                    danceManager.restoreVisibility(p.getUniqueId());
                    p.sendMessage("§aVisibility restored for you.");
                } else {
                    sender.sendMessage("Usage: /danse fixvisible <player>");
                }
                return true;
            }
            // Target specified
            String target = args[1];
            Player tp = Bukkit.getPlayerExact(target);
            if (tp == null) {
                    sender.sendMessage("§cPlayer not found: " + target);
                return true;
            }
            if (sender instanceof Player p && !p.hasPermission("danse.staff") && !p.isOp()) {
                p.sendMessage("§cYou do not have the danse.staff permission.");
                return true;
            }
            danceManager.restoreVisibility(tp.getUniqueId());
            sender.sendMessage("§aVisibility restored for " + tp.getName() + ".");
            return true;
        }

        // --- Player-only commands ---

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only a player can use this command.");
            return true;
        }

        if (!DanseGuard.isWorldAllowed(player, this)) {
            player.sendMessage("§cYou cannot dance in this world.");
            return true;
        }

        if (!player.hasPermission("danse.player")) {
            player.sendMessage("§cYou do not have the base permission to use /danse.");
            return true;
        }

        String useNode = getConfig().getString("permissions.useCommand", "danse.use");
        if (!DanseGuard.canUse(player, useNode, this)) {
            player.sendMessage("§cYou do not have permission.");
            return true;
        }

        try {
            if (args.length == 0) {
                menuManager.openPlayerMain(player);
                return true;
            }

            if (args[0].equalsIgnoreCase("stop")) {
                playlistManager.stopForPlayer(player.getUniqueId());
                danceManager.stopDance(player.getUniqueId());
                player.sendMessage("§aDance stopped.");
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                player.sendMessage("§eAvailable styles: §f" + String.join(", ", danceManager.getStyleNames()));
                return true;
            }

            if (args[0].equalsIgnoreCase("preview")) {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: §f/danse preview <style> [durationTicks]");
                    return true;
                }
                DanceStyle previewStyle = danceManager.parseStyle(args[1]);
                if (previewStyle == null) {
                    player.sendMessage("§cUnknown style. Use §f/danse list§c to see the available styles.");
                    return true;
                }
                int duration;
                if (args.length > 2) {
                    try {
                        duration = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cInvalid duration. Enter an integer number of ticks.");
                        return true;
                    }
                } else {
                    duration = getConfig().getInt("preview.defaultDurationTicks", 100);
                }
                danceManager.startDancePreview(player, previewStyle);
                BukkitTask task = Bukkit.getScheduler().runTaskLater(this,
                        () -> danceManager.stopDance(player.getUniqueId()), duration);
                danceManager.registerPreviewTask(player.getUniqueId(), task);
                player.sendMessage("§aPreview: §f" + previewStyle.getName()
                    + "§a — stops in §f" + duration + "§a ticks.");
                return true;
            }

            DanceStyle style = danceManager.parseStyle(args[0]);
            if (style == null) {
                player.sendMessage("§cUnknown style. Use §f/danse list§c to see the available styles.");
                return true;
            }

            // Style permission check
            String stylePerm = danceManager.getPermission(style.getName());
            if (stylePerm != null && !player.hasPermission(stylePerm)) {
                player.sendMessage("§cYou do not have permission for this dance style.");
                return true;
            }

            boolean hide = true;
            String target = null;

            if (args.length > 1) {
                if (args[1].equalsIgnoreCase("visible")) {
                    hide = false;
                } else {
                    target = args[1];
                }
            }

            // If another player's skin is used, check the permission first
            if (target != null && !player.hasPermission("danse.skin")) {
                player.sendMessage("§cYou do not have permission to use another player's skin.");
                return true;
            }

            danceManager.startDance(player, style, hide, target);
            player.sendMessage("§aYou are now dancing: §f" + style.getName() + (target != null ? " with the skin of " + target : ""));

        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Error handling /danse command", ex);
            player.sendMessage("§cAn error occurred.");
        }
        return true;
    }

    /** Displays the current plugin state and the sender's debug mode in chat. */
    private void sendDebugStatus(CommandSender sender) {
        sender.sendMessage("§e=== DEBUG STATUS ===");
        sender.sendMessage("§fModelEngine enabled: §7" + getServer().getPluginManager().isPluginEnabled("ModelEngine"));
        sender.sendMessage("§fConfig useModelEngine: §7" + getConfig().getBoolean("useModelEngine", false));
        sender.sendMessage("§fAvailable dances: §7" + danceManager.getStyleNames());
        if (sender instanceof Player p) {
            sender.sendMessage("§fDebug mode for you: §7" + (isPlayerDebug(p.getUniqueId()) ? "ON" : "OFF"));
        }
        sender.sendMessage("§e===================");
    }

    /** Returns {@code true} if debug mode is active for the given player. */
    public boolean isPlayerDebug(UUID id) {
        return debugPlayers.contains(id);
    }

    /** Toggles debug mode for the given player and returns the new state. */
    public boolean togglePlayerDebug(UUID id) {
        if (debugPlayers.contains(id)) {
            debugPlayers.remove(id);
            return false;
        } else {
            debugPlayers.add(id);
            return true;
        }
    }

    /** Handles /danse npc subcommands. */
    private boolean handleNpcCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §f/danse npc <spawn|move|delete|list|highlight|resize|style>");
            return true;
        }
        switch (args[1].toLowerCase()) {

            case "spawn" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayers only."); return true; }
                if (args.length < 4) { player.sendMessage("§cUsage: §f/danse npc spawn <id> <style> [playerName]"); return true; }
                String id = args[2];
                String styleName = args[3].toLowerCase();
                String skinTarget = args.length >= 5 ? args[4] : null;
                if (staticDancerManager.getDancerIds().contains(id)) {
                    player.sendMessage("§cThe ID '§f" + id + "§c' is already used by an NPC.");
                    return true;
                }
                if (!getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
                    player.sendMessage("§cModelEngine is not available.");
                    return true;
                }
                Location loc = player.getLocation();
                if (skinTarget != null && !skinTarget.isBlank()) {
                    if (!player.hasPermission("danse.skin")) {
                        player.sendMessage("§cYou do not have permission to use another player's skin.");
                        return true;
                    }
                    player.sendMessage("§7Fetching the skin for §f" + skinTarget + "§7...");
                    SkinService.fetchSkin(this, skinTarget, (profile) -> {
                        if (profile == null) { player.sendMessage("§cPlayer not found or Mojang error: §f" + skinTarget); return; }
                        Bukkit.getScheduler().runTask(this, () -> {
                            boolean spawned = staticDancerManager.spawnStaticDancer(id, loc, styleName, profile, skinTarget);
                                player.sendMessage(spawned
                                    ? "§aNPC '§f" + id + "§a' created with the skin of §f" + skinTarget + "§a."
                                    : "§cSpawn failed. Check the style '§f" + styleName + "§c'.");
                        });
                    });
                } else {
                    @SuppressWarnings("deprecation")
                    PlayerProfile profile = player.getPlayerProfile();
                    boolean spawned = staticDancerManager.spawnStaticDancer(id, loc, styleName, profile, player.getName());
                        player.sendMessage(spawned
                            ? "§aNPC '§f" + id + "§a' created with style '§f" + styleName + "§a'."
                            : "§cSpawn failed. Check the style '§f" + styleName + "§c'.");
                }
            }

            case "move" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cPlayers only."); return true; }
                if (args.length < 3) { player.sendMessage("§cUsage: §f/danse npc move <id>"); return true; }
                String id = args[2];
                player.sendMessage(staticDancerManager.moveStaticDancer(id, player.getLocation())
                        ? "§aNPC '§f" + id + "§a' moved to your position."
                        : "§cNo NPC found with ID: §f" + id);
            }

            case "delete" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: §f/danse npc delete <id>"); return true; }
                String id = args[2];
                sender.sendMessage(staticDancerManager.removeStaticDancer(id)
                        ? "§aNPC '§f" + id + "§a' deleted."
                        : "§cNo NPC found with ID: §f" + id);
            }

            case "list" -> {
                Set<String> ids = staticDancerManager.getDancerIds();
                if (ids.isEmpty()) {
                    sender.sendMessage("§eNo active NPCs.");
                } else {
                    sender.sendMessage("§eActive NPCs: §f" + String.join(", ", new java.util.TreeSet<>(ids)));
                }
            }

            case "highlight" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: §f/danse npc highlight <id> [seconds]"); return true; }
                String id = args[2];
                int seconds = 3;
                if (args.length >= 4) {
                    try { seconds = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                }
                sender.sendMessage(staticDancerManager.highlightDancer(id, seconds)
                        ? "§eNPC '§f" + id + "§e' highlighted for §f" + seconds + "s§e."
                        : "§cNPC '§f" + id + "§c' not found.");
            }

            case "resize" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: §f/danse npc resize <id> <value>"); return true; }
                String id = args[2];
                double scale;
                try {
                    scale = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid value. Enter a number between §f0.1§c and §f20.0§c.");
                    return true;
                }
                if (scale < 0.1 || scale > 20.0) {
                    sender.sendMessage("§cThe value must be between §f0.1§c and §f20.0§c.");
                    return true;
                }
                sender.sendMessage(staticDancerManager.setScale(id, scale)
                        ? "§aNPC '§f" + id + "§a' resized to §f" + scale + "§a."
                        : "§cNo NPC found with ID: §f" + id);
            }

            case "style" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: §f/danse npc style <id> <style>"); return true; }
                String id = args[2];
                String styleName = args[3].toLowerCase();
                if (!staticDancerManager.getDancerIds().contains(id)) {
                    sender.sendMessage("§cNo NPC found with ID: §f" + id);
                    return true;
                }
                if (!danceManager.getStyleNames().contains(styleName)) {
                    sender.sendMessage("§cUnknown style. Valid styles: §f" + String.join(", ", danceManager.getStyleNames()));
                    return true;
                }
                sender.sendMessage(staticDancerManager.changeDancerStyle(id, styleName)
                        ? "§aNPC '§f" + id + "§a' switched to style §f" + styleName + "§a."
                        : "§cFailed to change the style for '§f" + id + "§c'.");
            }

            default -> sender.sendMessage("§cUnknown subcommand. Use: spawn, move, delete, list, highlight, resize, style");
        }
        return true;
    }

    /** Collecte tous les modelId référencés dans la config (principal, fallback, styleModels). */
    private Set<String> resolveConfiguredModelIds() {
        Set<String> modelIds = new HashSet<>();
        String defaultModelId = getConfig().getString("modelEngine.defaultModelId", "danseur");
        modelIds.add(defaultModelId);

        if (getConfig().getBoolean("modelEngine.useFallbackMode", false)) {
            String fallbackModelId = getConfig().getString("modelEngine.fallbackModelId", "joueur_fallback");
            if (fallbackModelId != null && !fallbackModelId.isBlank()) {
                modelIds.add(fallbackModelId.trim());
            }
        }

        ConfigurationSection styleModels = getConfig().getConfigurationSection("modelEngine.styleModels");
        if (styleModels != null) {
            for (String key : styleModels.getKeys(false)) {
                modelIds.add(styleModels.getString(key));
            }
        }
        return modelIds;
    }
}