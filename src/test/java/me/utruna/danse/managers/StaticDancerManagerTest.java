package me.utruna.danse.managers;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.profile.PlayerProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour StaticDancerManager.
 *
 * Note : les tests nécessitant ModelEngineAPI (spawnStaticDancer, loadFromFile complet)
 * sont hors scope ici — ModelEngine est une dépendance runtime non disponible en test.
 * Les tests ci-dessous couvrent la logique pure (contrats API, persistance YAML)
 * en injectant des entrées directement via reflection pour éviter ModelEngine.
 */
class StaticDancerManagerTest {

    @TempDir
    Path tempDir;

    private ServerMock server;
    private DanseAvecLaStare pluginMock;
    private StaticDancerManager manager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();

        pluginMock = mock(DanseAvecLaStare.class);
        when(pluginMock.getDataFolder()).thenReturn(tempDir.toFile());
        when(pluginMock.getLogger()).thenReturn(Logger.getLogger("DanseTest"));

        FileConfiguration config = mock(FileConfiguration.class);
        when(pluginMock.getConfig()).thenReturn(config);

        manager = new StaticDancerManager(pluginMock);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // -------------------------------------------------------------------------
    // changeSkin — contrats de base
    // -------------------------------------------------------------------------

    @Test
    void changeSkin_returnsFalse_whenDancerDoesNotExist() {
        PlayerProfile profile = mock(PlayerProfile.class);
        assertFalse(manager.changeSkin("inexistant", profile, "skin1"),
                "changeSkin doit retourner false si le danseur n'existe pas");
    }

    @SuppressWarnings("deprecation")
    @Test
    void changeSkin_returnsFalse_whenProfileIsNull() throws Exception {
        World world = server.addSimpleWorld("testWorld");
        injectDancer("dancer_null_profile", mock(PlayerProfile.class), "oldSkin",
                new Location(world, 0, 64, 0, 0f, 0f));

        assertFalse(manager.changeSkin("dancer_null_profile", null, "newSkin"),
                "changeSkin doit retourner false si le profil est null");
    }

    @SuppressWarnings("deprecation")
    @Test
    void changeSkin_updatesProfileSkinNameAndPersistsToYaml() throws Exception {
        World world = server.addSimpleWorld("testWorld2");
        Location loc = new Location(world, 10, 65, -5, 90f, 0f);

        PlayerProfile oldProfile = mock(PlayerProfile.class);
        PlayerProfile newProfile = mock(PlayerProfile.class);
        injectDancer("dancer1", oldProfile, "oldSkin", loc);

        boolean result = manager.changeSkin("dancer1", newProfile, "newSkin");

        assertTrue(result, "changeSkin doit retourner true pour un danseur existant avec profil non-null");
        assertEquals(newProfile, manager.getDancerProfile("dancer1"),
                "Le profil doit être mis à jour");
        assertEquals("newSkin", manager.getDancerSkin("dancer1"),
                "Le nom de skin doit être mis à jour");

        // Vérification persistence
        File savedFile = new File(tempDir.toFile(), "static_dancers.yml");
        assertTrue(savedFile.exists(), "static_dancers.yml doit exister après changeSkin");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(savedFile);
        assertEquals("newSkin", saved.getString("dancers.dancer1.skin"),
                "La nouvelle valeur skin doit être dans le fichier YAML");
        assertEquals("testWorld2", saved.getString("dancers.dancer1.world"),
                "Le monde doit être persiste dans le YAML");
    }

    // -------------------------------------------------------------------------
    // removeStaticDancer
    // -------------------------------------------------------------------------

    @Test
    void removeStaticDancer_returnsFalse_whenDancerNotFound() {
        assertFalse(manager.removeStaticDancer("inexistant"),
                "removeStaticDancer doit retourner false si le danseur n'existe pas");
    }

    @SuppressWarnings("deprecation")
    @Test
    void removeStaticDancer_removesFromActiveDancers_whenFound() throws Exception {
        World world = server.addSimpleWorld("world");
        injectDancer("dancer_remove", mock(PlayerProfile.class), "skin", new Location(world, 0, 64, 0));

        assertTrue(manager.removeStaticDancer("dancer_remove"), "removeStaticDancer doit retourner true");
        assertFalse(manager.getDancerIds().contains("dancer_remove"),
                "Le danseur doit être absent de activeDancers après suppression");
    }

    // -------------------------------------------------------------------------
    // renameDancer
    // -------------------------------------------------------------------------

    @Test
    void renameDancer_returnsFalse_whenOldIdNotFound() {
        assertFalse(manager.renameDancer("inexistant", "nouveau"),
                "renameDancer doit retourner false si l'ancien ID n'existe pas");
    }

    @SuppressWarnings("deprecation")
    @Test
    void renameDancer_returnsFalse_whenNewIdAlreadyTaken() throws Exception {
        World world = server.addSimpleWorld("world");
        Location loc = new Location(world, 0, 64, 0);
        injectDancer("d_old", mock(PlayerProfile.class), "skin", loc);
        injectDancer("d_new", mock(PlayerProfile.class), "skin", loc);

        assertFalse(manager.renameDancer("d_old", "d_new"),
                "renameDancer doit retourner false si le nouvel ID est déjà pris");
    }

    @SuppressWarnings("deprecation")
    @Test
    void renameDancer_updatesKeysAndPersistsToYaml() throws Exception {
        World world = server.addSimpleWorld("world");
        Location loc = new Location(world, 5, 70, -3, 45f, 0f);
        injectDancer("d_old", mock(PlayerProfile.class), "mySkin", loc);

        assertTrue(manager.renameDancer("d_old", "d_new"), "renameDancer doit retourner true");
        assertFalse(manager.getDancerIds().contains("d_old"), "L'ancien ID doit être absent");
        assertTrue(manager.getDancerIds().contains("d_new"), "Le nouvel ID doit être présent");
        assertEquals("mySkin", manager.getDancerSkin("d_new"),
                "Le skin doit être conservé après renommage");

        File savedFile = new File(tempDir.toFile(), "static_dancers.yml");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(savedFile);
        assertNull(saved.get("dancers.d_old"), "L'ancien ID ne doit plus être dans le YAML");
        assertEquals("mySkin", saved.getString("dancers.d_new.skin"),
                "Le nouvel ID doit être persisté dans le YAML");
    }

    // -------------------------------------------------------------------------
    // getDancerLocation
    // -------------------------------------------------------------------------

    @Test
    void getDancerLocation_returnsNull_whenDancerNotFound() {
        assertNull(manager.getDancerLocation("inexistant"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void getDancerLocation_returnsClone_notSameReference() throws Exception {
        World world = server.addSimpleWorld("world");
        Location original = new Location(world, 10, 64, 20, 90f, 0f);
        injectDancer("dancer_loc", mock(PlayerProfile.class), "skin", original);

        Location returned = manager.getDancerLocation("dancer_loc");
        assertNotNull(returned);
        assertEquals(original.getX(), returned.getX());
        assertEquals(original.getY(), returned.getY());
        assertEquals(original.getZ(), returned.getZ());
        assertNotSame(original, returned, "getDancerLocation doit retourner un clone");
    }

    // -------------------------------------------------------------------------
    // setScale
    // -------------------------------------------------------------------------

    @Test
    void setScale_returnsFalse_whenDancerNotFound() {
        assertFalse(manager.setScale("inexistant", 2.0),
                "setScale doit retourner false si le danseur n'existe pas");
    }

    @SuppressWarnings("deprecation")
    @Test
    void setScale_returnsFalse_whenActiveModelIsNull() throws Exception {
        World world = server.addSimpleWorld("world");
        injectDancer("dancer_scale", mock(PlayerProfile.class), "skin", new Location(world, 0, 64, 0));

        // activeModel est null dans l'entrée injectée → doit retourner false
        assertFalse(manager.setScale("dancer_scale", 2.0),
                "setScale doit retourner false si activeModel est null");
        assertEquals(1.0, manager.getDancerScale("dancer_scale"),
                "La scale ne doit pas être modifiée si activeModel est null");
    }

    // -------------------------------------------------------------------------
    // Chorégraphie
    // -------------------------------------------------------------------------

    @Test
    void getChoreographyGroups_emptyInitially() {
        assertTrue(manager.getChoreographyGroups().isEmpty(),
                "Aucun groupe de chorégraphie à la création");
    }

    @Test
    void createChoreography_returnsFalse_whenDancerNotFound() {
        assertFalse(manager.createChoreography("group1", List.of("inexistant")),
                "createChoreography doit retourner false si un danseur est absent");
    }

    @SuppressWarnings("deprecation")
    @Test
    void createChoreography_createsGroupAndPersistsToYaml() throws Exception {
        World world = server.addSimpleWorld("world");
        Location loc = new Location(world, 0, 64, 0);
        injectDancer("d1", mock(PlayerProfile.class), "skin1", loc);
        injectDancer("d2", mock(PlayerProfile.class), "skin2", loc);

        assertTrue(manager.createChoreography("group1", List.of("d1", "d2")));
        assertTrue(manager.getChoreographyGroups().containsKey("group1"));
        assertTrue(manager.getChoreographyGroups().get("group1").containsAll(List.of("d1", "d2")));

        File choreoFile = new File(tempDir.toFile(), "choreography.yml");
        assertTrue(choreoFile.exists(), "choreography.yml doit être créé");
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(choreoFile);
        List<String> members = saved.getStringList("groups.group1");
        assertTrue(members.containsAll(List.of("d1", "d2")),
                "Les membres doivent être persistés dans choreography.yml");
    }

    @SuppressWarnings("deprecation")
    @Test
    void addToChoreography_addsDancerToGroup() throws Exception {
        World world = server.addSimpleWorld("world");
        Location loc = new Location(world, 0, 64, 0);
        injectDancer("d1", mock(PlayerProfile.class), "skin", loc);
        injectDancer("d2", mock(PlayerProfile.class), "skin", loc);

        manager.createChoreography("group1", List.of("d1"));
        assertTrue(manager.addToChoreography("group1", "d2"));
        assertTrue(manager.getChoreographyGroups().get("group1").contains("d2"),
                "d2 doit être dans le groupe après addToChoreography");
    }

    @SuppressWarnings("deprecation")
    @Test
    void removeFromChoreography_removesDancerFromGroup() throws Exception {
        World world = server.addSimpleWorld("world");
        Location loc = new Location(world, 0, 64, 0);
        injectDancer("d1", mock(PlayerProfile.class), "skin", loc);
        injectDancer("d2", mock(PlayerProfile.class), "skin", loc);

        manager.createChoreography("group1", List.of("d1", "d2"));
        assertTrue(manager.removeFromChoreography("group1", "d1"));
        assertFalse(manager.getChoreographyGroups().get("group1").contains("d1"),
                "d1 ne doit plus être dans le groupe");
        assertTrue(manager.getChoreographyGroups().get("group1").contains("d2"),
                "d2 doit rester dans le groupe");
    }

    @SuppressWarnings("deprecation")
    @Test
    void deleteChoreography_removesGroup() throws Exception {
        World world = server.addSimpleWorld("world");
        injectDancer("d1", mock(PlayerProfile.class), "skin", new Location(world, 0, 64, 0));

        manager.createChoreography("group1", List.of("d1"));
        assertTrue(manager.deleteChoreography("group1"));
        assertFalse(manager.getChoreographyGroups().containsKey("group1"),
                "Le groupe doit être absent après deleteChoreography");
    }

    @Test
    void deleteChoreography_returnsFalse_whenGroupNotFound() {
        assertFalse(manager.deleteChoreography("inexistant"),
                "deleteChoreography doit retourner false si le groupe n'existe pas");
    }

    // -------------------------------------------------------------------------
    // applyRenderRadiusToActiveDancers
    // -------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    @Test
    void applyRenderRadiusToActiveDancers_doesNotThrowWithNullDummy() throws Exception {
        World world = server.addSimpleWorld("world");
        injectDancer("dancer_nodummy", mock(PlayerProfile.class), "skin", new Location(world, 0, 64, 0));
        // dummy est null dans l'entrée injectée — ne doit pas lever NPE
        assertDoesNotThrow(() -> manager.applyRenderRadiusToActiveDancers(128));
    }

    // -------------------------------------------------------------------------
    // highlightDancer
    // -------------------------------------------------------------------------

    @Test
    void highlightDancer_returnsFalse_whenDancerNotFound() {
        assertFalse(manager.highlightDancer("inexistant", 3),
                "highlightDancer doit retourner false si le danseur n'existe pas");
    }

    @SuppressWarnings("deprecation")
    @Test
    void highlightDancer_returnsTrue_whenDancerExists() throws Exception {
        World world = server.addSimpleWorld("world");
        Location loc = new Location(world, 0, 64, 0);
        injectDancer("dancer_highlight", mock(PlayerProfile.class), "skin", loc);

        assertTrue(manager.highlightDancer("dancer_highlight", 1),
                "highlightDancer doit retourner true pour un danseur existant");
    }

    // -------------------------------------------------------------------------
    // API de base — smoke tests sans ModelEngine
    // -------------------------------------------------------------------------

    @Test
    void getDancerIds_emptyOnCreation() {
        assertTrue(manager.getDancerIds().isEmpty(),
                "Aucun danseur actif à la création");
    }

    @Test
    void getDancerProfile_returnsNull_whenDancerDoesNotExist() {
        assertNull(manager.getDancerProfile("inexistant"));
    }

    @Test
    void getDancerSkin_returnsNull_whenDancerDoesNotExist() {
        assertNull(manager.getDancerSkin("inexistant"));
    }

    @Test
    void getDancerStyle_returnsNull_whenDancerDoesNotExist() {
        assertNull(manager.getDancerStyle("inexistant"));
    }

    @Test
    void getDancerScale_returnsDefaultOne_whenDancerDoesNotExist() {
        assertEquals(1.0, manager.getDancerScale("inexistant"),
                "Scale par défaut doit être 1.0 si le danseur n'existe pas");
    }

    // -------------------------------------------------------------------------
    // getDancersNear
    // -------------------------------------------------------------------------

    @Test
    void getDancersNear_returnsEmpty_whenNoDancers() {
        World world = server.addSimpleWorld("world");
        assertTrue(manager.getDancersNear(new Location(world, 0, 64, 0), 10).isEmpty(),
                "Doit retourner une map vide s'il n'y a aucun danseur");
    }

    @SuppressWarnings("deprecation")
    @Test
    void getDancersNear_returnsEmpty_whenAllBeyondRadius() throws Exception {
        World world = server.addSimpleWorld("world");
        injectDancer("far1", mock(PlayerProfile.class), "skin", new Location(world, 50, 64, 0));
        injectDancer("far2", mock(PlayerProfile.class), "skin", new Location(world,  0, 64, 50));

        assertTrue(manager.getDancersNear(new Location(world, 0, 64, 0), 10).isEmpty(),
                "Doit retourner une map vide si tous les danseurs sont hors du rayon");
    }

    @SuppressWarnings("deprecation")
    @Test
    void getDancersNear_returnsAllWithinRadius() throws Exception {
        World world = server.addSimpleWorld("world");
        injectDancer("a", mock(PlayerProfile.class), "skin", new Location(world, 3, 64, 0));
        injectDancer("b", mock(PlayerProfile.class), "skin", new Location(world, 0, 64, 4));

        Map<String, Double> result = manager.getDancersNear(new Location(world, 0, 64, 0), 10);
        assertEquals(2, result.size());
        assertTrue(result.containsKey("a"));
        assertTrue(result.containsKey("b"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void getDancersNear_excludesDancersOutsideRadius() throws Exception {
        World world = server.addSimpleWorld("world");
        injectDancer("inside",  mock(PlayerProfile.class), "skin", new Location(world,  5, 64, 0));
        injectDancer("outside", mock(PlayerProfile.class), "skin", new Location(world, 20, 64, 0));

        Map<String, Double> result = manager.getDancersNear(new Location(world, 0, 64, 0), 10);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("inside"));
        assertFalse(result.containsKey("outside"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void getDancersNear_sortedByAscendingDistance() throws Exception {
        World world = server.addSimpleWorld("world");
        injectDancer("close", mock(PlayerProfile.class), "skin", new Location(world, 2, 64, 0));
        injectDancer("far",   mock(PlayerProfile.class), "skin", new Location(world, 8, 64, 0));
        injectDancer("mid",   mock(PlayerProfile.class), "skin", new Location(world, 5, 64, 0));

        List<String> order = new ArrayList<>(manager.getDancersNear(new Location(world, 0, 64, 0), 15).keySet());
        assertEquals(List.of("close", "mid", "far"), order,
                "Les danseurs doivent être triés par distance croissante");
    }

    @SuppressWarnings("deprecation")
    @Test
    void getDancersNear_distanceValueIsCorrect() throws Exception {
        World world = server.addSimpleWorld("world");
        // triangle 3-4-5 → distance exacte = 5.0
        injectDancer("d", mock(PlayerProfile.class), "skin", new Location(world, 3, 64, 4));

        Map<String, Double> result = manager.getDancersNear(new Location(world, 0, 64, 0), 10);
        assertEquals(1, result.size());
        assertEquals(5.0, result.get("d"), 1e-9,
                "La distance doit être exactement 5.0 (triangle pythagoricien 3-4-5)");
    }

    @SuppressWarnings("deprecation")
    @Test
    void getDancersNear_includedExactlyOnBoundary() throws Exception {
        World world = server.addSimpleWorld("world");
        injectDancer("boundary", mock(PlayerProfile.class), "skin", new Location(world, 10, 64, 0));

        Map<String, Double> result = manager.getDancersNear(new Location(world, 0, 64, 0), 10);
        assertTrue(result.containsKey("boundary"),
                "Un danseur exactement sur la limite du rayon doit être inclus");
        assertEquals(10.0, result.get("boundary"), 1e-9);
    }

    @SuppressWarnings("deprecation")
    @Test
    void getDancersNear_excludesDancerInDifferentWorld() throws Exception {
        World world1 = server.addSimpleWorld("world1");
        World world2 = server.addSimpleWorld("world2");
        injectDancer("other", mock(PlayerProfile.class), "skin", new Location(world2, 1, 64, 0));

        assertTrue(manager.getDancersNear(new Location(world1, 0, 64, 0), 100).isEmpty(),
                "Un danseur dans un monde différent ne doit pas être retourné");
    }

    // -------------------------------------------------------------------------
    // changeDancerSkin — avec alias cache
    // -------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    @Test
    void changeDancerSkin_returnsFalse_whenDancerDoesNotExist() {
        PlayerProfile profile = mock(PlayerProfile.class);
        assertFalse(manager.changeDancerSkin("inexistant", profile, null, "alias1"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void changeDancerSkin_returnsFalse_whenProfileIsNull() throws Exception {
        World world = server.addSimpleWorld("worldDCS");
        injectDancer("d_dcs_null", mock(PlayerProfile.class), "old", new Location(world, 0, 64, 0));
        assertFalse(manager.changeDancerSkin("d_dcs_null", null, null, "alias1"),
                "changeDancerSkin doit retourner false si le profil est null");
    }

    @SuppressWarnings("deprecation")
    @Test
    void changeDancerSkin_updatesProfileAliasAndPersists() throws Exception {
        World world = server.addSimpleWorld("worldDCS2");
        Location loc = new Location(world, 5, 64, 5, 0f, 0f);
        PlayerProfile oldProfile = mock(PlayerProfile.class);
        PlayerProfile newProfile = mock(PlayerProfile.class);
        injectDancer("d_dcs", oldProfile, "oldSkin", loc);

        boolean ok = manager.changeDancerSkin("d_dcs", newProfile, null, "my_alias");

        assertTrue(ok);
        assertSame(newProfile, manager.getDancerProfile("d_dcs"), "Profil doit être mis à jour");
        assertEquals("my_alias", manager.getDancerSkinAlias("d_dcs"), "Alias doit être enregistré");
        assertNull(manager.getDancerSkin("d_dcs"), "skinName doit être null quand alias est utilisé");

        // Persistance YAML
        File savedFile = new File(tempDir.toFile(), "static_dancers.yml");
        assertTrue(savedFile.exists());
        YamlConfiguration saved = YamlConfiguration.loadConfiguration(savedFile);
        assertEquals("my_alias", saved.getString("dancers.d_dcs.skin_alias"),
                "skin_alias doit être écrit dans le YAML");
        assertNull(saved.getString("dancers.d_dcs.skin"),
                "skin (nom joueur) doit être null quand alias est actif");
    }

    @SuppressWarnings("deprecation")
    @Test
    void changeSkin_clearsAlias() throws Exception {
        World world = server.addSimpleWorld("worldClearAlias");
        Location loc = new Location(world, 0, 64, 0);
        injectDancer("d_clear", mock(PlayerProfile.class), "oldSkin", loc);
        // Pose d'abord un alias
        manager.changeDancerSkin("d_clear", mock(PlayerProfile.class), null, "alias_x");
        assertEquals("alias_x", manager.getDancerSkinAlias("d_clear"));

        // changeSkin (sans alias) doit effacer l'alias
        manager.changeSkin("d_clear", mock(PlayerProfile.class), "newPlayerName");
        assertNull(manager.getDancerSkinAlias("d_clear"), "changeSkin doit effacer skinAlias");
        assertEquals("newPlayerName", manager.getDancerSkin("d_clear"));
    }

    @SuppressWarnings("deprecation")
    @Test
    void getDancerSkinAlias_returnsNull_whenNoDancerOrNoAlias() throws Exception {
        World world = server.addSimpleWorld("worldAlias");
        injectDancer("d_noalias", mock(PlayerProfile.class), "skin", new Location(world, 0, 64, 0));

        assertNull(manager.getDancerSkinAlias("inexistant"), "Doit retourner null pour danseur inconnu");
        assertNull(manager.getDancerSkinAlias("d_noalias"), "Doit retourner null si aucun alias défini");
    }

    // -------------------------------------------------------------------------
    // Helpers — injection d'une StaticDancerEntry via reflection
    // -------------------------------------------------------------------------

    /**
     * Injecte une StaticDancerEntry directement dans activeDancers via reflection,
     * sans passer par spawnStaticDancer (qui nécessite ModelEngine).
     */
    @SuppressWarnings({"deprecation", "unchecked"})
    private void injectDancer(String id, PlayerProfile profile, String skinName, Location location) throws Exception {
        Class<?> entryClass = findInnerClass("StaticDancerEntry");

        Constructor<?> ctor = entryClass.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object entry = ctor.newInstance();

        setField(entry, entryClass, "skinProfile", profile);
        setField(entry, entryClass, "skinName", skinName);
        setField(entry, entryClass, "styleName", "testStyle");
        setField(entry, entryClass, "location", location);
        setField(entry, entryClass, "activeModel", null); // null → applySkinToModel ignoré
        setField(entry, entryClass, "scale", 1.0);

        Field activeDancersField = StaticDancerManager.class.getDeclaredField("activeDancers");
        activeDancersField.setAccessible(true);
        Map<String, Object> activeDancers = (Map<String, Object>) activeDancersField.get(manager);
        activeDancers.put(id, entry);
    }

    private Class<?> findInnerClass(String simpleName) {
        for (Class<?> c : StaticDancerManager.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals(simpleName)) return c;
        }
        throw new IllegalStateException("Classe interne introuvable : " + simpleName);
    }

    private void setField(Object obj, Class<?> clazz, String fieldName, Object value) throws Exception {
        Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }
}
