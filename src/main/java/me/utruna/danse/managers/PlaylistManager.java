package me.utruna.danse.managers;

import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

/**
 * Gère les playlists de danses : séquences ordonnées de styles avec une durée par piste.
 *
 * <p>Une playlist peut être jouée pour :
 * <ul>
 *   <li>Un <b>joueur</b> : change son style de danse à chaque piste.</li>
 *   <li>Un <b>danseur statique</b> : change son animation à chaque piste.</li>
 *   <li>Un <b>groupe de chorégraphie</b> : change l'animation de tous les membres
 *       simultanément (synchronisation maintenue).</li>
 * </ul>
 *
 * Les playlists sont persistées dans {@code playlists.yml}.
 */
public class PlaylistManager {

    private final DanseAvecLaStare plugin;
    private final DanceManager danceManager;
    private final StaticDancerManager staticDancerManager;

    // Playlists définies (persistées)
    private final Map<String, Playlist> playlists = new LinkedHashMap<>();

    // Runners actifs par type de cible
    private final Map<UUID, PlaylistRunner>   playerRunners = new HashMap<>();
    private final Map<String, PlaylistRunner> dancerRunners = new HashMap<>();
    private final Map<String, PlaylistRunner> groupRunners  = new HashMap<>();

    // -------------------------------------------------------------------------
    // Modèles de données
    // -------------------------------------------------------------------------

    /**
     * Une piste d'une playlist : un style et le nombre de fois que l'animation doit jouer
     * avant de passer à la piste suivante.
     * La durée réelle est calculée automatiquement depuis le blueprint ModelEngine.
     */
    public record Track(String styleName, int repetitions) {}

    /** Durée par défaut d'une répétition (ticks) si le blueprint est inaccessible. */
    private static final int DEFAULT_TICKS_PER_REP = 40;

    /** Active les logs de debug pour le système de playlist. */
    private boolean debugEnabled = false;

    private void dbg(String msg) {
        if (debugEnabled) plugin.getLogger().info("[Playlist-DEBUG] " + msg);
    }

    public void setDebugEnabled(boolean enabled) {
        this.debugEnabled = enabled;
        plugin.getLogger().info("[Playlist] Debug " + (enabled ? "activé" : "désactivé") + ".");
    }

    public boolean isDebugEnabled() { return debugEnabled; }

    /** Définition d'une playlist. Immuable ; modifié par remplacement dans la map. */
    public static class Playlist {
        public final String id;
        public final List<Track> tracks;
        public final boolean loop;

        public Playlist(String id, List<Track> tracks, boolean loop) {
            this.id     = id;
            this.tracks = Collections.unmodifiableList(new ArrayList<>(tracks));
            this.loop   = loop;
        }
    }

    /** État d'exécution d'une playlist sur une cible donnée. */
    private static class PlaylistRunner {
        String    playlistId;
        int       currentIndex;
        BukkitTask task;
    }

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    public PlaylistManager(DanseAvecLaStare plugin,
                           DanceManager danceManager,
                           StaticDancerManager staticDancerManager) {
        this.plugin               = plugin;
        this.danceManager         = danceManager;
        this.staticDancerManager  = staticDancerManager;
    }

    // -------------------------------------------------------------------------
    // CRUD playlists
    // -------------------------------------------------------------------------

    /**
     * Crée une nouvelle playlist vide.
     *
     * @param loop  {@code true} = boucle infinie, {@code false} = s'arrête après la dernière piste
     * @return {@code false} si l'ID est déjà utilisé
     */
    public boolean createPlaylist(String id, boolean loop) {
        if (playlists.containsKey(id)) return false;
        playlists.put(id, new Playlist(id, new ArrayList<>(), loop));
        saveToFile();
        return true;
    }

    /**
     * Ajoute une piste en fin de playlist.
     *
     * @return {@code false} si la playlist n'existe pas ou si le style est inconnu
     */
    public boolean addTrack(String id, String styleName, int durationTicks) {
        Playlist p = playlists.get(id);
        if (p == null) return false;
        if (danceManager.parseStyle(styleName) == null) return false;

        List<Track> tracks = new ArrayList<>(p.tracks);
        tracks.add(new Track(styleName, durationTicks));
        playlists.put(id, new Playlist(id, tracks, p.loop));
        saveToFile();
        return true;
    }

    /**
     * Supprime la piste à l'index donné (0-based).
     *
     * @return {@code false} si la playlist ou l'index sont invalides
     */
    public boolean removeTrack(String id, int index) {
        Playlist p = playlists.get(id);
        if (p == null || index < 0 || index >= p.tracks.size()) return false;

        List<Track> tracks = new ArrayList<>(p.tracks);
        tracks.remove(index);
        playlists.put(id, new Playlist(id, tracks, p.loop));
        saveToFile();
        return true;
    }

    /**
     * Supprime une playlist et stoppe tous ses runners actifs.
     *
     * @return {@code false} si la playlist n'existe pas
     */
    public boolean deletePlaylist(String id) {
        if (!playlists.containsKey(id)) return false;
        cancelRunnersForPlaylist(id);
        playlists.remove(id);
        saveToFile();
        return true;
    }

    public Map<String, Playlist> getPlaylists() {
        return Collections.unmodifiableMap(playlists);
    }

    public Set<String> getPlaylistIds() {
        return Collections.unmodifiableSet(playlists.keySet());
    }

    // -------------------------------------------------------------------------
    // Play / Stop — joueur
    // -------------------------------------------------------------------------

    /**
     * Lance une playlist pour un joueur.
     *
     * @return {@code false} si la playlist est introuvable, vide, ou le joueur hors ligne
     */
    public boolean playForPlayer(UUID playerId, String playlistId) {
        Playlist p = playlists.get(playlistId);
        if (p == null || p.tracks.isEmpty()) return false;
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) return false;

        stopForPlayer(playerId);

        PlaylistRunner runner = newRunner(playlistId);
        playerRunners.put(playerId, runner);

        applyPlayerTrack(playerId, p.tracks.get(0));
        schedulePlayerNext(playerId, runner, p);
        return true;
    }

    /**
     * Stoppe la playlist en cours pour un joueur et arrête sa danse.
     *
     * @return {@code false} si aucune playlist n'était active
     */
    public boolean stopForPlayer(UUID playerId) {
        PlaylistRunner runner = playerRunners.remove(playerId);
        if (runner == null) return false;
        cancelTask(runner);
        danceManager.stopDance(playerId);
        return true;
    }

    // -------------------------------------------------------------------------
    // Play / Stop — danseur statique
    // -------------------------------------------------------------------------

    /**
     * Lance une playlist pour un danseur statique.
     *
     * @return {@code false} si la playlist ou le danseur est introuvable / vide
     */
    public boolean playForDancer(String dancerId, String playlistId) {
        Playlist p = playlists.get(playlistId);
        if (p == null || p.tracks.isEmpty()) return false;
        if (!staticDancerManager.getDancerIds().contains(dancerId)) return false;

        PlaylistRunner existing = dancerRunners.remove(dancerId);
        if (existing != null) cancelTask(existing);

        PlaylistRunner runner = newRunner(playlistId);
        dancerRunners.put(dancerId, runner);

        Track first = p.tracks.get(0);
        dbg("playForDancer(" + dancerId + ") → playlist='" + playlistId + "' piste#0='" + first.styleName() + "' ×" + first.repetitions() + " délai=" + computeDelay(first) + " ticks");
        staticDancerManager.changeAnimation(dancerId, first.styleName());
        scheduleDancerNext(dancerId, runner, p);
        return true;
    }

    /**
     * Stoppe la playlist en cours pour un danseur statique.
     *
     * @return {@code false} si aucune playlist n'était active
     */
    public boolean stopForDancer(String dancerId) {
        PlaylistRunner runner = dancerRunners.remove(dancerId);
        if (runner == null) return false;
        cancelTask(runner);
        dbg("stopForDancer(" + dancerId + ") → playlist arrêtée");
        return true;
    }

    // -------------------------------------------------------------------------
    // Play / Stop — groupe de chorégraphie
    // -------------------------------------------------------------------------

    /**
     * Lance une playlist pour un groupe de chorégraphie.
     * Les animations de tous les membres du groupe changent simultanément à chaque piste.
     *
     * @return {@code false} si la playlist ou le groupe est introuvable / vide
     */
    public boolean playForGroup(String groupId, String playlistId) {
        Playlist p = playlists.get(playlistId);
        if (p == null || p.tracks.isEmpty()) return false;
        if (!staticDancerManager.getChoreographyGroupIds().contains(groupId)) return false;

        PlaylistRunner existing = groupRunners.remove(groupId);
        if (existing != null) cancelTask(existing);

        PlaylistRunner runner = newRunner(playlistId);
        groupRunners.put(groupId, runner);

        Track first = p.tracks.get(0);
        dbg("playForGroup(" + groupId + ") → playlist='" + playlistId + "' piste#0='" + first.styleName() + "' ×" + first.repetitions() + " délai=" + computeDelay(first) + " ticks");
        staticDancerManager.changeGroupAnimation(groupId, first.styleName());
        scheduleGroupNext(groupId, runner, p);
        return true;
    }

    /**
     * Stoppe la playlist en cours pour un groupe.
     *
     * @return {@code false} si aucune playlist n'était active
     */
    public boolean stopForGroup(String groupId) {
        PlaylistRunner runner = groupRunners.remove(groupId);
        if (runner == null) return false;
        cancelTask(runner);
        dbg("stopForGroup(" + groupId + ") → playlist arrêtée");
        return true;
    }

    // -------------------------------------------------------------------------
    // Méthodes de statut
    // -------------------------------------------------------------------------

    /** Renvoie le nom de la playlist active pour un joueur, ou {@code null}. */
    public String getActivePlaylistForPlayer(UUID playerId) {
        PlaylistRunner r = playerRunners.get(playerId);
        return r == null ? null : r.playlistId;
    }

    /** Renvoie le nom de la playlist active pour un danseur, ou {@code null}. */
    public String getActivePlaylistForDancer(String dancerId) {
        PlaylistRunner r = dancerRunners.get(dancerId);
        return r == null ? null : r.playlistId;
    }

    /** Renvoie le nom de la playlist active pour un groupe, ou {@code null}. */
    public String getActivePlaylistForGroup(String groupId) {
        PlaylistRunner r = groupRunners.get(groupId);
        return r == null ? null : r.playlistId;
    }

    /** Retourne la piste en cours (0-based) pour un joueur, ou -1. */
    public int getCurrentTrackIndexForPlayer(UUID playerId) {
        PlaylistRunner r = playerRunners.get(playerId);
        return r == null ? -1 : r.currentIndex;
    }

    // -------------------------------------------------------------------------
    // Hooks de nettoyage appelés par StaticDancerManager
    // -------------------------------------------------------------------------

    /** Appelé quand un danseur statique est supprimé. */
    public void onDancerRemoved(String dancerId) {
        PlaylistRunner runner = dancerRunners.remove(dancerId);
        if (runner != null) cancelTask(runner);
    }

    /** Appelé quand un groupe de chorégraphie est supprimé. */
    public void onGroupDeleted(String groupId) {
        PlaylistRunner runner = groupRunners.remove(groupId);
        if (runner != null) cancelTask(runner);
    }

    /** Stoppe tous les runners actifs. Appelé dans onDisable. */
    public void stopAll() {
        playerRunners.values().forEach(this::cancelTask);
        dancerRunners.values().forEach(this::cancelTask);
        groupRunners.values().forEach(this::cancelTask);
        playerRunners.clear();
        dancerRunners.clear();
        groupRunners.clear();
    }

    // -------------------------------------------------------------------------
    // Persistance
    // -------------------------------------------------------------------------

    public void loadFromFile() {
        File file = getFile();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("playlists");
        if (root == null) return;

        int loaded = 0;
        for (String id : root.getKeys(false)) {
            ConfigurationSection ps = root.getConfigurationSection(id);
            if (ps == null) continue;

            boolean loop = ps.getBoolean("loop", true);
            List<Track> tracks = new ArrayList<>();

            ConfigurationSection tracksSection = ps.getConfigurationSection("tracks");
            if (tracksSection != null) {
                // Les clés sont "0", "1", "2", ... — on les trie numériquement
                List<String> keys = new ArrayList<>(tracksSection.getKeys(false));
                keys.sort(Comparator.comparingInt(k -> {
                    try { return Integer.parseInt(k); } catch (NumberFormatException e) { return 0; }
                }));
                for (String key : keys) {
                    ConfigurationSection ts = tracksSection.getConfigurationSection(key);
                    if (ts == null) continue;
                    String style = ts.getString("style");
                    int    reps  = ts.getInt("repetitions", 1);
                    if (style != null && !style.isBlank()) {
                        tracks.add(new Track(style, Math.max(1, reps)));
                    }
                }
            }

            playlists.put(id, new Playlist(id, tracks, loop));
            loaded++;
        }

        if (loaded > 0) {
            plugin.getLogger().info("[Playlist] " + loaded + " playlist(s) chargée(s).");
        }
    }

    private synchronized void saveToFile() {
        File file = getFile();
        YamlConfiguration yaml = new YamlConfiguration();

        for (Map.Entry<String, Playlist> entry : playlists.entrySet()) {
            String   id = entry.getKey();
            Playlist p  = entry.getValue();
            yaml.set("playlists." + id + ".loop", p.loop);
            for (int i = 0; i < p.tracks.size(); i++) {
                Track t = p.tracks.get(i);
                yaml.set("playlists." + id + ".tracks." + i + ".style",       t.styleName());
                yaml.set("playlists." + id + ".tracks." + i + ".repetitions", t.repetitions());
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Playlist] Impossible de sauvegarder playlists.yml", e);
        }
    }

    private File getFile() {
        plugin.getDataFolder().mkdirs();
        return new File(plugin.getDataFolder(), "playlists.yml");
    }

    // -------------------------------------------------------------------------
    // Internals — scheduling
    // -------------------------------------------------------------------------

    private PlaylistRunner newRunner(String playlistId) {
        PlaylistRunner runner = new PlaylistRunner();
        runner.playlistId    = playlistId;
        runner.currentIndex  = 0;
        return runner;
    }

    private void cancelTask(PlaylistRunner runner) {
        if (runner.task != null) {
            runner.task.cancel();
            runner.task = null;
        }
    }

    /** Applique la piste au joueur (sans message "Danse démarrée" parasite). */
    private void applyPlayerTrack(UUID playerId, Track track) {
        Player p = Bukkit.getPlayer(playerId);
        if (p == null || !p.isOnline()) return;
        DanceStyle style = danceManager.parseStyle(track.styleName());
        if (style == null) {
            plugin.getLogger().warning("[Playlist] Style inconnu dans la playlist: " + track.styleName());
            return;
        }
        danceManager.startDanceSilent(p, style, true, null);
    }

    private void schedulePlayerNext(UUID playerId, PlaylistRunner runner, Playlist playlist) {
        Track current = playlist.tracks.get(runner.currentIndex);
        runner.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Vérifier que le runner est toujours actif (pas stoppé entre-temps)
            if (!playerRunners.containsKey(playerId)) return;
            // Vérifier que le joueur est toujours en ligne
            if (Bukkit.getPlayer(playerId) == null) {
                playerRunners.remove(playerId);
                return;
            }

            runner.currentIndex++;
            if (runner.currentIndex >= playlist.tracks.size()) {
                if (playlist.loop) {
                    runner.currentIndex = 0;
                } else {
                    playerRunners.remove(playerId);
                    danceManager.stopDance(playerId);
                    return;
                }
            }

            applyPlayerTrack(playerId, playlist.tracks.get(runner.currentIndex));
            schedulePlayerNext(playerId, runner, playlist);
        }, computeDelay(current));
    }

    private void scheduleDancerNext(String dancerId, PlaylistRunner runner, Playlist playlist) {
        Track current = playlist.tracks.get(runner.currentIndex);
        long delay = computeDelay(current);
        dbg("scheduleDancerNext(" + dancerId + ") piste#" + runner.currentIndex + "='" + current.styleName() + "' → prochain dans " + delay + " ticks");
        runner.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!dancerRunners.containsKey(dancerId)) return;
            if (!staticDancerManager.getDancerIds().contains(dancerId)) {
                dancerRunners.remove(dancerId);
                return;
            }

            runner.currentIndex++;
            if (runner.currentIndex >= playlist.tracks.size()) {
                if (playlist.loop) {
                    runner.currentIndex = 0;
                } else {
                    dancerRunners.remove(dancerId);
                    dbg("Playlist dancer '" + dancerId + "' terminée (pas de boucle).");
                    return;
                }
            }

            Track next = playlist.tracks.get(runner.currentIndex);
            dbg("transition dancer '" + dancerId + "' → piste#" + runner.currentIndex + "='" + next.styleName() + "'");
            staticDancerManager.changeAnimation(dancerId, next.styleName());
            scheduleDancerNext(dancerId, runner, playlist);
        }, delay);
    }

    private void scheduleGroupNext(String groupId, PlaylistRunner runner, Playlist playlist) {
        Track current = playlist.tracks.get(runner.currentIndex);
        long delay = computeDelay(current);
        dbg("scheduleGroupNext(" + groupId + ") piste#" + runner.currentIndex + "='" + current.styleName() + "' → prochain dans " + delay + " ticks");
        runner.task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!groupRunners.containsKey(groupId)) return;
            if (!staticDancerManager.getChoreographyGroupIds().contains(groupId)) {
                groupRunners.remove(groupId);
                return;
            }

            runner.currentIndex++;
            if (runner.currentIndex >= playlist.tracks.size()) {
                if (playlist.loop) {
                    runner.currentIndex = 0;
                } else {
                    groupRunners.remove(groupId);
                    dbg("Playlist groupe '" + groupId + "' terminée (pas de boucle).");
                    return;
                }
            }

            Track next = playlist.tracks.get(runner.currentIndex);
            dbg("transition groupe '" + groupId + "' → piste#" + runner.currentIndex + "='" + next.styleName() + "'");
            staticDancerManager.changeGroupAnimation(groupId, next.styleName());
            scheduleGroupNext(groupId, runner, playlist);
        }, delay);
    }

    /**
     * Calcule le délai réel en ticks pour une piste : durée d'une répétition × nombre de répétitions.
     * La durée est lue depuis le blueprint ModelEngine ; si indisponible, on utilise le fallback.
     */
    private long computeDelay(Track track) {
        int animLength = resolveAnimationLengthTicks(track.styleName());
        long delay = (long) animLength * track.repetitions();
        dbg("computeDelay('" + track.styleName() + "') → longueurAnim=" + animLength + " ticks × " + track.repetitions() + " rép. = " + delay + " ticks (" + String.format("%.1f", delay / 20.0) + "s)");
        return delay;
    }

    /**
     * Lit la longueur d'une animation (en ticks) depuis le blueprint ModelEngine via reflection.
     * Retourne {@link #DEFAULT_TICKS_PER_REP} si la lecture échoue.
     *
     * <p>Accède à : {@code ModelEngineAPI.getBlueprint(modelId)
     *   .getAnimations().get(animName).getLength()}
     */
    private int resolveAnimationLengthTicks(String styleName) {
        org.bukkit.configuration.ConfigurationSection section =
                plugin.getConfig().getConfigurationSection("dances." + styleName);
        if (section == null) {
            dbg("resolveAnimationLength('" + styleName + "') → style introuvable dans config → fallback " + DEFAULT_TICKS_PER_REP);
            return DEFAULT_TICKS_PER_REP;
        }

        boolean useFallback = plugin.getConfig().getBoolean("modelEngine.useFallbackMode", false);
        String modelId = useFallback
                ? plugin.getConfig().getString("modelEngine.fallbackModelId", "joueur_fallback")
                : section.getString("modelId");
        String animName = section.getString("animationName");

        dbg("resolveAnimationLength('" + styleName + "') → modelId='" + modelId + "' animName='" + animName + "'");

        if (modelId == null || modelId.isBlank() || animName == null) {
            dbg("  → modelId ou animName null → fallback " + DEFAULT_TICKS_PER_REP);
            return DEFAULT_TICKS_PER_REP;
        }

        try {
            Object blueprint = com.ticxo.modelengine.api.ModelEngineAPI.class
                    .getMethod("getBlueprint", String.class)
                    .invoke(null, modelId.trim());
            if (blueprint == null) {
                dbg("  → getBlueprint('" + modelId + "') retourne null → fallback " + DEFAULT_TICKS_PER_REP);
                return DEFAULT_TICKS_PER_REP;
            }
            dbg("  → blueprint trouvé: " + blueprint.getClass().getSimpleName());

            Object animations = blueprint.getClass().getMethod("getAnimations").invoke(blueprint);
            if (!(animations instanceof java.util.Map)) {
                dbg("  → getAnimations() n'est pas une Map → fallback " + DEFAULT_TICKS_PER_REP);
                return DEFAULT_TICKS_PER_REP;
            }

            java.util.Map<?, ?> animMap = (java.util.Map<?, ?>) animations;
            dbg("  → animations disponibles: " + animMap.keySet());

            Object animBlueprint = null;
            for (java.util.Map.Entry<?, ?> e : animMap.entrySet()) {
                if (e.getKey() != null && e.getKey().toString().equalsIgnoreCase(animName)) {
                    animBlueprint = e.getValue();
                    break;
                }
            }
            if (animBlueprint == null) {
                dbg("  → animation '" + animName + "' introuvable dans le blueprint → fallback " + DEFAULT_TICKS_PER_REP);
                return DEFAULT_TICKS_PER_REP;
            }
            dbg("  → animBlueprint trouvé: " + animBlueprint.getClass().getSimpleName());

            // getLength/getDuration retournent des secondes ; getFrameCount/getTickLength retournent des ticks.
            for (String method : new String[]{"getTickLength", "getFrameCount", "getLength", "getDuration"}) {
                try {
                    Object result = animBlueprint.getClass().getMethod(method).invoke(animBlueprint);
                    dbg("  → " + method + "() = " + result);
                    if (result instanceof Number num) {
                        double raw = num.doubleValue();
                        if (raw > 0) {
                            // getTickLength / getFrameCount sont déjà en ticks ; getLength / getDuration sont en secondes
                            int len = (method.equals("getLength") || method.equals("getDuration"))
                                    ? (int) Math.round(raw * 20.0)
                                    : (int) Math.round(raw);
                            dbg("  → longueur retenue: " + len + " ticks (raw=" + raw + ")");
                            return len;
                        }
                    }
                } catch (NoSuchMethodException ignored) {
                    dbg("  → méthode " + method + "() absente");
                }
            }
            dbg("  → aucune méthode de longueur trouvée → fallback " + DEFAULT_TICKS_PER_REP);
        } catch (Exception e) {
            plugin.getLogger().warning("[Playlist] Durée d'animation indisponible pour '"
                    + styleName + "' (fallback " + DEFAULT_TICKS_PER_REP + " ticks) : " + e.getMessage());
            dbg("  → exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
        return DEFAULT_TICKS_PER_REP;
    }

    private void cancelRunnersForPlaylist(String playlistId) {
        playerRunners.entrySet().removeIf(e -> {
            if (playlistId.equals(e.getValue().playlistId)) { cancelTask(e.getValue()); return true; }
            return false;
        });
        dancerRunners.entrySet().removeIf(e -> {
            if (playlistId.equals(e.getValue().playlistId)) { cancelTask(e.getValue()); return true; }
            return false;
        });
        groupRunners.entrySet().removeIf(e -> {
            if (playlistId.equals(e.getValue().playlistId)) { cancelTask(e.getValue()); return true; }
            return false;
        });
    }
}
