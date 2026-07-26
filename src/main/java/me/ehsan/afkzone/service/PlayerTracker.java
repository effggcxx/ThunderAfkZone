package me.ehsan.afkzone.service;

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
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
    // Current-session AFK seconds, separate from per-reward progress counters.
    // Resets to 0 on every new tracking session (zone enter); NOT persisted -
    // lifetime totals live in StorageService and are untouched by this.
    private final Map<UUID, Integer> sessionAfkSeconds = new ConcurrentHashMap<>();

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
                              String msgEnterZone) {
        UUID id = player.getUniqueId();

        // If already tracking a different zone, clean up first
        if (playerZone.containsKey(id)) {
            stopTracking(id, null, null, 0, 0);
        }

        playerZone.put(id, zoneName);
        playerProgress.put(id, new ConcurrentHashMap<>());
        playerGivenOnce.put(id, ConcurrentHashMap.newKeySet());
        sessionAfkSeconds.put(id, 0);
        // Treat join as active so the AFK threshold has to pass before rewards start
        lastActive.put(id, System.currentTimeMillis());

        MessageUtils.sendStyled(player, msgEnterZone, zoneName, null);
        MessageUtils.playSound(player, enterSound, soundVolume, soundPitch);
    }

    public void stopTracking(UUID id, String msgExitZone, Sound exitSound, float soundVolume, float soundPitch) {
        playerProgress.remove(id);
        playerGivenOnce.remove(id);
        String zone = playerZone.remove(id);
        lastActive.remove(id);
        sessionAfkSeconds.remove(id);

        Player player = org.bukkit.Bukkit.getPlayer(id);
        if (player != null && zone != null) {
            if (msgExitZone != null) {
                MessageUtils.sendStyled(player, msgExitZone, zone, null);
            }
            MessageUtils.playSound(player, exitSound, soundVolume, soundPitch);
        }
    }

    public void stopTrackingSilent(UUID id) {
        playerProgress.remove(id);
        playerGivenOnce.remove(id);
        playerZone.remove(id);
        lastActive.remove(id);
        sessionAfkSeconds.remove(id);
    }

    // --- Activity ---

    public void markActive(UUID id) {
        lastActive.put(id, System.currentTimeMillis());
    }

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
        sessionAfkSeconds.merge(id, 1, Integer::sum);
    }

    public int getSessionSeconds(UUID id) {
        return sessionAfkSeconds.getOrDefault(id, 0);
    }

    public void clear() {
        playerProgress.clear();
        playerGivenOnce.clear();
        playerZone.clear();
        lastActive.clear();
        sessionAfkSeconds.clear();
    }
}