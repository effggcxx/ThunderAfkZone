package me.ehsan.afkzone.service;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.config.MessagesConfig;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.logging.Level;

/**
 * Handles reward delivery (command dispatch, item giving, etc.).
 * Extracted from the original RewardManager for better separation of concerns.
 */
public class RewardDispatcher {

    private final Main plugin;
    private final StorageService storageService;

    private Sound rewardSound = null;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;
    private MessagesConfig.MessageEntry msgRewardReceived;
    private MessagesConfig.MessageEntry msgRewardFailed;

    public RewardDispatcher(Main plugin, StorageService storageService) {
        this.plugin = plugin;
        this.storageService = storageService;
    }

    // --- Configuration ---

    public void setRewardSound(Sound sound) { this.rewardSound = sound; }
    public void setSoundVolume(float volume) { this.soundVolume = volume; }
    public void setSoundPitch(float pitch) { this.soundPitch = pitch; }
    public void setMsgRewardReceived(MessagesConfig.MessageEntry entry) { this.msgRewardReceived = entry; }
    public void setMsgRewardFailed(MessagesConfig.MessageEntry entry) { this.msgRewardFailed = entry; }

    // --- Delivery ---

    public void giveRewardToPlayer(Reward r, Player player) {
        giveRewardToPlayer(r, player, this.rewardSound);
    }

    /**
     * Same as {@link #giveRewardToPlayer(Reward, Player)} but lets the caller
     * supply the reward sound to use for this specific delivery - used by
     * RewardManager to apply a zone's reward_sound override, if any, without
     * touching the global default stored on this dispatcher.
     */
    public void giveRewardToPlayer(Reward r, Player player, Sound effectiveRewardSound) {
        if (r == null || player == null) return;

        boolean ok = true;
        String ex = r.getExecutor() == null ? "console" : r.getExecutor().toLowerCase(Locale.ROOT);

        try {
            switch (ex) {
                case "console" -> {
                    if (r.getCommand() != null && !r.getCommand().isEmpty()) {
                        String cmd = r.getCommand().replace("{player}", player.getName()).replace("{award}", r.getName());
                        ok = dispatchAndCheck(cmd, r.getName());
                    }
                }
                case "itemedit" -> {
                    String dispatch = "si give " + player.getName() + " " + r.getItemName() + " " + r.getAmount();
                    ok = dispatchAndCheck(dispatch, r.getName());
                }
                case "vanilla", "give" -> {
                    String giveCmd = "give " + player.getName() + " " + r.getItemName() + " " + r.getAmount();
                    ok = dispatchAndCheck(giveCmd, r.getName());
                }
                default -> {
                    if (r.getCommand() != null && !r.getCommand().isEmpty()) {
                        String fallback = r.getCommand().replace("{player}", player.getName()).replace("{award}", r.getName());
                        ok = dispatchAndCheck(fallback, r.getName());
                    }
                }
            }
        } catch (Exception ex2) {
            plugin.getLogger().severe("Failed to give reward " + r.getName() + ": " + ex2.getMessage());
            ok = false;
        }

        if (ok) {
            MessageUtils.sendStyled(player, msgRewardReceived, null, r.getName());
            MessageUtils.playSound(player, effectiveRewardSound, soundVolume, soundPitch);
            storageService.incrementRewardsReceived(player.getUniqueId());
        } else {
            MessageUtils.sendStyled(player, msgRewardFailed, null, r.getName());
        }
    }

    private boolean dispatchAndCheck(String command, String rewardName) {
        try {
            boolean result = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            if (!result) {
                plugin.getLogger().warning("Command for reward '" + rewardName + "' returned failure: /" + command);
            }
            return result;
        } catch (Exception ex) {
            plugin.getLogger().severe("Exception dispatching command for reward '" + rewardName + "': /" + command + " - " + ex.getMessage());
            return false;
        }
    }
}