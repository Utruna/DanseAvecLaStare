package me.utruna.danse;

import me.utruna.danse.commands.ChoreoCommandHandler;
import me.utruna.danse.commands.DanseTabCompleter;
import me.utruna.danse.commands.PlaylistCommandHandler;
import me.utruna.danse.listeners.PlayerListener;
import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.DanceStyle;
import me.utruna.danse.managers.PlaylistManager;
import me.utruna.danse.managers.SkinCacheManager;
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
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.UUID;

/**
 * Plugin principal DanseAvecLaStare.
 * Initialise le {@link DanceManager} et le {@link StaticDancerManager}, enregistre les listeners,
 * gère les commandes {@code /danse} et assure la migration automatique du {@code config.yml}.
 */
public class DanseAvecLaStare extends JavaPlugin {

    private DanceManager danceManager;
    private StaticDancerManager staticDancerManager;
    private SkinCacheManager skinCacheManager;
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
        skinCacheManager = new SkinCacheManager(this);
        staticDancerManager = new StaticDancerManager(this);
        staticDancerManager.setSkinCacheManager(skinCacheManager);
        playlistManager = new PlaylistManager(this, danceManager, staticDancerManager);
        staticDancerManager.setPlaylistManager(playlistManager);
        playlistManager.loadFromFile();

        getLogger().info("Option useModelEngine=" + getConfig().getBoolean("useModelEngine", false));

        if (getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            checkModelEngineBlueprints();
            // Délai de 60 ticks (3s) : ModelEngine charge ses blueprints en async après onEnable.
            // Sans délai, createActiveModel() renvoie null et les danseurs ne réapparaissent pas.
            // +40 ticks supplémentaires pour laisser le temps aux fetchs de skin async avant de
            // charger les groupes de chorégraphie (qui nécessitent que les danseurs soient actifs).
            Bukkit.getScheduler().runTaskLater(this, () -> {
                long loadDuration = staticDancerManager.loadFromFile();
                // +20 ticks de marge après le dernier lot pour que les skins alias soient appliqués
                Bukkit.getScheduler().runTaskLater(this, staticDancerManager::loadChoreographyFromFile, loadDuration + 20L);
            }, 60L);
        }

        menuManager = new DanceMenuManager(this, danceManager, staticDancerManager, playlistManager);
        playlistCommandHandler = new PlaylistCommandHandler(danceManager, playlistManager, staticDancerManager);
        choreoCommandHandler = new ChoreoCommandHandler(staticDancerManager);
        getServer().getPluginManager().registerEvents(new PlayerListener(danceManager, playlistManager, staticDancerManager, this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(menuManager), this);

        if (getCommand("danse") != null) {
            getCommand("danse").setExecutor(this);
            getCommand("danse").setTabCompleter(new DanseTabCompleter(danceManager, staticDancerManager, playlistManager, skinCacheManager));
        }
        getLogger().info("Le plugin de danse est prêt !");
    }

    /**
     * Met à jour automatiquement le config.yml avec les nouvelles clés depuis le fichier par défaut.
     * Les valeurs existantes ne sont pas modifiées, seules les clés manquantes sont ajoutées.
     */
    private void updateConfigIfNeeded() {
        try {
            File configFile = new File(getDataFolder(), "config.yml");
            if (!configFile.exists()) {
                return; // saveDefaultConfig() va s'en charger
            }

            java.io.InputStream defaultInput = getResource("config.yml");
            if (defaultInput == null) {
                getLogger().warning("Impossible de charger le config.yml par défaut depuis le JAR");
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
                getLogger().info("Config mis à jour de version " + configVersion + " à " + defaultVersion);
            }

            boolean needsSave = !beforeUpdate.equals(currentConfig.saveToString());

            if (needsSave) {
                String timestamp = java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                File backup = new File(getDataFolder(), "config.yml." + timestamp);
                try {
                    java.nio.file.Files.copy(configFile.toPath(), backup.toPath());
                    getLogger().info("✓ Ancienne config sauvegardée → " + backup.getName());
                } catch (Exception backupEx) {
                    getLogger().log(Level.WARNING, "Impossible de sauvegarder l'ancienne config", backupEx);
                }
                currentConfig.save(configFile);
                getLogger().info("✓ Config.yml mis à jour automatiquement (nouvelles clés ajoutées)");
            }

        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Erreur lors de la mise à jour du config.yml", ex);
        }
    }

    public SkinCacheManager getSkinCacheManager() { return skinCacheManager; }

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
        getLogger().info("Arrêt du plugin de danse.");
    }

    /** Vérifie que les fichiers .bbmodel configurés existent dans le dossier blueprints de ModelEngine. */
    private void checkModelEngineBlueprints() {
        File modelEngineFolder = new File(getDataFolder().getParentFile(), "ModelEngine");
        File blueprintsFolder = new File(modelEngineFolder, "blueprints");

        Set<String> modelIds = resolveConfiguredModelIds();
        for (String modelId : modelIds) {
            File modelFile = new File(blueprintsFolder, modelId + ".bbmodel");
            if (!modelFile.exists()) {
                getLogger().severe("[DanseAvecLaStare] ATTENTION: Modèle '" + modelId + ".bbmodel' introuvable dans " + blueprintsFolder.getPath());
            } else {
                getLogger().info("[DanseAvecLaStare] Modèle '" + modelId + ".bbmodel' trouvé.");
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
                p.sendMessage("§eMode debug " + (now ? "activé" : "désactivé") + " pour vous.");
            }
            sendDebugStatus(sender);
            return true;
        }

        // --- Menu staff ---

        if (args.length > 0 && args[0].equalsIgnoreCase("staff")) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage("§cJoueur uniquement.");
                return true;
            }
            if (!p.hasPermission("danse.staff")) {
                p.sendMessage("§cVous n'avez pas la permission danse.staff.");
                return true;
            }
            menuManager.openStaffMain(p);
            return true;
        }

        // --- Reload configuration ---

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            String adminNode = getConfig().getString("permissions.useAdmin", "danse.admin");
            if (!DanseGuard.canUse(sender, adminNode, this)) {
                sender.sendMessage("§cTu n'as pas la permission.");
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
                sender.sendMessage("§aConfiguration rechargée. §7Backup : §f" + backupName);
            } catch (Exception ex) {
                getLogger().log(Level.SEVERE, "Erreur lors du reload", ex);
                sender.sendMessage("§cErreur lors du rechargement de la configuration.");
            }
            return true;
        }

        // --- Skin cache ---

        if (args.length > 0 && args[0].equalsIgnoreCase("skin")) {
            if (sender instanceof Player p && !p.hasPermission("danse.static")) {
                sender.sendMessage("§cVous n'avez pas la permission danse.static.");
                return true;
            }
            return handleSkinCacheCommand(sender, args);
        }

        // --- NPC : gestion des danseurs statiques ---

        if (args.length > 0 && args[0].equalsIgnoreCase("npc")) {
            if (sender instanceof Player p && !p.hasPermission("danse.static")) {
                sender.sendMessage("§cVous n'avez pas la permission danse.static.");
                return true;
            }
            return handleNpcCommand(sender, args);
        }

        // --- Chorégraphie et playlists ---

        if (args.length > 0 && args[0].equalsIgnoreCase("choreo")) {
            String adminNode = getConfig().getString("permissions.useAdmin", "danse.admin");
            if (!DanseGuard.canUse(sender, adminNode, this)) {
                sender.sendMessage("§cTu n'as pas la permission.");
                return true;
            }
            return choreoCommandHandler.handle(sender, args);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("playlist")) {
            String playlistNode = getConfig().getString("permissions.usePlaylist", "danse.playlist");
            if (!DanseGuard.canUse(sender, playlistNode, this)) {
                sender.sendMessage("§cTu n'as pas la permission.");
                return true;
            }
            return playlistCommandHandler.handle(sender, args);
        }

        // --- Fix visibilité bloquée (admin / console) ---
        if (args.length > 0 && args[0].equalsIgnoreCase("fixvisible")) {
            // Usage: /danse fixvisible [player]
            if (args.length == 1) {
                if (sender instanceof Player p) {
                    danceManager.restoreVisibility(p.getUniqueId());
                    p.sendMessage("§aVisibilité rétablie pour vous.");
                } else {
                    sender.sendMessage("Usage: /danse fixvisible <player>");
                }
                return true;
            }
            // Target specified
            String target = args[1];
            Player tp = Bukkit.getPlayerExact(target);
            if (tp == null) {
                sender.sendMessage("§cJoueur introuvable: " + target);
                return true;
            }
            if (sender instanceof Player p && !p.hasPermission("danse.staff") && !p.isOp()) {
                p.sendMessage("§cVous n'avez pas la permission danse.staff.");
                return true;
            }
            danceManager.restoreVisibility(tp.getUniqueId());
            sender.sendMessage("§aVisibilité rétablie pour " + tp.getName() + ".");
            return true;
        }

        // --- Commandes joueur uniquement ---

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seul un joueur peut utiliser cette commande.");
            return true;
        }

        if (!DanseGuard.isWorldAllowed(player, this)) {
            player.sendMessage("§cTu ne peux pas danser dans ce monde.");
            return true;
        }

        if (!player.hasPermission("danse.player")) {
            player.sendMessage("§cVous n'avez pas la permission de base pour utiliser /danse.");
            return true;
        }

        String useNode = getConfig().getString("permissions.useCommand", "danse.use");
        if (!DanseGuard.canUse(player, useNode, this)) {
            player.sendMessage("§cTu n'as pas la permission.");
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
                player.sendMessage("§aDanse arrêtée.");
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                player.sendMessage("§eStyles disponibles: §f" + String.join(", ", danceManager.getStyleNames()));
                return true;
            }

            if (args[0].equalsIgnoreCase("preview")) {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: §f/danse preview <style> [duréeTicks]");
                    return true;
                }
                DanceStyle previewStyle = danceManager.parseStyle(args[1]);
                if (previewStyle == null) {
                    player.sendMessage("§cStyle inconnu. Utilisez §f/danse list§c pour la liste.");
                    return true;
                }
                int duration;
                if (args.length > 2) {
                    try {
                        duration = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cDurée invalide. Entrez un nombre entier de ticks.");
                        return true;
                    }
                } else {
                    duration = getConfig().getInt("preview.defaultDurationTicks", 100);
                }
                danceManager.startDancePreview(player, previewStyle);
                BukkitTask task = Bukkit.getScheduler().runTaskLater(this,
                        () -> danceManager.stopDance(player.getUniqueId()), duration);
                danceManager.registerPreviewTask(player.getUniqueId(), task);
                player.sendMessage("§aAperçu: §f" + previewStyle.getName()
                        + "§a — arrêt dans §f" + duration + "§a ticks.");
                return true;
            }

            DanceStyle style = danceManager.parseStyle(args[0]);
            if (style == null) {
                player.sendMessage("§cStyle inconnu. Utilisez §f/danse list§c pour la liste.");
                return true;
            }

            // Vérification de permission du style
            String stylePerm = danceManager.getPermission(style.getName());
            if (stylePerm != null && !player.hasPermission(stylePerm)) {
                player.sendMessage("§cVous n'avez pas la permission pour ce style de danse.");
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

            // Si utilisation du skin d'un autre joueur, vérifier la permission
            if (target != null && !player.hasPermission("danse.skin")) {
                player.sendMessage("§cVous n'avez pas la permission d'utiliser le skin d'un autre joueur.");
                return true;
            }

            danceManager.startDance(player, style, hide, target);
            player.sendMessage("§aTu commences à danser: §f" + style.getName() + (target != null ? " avec le skin de " + target : ""));

        } catch (Exception ex) {
            getLogger().log(Level.SEVERE, "Erreur commande /danse", ex);
            player.sendMessage("§cUne erreur est survenue.");
        }
        return true;
    }

    /** Affiche l'état courant du plugin et le mode debug du sender dans le chat. */
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

    /** Retourne {@code true} si le mode debug est actif pour le joueur donné. */
    public boolean isPlayerDebug(UUID id) {
        return debugPlayers.contains(id);
    }

    /** Bascule le mode debug pour le joueur donné et retourne le nouvel état. */
    public boolean togglePlayerDebug(UUID id) {
        if (debugPlayers.contains(id)) {
            debugPlayers.remove(id);
            return false;
        } else {
            debugPlayers.add(id);
            return true;
        }
    }

    /** Gère les sous-commandes /danse npc. */
    private boolean handleNpcCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §f/danse npc <spawn|move|delete|list [distance]|highlight|resize|style|skin|reloadskins [pseudo]>");
            return true;
        }
        switch (args[1].toLowerCase()) {

            case "spawn" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cJoueur uniquement."); return true; }
                if (args.length < 4) { player.sendMessage("§cUsage: §f/danse npc spawn <id> <style> [pseudo]"); return true; }
                String id = args[2];
                String styleName = args[3].toLowerCase();
                String skinTarget = args.length >= 5 ? args[4] : null;
                if (staticDancerManager.getDancerIds().contains(id)) {
                    player.sendMessage("§cL'ID '§f" + id + "§c' est déjà utilisé par un NPC.");
                    return true;
                }
                if (!getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
                    player.sendMessage("§cModelEngine n'est pas disponible.");
                    return true;
                }
                Location loc = player.getLocation();
                if (skinTarget != null && !skinTarget.isBlank()) {
                    if (!player.hasPermission("danse.skin")) {
                        player.sendMessage("§cVous n'avez pas la permission d'utiliser le skin d'un autre joueur.");
                        return true;
                    }
                    player.sendMessage("§7Récupération du skin de §f" + skinTarget + "§7...");
                    SkinService.fetchSkin(this, skinTarget, (profile) -> {
                        if (profile == null) {
                            player.sendMessage("§cSkin invalide : §f" + skinTarget + "§c. Le NPC n'a pas été créé.");
                            return;
                        }
                        Bukkit.getScheduler().runTask(this, () -> {
                            boolean spawned = staticDancerManager.spawnStaticDancer(id, loc, styleName, profile, skinTarget);
                            player.sendMessage(spawned
                                    ? "§aNPC '§f" + id + "§a' créé avec le skin de §f" + skinTarget + "§a."
                                    : "§cÉchec du spawn. Vérifie le style '§f" + styleName + "§c'.");
                        });
                    });
                } else {
                    @SuppressWarnings("deprecation")
                    PlayerProfile profile = player.getPlayerProfile();
                    boolean spawned = staticDancerManager.spawnStaticDancer(id, loc, styleName, profile, player.getName());
                    player.sendMessage(spawned
                            ? "§aNPC '§f" + id + "§a' créé avec le style '§f" + styleName + "§a'."
                            : "§cÉchec du spawn. Vérifie le style '§f" + styleName + "§c'.");
                }
            }

            case "move" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cJoueur uniquement."); return true; }
                if (args.length < 3) { player.sendMessage("§cUsage: §f/danse npc move <id>"); return true; }
                String id = args[2];
                player.sendMessage(staticDancerManager.moveStaticDancer(id, player.getLocation())
                        ? "§aNPC '§f" + id + "§a' déplacé à ta position."
                        : "§cAucun NPC avec l'ID: §f" + id);
            }

            case "delete" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: §f/danse npc delete <id>"); return true; }
                String id = args[2];
                sender.sendMessage(staticDancerManager.removeStaticDancer(id)
                        ? "§aNPC '§f" + id + "§a' supprimé."
                        : "§cAucun NPC avec l'ID: §f" + id);
            }

            case "list" -> {
                if (args.length >= 3) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("§cJoueur uniquement pour /danse npc list <distance>.");
                        return true;
                    }
                    double radius;
                    try {
                        radius = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cDistance invalide: §f" + args[2]);
                        return true;
                    }
                    if (radius <= 0) {
                        player.sendMessage("§cLa distance doit être positive.");
                        return true;
                    }
                    Map<String, Double> nearby = staticDancerManager.getDancersNear(player.getLocation(), radius);
                    if (nearby.isEmpty()) {
                        player.sendMessage("§eAucun NPC dans un rayon de §f" + radius + "§e blocs.");
                    } else {
                        player.sendMessage("§eNPCs dans un rayon de §f" + radius + "§e blocs §7(" + nearby.size() + ") :");
                        nearby.forEach((id, dist) -> {
                            String style = staticDancerManager.getDancerStyle(id);
                            player.sendMessage("§f  " + id + " §7— §f" + String.format("%.1f", dist) + " §7blocs"
                                    + (style != null ? " §7(§e" + style + "§7)" : ""));
                        });
                    }
                } else {
                    Set<String> ids = staticDancerManager.getDancerIds();
                    if (ids.isEmpty()) {
                        sender.sendMessage("§eAucun NPC actif.");
                    } else {
                        sender.sendMessage("§eNPCs actifs: §f" + String.join(", ", new java.util.TreeSet<>(ids)));
                    }
                }
            }

            case "highlight" -> {
                if (args.length < 3) { sender.sendMessage("§cUsage: §f/danse npc highlight <id> [secondes]"); return true; }
                String id = args[2];
                int seconds = 3;
                if (args.length >= 4) {
                    try { seconds = Integer.parseInt(args[3]); } catch (NumberFormatException ignored) {}
                }
                sender.sendMessage(staticDancerManager.highlightDancer(id, seconds)
                        ? "§eNPC '§f" + id + "§e' mis en surbrillance pendant §f" + seconds + "s§e."
                        : "§cNPC '§f" + id + "§c' introuvable.");
            }

            case "resize" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: §f/danse npc resize <id> <valeur>"); return true; }
                String id = args[2];
                double scale;
                try {
                    scale = Double.parseDouble(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cValeur invalide. Entrez un nombre entre §f0.1§c et §f20.0§c.");
                    return true;
                }
                if (scale < 0.1 || scale > 20.0) {
                    sender.sendMessage("§cLa valeur doit être comprise entre §f0.1§c et §f20.0§c.");
                    return true;
                }
                sender.sendMessage(staticDancerManager.setScale(id, scale)
                        ? "§aNPC '§f" + id + "§a' redimensionné à §f" + scale + "§a."
                        : "§cAucun NPC avec l'ID: §f" + id);
            }

            case "style" -> {
                if (args.length < 4) { sender.sendMessage("§cUsage: §f/danse npc style <id> <style>"); return true; }
                String id = args[2];
                String styleName = args[3].toLowerCase();
                if (!staticDancerManager.getDancerIds().contains(id)) {
                    sender.sendMessage("§cAucun NPC avec l'ID: §f" + id);
                    return true;
                }
                if (!danceManager.getStyleNames().contains(styleName)) {
                    sender.sendMessage("§cStyle inconnu. Styles valides: §f" + String.join(", ", danceManager.getStyleNames()));
                    return true;
                }
                sender.sendMessage(staticDancerManager.changeDancerStyle(id, styleName)
                        ? "§aNPC '§f" + id + "§a' passe sur le style §f" + styleName + "§a."
                        : "§cÉchec du changement de style pour '§f" + id + "§c'.");
            }

            case "skin" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse npc skin <id> <alias>");
                    return true;
                }
                String id = args[2];
                String alias = args[3];
                if (!staticDancerManager.getDancerIds().contains(id)) {
                    sender.sendMessage("§cAucun NPC avec l'ID: §f" + id);
                    return true;
                }
                @SuppressWarnings("deprecation")
                PlayerProfile cached = skinCacheManager.getSkin(alias);
                if (cached == null) {
                    sender.sendMessage("§cAlias '§f" + alias + "§c' introuvable dans le cache. Utilisez §f/danse skin list§c.");
                    return true;
                }
                boolean ok = staticDancerManager.changeDancerSkin(id, cached, null, alias);
                sender.sendMessage(ok
                        ? "§aSkin '§f" + alias + "§a' appliqué au NPC '§f" + id + "§a'."
                        : "§cÉchec de l'application du skin.");
            }

            case "reloadskins" -> {
                String playerFilter = args.length >= 3 ? args[2] : null;
                int count = staticDancerManager.reloadSkins(playerFilter);
                if (playerFilter != null) {
                    sender.sendMessage("§aReload du skin de §f" + playerFilter + "§a lancé sur §f" + count + "§a NPC(s).");
                } else {
                    sender.sendMessage("§aReload de tous les skins lancé sur §f" + count + "§a NPC(s).");
                }
            }

            default -> sender.sendMessage("§cSous-commande inconnue. Utilisez: spawn, move, delete, list [distance], highlight, resize, style, skin, reloadskins");
        }
        return true;
    }

    /** Gère les sous-commandes /danse skin. */
    @SuppressWarnings("deprecation")
    private boolean handleSkinCacheCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §f/danse skin <save|apply|list|remove>");
            return true;
        }
        switch (args[1].toLowerCase()) {

            case "save" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse skin save <alias> <pseudo>");
                    return true;
                }
                String alias = args[2].toLowerCase();
                String playerName = args[3];
                sender.sendMessage("§7Récupération du skin de §f" + playerName + "§7...");
                SkinService.fetchSkin(this, playerName, profile -> {
                    if (profile == null) {
                        sender.sendMessage("§cSkin invalide ou introuvable: §f" + playerName);
                        return;
                    }
                    skinCacheManager.saveSkin(alias, profile);
                    sender.sendMessage("§aSkin de §f" + playerName + "§a sauvegardé sous l'alias '§f" + alias + "§a'.");
                });
            }

            case "apply" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse skin apply <alias> <npcId>");
                    return true;
                }
                String alias = args[2];
                String npcId = args[3];
                if (!staticDancerManager.getDancerIds().contains(npcId)) {
                    sender.sendMessage("§cAucun NPC avec l'ID: §f" + npcId);
                    return true;
                }
                PlayerProfile cached = skinCacheManager.getSkin(alias);
                if (cached == null) {
                    sender.sendMessage("§cAlias '§f" + alias + "§c' introuvable. Utilisez §f/danse skin list§c.");
                    return true;
                }
                boolean ok = staticDancerManager.changeDancerSkin(npcId, cached, null, alias);
                sender.sendMessage(ok
                        ? "§aSkin '§f" + alias + "§a' appliqué au NPC '§f" + npcId + "§a'."
                        : "§cÉchec de l'application du skin.");
            }

            case "list" -> {
                java.util.Set<String> aliases = skinCacheManager.getAliases();
                if (aliases.isEmpty()) {
                    sender.sendMessage("§eAucun skin en cache.");
                } else {
                    sender.sendMessage("§eSkins en cache §7(" + aliases.size() + ") : §f" + String.join("§7, §f", aliases));
                }
            }

            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: §f/danse skin remove <alias>");
                    return true;
                }
                String alias = args[2];
                sender.sendMessage(skinCacheManager.removeSkin(alias)
                        ? "§aSkin '§f" + alias + "§a' supprimé du cache."
                        : "§cAlias '§f" + alias + "§c' introuvable dans le cache.");
            }

            default -> sender.sendMessage("§cSous-commande inconnue. Utilisez: save, apply, list, remove");
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