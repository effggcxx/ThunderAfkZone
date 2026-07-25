package me.ehsan.afkzone.managers;

import me.ehsan.afkzone.Main;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.lang.reflect.Method;

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
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) return null;
        for (String key : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
            String path = "zones." + key;
            String world = zonesConfig.getString(path + ".world", "");
            if (!loc.getWorld().getName().equals(world)) continue;
            int x1 = zonesConfig.getInt(path + ".x1", Integer.MIN_VALUE);
            int y1 = zonesConfig.getInt(path + ".y1", Integer.MIN_VALUE);
            int z1 = zonesConfig.getInt(path + ".z1", Integer.MIN_VALUE);
            int x2 = zonesConfig.getInt(path + ".x2", Integer.MAX_VALUE);
            int y2 = zonesConfig.getInt(path + ".y2", Integer.MAX_VALUE);
            int z2 = zonesConfig.getInt(path + ".z2", Integer.MAX_VALUE);
            int x = loc.getBlockX();
            int y = loc.getBlockY();
            int z = loc.getBlockZ();
            if (x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2) return key;
        }
        return null;
    }

    public void removeZone(String name) {
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones") || !zonesConfig.isSet("zones." + name)) {
            return;
        }
        zonesConfig.set("zones." + name, null);
        saveZonesFile();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean createZoneFromWorldEditSelection(Player player, String name) {
        Plugin wePlugin = plugin.getServer().getPluginManager().getPlugin("WorldEdit");
        if (wePlugin == null) {
            player.sendMessage("WorldEdit is not installed on this server.");
            return false;
        }

        try {
            Method getSelection = wePlugin.getClass().getMethod("getSelection", Player.class);
            Object selection = getSelection.invoke(wePlugin, player);
            if (selection == null) {
                player.sendMessage("You must make a WorldEdit selection with the wand first.");
                return false;
            }

            Location minLoc = null;
            Location maxLoc = null;
            try {
                Method getMinimumPoint = selection.getClass().getMethod("getMinimumPoint");
                Method getMaximumPoint = selection.getClass().getMethod("getMaximumPoint");
                Object min = getMinimumPoint.invoke(selection);
                Object max = getMaximumPoint.invoke(selection);
                if (min instanceof Location && max instanceof Location) {
                    minLoc = (Location) min;
                    maxLoc = (Location) max;
                }
            } catch (NoSuchMethodException ignore) {
            }

            if (minLoc == null || maxLoc == null) {
                try {
                    Method getMinimumPoint = selection.getClass().getMethod("getMinimum");
                    Method getMaximumPoint = selection.getClass().getMethod("getMaximum");
                    Object min = getMinimumPoint.invoke(selection);
                    Object max = getMaximumPoint.invoke(selection);
                    Method getBlockX = min.getClass().getMethod("getBlockX");
                    Method getBlockY = min.getClass().getMethod("getBlockY");
                    Method getBlockZ = min.getClass().getMethod("getBlockZ");
                    int x1 = ((Number) getBlockX.invoke(min)).intValue();
                    int y1 = ((Number) getBlockY.invoke(min)).intValue();
                    int z1 = ((Number) getBlockZ.invoke(min)).intValue();

                    Method getBlockX2 = max.getClass().getMethod("getBlockX");
                    Method getBlockY2 = max.getClass().getMethod("getBlockY");
                    Method getBlockZ2 = max.getClass().getMethod("getBlockZ");
                    int x2 = ((Number) getBlockX2.invoke(max)).intValue();
                    int y2 = ((Number) getBlockY2.invoke(max)).intValue();
                    int z2 = ((Number) getBlockZ2.invoke(max)).intValue();

                    World world = null;
                    try {
                        Method getWorld = selection.getClass().getMethod("getWorld");
                        Object w = getWorld.invoke(selection);
                        if (w instanceof World) world = (World) w;
                    } catch (NoSuchMethodException ignored) {
                    }

                    if (world == null) world = player.getWorld();

                    minLoc = new Location(world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
                    maxLoc = new Location(world, Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
                } catch (Exception ex) {
                    // fall through
                }
            }

            if (minLoc == null || maxLoc == null) {
                player.sendMessage("Could not determine selection corners from WorldEdit selection.");
                return false;
            }

            World world = minLoc.getWorld();
            int x1 = Math.min(minLoc.getBlockX(), maxLoc.getBlockX());
            int y1 = Math.min(minLoc.getBlockY(), maxLoc.getBlockY());
            int z1 = Math.min(minLoc.getBlockZ(), maxLoc.getBlockZ());
            int x2 = Math.max(minLoc.getBlockX(), maxLoc.getBlockX());
            int y2 = Math.max(minLoc.getBlockY(), maxLoc.getBlockY());
            int z2 = Math.max(minLoc.getBlockZ(), maxLoc.getBlockZ());

            String path = "zones." + name;
            zonesConfig.set(path + ".world", world.getName());
            zonesConfig.set(path + ".x1", x1);
            zonesConfig.set(path + ".y1", y1);
            zonesConfig.set(path + ".z1", z1);
            zonesConfig.set(path + ".x2", x2);
            zonesConfig.set(path + ".y2", y2);
            zonesConfig.set(path + ".z2", z2);
            saveZonesFile();

            player.sendMessage("AFK zone '" + name + "' created: " + world.getName() + " (" + x1 + "," + y1 + "," + z1 + ") -> (" + x2 + "," + y2 + "," + z2 + ")");
            return true;

        } catch (NoSuchMethodException e) {
            player.sendMessage("Incompatible WorldEdit version or API not available.");
        } catch (Exception e) {
            player.sendMessage("Error reading WorldEdit selection: " + e.getMessage());
            plugin.getLogger().severe("Error reading WorldEdit selection: " + e);
        }
        return false;
    }
}