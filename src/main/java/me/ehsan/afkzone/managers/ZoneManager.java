package me.ehsan.afkzone.managers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.service.SpatialZoneIndex;
import me.ehsan.afkzone.service.ZoneService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ZoneManager implements ZoneService {

    private final Main plugin;
    private final SpatialZoneIndex spatialIndex;
    private File zonesFile;
    private FileConfiguration zonesConfig;

    public ZoneManager(Main plugin, SpatialZoneIndex spatialIndex) {
        this.plugin = plugin;
        this.spatialIndex = spatialIndex;
        loadZonesFile();
    }

    public void loadZonesFile() {
        zonesFile = new File(plugin.getDataFolder(), "zones.yml");
        if (!zonesFile.exists()) {
            plugin.saveResource("zones.yml", false);
        }
        zonesConfig = YamlConfiguration.loadConfiguration(zonesFile);
        rebuildSpatialIndex();
    }

    /**
     * Rebuilds the spatial index from the zones config, with validation warnings.
     */
    private void rebuildSpatialIndex() {
        spatialIndex.clear();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) return;
        int loaded = 0;
        int warnings = 0;
        for (String key : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
            String path = "zones." + key;
            String world = zonesConfig.getString(path + ".world", "");

            // Validate world name
            if (world == null || world.isEmpty()) {
                plugin.getLogger().warning("Zone '" + key + "' has no world configured. Skipping.");
                warnings++;
                continue;
            }

            // Validate coordinates exist
            if (!zonesConfig.isSet(path + ".x1") || !zonesConfig.isSet(path + ".y1") || !zonesConfig.isSet(path + ".z1") ||
                !zonesConfig.isSet(path + ".x2") || !zonesConfig.isSet(path + ".y2") || !zonesConfig.isSet(path + ".z2")) {
                plugin.getLogger().warning("Zone '" + key + "' has missing coordinates. Skipping.");
                warnings++;
                continue;
            }

            int x1 = zonesConfig.getInt(path + ".x1");
            int y1 = zonesConfig.getInt(path + ".y1");
            int z1 = zonesConfig.getInt(path + ".z1");
            int x2 = zonesConfig.getInt(path + ".x2");
            int y2 = zonesConfig.getInt(path + ".y2");
            int z2 = zonesConfig.getInt(path + ".z2");

            // Validate zone has non-zero size
            if (x1 == x2 && y1 == y2 && z1 == z2) {
                plugin.getLogger().warning("Zone '" + key + "' has zero size (all corners are the same point). Skipping.");
                warnings++;
                continue;
            }

            // Check for invalid coordinates (NaN or extreme values)
            if (Math.abs(x1) > 30000000 || Math.abs(x2) > 30000000 ||
                Math.abs(y1) > 30000000 || Math.abs(y2) > 30000000 ||
                Math.abs(z1) > 30000000 || Math.abs(z2) > 30000000) {
                plugin.getLogger().warning("Zone '" + key + "' has coordinates outside the valid world range. Skipping.");
                warnings++;
                continue;
            }

            spatialIndex.addZone(key, world, x1, y1, z1, x2, y2, z2);
            loaded++;
        }
        if (warnings > 0) {
            plugin.getLogger().warning("Loaded " + loaded + " zones from zones.yml (" + warnings + " warnings)");
        }
    }

    public void saveZonesFile() {
        try {
            zonesConfig.save(zonesFile);
        } catch (Exception e) {
            plugin.getLogger().severe("Could not save zones.yml: " + e.getMessage());
        }
    }

    public FileConfiguration getZonesConfig() {
        return zonesConfig;
    }

    @Override
    public String findZoneForLocation(Location loc) {
        return spatialIndex.findZone(loc);
    }

    public SpatialZoneIndex getSpatialIndex() {
        return spatialIndex;
    }

    /**
     * Checks a candidate cuboid (in the given world) against every existing
     * zone for an axis-aligned bounding box overlap. Returns the name of the
     * first overlapping zone found, or null if the candidate is clear.
     * Coordinates don't need to be pre-normalized (min/max order doesn't
     * matter) - this normalizes internally the same way addZone() does.
     */
    public String findOverlappingZone(String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
        int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
        int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

        for (SpatialZoneIndex.ZoneBounds b : spatialIndex.getAllZones()) {
            if (!b.world().equals(world)) continue;
            boolean overlaps = minX <= b.x2() && b.x1() <= maxX
                    && minY <= b.y2() && b.y1() <= maxY
                    && minZ <= b.z2() && b.z1() <= maxZ;
            if (overlaps) {
                return b.name();
            }
        }
        return null;
    }

    @Override
    public Set<String> getZoneNames() {
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) {
            return Collections.emptySet();
        }
        return zonesConfig.getConfigurationSection("zones").getKeys(false);
    }

    @Override
    public boolean zoneExists(String name) {
        return zonesConfig != null
                && zonesConfig.isConfigurationSection("zones")
                && zonesConfig.isSet("zones." + name);
    }

    @Override
    public List<String> getZoneRewards(String zoneName) {
        if (!zoneExists(zoneName)) return Collections.emptyList();
        List<String> list = zonesConfig.getStringList("zones." + zoneName + ".rewards");
        return list != null ? list : Collections.emptyList();
    }

    @Override
    public void setZoneRewards(String zoneName, List<String> rewardNames) {
        if (!zoneExists(zoneName)) return;
        if (rewardNames == null || rewardNames.isEmpty()) {
            zonesConfig.set("zones." + zoneName + ".rewards", null);
        } else {
            zonesConfig.set("zones." + zoneName + ".rewards", new ArrayList<>(rewardNames));
        }
        saveZonesFile();
    }

    @Override
    public boolean addZoneReward(String zoneName, String rewardName) {
        if (!zoneExists(zoneName)) return false;
        List<String> current = new ArrayList<>(getZoneRewards(zoneName));
        if (current.contains(rewardName)) return false;
        current.add(rewardName);
        setZoneRewards(zoneName, current);
        return true;
    }

    @Override
    public boolean removeZoneReward(String zoneName, String rewardName) {
        if (!zoneExists(zoneName)) return false;
        List<String> current = new ArrayList<>(getZoneRewards(zoneName));
        if (!current.remove(rewardName)) return false;
        setZoneRewards(zoneName, current);
        return true;
    }

    @Override
    public void removeZone(String name) {
        if (!zoneExists(name)) return;
        zonesConfig.set("zones." + name, null);
        spatialIndex.removeZone(name);
        saveZonesFile();
    }

    @Override
    public boolean createZoneFromWorldEditSelection(Player player, String name) {
        // WorldEdit support removed - use wand selection instead
        msg(player, "<red>WorldEdit is not supported. Use <yellow>/afkzone wand</yellow> to select a region.</red>");
        return false;
    }

    // --- Per-zone configuration overrides ---

    @Override
    public String getZoneConfigString(String zoneName, String path, String defaultValue) {
        if (!zoneExists(zoneName)) return defaultValue;
        return zonesConfig.getString("zones." + zoneName + "." + path, defaultValue);
    }

    @Override
    public int getZoneConfigInt(String zoneName, String path, int defaultValue) {
        if (!zoneExists(zoneName)) return defaultValue;
        return zonesConfig.getInt("zones." + zoneName + "." + path, defaultValue);
    }

    @Override
    public boolean getZoneConfigBoolean(String zoneName, String path, boolean defaultValue) {
        if (!zoneExists(zoneName)) return defaultValue;
        return zonesConfig.getBoolean("zones." + zoneName + "." + path, defaultValue);
    }

    @Override
    public double getZoneConfigDouble(String zoneName, String path, double defaultValue) {
        if (!zoneExists(zoneName)) return defaultValue;
        return zonesConfig.getDouble("zones." + zoneName + "." + path, defaultValue);
    }

    @Override
    public List<String> getZoneEntryCommands(String zoneName) {
        if (!zoneExists(zoneName)) return Collections.emptyList();
        List<String> cmds = zonesConfig.getStringList("zones." + zoneName + ".on_enter.commands");
        return cmds != null ? cmds : Collections.emptyList();
    }

    @Override
    public List<String> getZoneExitCommands(String zoneName) {
        if (!zoneExists(zoneName)) return Collections.emptyList();
        List<String> cmds = zonesConfig.getStringList("zones." + zoneName + ".on_exit.commands");
        return cmds != null ? cmds : Collections.emptyList();
    }

    @Override
    public void reload() {
        loadZonesFile();
    }

    private void msg(Player player, String miniText) {
        try {
            player.sendMessage(MiniMessage.miniMessage().deserialize(miniText));
        } catch (Exception ex) {
            player.sendMessage(miniText.replaceAll("<[^>]+>", ""));
        }
    }
}