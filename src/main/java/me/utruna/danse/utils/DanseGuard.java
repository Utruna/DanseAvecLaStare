package me.utruna.danse.utils;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

/** Access-control utilities: permissions and world restrictions. */
public class DanseGuard {

    /**
    * Checks whether the sender has the given permission node.
    * Returns {@code true} without restriction if the node is null or blank in the config.
     */
    public static boolean canUse(CommandSender sender, String permissionNode, JavaPlugin plugin) {
        if (permissionNode == null || permissionNode.isBlank()) return true;
        return sender.hasPermission(permissionNode);
    }

    /**
    * Checks whether the player's world is allowed according to the config.
    * {@code allowedWorlds} (if not empty): the world must be listed there.
    * {@code deniedWorlds}: the world must not be listed there.
     */
    public static boolean isWorldAllowed(Player player, JavaPlugin plugin) {
        List<String> allowed = plugin.getConfig().getStringList("worlds.allowedWorlds");
        List<String> denied = plugin.getConfig().getStringList("worlds.deniedWorlds");
        String worldName = player.getWorld().getName();
        if (!allowed.isEmpty() && !allowed.contains(worldName)) return false;
        return !denied.contains(worldName);
    }
}
