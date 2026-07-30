package me.ehsan.afkzone.service;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.config.MessagesConfig;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Handles reward delivery by giving the saved ItemStack directly to the player.
 * Rewards are defined by holding an item in hand and saving it in-game with
 * /afkzone reward save <name>. The item properties are serialized and
 * stored, and this dispatcher gives the exact saved item.
 */
public class RewardDispatcher {

    private final Main plugin;
    private final StorageService storageService;

    private Sound rewardSound = null;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;
    private MessagesConfig.MessageEntry msgRewardReceived;
    private MessagesConfig.MessageEntry msgRewardFailed;
    private MessagesConfig.MessageEntry msgInventoryFull;

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
    public void setMsgInventoryFull(MessagesConfig.MessageEntry entry) { this.msgInventoryFull = entry; }

    // --- Delivery ---

    public void giveRewardToPlayer(Reward r, Player player) {
        giveRewardToPlayer(r, player, this.rewardSound);
    }

    /**
     * Gives the saved ItemStack to the player. Uses the reward's stored item
     * properties and amount. If the item stack is null (no item was saved),
     * the reward fails silently and a warning is logged.
     *
     * @param r                     the reward to give
     * @param player                the target player
     * @param effectiveRewardSound  the sound to play on successful delivery
     */
    public void giveRewardToPlayer(Reward r, Player player, Sound effectiveRewardSound) {
        if (r == null || player == null) return;

        boolean ok = true;
        boolean overflow = false;

        try {
            ItemStack item = r.getItemStack();
            if (item == null) {
                plugin.getLogger().warning("Reward '" + r.getName() + "' has no saved item stack - skipping");
                ok = false;
            } else {
                // Clone and set the configured amount
                ItemStack giveItem = item.clone();
                giveItem.setAmount(r.getAmount());

                // Add to player's inventory; drop if full
                java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(giveItem);
                if (!leftover.isEmpty()) {
                    overflow = true;
                    for (ItemStack drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to give reward " + r.getName() + ": " + ex.getMessage());
            ok = false;
        }

        if (ok) {
            MessageUtils.sendStyled(player, msgRewardReceived, null, r.getName());
            if (overflow) {
                MessageUtils.sendStyled(player, msgInventoryFull, null, r.getName());
            }
            MessageUtils.playSound(player, effectiveRewardSound, soundVolume, soundPitch);
            storageService.incrementRewardsReceived(player.getUniqueId());
        } else {
            MessageUtils.sendStyled(player, msgRewardFailed, null, r.getName());
        }
    }
}