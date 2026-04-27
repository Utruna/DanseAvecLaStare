package me.utruna.danse;

import me.utruna.danse.listeners.PlayerListener;
import me.utruna.danse.managers.DanceManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class DanseAvecLaStare extends JavaPlugin implements CommandExecutor {

    private DanceManager danceManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        danceManager = new DanceManager(this);

        getLogger().info("Config chargee depuis: " + getDataFolder().getAbsolutePath() + File.separator + "config.yml");
        getLogger().info("Option useModelEngine=" + getConfig().getBoolean("useModelEngine", false));

        if (getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            // Renommé pour correspondre au nouveau dossier
            checkModelEngineModels(); 
        }

        getLogger().info("Le plugin de danse est prêt !");

        getCommand("danse").setExecutor(this);
        getCommand("danse").setTabCompleter(new TabCompleter() {
            @Override
            public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
                if (args.length == 1) {
                    List<String> list = danceManager.getStyleNames();
                    list.add("stop");
                    list.add("list");
                    list.add("debug");
                    return list.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
                } else if (args.length == 2 && !args[0].equalsIgnoreCase("stop") && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("debug")) {
                    return List.of("visible", "off", "false").stream().filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
                return List.of();
            }
        });

        getServer().getPluginManager().registerEvents(new PlayerListener(danceManager), this);
    }

    @Override
    public void onDisable() {
        if (danceManager != null) {
            danceManager.stopAll();
        }
        getLogger().info("Le plugin de danse a ete desactive.");
    }

    // --- MISE À JOUR : On pointe vers 'models' et non 'blueprints' ---
    private void checkModelEngineModels() {
        boolean useModelEngine = getConfig().getBoolean("useModelEngine", false);
        if (!useModelEngine) return;

        File modelEngineFolder = new File(getDataFolder().getParentFile(), "ModelEngine");
        if (!modelEngineFolder.exists()) return;

        // Le changement critique est ici : on cherche dans "models"
        File modelsFolder = new File(modelEngineFolder, "models");

        Set<String> configuredModelIds = resolveConfiguredModelIds();

        for (String modelId : configuredModelIds) {
            File modelFile = new File(modelsFolder, modelId + ".bbmodel");
            if (!modelFile.exists()) {
                getLogger().severe("========================================");
                getLogger().severe("[DanseAvecLaStare] ATTENTION: Le fichier de modèle '" + modelId + ".bbmodel' est introuvable !");
                getLogger().severe("[DanseAvecLaStare] Veuillez le placer dans le dossier : plugins/ModelEngine/models");
                getLogger().severe("[DanseAvecLaStare] Sans ce fichier, la danse liée à ce modèle ne fonctionnera pas.");
                getLogger().severe("========================================");
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Seul un joueur peut danser.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            if (danceManager.isDancing(player.getUniqueId())) {
                danceManager.stopDance(player.getUniqueId());
                player.sendMessage("§aVous avez arrêté de danser.");
            } else {
                danceManager.startDance(player, danceManager.parseStyle("twist"));
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("stop")) {
            danceManager.stopDance(player.getUniqueId());
            player.sendMessage("§aVous avez arrêté de danser.");
            return true;
        }

        if (sub.equals("list")) {
            player.sendMessage("§aStyles de danse disponibles : §f" + danceManager.getStylesLabel());
            return true;
        }

        if (sub.equals("debug")) {
            runDebugCommand(sender);
            return true;
        }

        me.utruna.danse.managers.DanceStyle style = danceManager.parseStyle(sub);
        if (style != null) {
            boolean hide = true;
            if (args.length > 1) {
                String a2 = args[1].toLowerCase();
                if (a2.equals("visible") || a2.equals("off") || a2.equals("false")) {
                    hide = false;
                }
            }
            danceManager.startDance(player, style, hide);
        } else {
            player.sendMessage("§cStyle inconnu. Faites /danse list.");
        }

        return true;
    }

    private void runDebugCommand(CommandSender sender) {
        sender.sendMessage("§8=== §dDanseAvecLaStare Debug §8===");
        boolean useModelEngine = getConfig().getBoolean("useModelEngine", false);
        sender.sendMessage("§7useModelEngine (Config): §f" + useModelEngine);

        boolean citizensEnabled = getServer().getPluginManager().isPluginEnabled("Citizens");
        boolean modelEngineEnabled = getServer().getPluginManager().isPluginEnabled("ModelEngine");

        sender.sendMessage("§7Citizens plugin: §f" + citizensEnabled);
        sender.sendMessage("§7ModelEngine plugin: §f" + modelEngineEnabled);

        String defaultModelId = getConfig().getString("modelEngine.defaultModelId", "danseur");
        sender.sendMessage("§7defaultModelId: §f" + defaultModelId);

        File meFolder = new File(getDataFolder().getParentFile(), "ModelEngine");
        // MISE À JOUR pour le debug : on vérifie le dossier models
        File modelsFolder = new File(meFolder, "models"); 
        File modelFile = new File(modelsFolder, defaultModelId + ".bbmodel");

        if (modelEngineEnabled) {
            sender.sendMessage("§eDossier ME: §f" + meFolder.exists() + " (" + meFolder.getAbsolutePath() + ")");
            // Affichage mis à jour pour le joueur
            sender.sendMessage("§eDossier Models: §f" + modelsFolder.exists()); 
        }
        
        // Affichage mis à jour pour le joueur
        sender.sendMessage("§eModèle attendu: §f" + modelFile.getAbsolutePath());
        sender.sendMessage("§eModèle présent: §f" + modelFile.exists());

        if (modelEngineEnabled && useModelEngine && modelFile.exists()) {
            sender.sendMessage("§aMode actif attendu: ModelEngine");
        } else {
            sender.sendMessage("§cMode actif probable: Citizens (ou erreur de config ModelEngine)");
        }
    }

    private Set<String> resolveConfiguredModelIds() {
        Set<String> modelIds = new HashSet<>();

        String defaultModelId = getConfig().getString("modelEngine.defaultModelId",
                getConfig().getString("modelEngine.modelId", "danseur"));
        if (defaultModelId != null && !defaultModelId.isBlank()) {
            modelIds.add(defaultModelId.trim());
        }

        ConfigurationSection styleModels = getConfig().getConfigurationSection("modelEngine.styleModels");
        if (styleModels != null) {
            for (String style : styleModels.getKeys(false)) {
                String modelId = styleModels.getString(style);
                if (modelId != null && !modelId.isBlank()) {
                    modelIds.add(modelId.trim());
                }
            }
        }

        if (modelIds.isEmpty()) {
            modelIds.add("danseur");
        }

        return modelIds;
    }

    public DanceManager getDanceManager() {
        return danceManager;
    }
}