package me.utruna.danse;

import me.utruna.danse.listeners.PlayerListener;
import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.DanceStyle; // Importation correcte du style
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.UUID;

public class DanseAvecLaStare extends JavaPlugin {

    private DanceManager danceManager;
    private final Set<UUID> debugPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfigIfNeeded();
        reloadConfig();

        danceManager = new DanceManager(this);

        getLogger().info("Option useModelEngine=" + getConfig().getBoolean("useModelEngine", false));

        if (getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            checkModelEngineBlueprints();
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(danceManager), this);

        if (getCommand("danse") != null) {
            getCommand("danse").setExecutor(this);
            getCommand("danse").setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1) {
                    String partial = args[0].toLowerCase();
                    List<String> base = danceManager.getStyleNames();
                    base.add("list");
                    base.add("stop");
                    base.add("debug");
                    return base.stream()
                            .filter(s -> s.toLowerCase().startsWith(partial))
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
        if (danceManager != null) {
            danceManager.stopAll();
        }
        getLogger().info("Arrêt du plugin de danse.");
    }

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

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seul un joueur peut utiliser cette commande.");
            return true;
        }
// On ajoute 'true' (pour cacher le joueur) et 'null' (car pas de skin cible)

        try {
            // Cas : /danse (sans arguments)
            if (args.length == 0) {
                if (danceManager.isDancing(player.getUniqueId())) {
                    danceManager.stopDance(player.getUniqueId());
                    player.sendMessage("§aTu arrêtes de danser.");
                } else {
                    DanceStyle twist = danceManager.parseStyle("twist");
                    // On appelle la méthode startDance avec les 4 paramètres attendus
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
                player.sendMessage("§cStyle inconnu.");
                return true;
            }

            boolean hide = true;
            String target = null;

            // Gestion des arguments : /danse <style> [visible/joueur]
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

    public boolean isPlayerDebug(UUID id) {
        return debugPlayers.contains(id);
    }

    public boolean togglePlayerDebug(UUID id) {
        if (debugPlayers.contains(id)) {
            debugPlayers.remove(id);
            return false;
        } else {
            debugPlayers.add(id);
            return true;
        }
    }

    private Set<String> resolveConfiguredModelIds() {
        Set<String> modelIds = new HashSet<>();
        String defaultModelId = getConfig().getString("modelEngine.defaultModelId", "danseur");
        modelIds.add(defaultModelId);

        ConfigurationSection styleModels = getConfig().getConfigurationSection("modelEngine.styleModels");
        if (styleModels != null) {
            for (String key : styleModels.getKeys(false)) {
                modelIds.add(styleModels.getString(key));
            }
        }
        return modelIds;
    }
}