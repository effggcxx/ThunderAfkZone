package me.ehsan.afkzone.service;

import me.ehsan.afkzone.service.SpatialZoneIndex.ZoneBounds;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Displays visual zone boundaries using particles.
 * Shows particles along the edges of zones for nearby players.
 */
public class ParticleService {

    private final JavaPlugin plugin;
    private final SpatialZoneIndex spatialIndex;
    private final Map<UUID, BukkitRunnable> activeTasks = new HashMap<>();

    private boolean enabled = true;
    private int particleCount = 1;
    private double particleSpacing = 2.0;
    private double viewDistance = 48.0;
    private int intervalTicks = 40; // every 2 seconds
    private Particle particle = Particle.END_ROD;
    private Color color = null; // null = use default particle color

    public ParticleService(JavaPlugin plugin, SpatialZoneIndex spatialIndex) {
        this.plugin = plugin;
        this.spatialIndex = spatialIndex;
    }

    // --- Configuration ---

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isEnabled() { return enabled; }
    public void setParticleCount(int count) { this.particleCount = count; }
    public void setParticleSpacing(double spacing) { this.particleSpacing = spacing; }
    public void setViewDistance(double distance) { this.viewDistance = distance; }
    public void setIntervalTicks(int ticks) { this.intervalTicks = ticks; }
    public void setParticle(Particle particle) { this.particle = particle; }
    public void setColor(Color color) { this.color = color; }

    // --- Player tracking ---

    public void startShowing(Player player) {
        stopShowing(player);
        if (!enabled) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopShowing(player);
                    return;
                }
                showZoneBoundaries(player);
            }
        };
        task.runTaskTimer(plugin, 0L, intervalTicks);
        activeTasks.put(player.getUniqueId(), task);
    }

    public void stopShowing(Player player) {
        BukkitRunnable task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAll() {
        for (BukkitRunnable task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }

    // --- Rendering ---

    private void showZoneBoundaries(Player player) {
        Location playerLoc = player.getLocation();
        World world = playerLoc.getWorld();
        if (world == null) return;

        for (ZoneBounds zone : spatialIndex.getAllZones()) {
            if (!zone.world().equals(world.getName())) continue;

            // Check distance to zone
            double dist = distanceToZone(playerLoc, zone);
            if (dist > viewDistance) continue;

            // Draw edges
            drawEdges(world, zone, player);
        }
    }

    private void drawEdges(World world, ZoneBounds zone, Player player) {
        double spacing = particleSpacing;
        int count = particleCount;

        // Bottom rectangle
        drawLine(world, zone.x1(), zone.y1(), zone.z1(), zone.x2(), zone.y1(), zone.z1(), spacing, count, player);
        drawLine(world, zone.x2(), zone.y1(), zone.z1(), zone.x2(), zone.y1(), zone.z2(), spacing, count, player);
        drawLine(world, zone.x2(), zone.y1(), zone.z2(), zone.x1(), zone.y1(), zone.z2(), spacing, count, player);
        drawLine(world, zone.x1(), zone.y1(), zone.z2(), zone.x1(), zone.y1(), zone.z1(), spacing, count, player);

        // Top rectangle
        drawLine(world, zone.x1(), zone.y2(), zone.z1(), zone.x2(), zone.y2(), zone.z1(), spacing, count, player);
        drawLine(world, zone.x2(), zone.y2(), zone.z1(), zone.x2(), zone.y2(), zone.z2(), spacing, count, player);
        drawLine(world, zone.x2(), zone.y2(), zone.z2(), zone.x1(), zone.y2(), zone.z2(), spacing, count, player);
        drawLine(world, zone.x1(), zone.y2(), zone.z2(), zone.x1(), zone.y2(), zone.z1(), spacing, count, player);

        // Vertical edges
        drawLine(world, zone.x1(), zone.y1(), zone.z1(), zone.x1(), zone.y2(), zone.z1(), spacing, count, player);
        drawLine(world, zone.x2(), zone.y1(), zone.z1(), zone.x2(), zone.y2(), zone.z1(), spacing, count, player);
        drawLine(world, zone.x2(), zone.y1(), zone.z2(), zone.x2(), zone.y2(), zone.z2(), spacing, count, player);
        drawLine(world, zone.x1(), zone.y1(), zone.z2(), zone.x1(), zone.y2(), zone.z2(), spacing, count, player);
    }

    private void drawLine(World world, int x1, int y1, int z1, int x2, int y2, int z2,
                          double spacing, int count, Player player) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length == 0) return;

        int steps = Math.max(1, (int) (length / spacing));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double px = x1 + dx * t + 0.5;
            double py = y1 + dy * t + 0.5;
            double pz = z1 + dz * t + 0.5;

            if (color != null) {
                player.spawnParticle(Particle.DUST, px, py, pz, count, 0, 0, 0, 0,
                        new Particle.DustOptions(color, 1));
            } else {
                player.spawnParticle(particle, px, py, pz, count, 0, 0, 0, 0);
            }
        }
    }

    private double distanceToZone(Location loc, ZoneBounds zone) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        double cx = clamp(px, zone.x1(), zone.x2());
        double cy = clamp(py, zone.y1(), zone.y2());
        double cz = clamp(pz, zone.z1(), zone.z2());

        return Math.sqrt((px - cx) * (px - cx) + (py - cy) * (py - cy) + (pz - cz) * (pz - cz));
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }
/**
 * Restarts all active particle tasks so interval/enabled/spacing changes
 * take effect immediately.
 */
public void restartAll() {
    if (activeTasks.isEmpty()) return;

    java.util.Set<UUID> players = new java.util.HashSet<>(activeTasks.keySet());

    // Stop everything
    stopAll();

    // If particles are disabled, leave them stopped
    if (!enabled) return;

    // Restart particles for every player that previously had them
    for (UUID id : players) {
        org.bukkit.entity.Player player = org.bukkit.Bukkit.getPlayer(id);
        if (player != null && player.isOnline()) {
            startShowing(player);
        }
    }
} 
}