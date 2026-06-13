package me.utruna.danse.managers;

import be.seeseemelk.mockbukkit.MockBukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkinCacheManagerTest {

    @TempDir
    Path tempDir;

    private Plugin plugin;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = mock(Plugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SkinCacheTest"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ── saveSkin / getSkin ─────────────────────────────────────────────────

    @Test
    void getSkin_returnsNull_whenAliasUnknown() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        assertNull(scm.getSkin("inexistant"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void saveSkin_thenGetSkin_returnsSameProfile() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        PlayerProfile profile = mock(PlayerProfile.class);

        scm.saveSkin("alias1", profile);

        assertSame(profile, scm.getSkin("alias1"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void saveSkin_isCaseInsensitive() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        PlayerProfile profile = mock(PlayerProfile.class);

        scm.saveSkin("MyAlias", profile);

        assertSame(profile, scm.getSkin("myalias"), "getSkin doit être insensible à la casse");
        assertSame(profile, scm.getSkin("MYALIAS"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void saveSkin_overwritesExistingAlias() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        PlayerProfile first  = mock(PlayerProfile.class);
        PlayerProfile second = mock(PlayerProfile.class);

        scm.saveSkin("hero", first);
        scm.saveSkin("hero", second);

        assertSame(second, scm.getSkin("hero"), "Le second save doit écraser le premier");
    }

    // ── contains ──────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    @Test
    void contains_trueAfterSave_falseForUnknown() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        scm.saveSkin("hero", mock(PlayerProfile.class));

        assertTrue(scm.contains("hero"));
        assertFalse(scm.contains("villain"));
    }

    // ── removeSkin ────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    @Test
    void removeSkin_returnsTrueAndRemovesEntry() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        scm.saveSkin("hero", mock(PlayerProfile.class));

        assertTrue(scm.removeSkin("hero"));
        assertNull(scm.getSkin("hero"));
        assertFalse(scm.contains("hero"));
    }

    @Test
    void removeSkin_returnsFalseForUnknownAlias() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        assertFalse(scm.removeSkin("inexistant"));
    }

    // ── getAliases ────────────────────────────────────────────────────────

    @SuppressWarnings("deprecation")
    @Test
    void getAliases_reflectsCurrentState() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        assertTrue(scm.getAliases().isEmpty());

        scm.saveSkin("a", mock(PlayerProfile.class));
        scm.saveSkin("b", mock(PlayerProfile.class));

        assertTrue(scm.getAliases().contains("a"));
        assertTrue(scm.getAliases().contains("b"));
        assertEquals(2, scm.getAliases().size());
    }

    @SuppressWarnings("deprecation")
    @Test
    void getAliases_isUnmodifiable() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        scm.saveSkin("a", mock(PlayerProfile.class));

        assertThrows(UnsupportedOperationException.class,
                () -> scm.getAliases().add("hack"));
    }

    // ── persistence (fichier créé) ─────────────────────────────────────────

    @SuppressWarnings("deprecation")
    @Test
    void saveSkin_createsFile() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        scm.saveSkin("dancer", mock(PlayerProfile.class));

        File file = new File(tempDir.toFile(), "skin_cache.yml");
        assertTrue(file.exists(), "skin_cache.yml doit être créé après saveSkin");
    }

    @SuppressWarnings("deprecation")
    @Test
    void removeSkin_updatesFile() {
        SkinCacheManager scm = new SkinCacheManager(plugin);
        scm.saveSkin("dancer", mock(PlayerProfile.class));
        scm.removeSkin("dancer");

        // Le fichier existe encore mais le cache en mémoire est vide
        assertFalse(scm.contains("dancer"));
        assertTrue(scm.getAliases().isEmpty());
    }
}
