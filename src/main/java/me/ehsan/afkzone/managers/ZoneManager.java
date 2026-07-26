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
     * Rebuilds the spatial index from the zones config.
     */
    private void rebuildSpatialIndex() {
        spatialIndex.clear();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) return;
        for (String key : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
            String path = "zones." + key;
            String world = zonesConfig.getString(path + ".world", "");
            int x1 = zonesConfig.getInt(path + ".x1");
            int y1 = zonesConfig.getInt(path + ".y1");
            int z1 = zonesConfig.getInt(path + ".z1");
            int x2 = zonesConfig.getInt(path + ".x2");
            int y2 = zonesConfig.getInt(path + ".y2");
            int z2 = zonesConfig.getInt(path + ".z2");
            spatialIndex.addZone(key, world, x1, y1, z1, x2, y2, z2);
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