package me.ehsan.afkzone.listeners;

import me.ehsan.afkzone.managers.RewardManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class ActivityListener implements Listener {

    private final RewardManager rewardManager;

    public ActivityListener(RewardManager rewardManager) {
        this.rewardManager = rewardManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        rewardManager.markActive(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        rewardManager.markActive(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        rewardManager.markActive(e.getPlayer().getUniqueId());
    }
}
