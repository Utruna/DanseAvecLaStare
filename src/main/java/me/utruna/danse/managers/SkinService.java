package me.utruna.danse.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Service de récupération des skins joueur via l'API Mojang.
 *
 * <p>Les profils résolus sont mis en cache pour toute la session ({@link #clearCache()} peut
 * forcer un rechargement, par exemple lors d'un {@code /danse reload}).
 *
 * <p>Les appels Mojang (joueurs hors ligne) s'exécutent sur un pool de threads borné
 * ({@code DanseSkinFetcher-N}), dont la taille est lue depuis {@code skinFetcher.maxThreads}
 * dans {@code config.yml}. Appeler {@link #init(int)} depuis {@code onEnable} et
 * {@link #shutdown()} depuis {@code onDisable} pour gérer le cycle de vie du pool.
 *
 * <p>Le callback passé à {@link #fetchSkin} est <strong>toujours</strong> appelé sur le
 * thread principal Bukkit, que le profil provienne du cache, d'un joueur en ligne ou d'un
 * appel Mojang async.
 */
public class SkinService {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    /** Cache session des profils déjà résolus (pseudo lowercase → profil). */
    @SuppressWarnings("deprecation")
    private static final ConcurrentHashMap<String, PlayerProfile> CACHE = new ConcurrentHashMap<>();

    private static volatile ExecutorService executor = buildExecutor(4);

    private static ExecutorService buildExecutor(int threads) {
        return Executors.newFixedThreadPool(Math.max(1, threads), r -> {
            Thread t = new Thread(r, "DanseSkinFetcher-" + THREAD_COUNTER.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initialise (ou réinitialise) le pool de threads avec la taille donnée.
     * Doit être appelé depuis {@code onEnable} après le chargement de la config.
     * L'ancien pool (s'il existe) est arrêté proprement avant remplacement.
     */
    public static void init(int maxThreads) {
        ExecutorService old = executor;
        executor = buildExecutor(maxThreads);
        if (old != null) old.shutdown();
    }

    /**
     * Arrête le pool de threads proprement.
     * Doit être appelé depuis {@code onDisable} pour libérer les ressources.
     */
    public static void shutdown() {
        ExecutorService old = executor;
        executor = null;
        if (old != null) old.shutdown();
    }

    /** Vide le cache (appelé lors d'un /danse reload). */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
     * Récupère le profil skin d'un joueur de manière asynchrone.
     * Priorité : cache → joueur en ligne → appel Mojang (pool borné, timeout configurable).
     * Le callback est toujours appelé sur le thread principal Bukkit.
     */
    @SuppressWarnings("deprecation")
    public static void fetchSkin(Plugin plugin, String username, Consumer<PlayerProfile> callback) {
        String key = username.toLowerCase();

        // Cache : profil déjà résolu
        PlayerProfile cached = CACHE.get(key);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // Joueur en ligne : profil disponible immédiatement
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            PlayerProfile profile = online.getPlayerProfile();
            CACHE.put(key, profile);
            callback.accept(profile);
            return;
        }

        // Joueur hors ligne : appel Mojang sur pool borné
        ExecutorService exec = executor;
        if (exec == null || exec.isShutdown()) {
            plugin.getLogger().warning("[SkinService] Executor non disponible, impossible de charger le skin de " + username);
            callback.accept(null);
            return;
        }

        int timeoutSeconds = plugin.getConfig().getInt("skinFetcher.timeoutSeconds", 5);

        CompletableFuture.runAsync(() -> {
            PlayerProfile result = null;
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(username);
                PlayerProfile updated = profile.update().get(timeoutSeconds, TimeUnit.SECONDS);
                if (updated != null && updated.getTextures().getSkin() != null) {
                    CACHE.put(key, updated);
                    result = updated;
                } else {
                    plugin.getLogger().warning("[SkinService] Profil sans texture pour: " + username);
                }
            } catch (TimeoutException e) {
                plugin.getLogger().warning("[SkinService] Timeout Mojang pour " + username);
            } catch (Exception e) {
                plugin.getLogger().warning("[SkinService] Erreur Mojang pour " + username + ": " + e.getMessage());
            }
            final PlayerProfile finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        }, exec);
    }
}
