package me.utruna.danse;

import me.utruna.danse.listeners.PlayerListener;
import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.DanceStyle;
import me.utruna.danse.managers.SkinService;
import me.utruna.danse.managers.StaticDancerManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final Set<UUID> debugPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfigIfNeeded();
        reloadConfig();

        danceManager = new DanceManager(this);
        staticDancerManager = new StaticDancerManager(this);

        getLogger().info("Option useModelEngine=" + getConfig().getBoolean("useModelEngine", false));

        if (getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            checkModelEngineBlueprints();
            // Délai de 60 ticks (3s) : ModelEngine charge ses blueprints en async après onEnable.
            // Sans délai, createActiveModel() renvoie null et les danseurs ne réapparaissent pas.
            Bukkit.getScheduler().runTaskLater(this, staticDancerManager::loadFromFile, 60L);
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(danceManager), this);

        if (getCommand("danse") != null) {
            getCommand("danse").setExecutor(this);
            getCommand("danse").setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1) {
                    String partial = args[0].toLowerCase();
                    List<String> base = new ArrayList<>(danceManager.getStyleNames());
                    base.add("here");
                    base.add("move");
                    base.add("listID");
                    base.add("list");
                    base.add("delete");
                    base.add("stop");
                    base.add("debug");
                    return base.stream()
                            .filter(s -> s.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("move"))) {
                    String partial = args[1].toLowerCase();
                    return staticDancerManager.getDancerIds().stream()
                            .filter(id -> id.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
                }
                if (args.length == 3 && args[0].equalsIgnoreCase("here")) {
                    String partial = args[2].toLowerCase();
                    return danceManager.getStyleNames().stream()
                            .filter(s -> s.startsWith(partial))
                            .collect(Collectors.toList());
                }
                if (args.length == 4 && args[0].equalsIgnoreCase("here")) {
                    String partial = args[3].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
                }
                return List.of();
            });
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
                currentConfig.save(configFile);
                getLogger().info("✓ Config.yml mis à jour automatiquement (nouvelles clés ajoutées)");
            }

        } catch (Exception ex) {
            getLogger().log(Level.WARNING, "Erreur lors de la mise à jour du config.yml", ex);
        }
    }

    @Override
    public void onDisable() {
        if (staticDancerManager != null) {
            staticDancerManager.removeAll();
        }
        if (danceManager != null) {
            danceManager.stopAll();
        }
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

        // --- Commandes accessibles depuis la console et les joueurs ---

        if (args.length > 0 && args[0].equalsIgnoreCase("listID")) {
            Set<String> ids = staticDancerManager.getDancerIds();
            if (ids.isEmpty()) {
                sender.sendMessage("§eAucun danseur statique actif.");
            } else {
                sender.sendMessage("§eDanseurs statiques actifs: §f" + String.join(", ", new java.util.TreeSet<>(ids)));
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("delete")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /danse delete <id>");
                return true;
            }
            String id = args[1];
            if (staticDancerManager.removeStaticDancer(id)) {
                sender.sendMessage("§aDanseur '" + id + "' supprimé.");
            } else {
                sender.sendMessage("§cAucun danseur avec l'ID: §f" + id);
            }
            return true;
        }

        // --- Commandes joueur uniquement ---

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seul un joueur peut utiliser cette commande.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("here")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: §f/danse here <id> <style> [pseudo]");
                return true;
            }
            String id = args[1];
            String styleName = args[2].toLowerCase();
            String skinTarget = args.length >= 4 ? args[3] : null;

            if (staticDancerManager.getDancerIds().contains(id)) {
                player.sendMessage("§cL'ID '§f" + id + "§c' est déjà utilisé par un danseur statique.");
                return true;
            }
            if (!getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
                player.sendMessage("§cModelEngine n'est pas disponible.");
                return true;
            }

            Location loc = player.getLocation();

            if (skinTarget != null && !skinTarget.isBlank()) {
                // Skin d'un autre joueur via Mojang — récupération async puis spawn sur le thread principal
                player.sendMessage("§7Récupération du skin de §f" + skinTarget + "§7...");
                SkinService.fetchSkin(this, skinTarget, (profile) -> {
                    if (profile == null) {
                        player.sendMessage("§cJoueur introuvable ou erreur Mojang : §f" + skinTarget);
                        return;
                    }
                    Bukkit.getScheduler().runTask(this, () -> {
                        boolean spawned = staticDancerManager.spawnStaticDancer(id, loc, styleName, profile, skinTarget);
                        if (spawned) {
                            player.sendMessage("§aDanseur '§f" + id + "§a' apparu avec le skin de §f" + skinTarget + "§a.");
                        } else {
                            player.sendMessage("§cÉchec du spawn. Vérifie le style '§f" + styleName + "§c'.");
                        }
                    });
                });
            } else {
                // Skin du joueur qui lance la commande — on sauvegarde son pseudo pour la restauration
                @SuppressWarnings("deprecation")
                PlayerProfile profile = player.getPlayerProfile();
                boolean spawned = staticDancerManager.spawnStaticDancer(id, loc, styleName, profile, player.getName());
                if (spawned) {
                    player.sendMessage("§aDanseur '§f" + id + "§a' apparu avec le style '§f" + styleName + "§a'.");
                } else {
                    player.sendMessage("§cÉchec du spawn. Vérifie le style '§f" + styleName + "§c'.");
                }
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("move")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: §f/danse move <id>");
                return true;
            }
            String id = args[1];
            if (staticDancerManager.moveStaticDancer(id, player.getLocation())) {
                player.sendMessage("§aDanseur '§f" + id + "§a' déplacé à ta position.");
            } else {
                player.sendMessage("§cAucun danseur avec l'ID: §f" + id);
            }
            return true;
        }

        try {
            if (args.length == 0) {
                if (danceManager.isDancing(player.getUniqueId())) {
                    danceManager.stopDance(player.getUniqueId());
                    player.sendMessage("§aTu arrêtes de danser.");
                } else {
                    DanceStyle twist = danceManager.parseStyle("twist");
                    danceManager.startDance(player, twist, true, null);
                    player.sendMessage("§aTu commences à danser: §ftwist");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("stop")) {
                danceManager.stopDance(player.getUniqueId());
                player.sendMessage("§aDanse arrêtée.");
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                player.sendMessage("§eStyles disponibles: §f" + String.join(", ", danceManager.getStyleNames()));
                return true;
            }

            DanceStyle style = danceManager.parseStyle(args[0]);
            if (style == null) {
                player.sendMessage("§cStyle inconnu. Utilisez §f/danse list§c pour la liste.");
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