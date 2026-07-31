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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

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
    private boolean fullBox = true;
    private int maxParticlesPerEdge = 200;

    /** Log particle spawn failures at most once per session to avoid spam. */
    private final AtomicBoolean particleFailureLogged = new AtomicBoolean(false);

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
        if (this.renderDistance < 1.0) this.renderDistance = 1.0;

        // Default true so borders are visible as a full cuboid, not just the
        // often-underground bottom face at y1.
        this.fullBox = plugin.getConfig().getBoolean("border.full_box", true);

        this.maxParticlesPerEdge = plugin.getConfig().getInt("border.max_particles_per_edge", 200);
        if (this.maxParticlesPerEdge < 1) this.maxParticlesPerEdge = 1;

        // Allow logging a particle failure again after a reload
        particleFailureLogged.set(false);

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
        enabledPlayers.clear();
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
                plugin.getLogger().log(Level.WARNING,
                        "Error rendering border for " + player.getName(), e);
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

            // Distance to nearest point on the AABB — not the center, so large
            // zones still show borders when the player is near an edge.
            if (distanceSquaredToBox(playerLoc, b) > renderDistSq) continue;

            drawZoneBox(player, playerWorld, b);
        }
    }

    /**
     * Squared distance from a location to the nearest point on the zone's
     * axis-aligned bounding box. Zero if the player is inside the box.
     */
    private static double distanceSquaredToBox(Location loc, SpatialZoneIndex.ZoneBounds b) {
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        // Inclusive block bounds → continuous [min, max+1) volume matching how
        // particles are centered at block + 0.5 along each edge.
        double minX = b.x1();
        double maxX = b.x2() + 1.0;
        double minY = b.y1();
        double maxY = b.y2() + 1.0;
        double minZ = b.z1();
        double maxZ = b.z2() + 1.0;

        double dx = 0.0;
        if (px < minX) dx = minX - px;
        else if (px > maxX) dx = px - maxX;

        double dy = 0.0;
        if (py < minY) dy = minY - py;
        else if (py > maxY) dy = py - maxY;

        double dz = 0.0;
        if (pz < minZ) dz = minZ - pz;
        else if (pz > maxZ) dz = pz - maxZ;

        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Draws the zone border as particles. By default all 12 edges of the
     * cuboid are drawn; if full_box is false, only the 4 bottom edges are drawn.
     */
    private void drawZoneBox(Player player, World world, SpatialZoneIndex.ZoneBounds b) {
        int x1 = b.x1(), y1 = b.y1(), z1 = b.z1();
        int x2 = b.x2(), y2 = b.y2(), z2 = b.z2();

        // Bottom edges (y = y1)
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
            spawnParticleSafe(player, loc);
        }
    }

    /**
     * Spawns a particle for a single player with force=true so it is not
     * culled by the normal client particle range. Logs the first failure
     * (e.g. particle needs data) instead of failing silently forever.
     */
    private void spawnParticleSafe(Player player, Location loc) {
        try {
            // force=true: always send to this player regardless of distance/settings
            player.spawnParticle(particle, loc, 1, 0, 0, 0, 0, null, true);
        } catch (Exception e) {
            if (particleFailureLogged.compareAndSet(false, true)) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to spawn border particle '" + particle.name()
                                + "'. Try a simple type like END_ROD, FLAME, or CRIT in border.particle. "
                                + "Particles that need extra data (DUST, BLOCK, ITEM) are not supported.",
                        e);
            }
        }
    }
}
