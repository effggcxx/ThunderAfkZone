package me.ehsan.afkzone.service;

import me.ehsan.afkzone.config.MessagesConfig;
import me.ehsan.afkzone.util.MessageUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player AFK zone state: which zone they're in, their progress,
 * last activity time, and one-time rewards given.
 * Extracted from the original RewardManager for better separation of concerns.
 */
public class PlayerTracker {

    private final Map<UUID, Map<String, Integer>> playerProgress = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> playerGivenOnce = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerZone = new ConcurrentHashMap<>();
    // Last time the player performed an activity (move/chat/command/interact).
    // Used by RewardManager to enforce afk_threshold_seconds before progress ticks.
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    // Current-session seconds spent in zone (separate from per-reward progress).
    // Incremented every tick regardless of AFK threshold; resets to 0 on every
    // new tracking session (zone enter). NOT persisted - lifetime totals live
    // in StorageService and are untouched by this.
    // NOTE: This counts zone presence time, not idle/AFK time. If an AFK
    // threshold is configured, reward progress ticks slower than this counter.
    private final Map<UUID, Integer> sessionZoneSeconds = new ConcurrentHashMap<>();

    public PlayerTracker() {}

    // --- Zone tracking ---

    public String getPlayerZone(UUID id) {
        return playerZone.get(id);
    }

    public boolean isPlayerInAnyZone(UUID id) {
        return playerZone.containsKey(id);
    }

    public Map<UUID, String> getTrackedPlayers() {
        return playerZone;
    }

    public void startTracking(Player player, String zoneName, Sound enterSound, float soundVolume, float soundPitch,
                              MessagesConfig.MessageEntry enterEntry, boolean resetProgress) {
        UUID id = player.getUniqueId();

        // If already tracking a different zone, clean up first
        if (playerZone.containsKey(id)) {
            stopTracking(id, null, null, 0, 0, resetProgress);
        }

        playerZone.put(id, zoneName);
        // computeIfAbsent, not put: if progress was preserved from a previous
        // session (reset_progress_on_leave: false in config.yml), reuse it
        // instead of wiping it back to empty on re-entry.
        playerProgress.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
        playerGivenOnce.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        sessionZoneSeconds.put(id, 0);
        // Treat zone entry as activity so the AFK threshold must elapse
        // before reward progress starts ticking.
        lastActive.put(id, System.currentTimeMillis());

        MessageUtils.sendStyled(player, enterEntry, zoneName, null);
        MessageUtils.playSound(player, enterSound, soundVolume, soundPitch);
    }

    public void stopTracking(UUID id, MessagesConfig.MessageEntry exitEntry, Sound exitSound, float soundVolume, float soundPitch,
                              boolean resetProgress) {
        if (resetProgress) {
            playerProgress.remove(id);
            playerGivenOnce.remove(id);
        }
        String zone = playerZone.remove(id);
        sessionZoneSeconds.remove(id);
        lastActive.remove(id);

        Player player = org.bukkit.Bukkit.getPlayer(id);
        if (player != null && zone != null) {
            MessageUtils.sendStyled(player, exitEntry, zone, null);
            MessageUtils.playSound(player, exitSound, soundVolume, soundPitch);
        }
    }

    public void stopTrackingSilent(UUID id, boolean resetProgress) {
        if (resetProgress) {
            playerProgress.remove(id);
            playerGivenOnce.remove(id);
        }
        playerZone.remove(id);
        sessionZoneSeconds.remove(id);
        lastActive.remove(id);
    }

    // --- Activity / AFK threshold ---

    /**
     * Records that the player performed an action (move, chat, command, interact).
     * Resets the idle timer used by the AFK threshold check.
     */
    public void markActive(UUID id) {
        if (playerZone.containsKey(id)) {
            lastActive.put(id, System.currentTimeMillis());
        }
    }

    /**
     * Millis timestamp of the player's last activity, or current time if unknown.
     */
    public long getLastActiveTime(UUID id) {
        return lastActive.getOrDefault(id, System.currentTimeMillis());
    }

    // --- Progress ---

    public Map<String, Integer> getProgress(UUID id) {
        return playerProgress.computeIfAbsent(id, k -> new ConcurrentHashMap<>());
    }

    public Set<String> getGivenOnce(UUID id) {
        return playerGivenOnce.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
    }

    // --- Current-session AFK time (resets on every new tracking session) ---

    /**
     * Increments this session's AFK-seconds counter by one. Call once per
     * tick per player - NOT once per reward, unlike getProgress().
     */
    public void incrementSession(UUID id) {
        sessionZoneSeconds.merge(id, 1, Integer::sum);
    }

    public int getSessionSeconds(UUID id) {
        return sessionZoneSeconds.getOrDefault(id, 0);
    }

    public void clear() {
        playerProgress.clear();
        playerGivenOnce.clear();
        playerZone.clear();
        lastActive.clear();
        sessionZoneSeconds.clear();
    }
}