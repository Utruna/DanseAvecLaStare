package me.utruna.danse.utils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

/** Utilitaires de contrôle d'accès : permissions et restrictions par monde. */
public class DanseGuard {

    /**
     * Vérifie si le sender possède le nœud de permission donné.
     * Retourne {@code true} sans restriction si le nœud est null ou vide dans la config.
     */
    public static boolean canUse(CommandSender sender, String permissionNode, JavaPlugin plugin) {
        if (permissionNode == null || permissionNode.isBlank()) return true;
        return sender.hasPermission(permissionNode);
    }

    /**
     * Vérifie que le monde du joueur est autorisé selon la config.
     * {@code allowedWorlds} (si non vide) : le monde doit y figurer.
     * {@code deniedWorlds} : le monde ne doit pas y figurer.
     */
    public static boolean isWorldAllowed(Player player, JavaPlugin plugin) {
        List<String> allowed = plugin.getConfig().getStringList("worlds.allowedWorlds");
        List<String> denied = plugin.getConfig().getStringList("worlds.deniedWorlds");
        String worldName = player.getWorld().getName();
        if (!allowed.isEmpty() && !allowed.contains(worldName)) return false;
        return !denied.contains(worldName);
    }
}
