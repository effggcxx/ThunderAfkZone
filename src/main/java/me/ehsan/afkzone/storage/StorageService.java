package me.ehsan.afkzone.storage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Abstraction for persistent storage (YAML, SQLite, MySQL).
 */
public interface StorageService {

    /**
     * Initializes the storage backend.
     */
    void initialize();

    /**
     * Shuts down the storage backend (closes connections, etc.).
     */
    void shutdown();

    // --- Player statistics ---

    /**
     * Gets the total AFK time (in seconds) for a player.
     */
    long getTotalAfkTime(UUID playerId);

    /**
     * Adds AFK time for a player.
     */
    void addAfkTime(UUID playerId, long seconds);

    /**
     * Gets the total number of rewards a player has received.
     */
    int getTotalRewardsReceived(UUID playerId);

    /**
     * Increments the reward count for a player.
     */
    void incrementRewardsReceived(UUID playerId);

    /**
     * Gets the total AFK time in a specific zone for a player.
     */
    long getZoneAfkTime(UUID playerId, String zoneName);

    /**
     * Adds AFK time in a specific zone for a player.
     */
    void addZoneAfkTime(UUID playerId, String zoneName, long seconds);

    // --- Top lists ---

    /**
     * Gets the top players by total AFK time (player UUID -> seconds).
     */
    List<Map.Entry<UUID, Long>> getTopAfkTime(int limit);

    /**
     * Gets the top players by rewards received.
     */
    List<Map.Entry<UUID, Integer>> getTopRewards(int limit);

    // --- Reward progress persistence (survives restarts) ---

    /**
     * Saves a player's per-reward progress to persistent storage.
     * Called when progress changes or when the player leaves a zone.
     */
    void savePlayerProgress(UUID playerId, Map<String, Integer> progress);

    /**
     * Loads a player's per-reward progress from persistent storage.
     * Returns an empty map if no saved progress exists.
     */
    Map<String, Integer> loadPlayerProgress(UUID playerId);

    /**
     * Saves the set of once-given rewards for a player.
     * Called when the player leaves a zone or periodically.
     */
    void savePlayerGivenOnce(UUID playerId, Set<String> givenOnce);

    /**
     * Loads the set of once-given rewards for a player.
     * Returns an empty set if no saved data exists.
     */
    Set<String> loadPlayerGivenOnce(UUID playerId);

    // --- Pure in-memory fallback ---

    /**
     * Returns true if this storage backend is persistent (not just in-memory).
     */
    boolean isPersistent();
}