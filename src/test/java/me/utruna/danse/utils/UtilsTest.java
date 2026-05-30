package me.utruna.danse.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour Utils.colorize.
 *
 * Prérequis : net.md_5.bungee.api.ChatColor est disponible via paper-api (scope provided).
 * ChatColor.translateAlternateColorCodes et ChatColor.of sont de pures méthodes statiques
 * qui n'ont pas besoin d'un serveur en cours d'exécution.
 *
 * Si ChatColor n'est pas disponible sur le classpath de test (ex : environnement CI sans
 * paper-api), tous les tests de cette classe échoueront avec ClassNotFoundException.
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
        // Pattern.matcher(null) lève NPE — comportement documenté, non-modifiable sans breaking change.
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
        // #FF0000 doit être remplacé par la séquence §x§... de ChatColor
        String result = Utils.colorize("#FF0000Rouge");
        assertFalse(result.contains("#FF0000"), "Le code hex brut ne doit plus apparaître");
        assertTrue(result.contains(String.valueOf(SECTION)), "Le résultat doit contenir § (code couleur Minecraft)");
        assertTrue(result.contains("Rouge"), "Le texte suivant doit être conservé");
    }

    @Test
    void colorize_twoHexCodes_bothReplaced() {
        String result = Utils.colorize("#FF0000Rouge #00FF00Vert");
        assertFalse(result.contains("#FF0000"), "Premier hex doit être remplacé");
        assertFalse(result.contains("#00FF00"), "Second hex doit être remplacé");
        assertTrue(result.contains(String.valueOf(SECTION)));
        assertTrue(result.contains("Rouge"));
        assertTrue(result.contains("Vert"));
    }

    @Test
    void colorize_invalidAmpersandSequence_leftUnchanged() {
        // &# : '#' n'est pas un code couleur Minecraft valide → translateAlternateColorCodes ne le traduit pas
        // Le pattern hex #[a-fA-F0-9]{6} ne matche pas "invalid"
        assertEquals("&#invalid", Utils.colorize("&#invalid"));
    }

    @Test
    void colorize_hexAndAmpersandCode_bothConverted() {
        // Les deux mécanismes (hex + &) doivent coexister
        String result = Utils.colorize("#FF0000&aTexte");
        assertFalse(result.contains("#FF0000"), "Code hex doit être remplacé");
        assertTrue(result.contains(SECTION + "a"), "Code & doit être traduit en §a");
    }
}
