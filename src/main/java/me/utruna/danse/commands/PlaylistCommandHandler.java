package me.utruna.danse.commands;

import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.PlaylistManager;
import me.utruna.danse.managers.StaticDancerManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class PlaylistCommandHandler {

    private final DanceManager danceManager;
    private final PlaylistManager playlistManager;
    private final StaticDancerManager staticDancerManager;

    public PlaylistCommandHandler(DanceManager danceManager, PlaylistManager playlistManager, StaticDancerManager staticDancerManager) {
        this.danceManager = danceManager;
        this.playlistManager = playlistManager;
        this.staticDancerManager = staticDancerManager;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §f/danse playlist <create|add|remove|delete|info|list|set|stop|active>");
            return true;
        }

        switch (args[1].toLowerCase()) {

            case "create" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: §f/danse playlist create <id> [loop|once]");
                    return true;
                }
                boolean loop = args.length < 4 || !args[3].equalsIgnoreCase("once");
                if (playlistManager.createPlaylist(args[2], loop)) {
                    sender.sendMessage("§aPlaylist §f'" + args[2] + "'§a créée ("
                            + (loop ? "en boucle" : "une fois") + ").");
                } else {
                    sender.sendMessage("§cUne playlist avec l'ID §f'" + args[2] + "'§c existe déjà.");
                }
            }
            case "add" -> {
                if (args.length < 5) {
                    sender.sendMessage("§cUsage: §f/danse playlist add <id> <style> <répétitions>");
                    return true;
                }
                int reps;
                try {
                    reps = Integer.parseInt(args[4]);
                    if (reps < 1) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cNombre de répétitions invalide (entier ≥ 1). Ex: §f3");
                    return true;
                }
                if (playlistManager.addTrack(args[2], args[3], reps)) {
                    sender.sendMessage("§aPiste §f'" + args[3] + "'§a ajoutée à §f'" + args[2]
                            + "' §7(×" + reps + " répétition(s))§a.");
                } else {
                    sender.sendMessage("§cPlaylist ou style introuvable. Styles valides: §f"
                            + String.join(", ", danceManager.getStyleNames()));
                }
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse playlist remove <id> <index>");
                    return true;
                }
                int index;
                try { index = Integer.parseInt(args[3]); } catch (NumberFormatException e) {
                    sender.sendMessage("§cIndex invalide (commence à 0).");
                    return true;
                }
                if (playlistManager.removeTrack(args[2], index)) {
                    sender.sendMessage("§aPiste §f#" + index + "§a supprimée de §f'" + args[2] + "'§a.");
                } else {
                    sender.sendMessage("§cPlaylist ou index introuvable.");
                }
            }
            case "delete" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: §f/danse playlist delete <id>");
                    return true;
                }
                if (playlistManager.deletePlaylist(args[2])) {
                    sender.sendMessage("§aPlaylist §f'" + args[2] + "'§a supprimée.");
                } else {
                    sender.sendMessage("§cPlaylist §f'" + args[2] + "'§c introuvable.");
                }
            }
            case "info" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: §f/danse playlist info <id>");
                    return true;
                }
                PlaylistManager.Playlist p = playlistManager.getPlaylists().get(args[2]);
                if (p == null) { sender.sendMessage("§cPlaylist introuvable."); return true; }
                sender.sendMessage("§e=== Playlist: §f" + p.id + " §7(" + (p.loop ? "boucle" : "une fois") + ") ===");
                if (p.tracks.isEmpty()) { sender.sendMessage("§7(aucune piste)"); return true; }
                for (int i = 0; i < p.tracks.size(); i++) {
                    PlaylistManager.Track t = p.tracks.get(i);
                    sender.sendMessage("§f#" + i + " §7→ §f" + t.styleName()
                            + " §8[×" + t.repetitions() + " rép.]");
                }
            }
            case "list" -> {
                Map<String, PlaylistManager.Playlist> all = playlistManager.getPlaylists();
                if (all.isEmpty()) { sender.sendMessage("§eAucune playlist définie."); return true; }
                sender.sendMessage("§e=== Playlists ===");
                all.forEach((id, p) -> sender.sendMessage(
                        "§f" + id + " §7(" + p.tracks.size() + " piste(s), "
                                + (p.loop ? "boucle" : "une fois") + ")"));
            }
            case "set" -> {
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse playlist set <id> <player|dancer|group> [cible]");
                    return true;
                }
                String playlistId = args[2];
                String type = args[3].toLowerCase();
                switch (type) {
                    case "player" -> {
                        Player target = (args.length >= 5)
                                ? Bukkit.getPlayerExact(args[4])
                                : (sender instanceof Player p ? p : null);
                        if (target == null) {
                            sender.sendMessage("§cJoueur introuvable ou non connecté.");
                            return true;
                        }
                        if (playlistManager.playForPlayer(target.getUniqueId(), playlistId)) {
                            sender.sendMessage("§aPlaylist §f'" + playlistId + "'§a lancée pour §f" + target.getName() + "§a.");
                        } else {
                            sender.sendMessage("§cPlaylist introuvable ou vide.");
                        }
                    }
                    case "dancer" -> {
                        if (args.length < 5) { sender.sendMessage("§cUsage: §f/danse playlist set <id> dancer <dancerId>"); return true; }
                        if (playlistManager.playForDancer(args[4], playlistId)) {
                            sender.sendMessage("§aPlaylist §f'" + playlistId + "'§a lancée sur le danseur §f'" + args[4] + "'§a.");
                        } else {
                            sender.sendMessage("§cPlaylist ou danseur introuvable.");
                        }
                    }
                    case "group" -> {
                        if (args.length < 5) { sender.sendMessage("§cUsage: §f/danse playlist set <id> group <groupId>"); return true; }
                        if (playlistManager.playForGroup(args[4], playlistId)) {
                            sender.sendMessage("§aPlaylist §f'" + playlistId + "'§a lancée sur le groupe §f'" + args[4] + "'§a.");
                        } else {
                            sender.sendMessage("§cPlaylist ou groupe introuvable.");
                        }
                    }
                    default -> sender.sendMessage("§cType de cible invalide. Utilisez: player, dancer, group");
                }
            }
            case "stop" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: §f/danse playlist stop <player|dancer|group> [cible]");
                    return true;
                }
                String type = args[2].toLowerCase();
                switch (type) {
                    case "player" -> {
                        Player target = (args.length >= 4)
                                ? Bukkit.getPlayerExact(args[3])
                                : (sender instanceof Player p ? p : null);
                        if (target == null) { sender.sendMessage("§cJoueur introuvable."); return true; }
                        if (playlistManager.stopForPlayer(target.getUniqueId())) {
                            sender.sendMessage("§aPlaylist arrêtée pour §f" + target.getName() + "§a.");
                        } else {
                            sender.sendMessage("§eAucune playlist active pour §f" + target.getName() + "§e.");
                        }
                    }
                    case "dancer" -> {
                        if (args.length < 4) { sender.sendMessage("§cUsage: §f/danse playlist stop dancer <dancerId>"); return true; }
                        sender.sendMessage(playlistManager.stopForDancer(args[3])
                                ? "§aPlaylist arrêtée sur §f'" + args[3] + "'§a."
                                : "§eAucune playlist active sur §f'" + args[3] + "'§e.");
                    }
                    case "group" -> {
                        if (args.length < 4) { sender.sendMessage("§cUsage: §f/danse playlist stop group <groupId>"); return true; }
                        sender.sendMessage(playlistManager.stopForGroup(args[3])
                                ? "§aPlaylist arrêtée sur le groupe §f'" + args[3] + "'§a."
                                : "§eAucune playlist active sur le groupe §f'" + args[3] + "'§e.");
                    }
                    default -> sender.sendMessage("§cType de cible invalide. Utilisez: player, dancer, group");
                }
            }
            case "active" -> {
                sender.sendMessage("§e=== Playlists actives ===");
                boolean any = false;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    String pl = playlistManager.getActivePlaylistForPlayer(p.getUniqueId());
                    if (pl != null) {
                        int idx = playlistManager.getCurrentTrackIndexForPlayer(p.getUniqueId());
                        sender.sendMessage("§fJoueur §7" + p.getName() + " §8→ §f" + pl + " §7(piste #" + idx + ")");
                        any = true;
                    }
                }
                for (String dancerId : staticDancerManager.getDancerIds()) {
                    String pl = playlistManager.getActivePlaylistForDancer(dancerId);
                    if (pl != null) {
                        sender.sendMessage("§fDanseur §7'" + dancerId + "' §8→ §f" + pl);
                        any = true;
                    }
                }
                for (String groupId : staticDancerManager.getChoreographyGroupIds()) {
                    String pl = playlistManager.getActivePlaylistForGroup(groupId);
                    if (pl != null) {
                        sender.sendMessage("§fGroupe §7'" + groupId + "' §8→ §f" + pl);
                        any = true;
                    }
                }
                if (!any) sender.sendMessage("§7Aucune playlist en cours.");
            }
            case "debug" -> {
                boolean now = !playlistManager.isDebugEnabled();
                playlistManager.setDebugEnabled(now);
                sender.sendMessage("§ePlaylist debug " + (now ? "§aactivé" : "§cdésactivé") + "§e.");
            }
            default -> sender.sendMessage("§cSous-commande inconnue. Utilisez: create, add, remove, delete, info, list, set, stop, active, debug");
        }
        return true;
    }
}
