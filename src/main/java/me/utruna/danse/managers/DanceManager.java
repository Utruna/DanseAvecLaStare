package me.utruna.danse.managers;

import me.utruna.danse.DanseAvecLaStare;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import me.utruna.danse.managers.SkinService;
import org.bukkit.profile.PlayerProfile;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.io.StringWriter;
import java.io.PrintWriter;

public class DanceManager {

    private final DanseAvecLaStare plugin;

    private static class RunningDance {
        BukkitTask task;
        Dancer dancer;
        boolean previousInvisible;
    }

    private final Map<UUID, RunningDance> runningDances = new ConcurrentHashMap<>();

    private static final Map<String, DanceStyle> STYLES = new HashMap<>();
    static {
        STYLES.put("twist", new TwistStyle());
        STYLES.put("spin", new SpinStyle());
        STYLES.put("disco", new DiscoStyle());
        STYLES.put("moonwalk", new MoonwalkStyle());
        STYLES.put("wave", new WaveStyle());
    }

    public DanceManager(DanseAvecLaStare plugin) {
        this.plugin = plugin;
    }

    public boolean isDancing(UUID uuid) {
        return runningDances.containsKey(uuid);
    }

    public String getStylesLabel() {
        return STYLES.keySet().stream().sorted().collect(Collectors.joining(", "));
    }

    public List<String> getStyleNames() {
        List<String> list = new ArrayList<>(STYLES.keySet());
        list.sort(String::compareTo);
        return list;
    }

    public DanceStyle parseStyle(String value) {
        if (value == null) return null;
        return STYLES.get(value.toLowerCase(Locale.ROOT));
    }

    public void startDance(Player player, DanceStyle style) {
        startDance(player, style, true, null);
    }
    public void startDance(Player player, DanceStyle style, boolean hideFromOwner, String targetName) {
        stopDance(player.getUniqueId());

        if (targetName != null && !targetName.isBlank()) {
            SkinService.fetchSkin(plugin, targetName.trim(), (profile) -> {
                if (profile == null) {
                    player.sendMessage("§cJoueur introuvable ou erreur API.");
                    return;
                }
                ModelEngineDancer dancer = new ModelEngineDancer(plugin, resolveModelIdForStyle(style), profile);
                finishDanceSetup(player, dancer, style, hideFromOwner);
            });
            return;
        }

        final UUID uuid = player.getUniqueId();
        final Location origin = player.getLocation().clone();

        boolean useModelEngine = Bukkit.getPluginManager().isPluginEnabled("ModelEngine") 
            && plugin.getConfig().getBoolean("useModelEngine", false);

        Dancer dancer;
        if (useModelEngine) {
            String modelId = resolveModelIdForStyle(style);
            dancer = new ModelEngineDancer(modelId);
        } else {
            // Logique normale pour le joueur lui-même
            ModelEngineDancer dancer = new ModelEngineDancer(plugin, resolveModelIdForStyle(style), null); 
            // NOTE: Si skinProfile est null dans ModelEngineDancer, 
            // modifie le constructeur pour accepter null et faire new Dummy<>(player)
            finishDanceSetup(player, dancer, style, hideFromOwner);
        }
    }
    /**
     * Helper pour démarrer une danse avec un Dancer déjà configuré
     */
    private void startDanceWithDancer(UUID uuid, Player player, Location origin, Dancer dancer, DanceStyle style, boolean hideFromOwner) {
        try {
            dancer.spawn(origin, player);

            RunningDance running = new RunningDance();
            running.dancer = dancer;
            running.previousInvisible = player.isInvisible();

            player.setInvisible(true);

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                private int tick = 0;

                @Override
                public void run() {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null || !online.isOnline() || online.isDead()) {
                        stopDance(uuid);
                        return;
                    }

                    dancer.tick(tick, style);
                    tick++;
                }
            }, 0L, 1L);

            running.task = task;
            runningDances.put(uuid, running);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création de la danse", ex);
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            plugin.getLogger().severe(sw.toString());
            try {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.setInvisible(false);
            } catch (Exception ignore) {}
        }
    }
    /**
     * Récupère la paire [value, signature] pour le skin d'un pseudo Mojang.
     * Tente d'abord d'utiliser une API Paper via reflection, puis tombe en fallback HTTP.
     */
    private String[] fetchSkinData(String username) {
        try {
            // Attempt to use Mojang APIs via HTTP (reliable)
            // 1) Get UUID
            String uuidUrl = "https://api.mojang.com/users/profiles/minecraft/" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new java.net.URL(uuidUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            int code = conn.getResponseCode();
            if (code != 200) return null;
            java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line).append('\n');
            in.close();
            String body = sb.toString();
            // extract id
            java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("\"id\"\s*:\s*\"([0-9a-fA-F]+)\"");
            java.util.regex.Matcher idMatcher = idPattern.matcher(body);
            if (!idMatcher.find()) return null;
            String uuid = idMatcher.group(1);

            // 2) Query session server
            String profileUrl = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid + "?unsigned=false";
            conn = (java.net.HttpURLConnection) new java.net.URL(profileUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            code = conn.getResponseCode();
            if (code != 200) return null;
            in = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            sb = new StringBuilder();
            while ((line = in.readLine()) != null) sb.append(line).append('\n');
            in.close();
            body = sb.toString();

            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"name\"\s*:\s*\"textures\".*?\"value\"\s*:\s*\"([^\"]+)\".*?\"signature\"\s*:\s*\"([^\"]+)\"", java.util.regex.Pattern.DOTALL);
            java.util.regex.Matcher m = p.matcher(body);
            if (m.find()) {
                String value = m.group(1);
                String signature = m.group(2);
                return new String[]{value, signature};
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur fetchSkinData: " + e.getMessage());
        }
        return null;
    }

    public void stopDance(UUID uuid) {
        RunningDance running = runningDances.remove(uuid);
        if (running != null) {
            if (running.task != null) running.task.cancel();
            if (running.dancer != null) {
                running.dancer.stop();
            }

            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setInvisible(running.previousInvisible);
            }
        }
    }

    public void stopAll() {
        for (UUID uuid : runningDances.keySet()) {
            stopDance(uuid);
        }
    }

    private String resolveModelIdForStyle(DanceStyle style) {
        String styleName = style.getName().toLowerCase(Locale.ROOT);

        String byStyle = plugin.getConfig().getString("modelEngine.styleModels." + styleName);
        if (byStyle != null && !byStyle.isBlank()) {
            return byStyle.trim();
        }

        String defaultModel = plugin.getConfig().getString("modelEngine.defaultModelId");
        if (defaultModel != null && !defaultModel.isBlank()) {
            return defaultModel.trim();
        }

        // Backward compatibility for old config key.
        String legacy = plugin.getConfig().getString("modelEngine.modelId");
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }

        return "danseur";
    }


    private void finishDanceSetup(Player player, Dancer dancer, DanceStyle style, boolean hideFromOwner) {
        final UUID uuid = player.getUniqueId();
        final Location origin = player.getLocation().clone();

        try {
            dancer.spawn(origin, player);

            RunningDance running = new RunningDance();
            running.dancer = dancer;
            running.previousInvisible = player.isInvisible();

            player.setInvisible(true);

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                private int tick = 0;

                @Override
                public void run() {
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null || !online.isOnline() || online.isDead()) {
                        stopDance(uuid);
                        return;
                    }
                    dancer.tick(tick, style);
                    tick++;
                }
            }, 0L, 1L);

            running.task = task;
            runningDances.put(uuid, running);

        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du spawn du danseur", ex);
            try {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.setInvisible(false);
            } catch (Exception ignore) {}
        }
    }
    // Styles are implemented in dedicated classes under the same package.
}