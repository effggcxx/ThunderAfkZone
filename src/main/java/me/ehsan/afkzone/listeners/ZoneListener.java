package me.ehsan.afkzone.listeners;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.service.ParticleService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class ZoneListener implements Listener {

    private final ZoneManager zoneManager;
    private final RewardManager rewardManager;
    private final ParticleService particleService;

    public ZoneListener(Main plugin, ZoneManager zoneManager, RewardManager rewardManager, ParticleService particleService) {
        this.zoneManager = zoneManager;
        this.rewardManager = rewardManager;
        this.particleService = particleService;
    }

    @EventHandler
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
            particleService.startShowing(p);
        } else if (zone == null && prev != null) {
            // Left the zone
            rewardManager.stopTrackingPlayer(id);
            particleService.stopShowing(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        rewardManager.stopTrackingPlayer(id);
        particleService.stopShowing(p);
    }
}