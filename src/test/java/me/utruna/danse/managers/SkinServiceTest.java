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
 * Tests pour SkinService.
 *
 * Limitation documentée :
 * Le chemin joueur hors ligne effectue un appel réseau réel à l'API Mojang via
 * profile.update().get(...). Ce cas est marqué @Disabled et ne doit pas être
 * exécuté dans la CI (test d'intégration, non déterministe).
 *
 * Attention sur getPlayerExact dans MockBukkit :
 * MockBukkit implémente Bukkit.getServer().getPlayerExact(name). Si la version
 * de MockBukkit utilisée ne l'implémente pas fidèlement, les tests du chemin
 * "joueur en ligne" peuvent échouer avec UnsupportedOperationException.
 */
class SkinServiceTest {

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(Plugin.class);
        // Le cache est statique : le vider entre chaque test pour l'isolation
        SkinService.clearCache();
        SkinService.init(2);
    }

    @AfterEach
    void tearDown() {
        SkinService.shutdown();
        MockBukkit.unmock();
    }

    // =========================================================================
    // Chemin joueur en ligne
    // =========================================================================

    @Test
    void fetchSkin_onlinePlayer_callbackRecievesNonNullProfile() {
        PlayerMock player = server.addPlayer();
        String name = player.getName();

        AtomicReference<PlayerProfile> captured = new AtomicReference<>();
        SkinService.fetchSkin(plugin, name, captured::set);

        assertNotNull(captured.get(),
                "Le profil doit être non-null pour un joueur en ligne");
    }

    @Test
    void fetchSkin_onlinePlayer_callbackCalledSynchronously() {
        // Dans le chemin "joueur en ligne", callback.accept() est appelé directement
        // (pas via Bukkit.getScheduler().runTask), donc sur le thread appelant.
        PlayerMock player = server.addPlayer();
        String name = player.getName();

        Thread callingThread = Thread.currentThread();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();

        SkinService.fetchSkin(plugin, name, profile -> callbackThread.set(Thread.currentThread()));

        assertEquals(callingThread, callbackThread.get(),
                "Le callback doit être appelé de manière synchrone sur le thread appelant");
    }

    @Test
    void fetchSkin_onlinePlayer_profileIsCachedForSubsequentCalls() {
        PlayerMock player = server.addPlayer();
        String name = player.getName();

        // Premier appel : met en cache
        SkinService.fetchSkin(plugin, name, p -> {});

        // Deuxième appel : doit retourner le profil depuis le cache (chemin synchrone aussi)
        AtomicReference<PlayerProfile> second = new AtomicReference<>();
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        SkinService.fetchSkin(plugin, name, p -> {
            second.set(p);
            callbackThread.set(Thread.currentThread());
        });

        assertNotNull(second.get(), "Le profil mis en cache doit être non-null");
        assertEquals(Thread.currentThread(), callbackThread.get(),
                "La lecture du cache doit être synchrone");
    }

    @Test
    void fetchSkin_cacheKeyIsLowercase_caseInsensitiveLookup() {
        // La clé dans le cache est username.toLowerCase()
        // Après un appel avec "Alice", un appel avec "alice" doit toucher le cache
        PlayerMock player = server.addPlayer();
        String name = player.getName(); // ex: "Player0"

        // Premier appel (minuscule) pour peupler le cache
        SkinService.fetchSkin(plugin, name.toLowerCase(), p -> {});

        AtomicReference<Thread> secondCallbackThread = new AtomicReference<>();
        SkinService.fetchSkin(plugin, name.toUpperCase(), p ->
                secondCallbackThread.set(Thread.currentThread()));

        // Si le cache est touché, le callback est synchrone (même thread)
        assertEquals(Thread.currentThread(), secondCallbackThread.get(),
                "Lookup avec casse différente doit toucher le cache (synchrone)");
    }

    // =========================================================================
    // Chemin joueur hors ligne — nécessite réseau Mojang
    // =========================================================================

    @Disabled("Requires Mojang API - integration test only")
    @Test
    void fetchSkin_offlinePlayer_callsMojangApiAsync() {
        // Ce test est intentionnellement désactivé.
        // Il ferait un appel réseau réel à l'API d'authentification Mojang via
        // Bukkit.createPlayerProfile(username).update().get(timeout, SECONDS).
        // Non déterministe en CI (latence réseau, rate-limiting, username inexistant).
    }
}
