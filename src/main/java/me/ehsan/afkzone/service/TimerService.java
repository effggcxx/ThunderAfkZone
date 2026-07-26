package me.ehsan.afkzone.service;

import me.ehsan.afkzone.util.MessageUtils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages timer display for AFK rewards using bossbar, title, actionbar, or chat.
 * Extracted from the original RewardManager for better separation of concerns.
 */
public class TimerService {

    private final Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();

    private boolean enabled = true;
    private String template = "<gold><bold>Next reward in <timer></bold></gold>";
    private String display = "title";
    private String size = "big";
    private int titleFadeIn = 5;
    private int titleStay = 40;
    private int titleFadeOut = 5;

    public TimerService() {}

    // --- Configuration ---

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setTemplate(String template) { this.template = template; }
    public String getTemplate() { return template; }
    public void setDisplay(String display) { this.display = display; }
    public String getDisplay() { return display; }
    public void setSize(String size) { this.size = size; }
    public String getSize() { return size; }
    public void setTitleFadeIn(int fadeIn) { this.titleFadeIn = fadeIn; }
    public void setTitleStay(int stay) { this.titleStay = stay; }
    public void setTitleFadeOut(int fadeOut) { this.titleFadeOut = fadeOut; }

    // --- Display ---

    public void sendTimer(Player player, long secondsRemaining, long totalSeconds, String zoneName) {
        if (!enabled) return;
        MessageUtils.sendTimer(player, display, template, size,
                titleFadeIn, titleStay, titleFadeOut, true,
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