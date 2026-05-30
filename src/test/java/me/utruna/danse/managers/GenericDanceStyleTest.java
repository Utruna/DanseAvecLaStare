package me.utruna.danse.managers;

import org.bukkit.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour GenericDanceStyle.
 * La classe est dans le même package (src/test), ce qui rend normalizeYaw (protected) accessible.
 */
class GenericDanceStyleTest {

    private static final float DELTA = 1e-4f;

    private GenericDanceStyle staticStyle;
    private GenericDanceStyle dynamicStyle;

    @BeforeEach
    void setUp() {
        staticStyle  = new GenericDanceStyle("test", true,  "wave", 1.0, 1.0);
        dynamicStyle = new GenericDanceStyle("test", false, "wave", 1.0, 1.0);
    }

    // =========================================================================
    // normalizeYaw
    // =========================================================================

    @Test
    void normalizeYaw_zero_returnsZero() {
        assertEquals(0f, staticStyle.normalizeYaw(0f), DELTA);
    }

    @Test
    void normalizeYaw_belowThreshold_unchanged() {
        assertEquals(179.9f, staticStyle.normalizeYaw(179.9f), DELTA);
    }

    @Test
    void normalizeYaw_atThreshold180_mapsToNegative180() {
        // 180 ≥ 180 → 180 - 360 = -180
        assertEquals(-180f, staticStyle.normalizeYaw(180f), DELTA);
    }

    @Test
    void normalizeYaw_200_mapsToMinus160() {
        // 200 - 360 = -160
        assertEquals(-160f, staticStyle.normalizeYaw(200f), DELTA);
    }

    @Test
    void normalizeYaw_360_returnsZero() {
        // 360 - 360 = 0
        assertEquals(0f, staticStyle.normalizeYaw(360f), DELTA);
    }

    @Test
    void normalizeYaw_540_loopsTwiceToNegative180() {
        // 540 - 360 = 180 (encore ≥ 180) → 180 - 360 = -180
        // L'intervalle cible est [-180, 180) : 180 lui-même est exclu et ramené à -180.
        assertEquals(-180f, staticStyle.normalizeYaw(540f), DELTA);
    }

    @Test
    void normalizeYaw_negative180_isStable() {
        // La condition est yaw < -180, donc -180 ne déclenche pas la boucle
        assertEquals(-180f, staticStyle.normalizeYaw(-180f), DELTA);
    }

    @Test
    void normalizeYaw_negative181_mapsTo179() {
        // -181 + 360 = 179
        assertEquals(179f, staticStyle.normalizeYaw(-181f), DELTA);
    }

    @Test
    void normalizeYaw_negative360_returnsZero() {
        // -360 + 360 = 0
        assertEquals(0f, staticStyle.normalizeYaw(-360f), DELTA);
    }

    @Test
    void normalizeYaw_negative540_mapsToNegative180() {
        // -540 + 360 = -180 ; -180 < -180 ? Non → s'arrête
        assertEquals(-180f, staticStyle.normalizeYaw(-540f), DELTA);
    }

    // =========================================================================
    // computeLocation — mode STATIC
    // =========================================================================

    @Test
    void static_preservesXYZAndYaw_atVariousTicks() {
        Location origin = new Location(null, 10.0, 64.0, 20.0, 45.0f, 0.0f);

        for (int tick : new int[]{0, 1, 100}) {
            Location result = staticStyle.computeLocation(origin, tick);
            assertEquals(origin.getX(),   result.getX(),   1e-9,  "X at tick " + tick);
            assertEquals(origin.getY(),   result.getY(),   1e-9,  "Y at tick " + tick);
            assertEquals(origin.getZ(),   result.getZ(),   1e-9,  "Z at tick " + tick);
            assertEquals(origin.getYaw(), result.getYaw(), DELTA, "Yaw at tick " + tick);
        }
    }

    @Test
    void static_returnsClone_notSameReference() {
        Location origin = new Location(null, 5.0, 70.0, 5.0, 0.0f, 0.0f);
        assertNotSame(origin, staticStyle.computeLocation(origin, 0));
    }

    // =========================================================================
    // computeLocation — mode DYNAMIC
    // =========================================================================

    @Test
    void dynamic_preservesXYZ_acrossAllTicks() {
        Location origin = new Location(null, 3.0, 65.0, 7.0, 90.0f, 0.0f);

        for (int tick = 0; tick <= 50; tick++) {
            Location result = dynamicStyle.computeLocation(origin, tick);
            assertEquals(origin.getX(), result.getX(), 1e-9, "X at tick " + tick);
            assertEquals(origin.getY(), result.getY(), 1e-9, "Y at tick " + tick);
            assertEquals(origin.getZ(), result.getZ(), 1e-9, "Z at tick " + tick);
        }
    }

    @Test
    void dynamic_yawAtTick0_equalsOriginYaw() {
        // phase = 0 * 0.15 = 0 ; Math.sin(0) = 0 → aucun offset
        Location origin = new Location(null, 0.0, 64.0, 0.0, 30.0f, 0.0f);
        assertEquals(origin.getYaw(), dynamicStyle.computeLocation(origin, 0).getYaw(), DELTA);
    }

    @Test
    void dynamic_yawAtTick10_differFromOriginYaw() {
        // phase = 1.5 ; Math.sin(1.5) ≈ 0.997 → offset ≈ +39.9°
        Location origin = new Location(null, 0.0, 64.0, 0.0, 0.0f, 0.0f);
        float resultYaw = dynamicStyle.computeLocation(origin, 10).getYaw();
        assertNotEquals(origin.getYaw(), resultYaw, DELTA);
    }

    @Test
    void dynamic_yawAlwaysInRange_withHighStartYaw() {
        // Yaw de départ à 170 pour forcer le dépassement de borne sur le positif
        Location origin = new Location(null, 0.0, 64.0, 0.0, 170.0f, 0.0f);

        for (int tick = 0; tick <= 100; tick++) {
            float yaw = dynamicStyle.computeLocation(origin, tick).getYaw();
            assertTrue(yaw >= -180f && yaw < 180f,
                    "Yaw " + yaw + " hors de [-180, 180) au tick " + tick);
        }
    }

    @Test
    void dynamic_returnsClone_notSameReference() {
        Location origin = new Location(null, 0.0, 64.0, 0.0, 0.0f, 0.0f);
        assertNotSame(origin, dynamicStyle.computeLocation(origin, 5));
    }

    // =========================================================================
    // Getters
    // =========================================================================

    @Test
    void getName_returnsExactConstructorValue() {
        GenericDanceStyle s = new GenericDanceStyle("waltz", true, "wave", 0, 0);
        assertEquals("waltz", s.getName());
    }

    @Test
    void isStatic_trueWhenBuiltWithTrue() {
        assertTrue(new GenericDanceStyle("x", true, "wave", 0, 0).isStatic());
    }

    @Test
    void isStatic_falseWhenBuiltWithFalse() {
        assertFalse(new GenericDanceStyle("x", false, "wave", 0, 0).isStatic());
    }

    @Test
    void getPattern_nullPatternBecomesWave() {
        GenericDanceStyle s = new GenericDanceStyle("x", false, null, 0, 0);
        assertEquals("wave", s.getPattern());
    }

    @Test
    void getPattern_waveIsPreserved() {
        GenericDanceStyle s = new GenericDanceStyle("x", false, "wave", 0, 0);
        assertEquals("wave", s.getPattern());
    }

    @Test
    void getPattern_staticModePreservesPatternAsLowercase() {
        // Le constructeur applique toLowerCase() sur le pattern
        GenericDanceStyle s = new GenericDanceStyle("x", true, "CIRCLE", 0, 0);
        assertEquals("circle", s.getPattern());
    }
}
