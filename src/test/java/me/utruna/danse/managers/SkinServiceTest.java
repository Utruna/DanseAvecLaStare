package me.utruna.danse.managers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests pour SkinService.
 *
 * Limitation documentée :
 * Le chemin joueur hors ligne effectue un appel réseau réel à l'API Mojang via
 * profile.update().get(...). Ce test est gardé en manuel car il est non
 * déterministe et peut échouer selon la latence réseau ou le rate limiting Mojang.
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
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SkinServiceTest"));
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getInt("skinFetcher.timeoutSeconds", 5)).thenReturn(5);
        when(plugin.getConfig()).thenReturn(config);
        // Le cache est statique : le vider entre chaque test pour l'isolation
        SkinService.clearCache();
        SkinService.init(2);
    }

    @AfterEach
    void tearDown() {
        SkinService.resetRemoteProfileLoader();
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
    // Chemin joueur hors ligne — remoteProfileLoader injecté (pas de réseau)
    // =========================================================================

    @Test
    void fetchSkin_pending_deduplication_remoteLoaderCalledOnce() throws InterruptedException {
        // Vérifie que N appels simultanés pour le même pseudo hors-ligne ne déclenchent
        // qu'un seul appel au remoteProfileLoader (mécanisme PENDING).
        int N = 5;
        AtomicInteger loadCount = new AtomicInteger(0);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch loaderRelease = new CountDownLatch(1);
        org.bukkit.profile.PlayerProfile mockProfile = mock(org.bukkit.profile.PlayerProfile.class);

        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            loadCount.incrementAndGet();
            loaderStarted.countDown();
            try { loaderRelease.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return mockProfile;
        });

        List<AtomicReference<org.bukkit.profile.PlayerProfile>> results = new java.util.ArrayList<>();
        for (int i = 0; i < N; i++) {
            AtomicReference<org.bukkit.profile.PlayerProfile> ref = new AtomicReference<>();
            results.add(ref);
            SkinService.fetchSkin(plugin, "ghost_offline", ref::set);
        }

        assertTrue(loaderStarted.await(2, TimeUnit.SECONDS), "Le loader async doit démarrer");
        assertEquals(1, loadCount.get(), "Un seul appel loader pour N requêtes simultanées");

        loaderRelease.countDown();
        Thread.sleep(150);
        server.getScheduler().performOneTick();

        for (int i = 0; i < N; i++) {
            assertEquals(mockProfile, results.get(i).get(),
                    "Callback #" + i + " doit recevoir le même profil");
        }
    }

    @Test
    void fetchSkin_remoteSuccess_subsequentCallHitsCacheSynchronously() throws InterruptedException {
        // Vérifie qu'après un fetch remote réussi, le deuxième appel est synchrone (lecture CACHE).
        org.bukkit.profile.PlayerProfile mockProfile = mock(org.bukkit.profile.PlayerProfile.class);
        CountDownLatch loaderDone = new CountDownLatch(1);

        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            loaderDone.countDown();
            return mockProfile;
        });

        SkinService.fetchSkin(plugin, "ghost_offline2", p -> {});

        assertTrue(loaderDone.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        // Deuxième appel : doit être synchrone car profil en cache
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        AtomicReference<org.bukkit.profile.PlayerProfile> secondResult = new AtomicReference<>();
        SkinService.fetchSkin(plugin, "ghost_offline2", p -> {
            secondResult.set(p);
            callbackThread.set(Thread.currentThread());
        });

        assertEquals(Thread.currentThread(), callbackThread.get(),
                "Lecture cache doit être synchrone (même thread)");
        assertEquals(mockProfile, secondResult.get(),
                "Lecture cache doit retourner le même profil");
    }

    @Test
    void fetchSkin_remoteFailure_callbackReceivesNullAndProfileNotCached() throws InterruptedException {
        // Vérifie que si le loader retourne null, le callback reçoit null et CACHE reste vide.
        CountDownLatch loaderDone = new CountDownLatch(1);

        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            loaderDone.countDown();
            return null;
        });

        AtomicReference<org.bukkit.profile.PlayerProfile> firstResult = new AtomicReference<>(
                mock(org.bukkit.profile.PlayerProfile.class)); // sentinel non-null

        SkinService.fetchSkin(plugin, "ghost_offline3", firstResult::set);

        assertTrue(loaderDone.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        assertNull(firstResult.get(), "Le callback doit recevoir null en cas d'échec remote");

        // Deuxième appel : si le profil n'est PAS en cache, le callback ne sera pas appelé
        // de manière synchrone (il part en async).
        AtomicBoolean secondCalledSynchronously = new AtomicBoolean(false);
        SkinService.setRemoteProfileLoader((p, name, timeout) -> null); // éviter NPE au 2e fetch
        SkinService.fetchSkin(plugin, "ghost_offline3", p -> secondCalledSynchronously.set(true));

        assertFalse(secondCalledSynchronously.get(),
                "Un profil null ne doit PAS être mis en cache");
    }

    // =========================================================================
    // Cycle de vie — shutdown, clearCache, reinit
    // =========================================================================

    @Test
    void fetchSkin_afterShutdown_callbackReceivesNullImmediately() {
        SkinService.shutdown();
        org.bukkit.profile.PlayerProfile sentinel = mock(org.bukkit.profile.PlayerProfile.class);
        AtomicReference<org.bukkit.profile.PlayerProfile> result = new AtomicReference<>(sentinel);

        SkinService.fetchSkin(plugin, "offline_shutdown_test", result::set);

        assertNull(result.get(), "Après shutdown le callback doit recevoir null immédiatement");
    }

    @Test
    void fetchSkin_afterClearCache_profileIsRefetched() throws InterruptedException {
        org.bukkit.profile.PlayerProfile profile1 = mock(org.bukkit.profile.PlayerProfile.class);
        org.bukkit.profile.PlayerProfile profile2 = mock(org.bukkit.profile.PlayerProfile.class);
        AtomicInteger loadCount = new AtomicInteger(0);
        CountDownLatch firstLoaded = new CountDownLatch(1);

        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            int n = loadCount.incrementAndGet();
            firstLoaded.countDown();
            return n == 1 ? profile1 : profile2;
        });

        SkinService.fetchSkin(plugin, "ghost_clearcache", p -> {});
        assertTrue(firstLoaded.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        SkinService.clearCache();

        AtomicReference<org.bukkit.profile.PlayerProfile> secondResult = new AtomicReference<>();
        SkinService.fetchSkin(plugin, "ghost_clearcache", secondResult::set);
        assertNull(secondResult.get(), "Après clearCache la 2e requête ne doit pas être synchrone");
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        assertEquals(2, loadCount.get(), "clearCache doit forcer un 2e appel au loader");
        assertEquals(profile2, secondResult.get(), "La 2e requête doit obtenir profile2");
    }

    @Test
    void fetchSkin_reinitAfterShutdown_worksNormally() throws InterruptedException {
        SkinService.shutdown();
        SkinService.init(2);

        org.bukkit.profile.PlayerProfile mockProfile = mock(org.bukkit.profile.PlayerProfile.class);
        CountDownLatch loaded = new CountDownLatch(1);
        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            loaded.countDown();
            return mockProfile;
        });

        AtomicReference<org.bukkit.profile.PlayerProfile> result = new AtomicReference<>();
        SkinService.fetchSkin(plugin, "ghost_reinit", result::set);
        assertTrue(loaded.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        assertEquals(mockProfile, result.get(), "Après reinit fetchSkin doit fonctionner normalement");
    }

    // =========================================================================
    // Gestion des exceptions du loader
    // =========================================================================

    @Test
    void fetchSkin_loaderThrowsException_callbackReceivesNullAndNotCached() throws InterruptedException {
        CountDownLatch loaderCalled = new CountDownLatch(1);
        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            loaderCalled.countDown();
            throw new RuntimeException("Simulated Mojang API failure");
        });

        org.bukkit.profile.PlayerProfile sentinel = mock(org.bukkit.profile.PlayerProfile.class);
        AtomicReference<org.bukkit.profile.PlayerProfile> result = new AtomicReference<>(sentinel);
        SkinService.fetchSkin(plugin, "ghost_exception", result::set);

        assertTrue(loaderCalled.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        assertNull(result.get(), "Le callback doit recevoir null en cas d'exception du loader");

        // Vérifier non mis en cache : 2e appel doit repartir en async (pas synchrone)
        AtomicBoolean secondCalledSync = new AtomicBoolean(false);
        SkinService.setRemoteProfileLoader((p, name, timeout) -> null);
        SkinService.fetchSkin(plugin, "ghost_exception", p -> secondCalledSync.set(true));
        assertFalse(secondCalledSync.get(), "Un résultat d'exception ne doit pas être mis en cache");
    }

    @Test
    void fetchSkin_deduplication_loaderThrowsException_allCallbacksReceiveNull() throws InterruptedException {
        int N = 4;
        AtomicInteger loadCount = new AtomicInteger(0);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch loaderRelease = new CountDownLatch(1);

        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            loadCount.incrementAndGet();
            loaderStarted.countDown();
            try { loaderRelease.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            throw new RuntimeException("Simulated exception in dedup test");
        });

        List<AtomicReference<org.bukkit.profile.PlayerProfile>> results = new java.util.ArrayList<>();
        for (int i = 0; i < N; i++) {
            AtomicReference<org.bukkit.profile.PlayerProfile> ref =
                    new AtomicReference<>(mock(org.bukkit.profile.PlayerProfile.class)); // sentinel
            results.add(ref);
            SkinService.fetchSkin(plugin, "ghost_dedup_ex", ref::set);
        }

        assertTrue(loaderStarted.await(2, TimeUnit.SECONDS));
        assertEquals(1, loadCount.get(), "Un seul appel loader malgré N requêtes");
        loaderRelease.countDown();
        Thread.sleep(150);
        server.getScheduler().performOneTick();

        for (int i = 0; i < N; i++) {
            assertNull(results.get(i).get(), "Callback #" + i + " doit recevoir null en cas d'exception");
        }
    }

    @Test
    void fetchSkin_offlinePath_cacheKeyIsLowercase_caseInsensitiveLookup() throws InterruptedException {
        org.bukkit.profile.PlayerProfile mockProfile = mock(org.bukkit.profile.PlayerProfile.class);
        CountDownLatch loaded = new CountDownLatch(1);
        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            loaded.countDown();
            return mockProfile;
        });

        // 1er fetch avec nom en MAJUSCULES
        SkinService.fetchSkin(plugin, "GHOSTOFFLINE", p -> {});
        assertTrue(loaded.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        // 2e fetch avec minuscules → doit toucher le cache (synchrone)
        AtomicReference<Thread> callbackThread = new AtomicReference<>();
        SkinService.fetchSkin(plugin, "ghostoffline", p -> callbackThread.set(Thread.currentThread()));

        assertEquals(Thread.currentThread(), callbackThread.get(),
                "La clé cache offline est lowercase : lookup lowercase doit être synchrone");
    }

    // =========================================================================
    // Chemin joueur hors ligne — nécessite réseau Mojang
    // =========================================================================

    @Disabled("Test manuel d'intégration Mojang")
    @Test
    void fetchSkin_offlinePlayer_callsMojangApiAsync() throws InterruptedException {
        // Test d'intégration volontairement non déterministe.
        // Il fait un appel réseau réel à l'API d'authentification Mojang via
        // Bukkit.createPlayerProfile(username).update().get(timeout, SECONDS).
        String name = "Notch";
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<PlayerProfile> captured = new AtomicReference<>();

        SkinService.fetchSkin(plugin, name, profile -> {
            captured.set(profile);
            latch.countDown();
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS), "Le fetch Mojang a pris trop de temps");
        assertNotNull(captured.get(), "Le profil Mojang doit être résolu pour le joueur hors ligne");
    }
}
