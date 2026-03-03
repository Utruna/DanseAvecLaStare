package me.utruna.danse.managers;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DanceManagerTest {

    private final DanceManager danceManager = new DanceManager(null);

    @Test
    void parseStyleShouldBeCaseInsensitive() {
        assertEquals("twist", danceManager.parseStyle("twist").getName());
        assertEquals("spin", danceManager.parseStyle("SPIN").getName());
        assertEquals("disco", danceManager.parseStyle("DiScO").getName());
    }

    @Test
    void parseStyleShouldReturnNullForUnknownStyle() {
        assertNull(danceManager.parseStyle("bounce"));
    }

    @Test
    void getStylesLabelShouldContainAllStylesSorted() {
        assertEquals("disco, moonwalk, spin, twist, wave", danceManager.getStylesLabel());
    }

    @Test
    void twistShouldChangeYawAndKeepPosition() {
        Location origin = new Location(null, 10.0, 64.0, 20.0, 0.0f, 0.0f);

        DanceStyle twist = danceManager.parseStyle("twist");

        Location atTick0 = twist.computeLocation(origin, 0);
        Location atTick8 = twist.computeLocation(origin, 8);

        assertEquals(origin.getX(), atTick0.getX(), 1.0e-9);
        assertEquals(origin.getY(), atTick0.getY(), 1.0e-9);
        assertEquals(origin.getZ(), atTick0.getZ(), 1.0e-9);
        assertEquals(0.0f, atTick0.getYaw(), 1.0e-6f);

        assertEquals(origin.getX(), atTick8.getX(), 1.0e-9);
        assertNotEquals(origin.getYaw(), atTick8.getYaw(), 1.0e-6f);
        assertEquals(origin.getY(), atTick8.getY(), 1.0e-9);
    }

    @Test
    void spinShouldOnlyRotateYaw() {
        Location origin = new Location(null, 2.0, 70.0, 3.0, 15.0f, 0.0f);

        DanceStyle spin = danceManager.parseStyle("spin");

        Location atTick6 = spin.computeLocation(origin, 6);

        assertEquals(origin.getX(), atTick6.getX(), 1.0e-9);
        assertEquals(origin.getY(), atTick6.getY(), 1.0e-9);
        assertEquals(origin.getZ(), atTick6.getZ(), 1.0e-9);
        assertNotEquals(origin.getYaw(), atTick6.getYaw(), 1.0e-6f);
        assertEquals(0.0f, atTick6.getPitch(), 1.0e-6f);
    }

    @Test
    void discoShouldNeverGoBelowOriginY() {
        Location origin = new Location(null, 0.0, 80.0, 0.0, 45.0f, 0.0f);

        DanceStyle disco = danceManager.parseStyle("disco");

        for (int tick = 0; tick <= 80; tick += 2) {
            Location current = disco.computeLocation(origin, tick);
            assertTrue(current.getY() >= origin.getY(), "Y should stay above or equal to origin");
        }
    }
}
