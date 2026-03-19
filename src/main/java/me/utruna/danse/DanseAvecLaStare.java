package me.utruna.danse;

import me.utruna.danse.listeners.PlayerListener;
import me.utruna.danse.managers.DanceManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import java.util.List;
import java.util.stream.Collectors;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.io.File;

public class DanseAvecLaStare extends JavaPlugin implements CommandExecutor {

    private DanceManager danceManager;

    @Override
    public void onEnable() {
        danceManager = new DanceManager(this);

        if (getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            checkModelEngineBlueprints();
        }

        getLogger().info("Le plugin de danse est prêt !");
        getServer().getPluginManager().registerEvents(new PlayerListener(danceManager), this);

        if (getCommand("danse") != null) {
            getCommand("danse").setExecutor(this);
            // Tab completion: suggest styles + control words
            getCommand("danse").setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1) {
                    String partial = args[0].toLowerCase();
                    List<String> base = danceManager.getStyleNames();
                    base.add("list");
                    base.add("stop");
                    return base.stream()
                            .filter(s -> s.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
                }
                return List.of();
            });
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
        File modelFile = new File(blueprintsFolder, "danseur.bbmodel");

        if (!modelFile.exists()) {
            getLogger().severe("========================================");
            getLogger().severe("[DanseAvecLaStare] ATTENTION: Le fichier de modèle 'danseur.bbmodel' est introuvable !");
            getLogger().severe("[DanseAvecLaStare] Veuillez le placer dans le dossier : " + blueprintsFolder.getPath());
            getLogger().severe("[DanseAvecLaStare] Sans ce fichier, l'animation de danse via ModelEngine ne fonctionnera pas.");
            getLogger().severe("========================================");
        } else {
            getLogger().info("[DanseAvecLaStare] Modèle 'danseur.bbmodel' trouvé avec succès dans ModelEngine.");
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("danse")) {
            try {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Seul un joueur peut utiliser cette commande.");
                return true;
            }

            if (args.length == 0) {
                if (danceManager.isDancing(player.getUniqueId())) {
                    danceManager.stopDance(player.getUniqueId());
                } else {
                    danceManager.startDance(player, danceManager.parseStyle("twist"));
                    player.sendMessage("§aTu commences à danser: §ftwist");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("stop")) {
                danceManager.stopDance(player.getUniqueId());
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                player.sendMessage("§eStyles disponibles: §f" + danceManager.getStylesLabel());
                return true;
            }

            me.utruna.danse.managers.DanceStyle style = danceManager.parseStyle(args[0]);
            if (style == null) {
                player.sendMessage("§cStyle inconnu. Utilise §f/danse list§c.");
                return true;
            }
            boolean hide = true;
            if (args.length > 1) {
                String f = args[1].toLowerCase();
                if (f.equals("off") || f.equals("false") || f.equals("no") || f.equals("0") || f.equals("visible")) {
                    hide = false;
                }
            }

            danceManager.startDance(player, style, hide);
            player.sendMessage("§aTu commences à danser: §f" + style.getName());
            return true;
            } catch (Exception ex) {
                getLogger().severe("Erreur lors de l'exécution de la commande /danse:");
                java.io.StringWriter sw = new java.io.StringWriter();
                ex.printStackTrace(new java.io.PrintWriter(sw));
                getLogger().severe(sw.toString());
                if (sender instanceof Player) {
                    sender.sendMessage("§cUne erreur est survenue. Voir les logs du serveur.");
                }
                return true;
            }
        }

        return false;
    }

    public DanceManager getDanceManager() {
        return danceManager;
    }
}