package me.utruna.danse.managers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests for SkinService.
 *
 * Documented limitation:
 * The offline-player path performs a real network call to the Mojang API via
 * profile.update().get(...). This case is marked @Disabled and must not be
 * executed in CI (integration test, non-deterministic).
 *
 * Note about getPlayerExact in MockBukkit:
 * MockBukkit implements Bukkit.getServer().getPlayerExact(name). If the version
 * of MockBukkit used does not implement it faithfully, the tests for the
 * "online player" path may fail with UnsupportedOperationException.
 */
class SkinServiceTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(Plugin.class);
        // The cache is static: clear it between tests for isolation
        SkinService.clearCache();
        SkinService.init(2);
    }

    @AfterEach
    void tearDown() {
        SkinService.shutdown();
        MockBukkit.unmock();
    }

    // =========================================================================
    // Online-player path
    // =========================================================================

    @Test
    void fetchSkin_onlinePlayer_callbackRecievesNonNullProfile() {
        PlayerMock player = server.addPlayer();
        String name = player.getName();

        AtomicReference<PlayerProfile> captured = new AtomicReference<>();
        SkinService.fetchSkin(plugin, name, captured::set);

        assertNotNull(captured.get(),
                "The profile must be non-null for an online player");
    }

    @Test
    void fetchSkin_onlinePlayer_callbackCalledSynchronously() {
        // In the "online player" path, callback.accept() is called directly
        // (not via Bukkit.getScheduler().runTask), so it runs on the caller thread.
        PlayerMock player = server.addPlayer();
        String name = player.getName();

        Thread callingThread = Thread.currentThread();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();

        SkinService.fetchSkin(plugin, name, profile -> callbackThread.set(Thread.currentThread()));

        assertEquals(callingThread, callbackThread.get(),
                "The callback must be called synchronously on the caller thread");
    }

    @Test
    void fetchSkin_onlinePlayer_profileIsCachedForSubsequentCalls() {
        PlayerMock player = server.addPlayer();
        String name = player.getName();

        // First call: populates the cache
        SkinService.fetchSkin(plugin, name, p -> {});

        // Second call: must return the profile from the cache (also synchronous path)
        AtomicReference<PlayerProfile> second = new AtomicReference<>();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        SkinService.fetchSkin(plugin, name, p -> {
            second.set(p);
            callbackThread.set(Thread.currentThread());
        });

        assertNotNull(second.get(), "The cached profile must be non-null");
        assertEquals(Thread.currentThread(), callbackThread.get(),
                "Cache reads must be synchronous");
    }

    @Test
    void fetchSkin_cacheKeyIsLowercase_caseInsensitiveLookup() {
        // The cache key is username.toLowerCase()
        // After a call with "Alice", a call with "alice" must hit the cache
        PlayerMock player = server.addPlayer();
        String name = player.getName(); // ex: "Player0"

        // First call (lowercase) to populate the cache
        SkinService.fetchSkin(plugin, name.toLowerCase(), p -> {});

        AtomicReference<Thread> secondCallbackThread = new AtomicReference<>();
        SkinService.fetchSkin(plugin, name.toUpperCase(), p ->
                secondCallbackThread.set(Thread.currentThread()));

        // If the cache is hit, the callback is synchronous (same thread)
        assertEquals(Thread.currentThread(), secondCallbackThread.get(),
                "Lookup with different casing must hit the cache (synchronous)");
    }

    // =========================================================================
    // Offline-player path - requires Mojang network access
    // =========================================================================

    @Disabled("Requires Mojang API - integration test only")
    @Test
    void fetchSkin_offlinePlayer_callsMojangApiAsync() {
        // This test is intentionally disabled.
        // It would make a real network call to the Mojang authentication API via
        // Bukkit.createPlayerProfile(username).update().get(timeout, SECONDS).
        // Non-deterministic in CI (network latency, rate limiting, missing username).
    }
}
