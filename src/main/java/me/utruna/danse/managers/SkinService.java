package me.utruna.danse.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Player skin retrieval service through the Mojang API.
 *
 * <p>Resolved profiles are cached for the entire session ({@link #clearCache()} can
 * force a reload, for example during {@code /danse reload}).
 *
 * <p>Mojang calls (offline players) run on a bounded thread pool
 * ({@code DanseSkinFetcher-N}), sized from {@code skinFetcher.maxThreads}
 * in {@code config.yml}. Call {@link #init(int)} from {@code onEnable} and
 * {@link #shutdown()} from {@code onDisable} to manage the pool lifecycle.
 *
 * <p>The callback passed to {@link #fetchSkin} is <strong>always</strong> called on the
 * Bukkit main thread, whether the profile comes from the cache, an online player, or an
 * async Mojang call.
 */
public class SkinService {

    private static final AtomicInteger THREAD_COUNTER = new AtomicInteger(0);

    /** Session cache for already resolved profiles (lowercase username -> profile). */
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
    * Initializes (or reinitializes) the thread pool with the given size.
    * Must be called from {@code onEnable} after loading the config.
    * The previous pool (if any) is shut down cleanly before replacement.
     */
    public static void init(int maxThreads) {
        ExecutorService old = executor;
        executor = buildExecutor(maxThreads);
        if (old != null) old.shutdown();
    }

    /**
    * Shuts down the thread pool cleanly.
    * Must be called from {@code onDisable} to release resources.
     */
    public static void shutdown() {
        ExecutorService old = executor;
        executor = null;
        if (old != null) old.shutdown();
    }

    /** Clears the cache (called during a /danse reload). */
    public static void clearCache() {
        CACHE.clear();
    }

    /**
    * Retrieves a player's skin profile asynchronously.
    * Priority: cache -> online player -> Mojang call (bounded pool, configurable timeout).
    * The callback is always called on the Bukkit main thread.
     */
    @SuppressWarnings("deprecation")
    public static void fetchSkin(Plugin plugin, String username, Consumer<PlayerProfile> callback) {
        String key = username.toLowerCase();

        // Cache: profile already resolved
        PlayerProfile cached = CACHE.get(key);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // Online player: profile available immediately
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            PlayerProfile profile = online.getPlayerProfile();
            CACHE.put(key, profile);
            callback.accept(profile);
            return;
        }

        // Offline player: Mojang call on bounded pool
        ExecutorService exec = executor;
        if (exec == null || exec.isShutdown()) {
            plugin.getLogger().warning("[SkinService] Executor unavailable, cannot load skin for " + username);
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
                    plugin.getLogger().warning("[SkinService] Profile without texture for: " + username);
                }
            } catch (TimeoutException e) {
                plugin.getLogger().warning("[SkinService] Mojang timeout for " + username);
            } catch (Exception e) {
                plugin.getLogger().warning("[SkinService] Mojang error for " + username + ": " + e.getMessage());
            }
            final PlayerProfile finalResult = result;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalResult));
        }, exec);
    }
}
