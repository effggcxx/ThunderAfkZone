package me.ehsan.afkzone.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/**
 * Handles zone enter/exit detection AND activity marking in a single listener
 * to guarantee ordering: zone transitions are processed at LOWEST priority,
 * activity marking at MONITOR priority.
 */
public class ZoneListener implements Listener {

    private final ZoneManager zoneManager;
    private final RewardManager rewardManager;

    public ZoneListener(ZoneManager zoneManager, RewardManager rewardManager) {
        this.zoneManager = zoneManager;
        this.rewardManager = rewardManager;
    }

    // --- Zone enter/exit detection (LOWEST priority so it runs first) ---

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMove(PlayerMoveEvent e) {
        // Ignore head rotation / small non-block moves
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Player p = e.getPlayer();
        String zone = zoneManager.findZoneForLocation(p.getLocation());
        UUID id = p.getUniqueId();
        String prev = rewardManager.getPlayerZone(id);

        if (zone != null && (prev == null || !prev.equals(zone))) {
            // Entered a (new) zone
            rewardManager.startTrackingPlayer(p, zone);
        } else if (zone == null && prev != null) {
            // Left the zone
            rewardManager.stopTrackingPlayer(id);
        }
    }

    // --- Activity marking (MONITOR priority so it runs after zone logic) ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMoveActivity(PlayerMoveEvent e) {
        // Only count actual block changes — head rotation alone is not activity
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
        rewardManager.markActive(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        rewardManager.markActive(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        rewardManager.markActive(e.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        rewardManager.markActive(e.getPlayer().getUniqueId());
    }

    // --- Cleanup on quit ---

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        rewardManager.stopTrackingPlayer(id);
    }
}