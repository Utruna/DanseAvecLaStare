package me.utruna.danse.commands;

import me.utruna.danse.managers.StaticDancerManager;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ChoreoCommandHandler {

    private final StaticDancerManager staticDancerManager;

    public ChoreoCommandHandler(StaticDancerManager staticDancerManager) {
        this.staticDancerManager = staticDancerManager;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §f/danse choreo <create|add|remove|sync|delete|list>");
            return true;
        }

        switch (args[1].toLowerCase()) {
            case "create" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse choreo create <groupId> <id1> [id2...]");
                    return true;
                }
                String groupId = args[2];
                List<String> ids = Arrays.asList(args).subList(3, args.length);
                if (staticDancerManager.createChoreography(groupId, ids)) {
                        sender.sendMessage("§aGroup §f'" + groupId + "'§a created with §f" + ids.size()
                            + "§a dancer(s). Animations synchronized!");
                } else {
                        sender.sendMessage("§cError: one or more IDs were not found. Valid IDs: §f"
                            + String.join(", ", staticDancerManager.getDancerIds()));
                }
            }
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse choreo add <groupId> <id>");
                    return true;
                }
                if (staticDancerManager.addToChoreography(args[2], args[3])) {
                    sender.sendMessage("§a'§f" + args[3] + "§a' added to group §f'" + args[2]
                            + "'§a. Resynchronization complete.");
                } else {
                    sender.sendMessage("§cDancer '§f" + args[3] + "§c' not found.");
                }
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse choreo remove <groupId> <id>");
                    return true;
                }
                if (staticDancerManager.removeFromChoreography(args[2], args[3])) {
                    sender.sendMessage("§a'§f" + args[3] + "§a' removed from group §f'" + args[2] + "'§a.");
                } else {
                    sender.sendMessage("§cDancer or group not found.");
                }
            }
            case "sync" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: §f/danse choreo sync <groupId>");
                    return true;
                }
                if (staticDancerManager.syncChoreography(args[2])) {
                    sender.sendMessage("§aGroup §f'" + args[2] + "'§a resynchronized!");
                } else {
                    sender.sendMessage("§cGroup '§f" + args[2] + "§c' not found.");
                }
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: §f/danse choreo delete <groupId>");
                    return true;
                }
                if (staticDancerManager.deleteChoreography(args[2])) {
                    sender.sendMessage("§aGroup §f'" + args[2] + "'§a deleted. Dancers are now in individual mode.");
                } else {
                    sender.sendMessage("§cGroup '§f" + args[2] + "§c' not found.");
                }
            }
            case "list" -> {
                Map<String, Set<String>> groups = staticDancerManager.getChoreographyGroups();
                if (groups.isEmpty()) {
                    sender.sendMessage("§eNo active choreography groups.");
                } else {
                    sender.sendMessage("§e=== Choreography groups ===");
                    groups.forEach((gId, members) ->
                            sender.sendMessage("§f" + gId + " §7(" + members.size() + ") §8→ §7" + String.join(", ", members)));
                }
            }
            default -> sender.sendMessage("§cUnknown subcommand. Use: create, add, remove, sync, delete, list");
        }
        return true;
    }
}
