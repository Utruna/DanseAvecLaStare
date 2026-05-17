package me.utruna.danse;

import me.utruna.danse.listeners.PlayerListener;
import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.DanceStyle;
import me.utruna.danse.managers.PlaylistManager;
import me.utruna.danse.managers.SkinService;
import me.utruna.danse.managers.StaticDancerManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.profile.PlayerProfile;
import org.jetbrains.annotations.NotNull;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.UUID;

/**
 * Plugin principal DanseAvecLaStare.
 * Initialise le {@link DanceManager} et le {@link StaticDancerManager}, enregistre les listeners,
 * gère les commandes {@code /danse} et assure la migration automatique du {@code config.yml}.
 */
public class DanseAvecLaStare extends JavaPlugin {

    private DanceManager danceManager;
    private StaticDancerManager staticDancerManager;
    private PlaylistManager playlistManager;
    private final Set<UUID> debugPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfigIfNeeded();
        reloadConfig();

        danceManager = new DanceManager(this);
        staticDancerManager = new StaticDancerManager(this);
        playlistManager = new PlaylistManager(this, danceManager, staticDancerManager);
        staticDancerManager.setPlaylistManager(playlistManager);
        playlistManager.loadFromFile();

        getLogger().info("Option useModelEngine=" + getConfig().getBoolean("useModelEngine", false));

        if (getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
            checkModelEngineBlueprints();
            // Délai de 60 ticks (3s) : ModelEngine charge ses blueprints en async après onEnable.
            // Sans délai, createActiveModel() renvoie null et les danseurs ne réapparaissent pas.
            // +40 ticks supplémentaires pour laisser le temps aux fetchs de skin async avant de
            // charger les groupes de chorégraphie (qui nécessitent que les danseurs soient actifs).
            Bukkit.getScheduler().runTaskLater(this, () -> {
                staticDancerManager.loadFromFile();
                Bukkit.getScheduler().runTaskLater(this, staticDancerManager::loadChoreographyFromFile, 40L);
            }, 60L);
        }

        getServer().getPluginManager().registerEvents(new PlayerListener(danceManager, playlistManager, staticDancerManager, this), this);

        if (getCommand("danse") != null) {
            getCommand("danse").setExecutor(this);
            getCommand("danse").setTabCompleter((sender, command, alias, args) -> {
                if (args.length == 1) {
                    String partial = args[0].toLowerCase();
                    List<String> base = new ArrayList<>(danceManager.getStyleNames());
                    base.add("here");
                    base.add("move");
                    base.add("listID");
                    base.add("list");
                    base.add("delete");
                    base.add("stop");
                    base.add("choreo");
                    base.add("playlist");
                    base.add("debug");
                    return base.stream()
                            .filter(s -> s.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("move"))) {
                    String partial = args[1].toLowerCase();
                    return staticDancerManager.getDancerIds().stream()
                            .filter(id -> id.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
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
                    // "create" → pas de suggestion de groupId (libre)
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
                    Map<String, Set<String>> groups = staticDancerManager.getChoreographyGroups();
                    Set<String> members = groups.getOrDefault(groupId, Set.of());
                    return members.stream()
                            .filter(id -> id.toLowerCase().startsWith(partial))
                            .collect(Collectors.toList());
                }
                // /danse playlist <subcommand>
                if (args.length == 2 && args[0].equalsIgnoreCase("playlist")) {
                    String partial = args[1].toLowerCase();
                    return List.of("create", "add", "remove", "delete", "info", "list", "play", "stop", "active", "debug").stream()
                            .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
                }
                // /danse playlist <create|delete|info|stop> <playlistId>
                if (args.length == 3 && args[0].equalsIgnoreCase("playlist")) {
                    String sub = args[1].toLowerCase();
                    String partial = args[2].toLowerCase();
                    if (List.of("add", "remove", "delete", "info", "play").contains(sub)) {
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
                // /danse playlist play <playlistId> <player|dancer|group>
                if (args.length == 4 && args[0].equalsIgnoreCase("playlist")
                        && args[1].equalsIgnoreCase("play")) {
                    String partial = args[3].toLowerCase();
                    return List.of("player", "dancer", "group").stream()
                            .filter(s -> s.startsWith(partial)).collect(Collectors.toList());
                }
                // /danse playlist play <playlistId> player <playerName>
                if (args.length == 5 && args[0].equalsIgnoreCase("playlist")
                        && args[1].equalsIgnoreCase("play") && args[3].equalsIgnoreCase("player")) {
                    String partial = args[4].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(partial)).collect(Collectors.toList());
                }
                // /danse playlist play <playlistId> dancer <dancerId>
                if (args.length == 5 && args[0].equalsIgnoreCase("playlist")
                        && args[1].equalsIgnoreCase("play") && args[3].equalsIgnoreCase("dancer")) {
                    String partial = args[4].toLowerCase();
                    return staticDancerManager.getDancerIds().stream()
                            .filter(id -> id.toLowerCase().startsWith(partial)).collect(Collectors.toList());
                }
                // /danse playlist play <playlistId> group <groupId>
                if (args.length == 5 && args[0].equalsIgnoreCase("playlist")
                        && args[1].equalsIgnoreCase("play") && args[3].equalsIgnoreCase("group")) {
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
                if (args.length == 3 && args[0].equalsIgnoreCase("here")) {
                    String partial = args[2].toLowerCase();
                    return danceManager.getStyleNames().stream()
                            .filter(s -> s.startsWith(partial))
                            .collect(Collectors.toList());
                }
                if (args.length == 4 && args[0].equalsIgnoreCase("here")) {
                    String partial = args[3].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(partial))
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
        if (playlistManager != null) {
            playlistManager.stopAll();
        }
        if (staticDancerManager != null) {
            staticDancerManager.removeAll();
        }
        if (danceManager != null) {
            danceManager.stopAll();
        }
        getLogger().info("Arrêt du plugin de danse.");
    }

    /** Vérifie que les fichiers .bbmodel configurés existent dans le dossier blueprints de ModelEngine. */
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

        // --- Commandes accessibles depuis la console et les joueurs ---

        if (args.length > 0 && args[0].equalsIgnoreCase("listID")) {
            Set<String> ids = staticDancerManager.getDancerIds();
            if (ids.isEmpty()) {
                sender.sendMessage("§eAucun danseur statique actif.");
            } else {
                sender.sendMessage("§eDanseurs statiques actifs: §f" + String.join(", ", new java.util.TreeSet<>(ids)));
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("delete")) {
            if (args.length < 2) {
                sender.sendMessage("§cUsage: /danse delete <id>");
                return true;
            }
            String id = args[1];
            if (staticDancerManager.removeStaticDancer(id)) {
                sender.sendMessage("§aDanseur '" + id + "' supprimé.");
            } else {
                sender.sendMessage("§cAucun danseur avec l'ID: §f" + id);
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("choreo")) {
            return handleChoreoCommand(sender, args);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("playlist")) {
            return handlePlaylistCommand(sender, args);
        }

        // --- Commandes joueur uniquement ---

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Seul un joueur peut utiliser cette commande.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("here")) {
            if (args.length < 3) {
                player.sendMessage("§cUsage: §f/danse here <id> <style> [pseudo]");
                return true;
            }
            String id = args[1];
            String styleName = args[2].toLowerCase();
            String skinTarget = args.length >= 4 ? args[3] : null;

            if (staticDancerManager.getDancerIds().contains(id)) {
                player.sendMessage("§cL'ID '§f" + id + "§c' est déjà utilisé par un danseur statique.");
                return true;
            }
            if (!getServer().getPluginManager().isPluginEnabled("ModelEngine")) {
                player.sendMessage("§cModelEngine n'est pas disponible.");
                return true;
            }

            Location loc = player.getLocation();

            if (skinTarget != null && !skinTarget.isBlank()) {
                // Skin d'un autre joueur via Mojang — récupération async puis spawn sur le thread principal
                player.sendMessage("§7Récupération du skin de §f" + skinTarget + "§7...");
                SkinService.fetchSkin(this, skinTarget, (profile) -> {
                    if (profile == null) {
                        player.sendMessage("§cJoueur introuvable ou erreur Mojang : §f" + skinTarget);
                        return;
                    }
                    Bukkit.getScheduler().runTask(this, () -> {
                        boolean spawned = staticDancerManager.spawnStaticDancer(id, loc, styleName, profile, skinTarget);
                        if (spawned) {
                            player.sendMessage("§aDanseur '§f" + id + "§a' apparu avec le skin de §f" + skinTarget + "§a.");
                        } else {
                            player.sendMessage("§cÉchec du spawn. Vérifie le style '§f" + styleName + "§c'.");
                        }
                    });
                });
            } else {
                // Skin du joueur qui lance la commande — on sauvegarde son pseudo pour la restauration
                @SuppressWarnings("deprecation")
                PlayerProfile profile = player.getPlayerProfile();
                boolean spawned = staticDancerManager.spawnStaticDancer(id, loc, styleName, profile, player.getName());
                if (spawned) {
                    player.sendMessage("§aDanseur '§f" + id + "§a' apparu avec le style '§f" + styleName + "§a'.");
                } else {
                    player.sendMessage("§cÉchec du spawn. Vérifie le style '§f" + styleName + "§c'.");
                }
            }
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("move")) {
            if (args.length < 2) {
                player.sendMessage("§cUsage: §f/danse move <id>");
                return true;
            }
            String id = args[1];
            if (staticDancerManager.moveStaticDancer(id, player.getLocation())) {
                player.sendMessage("§aDanseur '§f" + id + "§a' déplacé à ta position.");
            } else {
                player.sendMessage("§cAucun danseur avec l'ID: §f" + id);
            }
            return true;
        }

        try {
            if (args.length == 0) {
                if (danceManager.isDancing(player.getUniqueId())) {
                    danceManager.stopDance(player.getUniqueId());
                    player.sendMessage("§aTu arrêtes de danser.");
                } else {
                    DanceStyle twist = danceManager.parseStyle("twist");
                    danceManager.startDance(player, twist, true, null);
                    player.sendMessage("§aTu commences à danser: §ftwist");
                }
                return true;
            }

            if (args[0].equalsIgnoreCase("stop")) {
                playlistManager.stopForPlayer(player.getUniqueId());
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
                player.sendMessage("§cStyle inconnu. Utilisez §f/danse list§c pour la liste.");
                return true;
            }

            boolean hide = true;
            String target = null;

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

    /** Affiche l'état courant du plugin et le mode debug du sender dans le chat. */
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

    /** Retourne {@code true} si le mode debug est actif pour le joueur donné. */
    public boolean isPlayerDebug(UUID id) {
        return debugPlayers.contains(id);
    }

    /** Bascule le mode debug pour le joueur donné et retourne le nouvel état. */
    public boolean togglePlayerDebug(UUID id) {
        if (debugPlayers.contains(id)) {
            debugPlayers.remove(id);
            return false;
        } else {
            debugPlayers.add(id);
            return true;
        }
    }

    /** Gère les sous-commandes /danse playlist. */
    private boolean handlePlaylistCommand(CommandSender sender, String[] args) {
        // args[0] = "playlist"
        if (args.length < 2) {
            sender.sendMessage("§eUsage: §f/danse playlist <create|add|remove|delete|info|list|play|stop|active>");
            return true;
        }

        switch (args[1].toLowerCase()) {

            // --- Gestion des playlists ---
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

            // --- Lecture ---
            case "play" -> {
                // /danse playlist play <playlistId> player [playerName]
                // /danse playlist play <playlistId> dancer <dancerId>
                // /danse playlist play <playlistId> group  <groupId>
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: §f/danse playlist play <id> <player|dancer|group> [cible]");
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
                        if (args.length < 5) { sender.sendMessage("§cUsage: §f/danse playlist play <id> dancer <dancerId>"); return true; }
                        if (playlistManager.playForDancer(args[4], playlistId)) {
                            sender.sendMessage("§aPlaylist §f'" + playlistId + "'§a lancée sur le danseur §f'" + args[4] + "'§a.");
                        } else {
                            sender.sendMessage("§cPlaylist ou danseur introuvable.");
                        }
                    }
                    case "group" -> {
                        if (args.length < 5) { sender.sendMessage("§cUsage: §f/danse playlist play <id> group <groupId>"); return true; }
                        if (playlistManager.playForGroup(args[4], playlistId)) {
                            sender.sendMessage("§aPlaylist §f'" + playlistId + "'§a lancée sur le groupe §f'" + args[4] + "'§a.");
                        } else {
                            sender.sendMessage("§cPlaylist ou groupe introuvable.");
                        }
                    }
                    default -> sender.sendMessage("§cType de cible invalide. Utilisez: player, dancer, group");
                }
            }

            // --- Arrêt ---
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

            // --- Statut ---
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

            // --- Debug ---
            case "debug" -> {
                boolean now = !playlistManager.isDebugEnabled();
                playlistManager.setDebugEnabled(now);
                sender.sendMessage("§ePlaylist debug " + (now ? "§aactivé" : "§cdésactivé") + "§e.");
            }

            default -> sender.sendMessage("§cSous-commande inconnue. Utilisez: create, add, remove, delete, info, list, play, stop, active, debug");
        }
        return true;
    }

    /** Gère les sous-commandes /danse choreo. */
    private boolean handleChoreoCommand(CommandSender sender, String[] args) {
        // args[0] = "choreo"
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

    /** Collecte tous les modelId référencés dans la config (principal, fallback, styleModels). */
    private Set<String> resolveConfiguredModelIds() {
        Set<String> modelIds = new HashSet<>();
        String defaultModelId = getConfig().getString("modelEngine.defaultModelId", "danseur");
        modelIds.add(defaultModelId);

        if (getConfig().getBoolean("modelEngine.useFallbackMode", false)) {
            String fallbackModelId = getConfig().getString("modelEngine.fallbackModelId", "joueur_fallback");
            if (fallbackModelId != null && !fallbackModelId.isBlank()) {
                modelIds.add(fallbackModelId.trim());
            }
        }

        ConfigurationSection styleModels = getConfig().getConfigurationSection("modelEngine.styleModels");
        if (styleModels != null) {
            for (String key : styleModels.getKeys(false)) {
                modelIds.add(styleModels.getString(key));
            }
        }
        return modelIds;
    }
}