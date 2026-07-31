package me.ehsan.afkzone.managers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.service.SpatialZoneIndex;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages per-player zone border particle visualization.
 * <p>
 * When enabled for a player, particles are drawn around nearby zone borders.
 * Particles are sent only to the player who enabled them (via
 * {@link Player#spawnParticle}), so other players see nothing.
 */
public class BorderManager {

    private final Main plugin;
    private final ZoneManager zoneManager;
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    // Config values (loaded from config.yml -> border.*)
    private Particle particle = Particle.END_ROD;
    private int intervalTicks = 10;
    private double density = 1.0;
    private double renderDistance = 64.0;
    private boolean fullBox = false;
    private int maxParticlesPerEdge = 200;

    public BorderManager(Main plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
    }

    /**
     * Loads (or reloads) configuration from config.yml. If the scheduler is
     * already running, it is restarted so a changed interval_ticks takes
     * effect immediately. Enabled players are preserved across reloads.
     */
    public void loadConfig() {
        String particleName = plugin.getConfig().getString("border.particle", "END_ROD");
        try {
            this.particle = Particle.valueOf(particleName.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid particle type '" + particleName
                    + "' in border.particle, defaulting to END_ROD");
            this.particle = Particle.END_ROD;
        }

        this.intervalTicks = plugin.getConfig().getInt("border.interval_ticks", 10);
        if (this.intervalTicks < 1) this.intervalTicks = 1;

        this.density = plugin.getConfig().getDouble("border.density", 1.0);
        if (this.density < 0.1) this.density = 0.1;

        this.renderDistance = plugin.getConfig().getDouble("border.render_distance", 64.0);

        this.fullBox = plugin.getConfig().getBoolean("border.full_box", false);

        this.maxParticlesPerEdge = plugin.getConfig().getInt("border.max_particles_per_edge", 200);
        if (this.maxParticlesPerEdge < 1) this.maxParticlesPerEdge = 1;

        // Restart the scheduler if it's running so a changed interval takes effect
        if (task != null) {
            task.cancel();
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::renderBorders, intervalTicks, intervalTicks);
        }
    }

    /**
     * Starts the repeating particle render task. Safe to call once during
     * onEnable; calling again is a no-op.
     */
    public void start() {
        if (task != null) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::renderBorders, intervalTicks, intervalTicks);
    }

    /**
     * Stops the particle render task. Called during onDisable.
     */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Toggles border display for the given player.
     *
     * @return the new state (true = enabled, false = disabled)
     */
    public boolean toggle(Player player) {
        UUID id = player.getUniqueId();
        if (enabledPlayers.contains(id)) {
            enabledPlayers.remove(id);
            return false;
        } else {
            enabledPlayers.add(id);
            return true;
        }
    }

    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    public void setEnabled(Player player, boolean enabled) {
        if (enabled) {
            enabledPlayers.add(player.getUniqueId());
        } else {
            enabledPlayers.remove(player.getUniqueId());
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void renderBorders() {
        if (enabledPlayers.isEmpty()) return;
        if (zoneManager == null) return;

        for (UUID playerId : enabledPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                enabledPlayers.remove(playerId);
                continue;
            }
            try {
                renderForPlayer(player);
            } catch (Exception e) {
                plugin.getLogger().warning("Error rendering border for "
                        + player.getName() + ": " + e.getMessage());
            }
        }
    }

    private void renderForPlayer(Player player) {
        World playerWorld = player.getWorld();
        Location playerLoc = player.getLocation();
        double renderDistSq = renderDistance * renderDistance;

        for (SpatialZoneIndex.ZoneBounds b : zoneManager.getSpatialIndex().getAllZones()) {
            // Skip zones in other worlds
            if (!b.world().equals(playerWorld.getName())) continue;

            // Skip zones whose center is too far away
            double cx = (b.x1() + b.x2()) / 2.0;
            double cy = (b.y1() + b.y2()) / 2.0;
            double cz = (b.z1() + b.z2()) / 2.0;
            double distSq = playerLoc.distanceSquared(new Location(playerWorld, cx, cy, cz));
            if (distSq > renderDistSq) continue;

            drawZoneBox(player, playerWorld, b);
        }
    }

    /**
     * Draws the zone border as particles. By default only the 4 bottom
     * (ground) edges are drawn; if full_box is enabled, all 12 edges of the
     * cuboid are drawn.
     */
    private void drawZoneBox(Player player, World world, SpatialZoneIndex.ZoneBounds b) {
        int x1 = b.x1(), y1 = b.y1(), z1 = b.z1();
        int x2 = b.x2(), y2 = b.y2(), z2 = b.z2();

        // Bottom edges (y = y1) - always drawn
        drawEdge(player, world, x1, y1, z1, x2, y1, z1);
        drawEdge(player, world, x1, y1, z2, x2, y1, z2);
        drawEdge(player, world, x1, y1, z1, x1, y1, z2);
        drawEdge(player, world, x2, y1, z1, x2, y1, z2);

        if (fullBox) {
            // Top edges (y = y2)
            drawEdge(player, world, x1, y2, z1, x2, y2, z1);
            drawEdge(player, world, x1, y2, z2, x2, y2, z2);
            drawEdge(player, world, x1, y2, z1, x1, y2, z2);
            drawEdge(player, world, x2, y2, z1, x2, y2, z2);

            // Vertical edges
            drawEdge(player, world, x1, y1, z1, x1, y2, z1);
            drawEdge(player, world, x2, y1, z1, x2, y2, z1);
            drawEdge(player, world, x1, y1, z2, x1, y2, z2);
            drawEdge(player, world, x2, y1, z2, x2, y2, z2);
        }
    }

    /**
     * Draws a single edge of the zone box as a line of particles.
     * Particles are centered in blocks (+0.5) and spaced according to density.
     */
    private void drawEdge(Player player, World world, int x1, int y1, int z1, int x2, int y2, int z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.001) return;

        int steps = (int) Math.min(maxParticlesPerEdge, Math.max(1, length / density));
        Location loc = new Location(world, 0, 0, 0);
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            loc.setX(x1 + dx * t + 0.5);
            loc.setY(y1 + dy * t + 0.5);
            loc.setZ(z1 + dz * t + 0.5);
            try {
                player.spawnParticle(particle, loc, 1, 0, 0, 0, 0);
            } catch (Exception ignored) {
                // Some particles require additional data; skip if they fail
            }
        }
    }
}