package me.ehsan.afkzone.config;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.util.MessageUtils;
import net.kyori.adventure.bossbar.BossBar;
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
    private final File dataFolder;
    private final java.util.logging.Logger logger;
    private FileConfiguration messages;

    // Message data: text + display mode
    private MessageEntry enterZone;
    private MessageEntry exitZone;
    private MessageEntry rewardReceived;
    private MessageEntry rewardFailed;
    private MessageEntry inventoryFull;
    private TimerMessageEntry timer;

    public MessagesConfig(Main plugin) {
        this.plugin = plugin;
        this.dataFolder = plugin.getDataFolder();
        this.logger = plugin.getLogger();
    }

    public MessagesConfig(File dataFolder, java.util.logging.Logger logger) {
        this.plugin = null;
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    /**
     * Loads (or reloads) messages.yml from disk.
     */
    public void load() {
        File file = new File(dataFolder, "messages.yml");
        if (!file.exists()) {
            if (plugin != null) {
                plugin.saveResource("messages.yml", false);
            } else {
                logger.warning("messages.yml not found in " + dataFolder + " and no plugin instance was supplied to create it.");
                return;
            }
        }
        messages = YamlConfiguration.loadConfiguration(file);

        enterZone = loadMessageEntry("enter_zone");
        exitZone = loadMessageEntry("exit_zone");
        rewardReceived = loadMessageEntry("reward_received");
        rewardFailed = loadMessageEntry("reward_failed");
        inventoryFull = loadMessageEntry("inventory_full");
        timer = loadTimerMessageEntry("timer");

        logger.info("Messages config loaded (" + file.getAbsolutePath() + ")");
    }

    private MessageEntry loadMessageEntry(String path) {
        String text = messages.getString(path + ".text", "");
        String display = messages.getString(path + ".display", "chat");
        int fadeIn = messages.getInt(path + ".title.fade_in", 5);
        int stay = messages.getInt(path + ".title.stay", 40);
        int fadeOut = messages.getInt(path + ".title.fade_out", 5);
        BossBar.Color bossBarColor = MessageUtils.parseBossBarColor(
                messages.getString(path + ".bossbar_color", "yellow"),
                BossBar.Color.YELLOW, logger, path + ".bossbar_color");
        return new MessageEntry(text, display, fadeIn, stay, fadeOut, bossBarColor);
    }

    private TimerMessageEntry loadTimerMessageEntry(String path) {
        String text = messages.getString(path + ".text", "<timer> remaining until next reward");
        String display = messages.getString(path + ".display", "bossbar");
        int fadeIn = messages.getInt(path + ".title.fade_in", 5);
        int stay = messages.getInt(path + ".title.stay", 40);
        int fadeOut = messages.getInt(path + ".title.fade_out", 5);
        // Timer defaults to purple instead of the generic yellow default, since
        // it's the message most likely to actually be shown as a boss bar.
        BossBar.Color bossBarColor = MessageUtils.parseBossBarColor(
                messages.getString(path + ".bossbar_color", "purple"),
                BossBar.Color.PURPLE, logger, path + ".bossbar_color");
        return new TimerMessageEntry(text, display, fadeIn, stay, fadeOut, bossBarColor);
    }

    // --- Getters ---

    public MessageEntry getEnterZone() { return enterZone; }
    public MessageEntry getExitZone() { return exitZone; }
    public MessageEntry getRewardReceived() { return rewardReceived; }
    public MessageEntry getRewardFailed() { return rewardFailed; }
    public MessageEntry getInventoryFull() { return inventoryFull; }
    public TimerMessageEntry getTimer() { return timer; }

    // --- Data classes ---

    public static class MessageEntry {
        private final String text;
        private final String display;
        private final int titleFadeIn;
        private final int titleStay;
        private final int titleFadeOut;
        private final BossBar.Color bossBarColor;

        public MessageEntry(String text, String display) {
            this(text, display, 5, 40, 5, BossBar.Color.YELLOW);
        }

        public MessageEntry(String text, String display, int titleFadeIn, int titleStay, int titleFadeOut, BossBar.Color bossBarColor) {
            this.text = text;
            this.display = display;
            this.titleFadeIn = titleFadeIn;
            this.titleStay = titleStay;
            this.titleFadeOut = titleFadeOut;
            this.bossBarColor = bossBarColor;
        }

        public String getText() { return text; }
        public String getDisplay() { return display; }
        public int getTitleFadeIn() { return titleFadeIn; }
        public int getTitleStay() { return titleStay; }
        public int getTitleFadeOut() { return titleFadeOut; }
        public BossBar.Color getBossBarColor() { return bossBarColor; }
    }

    public static class TimerMessageEntry extends MessageEntry {
        private final int titleFadeIn;
        private final int titleStay;
        private final int titleFadeOut;
        private final BossBar.Color bossBarColor;

        public TimerMessageEntry(String text, String display,
                                 int titleFadeIn, int titleStay, int titleFadeOut, BossBar.Color bossBarColor) {
            super(text, display);
            this.titleFadeIn = titleFadeIn;
            this.titleStay = titleStay;
            this.titleFadeOut = titleFadeOut;
            this.bossBarColor = bossBarColor;
        }

        public int getTitleFadeIn() { return titleFadeIn; }
        public int getTitleStay() { return titleStay; }
        public int getTitleFadeOut() { return titleFadeOut; }
        public BossBar.Color getBossBarColor() { return bossBarColor; }
    }
}