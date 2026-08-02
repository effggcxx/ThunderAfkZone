package me.ehsan.afkzone.placeholder;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.models.NextRewardInfo;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.service.PlayerTracker;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * PlaceholderAPI expansion for ThunderAfkZone.
 * Provides: %afkzone_zone%, %afkzone_time%, %afkzone_next_reward%, %afkzone_rewards_received%, %afkzone_afk_time%, %afkzone_zone_time_<zone>%
 *
 * Delegates reward logic to RewardManager to avoid code duplication.
 */
public class AfkZoneExpansion extends PlaceholderExpansion {

    private final Main plugin;
    private final RewardManager rewardManager;
    private final PlayerTracker playerTracker;
    private final StorageService storageService;

    public AfkZoneExpansion(Main plugin, PlayerTracker playerTracker,
                            StorageService storageService) {
        this.plugin = plugin;
        this.rewardManager = plugin.getRewardManager();
        this.playerTracker = playerTracker;
        this.storageService = storageService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "afkzone";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Ehsan";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Keep the expansion loaded even after /reload
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) return "";

        UUID id = player.getUniqueId();

        switch (params.toLowerCase()) {
            case "zone" -> {
                String zone = playerTracker.getPlayerZone(id);
                return zone != null ? zone : "";
            }
            case "in_zone" -> {
                return playerTracker.isPlayerInAnyZone(id) ? "yes" : "no";
            }
            case "time" -> {
                // Current-session AFK time (resets when the player leaves the zone)
                if (!playerTracker.isPlayerInAnyZone(id)) return "0s";
                int totalSeconds = playerTracker.getSessionSeconds(id);
                return MessageUtils.formatDuration(totalSeconds);
            }
            case "next_reward" -> {
                String zone = playerTracker.getPlayerZone(id);
                if (zone == null) return "";
                Map<String, Integer> prog = playerTracker.getProgress(id);
                Set<String> given = playerTracker.getGivenOnce(id);
                List<Reward> zoneRewards = rewardManager.getRewardsForZone(zone);
                NextRewardInfo info = rewardManager.getNearestReward(prog, given, zoneRewards);
                if (info.getRemainingSeconds() <= 0) return "";
                return MessageUtils.formatDuration(info.getRemainingSeconds());
            }
            case "rewards_received" -> {
                return String.valueOf(storageService.getTotalRewardsReceived(id));
            }
            case "afk_time" -> {
                // AFK time is flushed to storage periodically, not every second
                // (see RewardManager.flushAfkTimeAsync) - add back what's still
                // buffered in memory so this placeholder stays accurate.
                long seconds = storageService.getTotalAfkTime(id) + rewardManager.getPendingAfkSeconds(id);
                return MessageUtils.formatDuration(seconds);
            }
            default -> {
                // Handle zone-specific placeholders: zone_time_<zone>
                if (params.startsWith("zone_time_")) {
                    String zoneName = params.substring("zone_time_".length());
                    long seconds = storageService.getZoneAfkTime(id, zoneName) + rewardManager.getPendingZoneAfkSeconds(id, zoneName);
                    return MessageUtils.formatDuration(seconds);
                }
                return "";
            }
        }
    }
}