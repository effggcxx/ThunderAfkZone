package me.ehsan.afkzone.managers;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionManager;
import me.ehsan.afkzone.Main;
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

public class ZoneManager {

    private final Main plugin;
    private File zonesFile;
    private FileConfiguration zonesConfig;

    public ZoneManager(Main plugin) {
        this.plugin = plugin;
        loadZonesFile();
    }

    public void loadZonesFile() {
        zonesFile = new File(plugin.getDataFolder(), "zones.yml");
        if (!zonesFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                zonesFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("Could not create zones.yml: " + e.getMessage());
            }
        }
        zonesConfig = YamlConfiguration.loadConfiguration(zonesFile);
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

    public String findZoneForLocation(Location loc) {
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones") || loc.getWorld() == null) {
            return null;
        }
        String worldName = loc.getWorld().getName();
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        for (String key : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
            String path = "zones." + key;
            if (!worldName.equals(zonesConfig.getString(path + ".world", ""))) continue;

            int x1 = zonesConfig.getInt(path + ".x1");
            int y1 = zonesConfig.getInt(path + ".y1");
            int z1 = zonesConfig.getInt(path + ".z1");
            int x2 = zonesConfig.getInt(path + ".x2");
            int y2 = zonesConfig.getInt(path + ".y2");
            int z2 = zonesConfig.getInt(path + ".z2");

            if (x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2) {
                return key;
            }
        }
        return null;
    }

    public Set<String> getZoneNames() {
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) {
            return Collections.emptySet();
        }
        return zonesConfig.getConfigurationSection("zones").getKeys(false);
    }

    public boolean zoneExists(String name) {
        return zonesConfig != null
                && zonesConfig.isConfigurationSection("zones")
                && zonesConfig.isSet("zones." + name);
    }

    /**
     * Returns the list of reward names configured for this zone.
     * Empty list means "use all enabled global rewards" (backward compatible).
     */
    public List<String> getZoneRewards(String zoneName) {
        if (!zoneExists(zoneName)) return Collections.emptyList();
        List<String> list = zonesConfig.getStringList("zones." + zoneName + ".rewards");
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Sets the reward list for a zone. Pass an empty list to fall back to all global rewards.
     */
    public void setZoneRewards(String zoneName, List<String> rewardNames) {
        if (!zoneExists(zoneName)) return;
        if (rewardNames == null || rewardNames.isEmpty()) {
            zonesConfig.set("zones." + zoneName + ".rewards", null);
        } else {
            zonesConfig.set("zones." + zoneName + ".rewards", new ArrayList<>(rewardNames));
        }
        saveZonesFile();
    }

    public boolean addZoneReward(String zoneName, String rewardName) {
        if (!zoneExists(zoneName)) return false;
        List<String> current = new ArrayList<>(getZoneRewards(zoneName));
        if (current.contains(rewardName)) return false;
        current.add(rewardName);
        setZoneRewards(zoneName, current);
        return true;
    }

    public boolean removeZoneReward(String zoneName, String rewardName) {
        if (!zoneExists(zoneName)) return false;
        List<String> current = new ArrayList<>(getZoneRewards(zoneName));
        if (!current.remove(rewardName)) return false;
        setZoneRewards(zoneName, current);
        return true;
    }

    public void removeZone(String name) {
        if (!zoneExists(name)) return;
        zonesConfig.set("zones." + name, null);
        saveZonesFile();
    }

    /**
     * Creates a zone from the player's current WorldEdit selection using the official WorldEdit API.
     */
    public boolean createZoneFromWorldEditSelection(Player player, String name) {
        if (plugin.getServer().getPluginManager().getPlugin("WorldEdit") == null) {
            player.sendMessage("§cWorldEdit is not installed on this server.");
            return false;
        }

        try {
            BukkitPlayer wePlayer = BukkitAdapter.adapt(player);
            SessionManager sessionManager = WorldEdit.getInstance().getSessionManager();
            LocalSession session = sessionManager.get(wePlayer);

            Region region = session.getSelection(wePlayer.getWorld());
            if (region == null) {
                player.sendMessage("§cYou must make a WorldEdit selection with the wand first.");
                return false;
            }

            BlockVector3 min = region.getMinimumPoint();
            BlockVector3 max = region.getMaximumPoint();

            World world = BukkitAdapter.adapt(region.getWorld());
            if (world == null) {
                world = player.getWorld();
            }

            int x1 = Math.min(min.x(), max.x());
            int y1 = Math.min(min.y(), max.y());
            int z1 = Math.min(min.z(), max.z());
            int x2 = Math.max(min.x(), max.x());
            int y2 = Math.max(min.y(), max.y());
            int z2 = Math.max(min.z(), max.z());

            String path = "zones." + name;
            zonesConfig.set(path + ".world", world.getName());
            zonesConfig.set(path + ".x1", x1);
            zonesConfig.set(path + ".y1", y1);
            zonesConfig.set(path + ".z1", z1);
            zonesConfig.set(path + ".x2", x2);
            zonesConfig.set(path + ".y2", y2);
            zonesConfig.set(path + ".z2", z2);
            // No rewards list → uses all global rewards by default
            saveZonesFile();

            player.sendMessage("§aAFK zone '" + name + "' created: " + world.getName()
                    + " (" + x1 + "," + y1 + "," + z1 + ") → (" + x2 + "," + y2 + "," + z2 + ")");
            player.sendMessage("§7Tip: Use §e/afkzone zonereward add " + name + " <reward>§7 to restrict rewards for this zone.");
            return true;

        } catch (com.sk89q.worldedit.IncompleteRegionException e) {
            player.sendMessage("§cIncomplete WorldEdit selection. Select both corners first.");
            return false;
        } catch (Exception e) {
            player.sendMessage("§cError reading WorldEdit selection: " + e.getMessage());
            plugin.getLogger().severe("Error reading WorldEdit selection: " + e);
            return false;
        }
    }
}
