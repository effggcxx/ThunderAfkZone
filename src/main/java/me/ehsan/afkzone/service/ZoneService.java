package me.ehsan.afkzone.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Set;

/**
 * Service interface for zone management operations.
 */
public interface ZoneService {

    /**
     * Finds the zone name at the given location, or null if not in any zone.
     */
    String findZoneForLocation(Location loc);

    /**
     * Returns all zone names.
     */
    Set<String> getZoneNames();

    /**
     * Checks if a zone exists.
     */
    boolean zoneExists(String name);

    /**
     * Returns the reward names assigned to a zone (empty = all global rewards).
     */
    List<String> getZoneRewards(String zoneName);

    /**
     * Sets the reward list for a zone.
     */
    void setZoneRewards(String zoneName, List<String> rewardNames);

    /**
     * Adds a reward to a zone.
     */
    boolean addZoneReward(String zoneName, String rewardName);

    /**
     * Removes a reward from a zone.
     */
    boolean removeZoneReward(String zoneName, String rewardName);

    /**
     * Removes a zone entirely.
     */
    void removeZone(String name);

    /**
     * Creates a zone from the player's WorldEdit selection.
     */
    boolean createZoneFromWorldEditSelection(Player player, String name);

    /**
     * Gets a per-zone config value (string), falling back to global default.
     */
    String getZoneConfigString(String zoneName, String path, String defaultValue);

    /**
     * Gets a per-zone config value (int), falling back to global default.
     */
    int getZoneConfigInt(String zoneName, String path, int defaultValue);

    /**
     * Gets a per-zone config value (boolean), falling back to global default.
     */
    boolean getZoneConfigBoolean(String zoneName, String path, boolean defaultValue);

    /**
     * Gets a per-zone config value (double), falling back to global default.
     */
    double getZoneConfigDouble(String zoneName, String path, double defaultValue);

    /**
     * Gets the entry commands for a zone.
     */
    List<String> getZoneEntryCommands(String zoneName);

    /**
     * Gets the exit commands for a zone.
     */
    List<String> getZoneExitCommands(String zoneName);

    /**
     * Reloads zones from disk.
     */
    void reload();
}