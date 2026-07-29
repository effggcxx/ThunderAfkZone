package me.ehsan.afkzone.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.ehsan.afkzone.managers.RewardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;

/**
 * Marks players as active so the AFK threshold in RewardManager can decide
 * whether reward progress should tick. Only players currently inside a zone
 * are updated (cheap no-op for everyone else).
 */
public class ActivityListener implements Listener {

    private final RewardManager rewardManager;

    public ActivityListener(RewardManager rewardManager) {
        this.rewardManager = rewardManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
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
}
