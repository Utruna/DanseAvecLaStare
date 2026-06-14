package me.utruna.danse.commands;

import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.PlaylistManager;
import me.utruna.danse.managers.SkinCacheManager;
import me.utruna.danse.managers.StaticDancerManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DanseTabCompleter implements TabCompleter {

    private final DanceManager danceManager;
    private final StaticDancerManager staticDancerManager;
    private final PlaylistManager playlistManager;
    private final SkinCacheManager skinCacheManager;

    public DanseTabCompleter(DanceManager danceManager, StaticDancerManager staticDancerManager,
                             PlaylistManager playlistManager, SkinCacheManager skinCacheManager) {
        this.danceManager = danceManager;
        this.staticDancerManager = staticDancerManager;
        this.playlistManager = playlistManager;
        this.skinCacheManager = skinCacheManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> base = new ArrayList<>(danceManager.getStyleNames());
            base.add("npc");
            base.add("skin");
            base.add("list");
            base.add("stop");
            base.add("choreo");
            base.add("playlist");
            base.add("staff");
            base.add("debug");
            base.add("preview");
            base.add("reload");
            return base.stream()
                    .filter(s -> s.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        // /danse preview <style>
        if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            String partial = args[1].toLowerCase();
            return danceManager.getStyleNames().stream()
                    .filter(s -> s.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        // /danse skin <subcommand>
        if (args.length == 2 && args[0].equalsIgnoreCase("skin")) {
            String partial = args[1].toLowerCase();
            return List.of("save", "apply", "list", "remove").stream()
                    .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        // /danse skin save <alias> <pseudo>  →  arg 3 = alias (libre), arg 4 = joueur en ligne
        if (args.length == 4 && args[0].equalsIgnoreCase("skin") && args[1].equalsIgnoreCase("save")) {
            String partial = args[3].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        }
        // /danse skin apply <alias> <npcId>
        if (args.length == 3 && args[0].equalsIgnoreCase("skin")
                && (args[1].equalsIgnoreCase("apply") || args[1].equalsIgnoreCase("remove"))) {
            String partial = args[2].toLowerCase();
            return skinCacheManager == null ? List.of() : skinCacheManager.getAliases().stream()
                    .filter(a -> a.startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("skin") && args[1].equalsIgnoreCase("apply")) {
            String partial = args[3].toLowerCase();
            return staticDancerManager.getDancerIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        }
        // /danse npc <subcommand>
        if (args.length == 2 && args[0].equalsIgnoreCase("npc")) {
            String partial = args[1].toLowerCase();
            return List.of("spawn", "move", "delete", "list", "highlight", "resize", "style", "skin", "reloadskins").stream()
                    .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        // /danse npc <move|delete|highlight|resize|style|skin> <id>
        if (args.length == 3 && args[0].equalsIgnoreCase("npc")) {
            String sub = args[1].toLowerCase();
            if (List.of("move", "delete", "highlight", "resize", "style", "skin").contains(sub)) {
                String partial = args[2].toLowerCase();
                return staticDancerManager.getDancerIds().stream()
                        .filter(id -> id.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
            // /danse npc reloadskins <pseudo> → joueurs en ligne
            if (sub.equals("reloadskins")) {
                String partial = args[2].toLowerCase();
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(partial)).collect(Collectors.toList());
            }
        }
        // /danse npc skin <id> <alias>
        if (args.length == 4 && args[0].equalsIgnoreCase("npc") && args[1].equalsIgnoreCase("skin")) {
            String partial = args[3].toLowerCase();
            return skinCacheManager == null ? List.of() : skinCacheManager.getAliases().stream()
                    .filter(a -> a.startsWith(partial)).collect(Collectors.toList());
        }
        // /danse npc spawn <id> <style>   et   /danse npc style <id> <style>
        if (args.length == 4 && args[0].equalsIgnoreCase("npc")
                && (args[1].equalsIgnoreCase("spawn") || args[1].equalsIgnoreCase("style"))) {
            String partial = args[3].toLowerCase();
            return danceManager.getStyleNames().stream()
                    .filter(s -> s.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        // /danse npc spawn <id> <style> <pseudo>
        if (args.length == 5 && args[0].equalsIgnoreCase("npc")
                && args[1].equalsIgnoreCase("spawn")) {
            String partial = args[4].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("choreo")) {
            String partial = args[1].toLowerCase();
            return List.of("create", "add", "remove", "sync", "delete", "list").stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }
        // /danse choreo <create|add|remove|sync|delete> <groupId>
        if (args.length == 3 && args[0].equalsIgnoreCase("choreo")) {
            String sub = args[1].toLowerCase();
            if (List.of("add", "remove", "sync", "delete").contains(sub)) {
                String partial = args[2].toLowerCase();
                return staticDancerManager.getChoreographyGroupIds().stream()
                        .filter(id -> id.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
        }
        // /danse choreo create <groupId> <id1> [id2...] → suggestion des IDs de danseurs
        if (args.length >= 4 && args[0].equalsIgnoreCase("choreo")
                && args[1].equalsIgnoreCase("create")) {
            String partial = args[args.length - 1].toLowerCase();
            return staticDancerManager.getDancerIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        // /danse choreo add <groupId> <dancerId>
        if (args.length == 4 && args[0].equalsIgnoreCase("choreo")
                && args[1].equalsIgnoreCase("add")) {
            String partial = args[3].toLowerCase();
            return staticDancerManager.getDancerIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        // /danse choreo remove <groupId> <dancerId>
        if (args.length == 4 && args[0].equalsIgnoreCase("choreo")
                && args[1].equalsIgnoreCase("remove")) {
            String groupId = args[2];
            String partial = args[3].toLowerCase();
            return staticDancerManager.getChoreographyGroups().getOrDefault(groupId, java.util.Set.of())
                    .stream()
                    .filter(id -> id.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        // /danse playlist <subcommand>
        if (args.length == 2 && args[0].equalsIgnoreCase("playlist")) {
            String partial = args[1].toLowerCase();
            return List.of("create", "add", "remove", "delete", "info", "list", "set", "stop", "active", "debug").stream()
                    .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        // /danse playlist <create|delete|info|stop> <playlistId>
        if (args.length == 3 && args[0].equalsIgnoreCase("playlist")) {
            String sub = args[1].toLowerCase();
            String partial = args[2].toLowerCase();
            if (List.of("add", "remove", "delete", "info", "set").contains(sub)) {
                return playlistManager.getPlaylistIds().stream()
                        .filter(id -> id.toLowerCase().startsWith(partial))
                        .collect(Collectors.toList());
            }
            if (sub.equals("stop")) {
                return List.of("player", "dancer", "group").stream()
                        .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
            }
        }
        // /danse playlist add <playlistId> <style> <durationTicks>
        if (args.length == 4 && args[0].equalsIgnoreCase("playlist")
                && args[1].equalsIgnoreCase("add")) {
            String partial = args[3].toLowerCase();
            return danceManager.getStyleNames().stream()
                    .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        // /danse playlist set <playlistId> <player|dancer|group>
        if (args.length == 4 && args[0].equalsIgnoreCase("playlist")
                && args[1].equalsIgnoreCase("set")) {
            String partial = args[3].toLowerCase();
            return List.of("player", "dancer", "group").stream()
                    .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
        }
        // /danse playlist set <playlistId> player <playerName>
        if (args.length == 5 && args[0].equalsIgnoreCase("playlist")
                && args[1].equalsIgnoreCase("set") && args[3].equalsIgnoreCase("player")) {
            String partial = args[4].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        }
        // /danse playlist set <playlistId> dancer <dancerId>
        if (args.length == 5 && args[0].equalsIgnoreCase("playlist")
                && args[1].equalsIgnoreCase("set") && args[3].equalsIgnoreCase("dancer")) {
            String partial = args[4].toLowerCase();
            return staticDancerManager.getDancerIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        }
        // /danse playlist set <playlistId> group <groupId>
        if (args.length == 5 && args[0].equalsIgnoreCase("playlist")
                && args[1].equalsIgnoreCase("set") && args[3].equalsIgnoreCase("group")) {
            String partial = args[4].toLowerCase();
            return staticDancerManager.getChoreographyGroupIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(partial)).collect(Collectors.toList());
        }
        // /danse playlist stop <player|dancer|group> <target>
        if (args.length == 4 && args[0].equalsIgnoreCase("playlist")
                && args[1].equalsIgnoreCase("stop")) {
            String type = args[2].toLowerCase();
            String partial = args[3].toLowerCase();
            if (type.equals("player")) {
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(partial)).collect(Collectors.toList());
            }
            if (type.equals("dancer")) {
                return staticDancerManager.getDancerIds().stream()
                        .filter(id -> id.toLowerCase().startsWith(partial)).collect(Collectors.toList());
            }
            if (type.equals("group")) {
                return staticDancerManager.getChoreographyGroupIds().stream()
                        .filter(id -> id.toLowerCase().startsWith(partial)).collect(Collectors.toList());
            }
        }
        return List.of();
    }
}
