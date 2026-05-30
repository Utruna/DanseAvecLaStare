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
                    sender.sendMessage("§aGroupe §f'" + groupId + "'§a créé avec §f" + ids.size()
                            + "§a danseur(s). Animations synchronisées !");
                } else {
                    sender.sendMessage("§cErreur : un ou plusieurs IDs sont introuvables. IDs valides : §f"
                            + String.join(", ", staticDancerManager.getDancerIds()));
                }
            }
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse choreo add <groupId> <id>");
                    return true;
                }
                if (staticDancerManager.addToChoreography(args[2], args[3])) {
                    sender.sendMessage("§a'§f" + args[3] + "§a' ajouté au groupe §f'" + args[2]
                            + "'§a. Re-synchronisation effectuée.");
                } else {
                    sender.sendMessage("§cDanseur '§f" + args[3] + "§c' introuvable.");
                }
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse choreo remove <groupId> <id>");
                    return true;
                }
                if (staticDancerManager.removeFromChoreography(args[2], args[3])) {
                    sender.sendMessage("§a'§f" + args[3] + "§a' retiré du groupe §f'" + args[2] + "'§a.");
                } else {
                    sender.sendMessage("§cDanseur ou groupe introuvable.");
                }
            }
            case "sync" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: §f/danse choreo sync <groupId>");
                    return true;
                }
                if (staticDancerManager.syncChoreography(args[2])) {
                    sender.sendMessage("§aGroupe §f'" + args[2] + "'§a re-synchronisé !");
                } else {
                    sender.sendMessage("§cGroupe '§f" + args[2] + "§c' introuvable.");
                }
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: §f/danse choreo delete <groupId>");
                    return true;
                }
                if (staticDancerManager.deleteChoreography(args[2])) {
                    sender.sendMessage("§aGroupe §f'" + args[2] + "'§a supprimé. Danseurs en mode individuel.");
                } else {
                    sender.sendMessage("§cGroupe '§f" + args[2] + "§c' introuvable.");
                }
            }
            case "list" -> {
                Map<String, Set<String>> groups = staticDancerManager.getChoreographyGroups();
                if (groups.isEmpty()) {
                    sender.sendMessage("§eAucun groupe de chorégraphie actif.");
                } else {
                    sender.sendMessage("§e=== Groupes de chorégraphie ===");
                    groups.forEach((gId, members) ->
                            sender.sendMessage("§f" + gId + " §7(" + members.size() + ") §8→ §7" + String.join(", ", members)));
                }
            }
            default -> sender.sendMessage("§cSous-commande inconnue. Utilisez: create, add, remove, sync, delete, list");
        }
        return true;
    }
}
