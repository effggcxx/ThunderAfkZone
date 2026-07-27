package me.ehsan.afkzone.util;

import me.ehsan.afkzone.config.MessagesConfig;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Shared message / timer helpers.
 */
public final class MessageUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private MessageUtils() {}

    /**
     * Sends a message to a player using the configured display mode.
     *
     * @param player     the target player
     * @param entry      the message entry (text + display mode)
     * @param zoneName   zone name placeholder, or null
     * @param rewardName reward name placeholder, or null
     */
    public static void sendStyled(Player player, MessagesConfig.MessageEntry entry, String zoneName, String rewardName) {
        if (entry == null || entry.getText() == null || entry.getText().isEmpty()) return;
        String text = entry.getText();
        if (zoneName != null) text = text.replace("<zone>", zoneName);
        if (rewardName != null) text = text.replace("<reward>", rewardName);
        text = text.replace("<player>", player.getName());

        Component component;
        try {
            component = MINI_MESSAGE.deserialize(text);
        } catch (Exception ex) {
            component = Component.text(text.replaceAll("<[^>]+>", ""));
        }

        String display = entry.getDisplay() != null ? entry.getDisplay().toLowerCase(Locale.ROOT) : "chat";
        switch (display) {
            case "actionbar" -> player.sendActionBar(component);
            case "title" -> player.showTitle(Title.title(component, Component.empty(),
                    Title.Times.times(
                            Duration.ofMillis(5 * 50L),
                            Duration.ofMillis(40 * 50L),
                            Duration.ofMillis(5 * 50L)
                    )));
            case "bossbar" -> {
                BossBar bar = BossBar.bossBar(component, 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                player.showBossBar(bar);
                // Auto-hide after 3 seconds
                org.bukkit.Bukkit.getScheduler().runTaskLater(
                        org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(MessageUtils.class),
                        () -> player.hideBossBar(bar), 60L);
            }
            default -> player.sendMessage(component);
        }
    }

    /**
     * Legacy overload for backward compatibility - sends as chat message.
     */
    public static void sendStyled(Player player, String template, String zoneName, String rewardName) {
        if (template == null || template.isEmpty()) return;
        String text = template;
        if (zoneName != null) text = text.replace("<zone>", zoneName);
        if (rewardName != null) text = text.replace("<reward>", rewardName);
        if (player != null) text = text.replace("<player>", player.getName());
        try {
            player.sendMessage(MINI_MESSAGE.deserialize(text));
        } catch (Exception ex) {
            player.sendMessage(text.replaceAll("<[^>]+>", ""));
        }
    }

    public static Component buildTimerComponent(String timerTemplate, String timerSize,
                                                 long secondsRemaining, String zoneName, String playerName) {
        String text = timerTemplate
                .replace("<timer>", formatTime(secondsRemaining))
                .replace("<zone>", zoneName == null ? "" : zoneName)
                .replace("<player>", playerName == null ? "" : playerName);
        if ("mini".equalsIgnoreCase(timerSize)) {
            text = "<gray><italic>" + text + "</italic></gray>";
        } else if ("big".equalsIgnoreCase(timerSize)) {
            text = "<gold><bold>" + text + "</bold></gold>";
        }
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (Exception ex) {
            return Component.text("Next reward in " + formatTime(secondsRemaining));
        }
    }

    public static void sendTimer(Player player, String timerDisplay,
                                  String timerTemplate, String timerSize,
                                  int titleFadeIn, int titleStay, int titleFadeOut,
                                  boolean timerEnabled, long secondsRemaining, long totalSeconds,
                                  String zoneName, Map<UUID, BossBar> activeBossBars) {
        if (!timerEnabled) return;
        Component component = buildTimerComponent(timerTemplate, timerSize, secondsRemaining, zoneName, player.getName());
        UUID id = player.getUniqueId();

        switch (timerDisplay.toLowerCase(Locale.ROOT)) {
            case "actionbar" -> player.sendActionBar(component);
            case "chat" -> player.sendMessage(component);
            case "bossbar" -> {
                float progress = totalSeconds > 0
                        ? clamp01((float) (totalSeconds - secondsRemaining) / (float) totalSeconds)
                        : 0f;
                BossBar bar = activeBossBars.get(id);
                if (bar == null) {
                    bar = BossBar.bossBar(component, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                    activeBossBars.put(id, bar);
                    player.showBossBar(bar);
                } else {
                    bar.name(component);
                    bar.progress(progress);
                }
            }
            default -> player.showTitle(Title.title(component, Component.empty(),
                    Title.Times.times(
                            Duration.ofMillis(titleFadeIn * 50L),
                            Duration.ofMillis(titleStay * 50L),
                            Duration.ofMillis(titleFadeOut * 50L)
                    )));
        }
    }

    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        if (sound != null) {
            player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    private static String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        if (seconds >= 60) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return mins + ":" + String.format("%02d", secs);
        }
        return seconds + "s";
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    public static Sound parseSound(String soundName, Logger logger, String configPath) {
        if (soundName == null || soundName.isEmpty()) return null;
        try {
            return Sound.valueOf(soundName.toUpperCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException ex) {
            logger.warning("Invalid sound name in config path " + configPath + ": '" + soundName + "'. Sound disabled.");
            return null;
        }
    }

    /**
     * Formats a duration in seconds to a human-readable string (e.g. "5:30", "45s").
     */
    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "0s";
        if (seconds >= 60) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return mins + ":" + String.format("%02d", secs);
        }
        return seconds + "s";
    }
}