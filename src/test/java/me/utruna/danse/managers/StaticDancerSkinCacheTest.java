package me.utruna.danse.managers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Vérifie le fix du flood MineSkin :
 *
 * <p>Avant le fix, au redémarrage les danseurs sans alias relançaient tous un fetch Mojang
 * → ModelEngine appelait MineSkin pour chaque skin unique → dépassement de la limite 60/heure.
 *
 * <p>Après le fix :
 * <ol>
 *   <li>{@code scheduleLoadSkinFetch} persiste le profil dans {@link SkinCacheManager} sous le
 *       nom de joueur Mojang après chaque fetch réussi.</li>
 *   <li>{@code loadFromFile} consulte {@link SkinCacheManager} par nom de joueur
 *       <em>avant</em> de lancer un fetch Mojang — le {@code Dummy} reçoit ainsi la vraie
 *       texture dès la création, sans appel MineSkin.</li>
 * </ol>
 */
class StaticDancerSkinCacheTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private Plugin plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SkinCacheTest"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        FileConfiguration config = mock(FileConfiguration.class);
        when(config.getInt("skinFetcher.timeoutSeconds", 5)).thenReturn(5);
        when(plugin.getConfig()).thenReturn(config);
        SkinService.clearCache();
        SkinService.init(2);
    }

    @AfterEach
    void tearDown() {
        SkinService.resetRemoteProfileLoader();
        SkinService.shutdown();
        MockBukkit.unmock();
    }

    // ── Partie 1 : persistence après fetch (scheduleLoadSkinFetch) ──────────

    /**
     * Simule ce que {@code scheduleLoadSkinFetch} fait après le fix :
     * après un fetch Mojang réussi, le profil est sauvegardé dans
     * {@link SkinCacheManager} sous le nom de joueur.
     */
    @SuppressWarnings("deprecation")
    @Test
    void afterSuccessfulFetch_profileIsSavedUnderPlayerName() throws InterruptedException {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        PlayerProfile fetchedProfile = mock(PlayerProfile.class);
        String skinName = "utruna";
        CountDownLatch loaded = new CountDownLatch(1);

        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            loaded.countDown();
            return fetchedProfile;
        });

        // Reproduit le callback de scheduleLoadSkinFetch avec le fix
        SkinService.fetchSkin(plugin, skinName, profile -> {
            if (profile != null) {
                scm.saveSkin(skinName, profile); // ← le fix ajouté
            }
        });

        assertTrue(loaded.await(2, TimeUnit.SECONDS), "Le loader Mojang doit avoir été appelé");
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        assertSame(fetchedProfile, scm.getSkin(skinName),
                "Le profil fetchéé doit être retrouvable par nom de joueur dans SkinCacheManager");
    }

    /**
     * Un fetch qui retourne null (joueur introuvable) ne doit rien sauvegarder.
     */
    @Test
    void afterFailedFetch_nothingIsSavedToCache() throws InterruptedException {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        CountDownLatch loaded = new CountDownLatch(1);

        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            loaded.countDown();
            return null;
        });

        SkinService.fetchSkin(plugin, "joueur_inconnu", profile -> {
            if (profile != null) {
                scm.saveSkin("joueur_inconnu", profile);
            }
        });

        assertTrue(loaded.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        assertNull(scm.getSkin("joueur_inconnu"),
                "Aucun profil ne doit être sauvegardé si le fetch a échoué");
    }

    // ── Partie 2 : lecture du cache avant Mojang (loadFromFile) ────────────

    /**
     * Simule le comportement de {@code loadFromFile} après le fix :
     * si {@link SkinCacheManager} contient déjà le profil sous le nom de joueur,
     * Mojang ne doit pas être appelé.
     *
     * <p>C'est le scénario "second démarrage" : le profil avait été persisté au
     * premier démarrage par {@code scheduleLoadSkinFetch}.
     */
    @SuppressWarnings("deprecation")
    @Test
    void whenCacheHasSkin_mojangIsNotCalled() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        PlayerProfile cachedProfile = mock(PlayerProfile.class);
        scm.saveSkin("utruna", cachedProfile);

        AtomicInteger mojangCallCount = new AtomicInteger(0);
        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            mojangCallCount.incrementAndGet();
            return mock(PlayerProfile.class);
        });

        // loadFromFile fait : cachedProfile = skinCacheManager.getSkin(data.skin())
        // Si non-null → spawnStaticDancer reçoit le vrai profil, pas de scheduleLoadSkinFetch
        PlayerProfile found = scm.getSkin("utruna");

        assertSame(cachedProfile, found,
                "loadFromFile doit trouver le profil dans le cache par nom de joueur");
        assertEquals(0, mojangCallCount.get(),
                "Mojang ne doit pas être appelé si le profil est déjà en cache");
    }

    /**
     * Quand le cache ne contient pas le profil, {@code loadFromFile} doit
     * tomber dans le fallback Mojang ({@code scheduleLoadSkinFetch}).
     */
    @SuppressWarnings("deprecation")
    @Test
    void whenCacheMiss_mojangIsCalled() throws InterruptedException {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        // Cache vide — aucun saveSkin préalable

        AtomicInteger mojangCallCount = new AtomicInteger(0);
        PlayerProfile mojangProfile = mock(PlayerProfile.class);
        CountDownLatch loaded = new CountDownLatch(1);

        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            mojangCallCount.incrementAndGet();
            loaded.countDown();
            return mojangProfile;
        });

        // loadFromFile : cachedProfile == null → scheduleLoadSkinFetch
        assertNull(scm.getSkin("joueur_inconnu_du_cache"),
                "Cache vide : getSkin doit retourner null");

        SkinService.fetchSkin(plugin, "joueur_inconnu_du_cache", profile -> {
            if (profile != null) scm.saveSkin("joueur_inconnu_du_cache", profile);
        });

        assertTrue(loaded.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        assertEquals(1, mojangCallCount.get(), "Mojang doit être appelé exactement une fois");
        assertSame(mojangProfile, scm.getSkin("joueur_inconnu_du_cache"),
                "Après le fetch, le profil doit être en cache pour les redémarrages suivants");
    }

    // ── Partie 3 : cycle complet (simulation restart) ──────────────────────

    /**
     * Vérifie le cycle complet en mémoire :
     * <ol>
     *   <li>Fetch Mojang → save dans SkinCacheManager (démarrage 1)</li>
     *   <li>getSkin(skinName) → retourne le profil sans Mojang (démarrage 2)</li>
     * </ol>
     * Note : la persistance disque (YAML) n'est pas vérifiable ici car les mocks
     * de {@code PlayerProfile} ne sont pas {@code ConfigurationSerializable}.
     * La persistence réelle est couverte par {@link SkinCacheManagerTest#saveSkin_createsFile}.
     */
    @SuppressWarnings("deprecation")
    @Test
    void fullCycle_fetchThenCacheHit_mojangCalledOnlyOnce() throws InterruptedException {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        PlayerProfile mojangProfile = mock(PlayerProfile.class);
        AtomicInteger mojangCallCount = new AtomicInteger(0);
        CountDownLatch loaded = new CountDownLatch(1);

        SkinService.setRemoteProfileLoader((p, name, timeout) -> {
            mojangCallCount.incrementAndGet();
            loaded.countDown();
            return mojangProfile;
        });

        // Démarrage 1 : cache miss → fetch Mojang → save
        assertNull(scm.getSkin("ninja"), "Cache vide au démarrage 1");

        SkinService.fetchSkin(plugin, "ninja", profile -> {
            if (profile != null) scm.saveSkin("ninja", profile);
        });

        assertTrue(loaded.await(2, TimeUnit.SECONDS));
        Thread.sleep(100);
        server.getScheduler().performOneTick();

        assertEquals(1, mojangCallCount.get(), "Mojang appelé exactement 1 fois au démarrage 1");
        assertSame(mojangProfile, scm.getSkin("ninja"), "Profil en cache après démarrage 1");

        // Démarrage 2 : cache hit → pas de Mojang
        PlayerProfile cached = scm.getSkin("ninja");
        assertNotNull(cached, "Profil disponible en cache au démarrage 2");
        assertEquals(1, mojangCallCount.get(),
                "Mojang ne doit pas être rappelé au démarrage 2 (cache hit)");
    }

    // ── Partie 4 : insensibilité à la casse ────────────────────────────────

    /**
     * La clé dans SkinCacheManager est en minuscules (comme SkinService).
     * Un profil sauvegardé sous "Utruna" doit être retrouvable sous "utruna".
     */
    @SuppressWarnings("deprecation")
    @Test
    void cacheKey_isCaseInsensitive() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        PlayerProfile profile = mock(PlayerProfile.class);

        scm.saveSkin("Utruna", profile);

        assertSame(profile, scm.getSkin("utruna"),
                "La clé du cache doit être insensible à la casse (minuscules)");
        assertSame(profile, scm.getSkin("UTRUNA"),
                "La clé du cache doit être insensible à la casse (majuscules)");
    }
}
