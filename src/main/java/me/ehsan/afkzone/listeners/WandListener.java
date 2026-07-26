package me.ehsan.afkzone.listeners;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.models.WandSelection;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the wooden hoe wand for selecting cuboid regions.
 * Left-click = position 1, Right-click = position 2.
 * Shows particle boundaries around the selected area.
 */
public class WandListener implements Listener {

    private final Main plugin;
    private final Map<UUID, WandSelection> selections = new HashMap<>();
    private final Map<UUID, BukkitRunnable> visualTasks = new HashMap<>();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Configurable
    private Material wandMaterial = Material.WOODEN_HOE;
    private boolean particlesEnabled = true;
    private Particle particleType = Particle.END_ROD;
    private int particleCount = 1;
    private double particleSpacing = 1.5;

    public WandListener(Main plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        var cfg = plugin.getConfig();
        String matName = cfg.getString("wand.item", "WOODEN_HOE");
        try {
            wandMaterial = Material.valueOf(matName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid wand material '" + matName + "'. Using WOODEN_HOE.");
            wandMaterial = Material.WOODEN_HOE;
        }
        particlesEnabled = cfg.getBoolean("wand.particles.enabled", true);
        particleCount = cfg.getInt("wand.particles.count", 1);
        particleSpacing = cfg.getDouble("wand.particles.spacing", 1.5);

        String typeName = cfg.getString("wand.particles.type", "END_ROD");
        try {
            particleType = Particle.valueOf(typeName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid wand particle type '" + typeName + "'. Using END_ROD.");
            particleType = Particle.END_ROD;
        }
    }

    public WandSelection getSelection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), k -> new WandSelection());
    }

    public void clearSelection(Player player) {
        UUID id = player.getUniqueId();
        selections.remove(id);
        stopVisualTask(id);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        // Check if holding the wand
        if (player.getInventory().getItemInMainHand().getType() != wandMaterial) return;

        // Only handle block clicks
        if (event.getClickedBlock() == null) return;

        event.setCancelled(true);

        Location loc = event.getClickedBlock().getLocation();
        WandSelection sel = getSelection(player);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Position 1
            sel.setPos1(loc);
            msg(player, "<green>Set position 1 to: <white>" + formatLoc(loc) + "</white></green>");
            if (sel.isComplete()) {
                msg(player, "<gray>Selection size: <white>" + sel.getDimensions() + "</white></gray>");
                msg(player, "<gray>Use <yellow>/afkzone create [name]</yellow> to create the zone.</gray>");
            } else {
                msg(player, "<gray>Now select position 2 with right-click.</gray>");
            }
            startVisualTask(player, sel);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Position 2
            sel.setPos2(loc);
            msg(player, "<green>Set position 2 to: <white>" + formatLoc(loc) + "</white></green>");
            if (sel.isComplete()) {
                msg(player, "<gray>Selection size: <white>" + sel.getDimensions() + "</white></gray>");
                msg(player, "<gray>Use <yellow>/afkzone create [name]</yellow> to create the zone.</gray>");
            } else {
                msg(player, "<gray>Now select position 1 with left-click.</gray>");
            }
            startVisualTask(player, sel);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        selections.remove(id);
        stopVisualTask(id);
    }

    // --- Visual selection particles ---

    private void startVisualTask(Player player, WandSelection sel) {
        UUID id = player.getUniqueId();
        stopVisualTask(id);

        if (!particlesEnabled) return;

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopVisualTask(id);
                    return;
                }
                if (!sel.isComplete()) {
                    // Show single point if only one position is set
                    Location single = sel.getPos1() != null ? sel.getPos1() : sel.getPos2();
                    if (single != null) {
                        showPoint(player, single);
                    }
                    return;
                }
                showSelectionBoundaries(player, sel);
            }
        };
        task.runTaskTimer(plugin, 0L, 20L); // every second
        visualTasks.put(id, task);
    }

    private void stopVisualTask(UUID id) {
        BukkitRunnable task = visualTasks.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    public void stopAllVisualTasks() {
        for (BukkitRunnable task : visualTasks.values()) {
            task.cancel();
        }
        visualTasks.clear();
    }

    private void showPoint(Player player, Location loc) {
        World world = loc.getWorld();
        if (world == null) return;
        double x = loc.getX() + 0.5;
        double y = loc.getY() + 0.5;
        double z = loc.getZ() + 0.5;
        player.spawnParticle(particleType, x, y, z, particleCount * 3, 0.3, 0.3, 0.3, 0);
    }

    private void showSelectionBoundaries(Player player, WandSelection sel) {
        Location min = sel.getMin();
        Location max = sel.getMax();
        if (min == null || max == null) return;

        World world = min.getWorld();
        if (world == null) return;

        int x1 = min.getBlockX();
        int y1 = min.getBlockY();
        int z1 = min.getBlockZ();
        int x2 = max.getBlockX();
        int y2 = max.getBlockY();
        int z2 = max.getBlockZ();

        double spacing = particleSpacing;

        // Bottom rectangle
        drawLine(world, x1, y1, z1, x2, y1, z1, spacing, player);
        drawLine(world, x2, y1, z1, x2, y1, z2, spacing, player);
        drawLine(world, x2, y1, z2, x1, y1, z2, spacing, player);
        drawLine(world, x1, y1, z2, x1, y1, z1, spacing, player);

        // Top rectangle
        drawLine(world, x1, y2, z1, x2, y2, z1, spacing, player);
        drawLine(world, x2, y2, z1, x2, y2, z2, spacing, player);
        drawLine(world, x2, y2, z2, x1, y2, z2, spacing, player);
        drawLine(world, x1, y2, z2, x1, y2, z1, spacing, player);

        // Vertical edges
        drawLine(world, x1, y1, z1, x1, y2, z1, spacing, player);
        drawLine(world, x2, y1, z1, x2, y2, z1, spacing, player);
        drawLine(world, x2, y1, z2, x2, y2, z2, spacing, player);
        drawLine(world, x1, y1, z2, x1, y2, z2, spacing, player);
    }

    private void drawLine(World world, int x1, int y1, int z1, int x2, int y2, int z2,
                          double spacing, Player player) {
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
            player.spawnParticle(particleType, px, py, pz, particleCount, 0, 0, 0, 0);
        }
    }

    // --- Helpers ---

    private String formatLoc(Location loc) {
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private void msg(Player player, String miniText) {
        try {
            player.sendMessage(MM.deserialize(miniText));
        } catch (Exception ex) {
            player.sendMessage(miniText.replaceAll("<[^>]+>", ""));
        }
    }
}