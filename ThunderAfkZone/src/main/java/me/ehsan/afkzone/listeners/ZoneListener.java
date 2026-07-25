package me.ehsan.afkzone.listeners;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class ZoneListener implements Listener {

    private final Main plugin;
    private final ZoneManager zoneManager;
    private final RewardManager rewardManager;

    public ZoneListener(Main plugin, ZoneManager zoneManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.rewardManager = rewardManager;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent e) {
        if (e.getFrom().getBlockX() == e.getTo().getBlockX() &&
                e.getFrom().getBlockY() == e.getTo().getBlockY() &&
                e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;

        Player p = e.getPlayer();
        String zone = zoneManager.findZoneForLocation(p.getLocation());
        UUID id = p.getUniqueId();
        String prev = rewardManager.getPlayerZone(id);

        if (zone != null && (prev == null || !prev.equals(zone))) {
            rewardManager.startTrackingPlayer(p, zone);
        } else if (zone == null && prev != null) {
            rewardManager.stopTrackingPlayer(id);
            rewardManager.sendExitMessage(p, prev);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        rewardManager.stopTrackingPlayer(e.getPlayer().getUniqueId());
    }
}