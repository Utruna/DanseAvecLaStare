package me.utruna.danse;

import me.utruna.danse.listeners.PlayerListener;
import me.utruna.danse.managers.DanceManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DanseAvecLaStare extends JavaPlugin implements CommandExecutor {

    private DanceManager danceManager;

    @Override
    public void onEnable() {
        danceManager = new DanceManager(this);

        getLogger().info("Le plugin de danse est prêt !");
        getServer().getPluginManager().registerEvents(new PlayerListener(danceManager), this);

        if (getCommand("danse") != null) {
            getCommand("danse").setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        if (danceManager != null) {
            danceManager.stopAll();
        }
        getLogger().info("Arrêt du plugin de danse.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("danse")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Seul un joueur peut utiliser cette commande.");
                return true;
            }

            if (args.length == 0) {
                if (danceManager.isDancing(player.getUniqueId())) {
                    danceManager.stopDance(player, true);
                } else {
                    danceManager.startDance(player, DanceManager.DanceStyle.TWIST);
                    player.sendMessage("§aTu commences à danser: §fTWIST");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("stop")) {
                danceManager.stopDance(player, true);
                return true;
            }

            if (args[0].equalsIgnoreCase("list")) {
                player.sendMessage("§eStyles disponibles: §f" + danceManager.getStylesLabel());
                return true;
            }

            DanceManager.DanceStyle style = danceManager.parseStyle(args[0]);
            if (style == null) {
                player.sendMessage("§cStyle inconnu. Utilise §f/danse list§c.");
                return true;
            }

            danceManager.startDance(player, style);
            player.sendMessage("§aTu commences à danser: §f" + style.name());
            return true;
        }

        return false;
    }

    public DanceManager getDanceManager() {
        return danceManager;
    }
}