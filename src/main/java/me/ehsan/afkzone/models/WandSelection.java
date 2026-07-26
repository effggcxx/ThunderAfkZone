package me.ehsan.afkzone.models;

import org.bukkit.Location;

/**
 * Stores a player's wand selection (two corners of a cuboid).
 */
public class WandSelection {

    private Location pos1;
    private Location pos2;

    public WandSelection() {}

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public boolean isComplete() {
        return pos1 != null && pos2 != null && pos1.getWorld().equals(pos2.getWorld());
    }

    public void clear() {
        this.pos1 = null;
        this.pos2 = null;
    }

    /**
     * Returns the minimum corner of the selection.
     */
    public Location getMin() {
        if (!isComplete()) return null;
        return new Location(
                pos1.getWorld(),
                Math.min(pos1.getBlockX(), pos2.getBlockX()),
                Math.min(pos1.getBlockY(), pos2.getBlockY()),
                Math.min(pos1.getBlockZ(), pos2.getBlockZ())
        );
    }

    /**
     * Returns the maximum corner of the selection.
     */
    public Location getMax() {
        if (!isComplete()) return null;
        return new Location(
                pos1.getWorld(),
                Math.max(pos1.getBlockX(), pos2.getBlockX()),
                Math.max(pos1.getBlockY(), pos2.getBlockY()),
                Math.max(pos1.getBlockZ(), pos2.getBlockZ())
        );
    }

    /**
     * Returns the dimensions of the selection (width, height, depth).
     */
    public String getDimensions() {
        if (!isComplete()) return "incomplete";
        Location min = getMin();
        Location max = getMax();
        int w = max.getBlockX() - min.getBlockX() + 1;
        int h = max.getBlockY() - min.getBlockY() + 1;
        int d = max.getBlockZ() - min.getBlockZ() + 1;
        return w + " x " + h + " x " + d;
    }
}