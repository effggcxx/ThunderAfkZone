package me.ehsan.afkzone.config;

import me.ehsan.afkzone.Main;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.logging.Level;

/**
 * Loads and provides access to all player-facing messages from messages.yml.
 * Supports per-message display modes: chat, title, actionbar, bossbar.
 */
public class MessagesConfig {

    private final Main plugin;
    private FileConfiguration messages;

    // Message data: text + display mode
    private MessageEntry enterZone;
    private MessageEntry exitZone;
    private MessageEntry rewardReceived;
    private MessageEntry rewardFailed;
    private TimerMessageEntry timer;

    public MessagesConfig(Main plugin) {
        this.plugin = plugin;
    }

    /**
     * Loads (or reloads) messages.yml from disk.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);

        enterZone = loadMessageEntry("enter_zone");
        exitZone = loadMessageEntry("exit_zone");
        rewardReceived = loadMessageEntry("reward_received");
        rewardFailed = loadMessageEntry("reward_failed");
        timer = loadTimerMessageEntry("timer");

        plugin.getLogger().info("Messages config loaded (" + file.getAbsolutePath() + ")");
    }

    private MessageEntry loadMessageEntry(String path) {
        String text = messages.getString(path + ".text", "");
        String display = messages.getString(path + ".display", "chat");
        return new MessageEntry(text, display);
    }

    private TimerMessageEntry loadTimerMessageEntry(String path) {
        String text = messages.getString(path + ".text", "<timer> remaining until next reward");
        String display = messages.getString(path + ".display", "title");
        String size = messages.getString(path + ".size", "big");
        int fadeIn = messages.getInt(path + ".title.fade_in", 5);
        int stay = messages.getInt(path + ".title.stay", 40);
        int fadeOut = messages.getInt(path + ".title.fade_out", 5);
        return new TimerMessageEntry(text, display, size, fadeIn, stay, fadeOut);
    }

    // --- Getters ---

    public MessageEntry getEnterZone() { return enterZone; }
    public MessageEntry getExitZone() { return exitZone; }
    public MessageEntry getRewardReceived() { return rewardReceived; }
    public MessageEntry getRewardFailed() { return rewardFailed; }
    public TimerMessageEntry getTimer() { return timer; }

    // --- Data classes ---

    public static class MessageEntry {
        private final String text;
        private final String display;

        public MessageEntry(String text, String display) {
            this.text = text;
            this.display = display;
        }

        public String getText() { return text; }
        public String getDisplay() { return display; }
    }

    public static class TimerMessageEntry extends MessageEntry {
        private final String size;
        private final int titleFadeIn;
        private final int titleStay;
        private final int titleFadeOut;

        public TimerMessageEntry(String text, String display, String size,
                                 int titleFadeIn, int titleStay, int titleFadeOut) {
            super(text, display);
            this.size = size;
            this.titleFadeIn = titleFadeIn;
            this.titleStay = titleStay;
            this.titleFadeOut = titleFadeOut;
        }

        public String getSize() { return size; }
        public int getTitleFadeIn() { return titleFadeIn; }
        public int getTitleStay() { return titleStay; }
        public int getTitleFadeOut() { return titleFadeOut; }
    }
}