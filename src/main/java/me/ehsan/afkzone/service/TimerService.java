package me.ehsan.afkzone.service;

import me.ehsan.afkzone.config.MessagesConfig;
import me.ehsan.afkzone.util.MessageUtils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages timer display for AFK rewards using bossbar, title, actionbar, or chat.
 * Uses MessagesConfig for template, display mode, and title timing.
 */
public class TimerService {

    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private MessagesConfig.TimerMessageEntry timerConfig;

    public TimerService() {}

    // --- Configuration ---

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }

    public void setTimerConfig(MessagesConfig.TimerMessageEntry config) {
        this.timerConfig = config;
    }

    public MessagesConfig.TimerMessageEntry getTimerConfig() { return timerConfig; }

    // --- Display ---

    public void sendTimer(Player player, long secondsRemaining, long totalSeconds, String zoneName) {
        if (!enabled || timerConfig == null) return;
        MessageUtils.sendTimer(player, timerConfig.getDisplay(), timerConfig.getText(), timerConfig.getSize(),
                timerConfig.getTitleFadeIn(), timerConfig.getTitleStay(), timerConfig.getTitleFadeOut(), true,
                secondsRemaining, totalSeconds, zoneName, activeBossBars);
    }

    public void hideAllBossBars() {
        for (Map.Entry<UUID, BossBar> entry : activeBossBars.entrySet()) {
            Player p = org.bukkit.Bukkit.getPlayer(entry.getKey());
            if (p != null) {
                p.hideBossBar(entry.getValue());
            }
        }
        activeBossBars.clear();
    }

    public void removePlayer(Player player) {
        BossBar bar = activeBossBars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }
}