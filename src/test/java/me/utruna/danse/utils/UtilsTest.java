package me.utruna.danse.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Utils.colorize.
 *
 * Prerequisite: net.md_5.bungee.api.ChatColor is available through paper-api (provided scope).
 * ChatColor.translateAlternateColorCodes and ChatColor.of are pure static methods
 * that do not require a running server.
 *
 * If ChatColor is not available on the test classpath (for example, a CI environment without
 * paper-api), all tests in this class will fail with ClassNotFoundException.
 */
class UtilsTest {

    private static final char SECTION = '§'; // §

    @Test
    void colorize_noColorCode_returnsStringUnchanged() {
        assertEquals("Bonjour tout le monde", Utils.colorize("Bonjour tout le monde"));
    }

    @Test
    void colorize_emptyString_returnsEmptyString() {
        assertEquals("", Utils.colorize(""));
    }

    @Test
    void colorize_null_throwsNullPointerException() {
        // Pattern.matcher(null) throws NPE - documented behavior, not changeable without a breaking change.
        assertThrows(NullPointerException.class, () -> Utils.colorize(null));
    }

    @Test
    void colorize_ampersandColorCode_translatedToSectionSign() {
        // &a → §a (vert)
        String result = Utils.colorize("&aBonjour");
        assertEquals(SECTION + "aBonjour", result);
    }

    @Test
    void colorize_formattingAndResetCodes_bothTranslated() {
        // &l = gras, &r = reset
        String result = Utils.colorize("&lBold &rReset");
        assertEquals(SECTION + "lBold " + SECTION + "rReset", result);
    }

    @Test
    void colorize_singleHexCode_replacedWithSectionSequence() {
        // #FF0000 must be replaced by ChatColor's §x§... sequence
        String result = Utils.colorize("#FF0000Rouge");
        assertFalse(result.contains("#FF0000"), "The raw hex code must no longer appear");
        assertTrue(result.contains(String.valueOf(SECTION)), "The result must contain § (Minecraft color code)");
        assertTrue(result.contains("Rouge"), "The following text must be preserved");
    }

    @Test
    void colorize_twoHexCodes_bothReplaced() {
        String result = Utils.colorize("#FF0000Rouge #00FF00Vert");
        assertFalse(result.contains("#FF0000"), "First hex must be replaced");
        assertFalse(result.contains("#00FF00"), "Second hex must be replaced");
        assertTrue(result.contains(String.valueOf(SECTION)));
        assertTrue(result.contains("Rouge"));
        assertTrue(result.contains("Vert"));
    }

    @Test
    void colorize_invalidAmpersandSequence_leftUnchanged() {
        // &#: '#' is not a valid Minecraft color code -> translateAlternateColorCodes does not translate it
        // The hex pattern #[a-fA-F0-9]{6} does not match "invalid"
        assertEquals("&#invalid", Utils.colorize("&#invalid"));
    }

    @Test
    void colorize_hexAndAmpersandCode_bothConverted() {
        // Both mechanisms (hex + &) must coexist
        String result = Utils.colorize("#FF0000&aTexte");
        assertFalse(result.contains("#FF0000"), "Hex code must be replaced");
        assertTrue(result.contains(SECTION + "a"), "& code must be translated to §a");
    }
}
