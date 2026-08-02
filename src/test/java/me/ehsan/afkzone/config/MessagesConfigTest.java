package me.ehsan.afkzone.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void loadReadsTitleTimingSettingsForRegularMessages() throws Exception {
        Path dataFolder = tempDir.resolve("plugin");
        Files.createDirectories(dataFolder);
        Files.writeString(dataFolder.resolve("messages.yml"), """
                enter_zone:
                  text: "Hello"
                  display: "title"
                  title:
                    fade_in: 10
                    stay: 20
                    fade_out: 30
                """);

        MessagesConfig config = new MessagesConfig(dataFolder.toFile(), Logger.getLogger("test"));
        config.load();

        MessagesConfig.MessageEntry entry = config.getEnterZone();
        assertEquals("title", entry.getDisplay());
        assertEquals(10, entry.getTitleFadeIn());
        assertEquals(20, entry.getTitleStay());
        assertEquals(30, entry.getTitleFadeOut());
    }

}
