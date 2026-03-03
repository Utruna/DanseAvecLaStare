package me.utruna.danse;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PluginYamlTest {

    @Test
    void pluginYmlShouldNotContainTabs() throws IOException {
        String content = Files.readString(Path.of("src/main/resources/plugin.yml"));
        assertFalse(content.contains("\t"), "plugin.yml ne doit pas contenir de tabulations");
    }

    @Test
    void pluginYmlShouldContainDanseCommand() throws IOException {
        String content = Files.readString(Path.of("src/main/resources/plugin.yml"));

        assertTrue(content.contains("commands:"));
        assertTrue(content.contains("danse:"));
        assertTrue(content.contains("usage: /danse"));
    }
}
