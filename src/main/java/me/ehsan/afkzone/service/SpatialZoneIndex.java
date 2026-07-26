package me.ehsan.afkzone.service;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A spatial index for fast zone lookups using a grid-based approach.
 * Divides the world into cells and maps zones to the cells they occupy,
 * so lookups only check zones in the relevant cell rather than all zones.
 */
public class SpatialZoneIndex {

    private static final int CELL_SIZE = 64; // blocks per cell

    private final Map<String, ZoneBounds> zones = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> grid = new ConcurrentHashMap<>();

    /**
     * Represents the axis-aligned bounding box of a zone.
     */
    public record ZoneBounds(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        public boolean contains(int x, int y, int z) {
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }
    }

    /**
     * Adds or updates a zone in the index.
     */
    public void addZone(String name, String world, int x1, int y1, int z1, int x2, int y2, int z2) {
        removeZone(name);
        ZoneBounds bounds = new ZoneBounds(name, world,
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
        zones.put(name, bounds);
        indexZone(bounds);
    }

    /**
     * Removes a zone from the index.
     */
    public void removeZone(String name) {
        ZoneBounds old = zones.remove(name);
        if (old != null) {
            // Remove from all grid cells it occupied
            int cx1 = cell(old.x1);
            int cx2 = cell(old.x2);
            int cz1 = cell(old.z1);
            int cz2 = cell(old.z2);
            for (int cx = cx1; cx <= cx2; cx++) {
                for (int cz = cz1; cz <= cz2; cz++) {
                    long key = cellKey(old.world, cx, cz);
                    Set<String> cellZones = grid.get(key);
                    if (cellZones != null) {
                        cellZones.remove(name);
                        if (cellZones.isEmpty()) {
                            grid.remove(key);
                        }
                    }
                }
            }
        }
    }

    /**
     * Clears the entire index.
     */
    public void clear() {
        zones.clear();
        grid.clear();
    }

    /**
     * Finds the zone at the given location, or null if not in any zone.
     * Only checks zones in the same grid cell as the location.
     */
    public String findZone(Location loc) {
        if (loc.getWorld() == null) return null;
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        String worldName = loc.getWorld().getName();

        long key = cellKey(worldName, cell(x), cell(z));
        Set<String> candidates = grid.get(key);
        if (candidates == null || candidates.isEmpty()) return null;

        String best = null;
        for (String name : candidates) {
            ZoneBounds b = zones.get(name);
            if (b != null && b.world.equals(worldName) && b.contains(x, y, z)) {
                // Return the first match (or could prioritize by volume/size)
                if (best == null) best = name;
            }
        }
        return best;
    }

    /**
     * Returns all zone bounds for iteration.
     */
    public Collection<ZoneBounds> getAllZones() {
        return zones.values();
    }

    /**
     * Returns a specific zone's bounds.
     */
    public ZoneBounds getZone(String name) {
        return zones.get(name);
    }

    // --- private helpers ---

    private void indexZone(ZoneBounds b) {
        int cx1 = cell(b.x1);
        int cx2 = cell(b.x2);
        int cz1 = cell(b.z1);
        int cz2 = cell(b.z2);
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                long key = cellKey(b.world, cx, cz);
                grid.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet()).add(b.name);
            }
        }
    }

    private static int cell(int block) {
        return Math.floorDiv(block, CELL_SIZE);
    }

    private static long cellKey(String world, int cx, int cz) {
        // Combine world hash and cell coordinates into a single long key
        long w = (long) world.hashCode() & 0xFFFFFFFFL;
        return (w << 32) | ((long) cx << 16) & 0xFFFFFFFF0000L | (cz & 0xFFFF);
    }
}