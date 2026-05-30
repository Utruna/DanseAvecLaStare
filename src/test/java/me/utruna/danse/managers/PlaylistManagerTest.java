package me.utruna.danse.managers;

import me.utruna.danse.DanseAvecLaStare;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlaylistManager - pure CRUD, without scheduling or Bukkit.
 *
 * Note: addTrack / removeTrack call resyncPlaylist() internally.
 * Since no runner is active in these tests, resyncPlaylist() does not trigger
 * any call to the Bukkit scheduler or to staticDancerManager.
 *
 * Not tested here (separate integration test):
 * - computeDelay(): calls ModelEngineAPI statically -> requires mockStatic
 * - playForPlayer / playForDancer / playForGroup: require MockBukkit + scheduler
 */
class PlaylistManagerTest {

    private DanseAvecLaStare plugin;
    private DanceManager     danceManager;
    private StaticDancerManager staticDancerManager;
    private PlaylistManager pm;
    private File tempDir;

    @BeforeEach
    void setUp() throws Exception {
        plugin              = mock(DanseAvecLaStare.class);
        danceManager        = mock(DanceManager.class);
        staticDancerManager = mock(StaticDancerManager.class);

        tempDir = Files.createTempDirectory("playlist-test").toFile();
        when(plugin.getDataFolder()).thenReturn(tempDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test-playlist"));

        // Valid styles
        DanceStyle stubStyle = new GenericDanceStyle("stub", true, "wave", 0, 0);
        when(danceManager.parseStyle("twist")).thenReturn(stubStyle);
        when(danceManager.parseStyle("disco")).thenReturn(stubStyle);
        when(danceManager.parseStyle("waltz")).thenReturn(stubStyle);
        // Unknown style -> null
        when(danceManager.parseStyle("unknown")).thenReturn(null);
        // Any other unstubbed call -> null (Mockito default behavior)

        pm = new PlaylistManager(plugin, danceManager, staticDancerManager);
    }

    @AfterEach
    void tearDown() {
        // Best-effort cleanup of the temporary directory
        if (tempDir != null) {
            File yaml = new File(tempDir, "playlists.yml");
            yaml.delete();
            tempDir.delete();
        }
    }

    // =========================================================================
    // createPlaylist
    // =========================================================================

    @Test
    void createPlaylist_loop_returnsTrueAndIsPersisted() {
        assertTrue(pm.createPlaylist("pl1", true));
        PlaylistManager.Playlist p = pm.getPlaylists().get("pl1");
        assertNotNull(p);
        assertTrue(p.loop);
        assertTrue(p.tracks.isEmpty());
    }

    @Test
    void createPlaylist_noLoop_storedWithLoopFalse() {
        pm.createPlaylist("pl2", false);
        assertFalse(pm.getPlaylists().get("pl2").loop);
    }

    @Test
    void createPlaylist_twoDifferentIds_bothPresent() {
        pm.createPlaylist("a", true);
        pm.createPlaylist("b", false);
        assertTrue(pm.getPlaylists().containsKey("a"));
        assertTrue(pm.getPlaylists().containsKey("b"));
    }

    @Test
    void createPlaylist_duplicateId_returnsFalseAndLeaveOriginalUnchanged() {
        pm.createPlaylist("dup", true);
        pm.addTrack("dup", "twist", 2); // on ajoute une piste pour vérifier qu'elle reste

        boolean second = pm.createPlaylist("dup", false);

        assertFalse(second, "Must return false on duplicate IDs");
        PlaylistManager.Playlist p = pm.getPlaylists().get("dup");
        assertTrue(p.loop,          "Original loop flag must be preserved");
        assertEquals(1, p.tracks.size(), "Original track must be preserved");
    }

    // =========================================================================
    // addTrack
    // =========================================================================

    @Test
    void addTrack_validStyle_returnsTrueAndTrackIsPresent() {
        pm.createPlaylist("pl", true);
        assertTrue(pm.addTrack("pl", "twist", 3));

        PlaylistManager.Playlist p = pm.getPlaylists().get("pl");
        assertEquals(1, p.tracks.size());
        assertEquals("twist", p.tracks.get(0).styleName());
        assertEquals(3,       p.tracks.get(0).repetitions());
    }

    @Test
    void addTrack_playlistNotFound_returnsFalse() {
        assertFalse(pm.addTrack("inexistant", "twist", 1));
    }

    @Test
    void addTrack_unknownStyle_returnsFalse() {
        pm.createPlaylist("pl", true);
        assertFalse(pm.addTrack("pl", "unknown", 1));
        assertTrue(pm.getPlaylists().get("pl").tracks.isEmpty());
    }

    @Test
    void addTrack_multipleTracks_orderPreservedFIFO() {
        pm.createPlaylist("pl", true);
        pm.addTrack("pl", "twist", 1);
        pm.addTrack("pl", "disco", 2);
        pm.addTrack("pl", "waltz", 3);

        PlaylistManager.Playlist p = pm.getPlaylists().get("pl");
        assertEquals(3, p.tracks.size());
        assertEquals("twist", p.tracks.get(0).styleName());
        assertEquals("disco", p.tracks.get(1).styleName());
        assertEquals("waltz", p.tracks.get(2).styleName());
    }

    // =========================================================================
    // removeTrack
    // =========================================================================

    @Test
    void removeTrack_firstTrackOfThree_returnsTrueAndOrderPreserved() {
        pm.createPlaylist("pl", true);
        pm.addTrack("pl", "twist", 1);
        pm.addTrack("pl", "disco", 1);
        pm.addTrack("pl", "waltz", 1);

        assertTrue(pm.removeTrack("pl", 0));

        PlaylistManager.Playlist p = pm.getPlaylists().get("pl");
        assertEquals(2, p.tracks.size());
        assertEquals("disco", p.tracks.get(0).styleName());
        assertEquals("waltz", p.tracks.get(1).styleName());
    }

    @Test
    void removeTrack_lastTrack_playlistBecomesEmpty() {
        pm.createPlaylist("pl", true);
        pm.addTrack("pl", "twist", 1);

        assertTrue(pm.removeTrack("pl", 0));
        assertTrue(pm.getPlaylists().get("pl").tracks.isEmpty());
    }

    @Test
    void removeTrack_negativeIndex_returnsFalse() {
        pm.createPlaylist("pl", true);
        pm.addTrack("pl", "twist", 1);
        assertFalse(pm.removeTrack("pl", -1));
    }

    @Test
    void removeTrack_indexEqualToSize_returnsFalse() {
        pm.createPlaylist("pl", true);
        pm.addTrack("pl", "twist", 1);
        assertFalse(pm.removeTrack("pl", 1)); // size = 1, index 1 is out of bounds
    }

    @Test
    void removeTrack_playlistNotFound_returnsFalse() {
        assertFalse(pm.removeTrack("inexistant", 0));
    }

    // =========================================================================
    // deletePlaylist
    // =========================================================================

    @Test
    void deletePlaylist_existing_returnsTrueAndRemovedFromMap() {
        pm.createPlaylist("del", true);
        assertTrue(pm.deletePlaylist("del"));
        assertFalse(pm.getPlaylists().containsKey("del"));
    }

    @Test
    void deletePlaylist_notExisting_returnsFalse() {
        assertFalse(pm.deletePlaylist("fantome"));
    }

    @Test
    void deletePlaylist_thenRecreateWithSameId_succeeds() {
        pm.createPlaylist("recycle", true);
        pm.deletePlaylist("recycle");
        assertTrue(pm.createPlaylist("recycle", false));
        assertFalse(pm.getPlaylists().get("recycle").loop);
    }

    // =========================================================================
    // Immutability of returned collections
    // =========================================================================

    @Test
    void getPlaylists_returnedMapIsUnmodifiable() {
        pm.createPlaylist("p", true);
        Map<String, PlaylistManager.Playlist> map = pm.getPlaylists();
        assertThrows(UnsupportedOperationException.class, () -> map.put("hack", null));
    }

    @Test
    void getPlaylistIds_returnedSetIsUnmodifiable() {
        pm.createPlaylist("p", true);
        Set<String> ids = pm.getPlaylistIds();
        assertThrows(UnsupportedOperationException.class, () -> ids.add("hack"));
    }
}
