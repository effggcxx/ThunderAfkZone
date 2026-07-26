package me.ehsan.afkzone.placeholder;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.models.NextRewardInfo;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.service.PlayerTracker;
import me.ehsan.afkzone.service.ZoneService;
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
import java.util.stream.Collectors;

/**
 * PlaceholderAPI expansion for ThunderAfkZone.
 * Provides: %afkzone_zone%, %afkzone_time%, %afkzone_next_reward%, %afkzone_rewards_received%, %afkzone_afk_time%, %afkzone_zone_time_<zone>%
 */
public class AfkZoneExpansion extends PlaceholderExpansion {

    private final Main plugin;
    private final ZoneService zoneService;
    private final PlayerTracker playerTracker;
    private final StorageService storageService;
    private final Map<String, Reward> rewards;

    public AfkZoneExpansion(Main plugin, ZoneService zoneService, PlayerTracker playerTracker,
                            StorageService storageService, Map<String, Reward> rewards) {
        this.plugin = plugin;
        this.zoneService = zoneService;
        this.playerTracker = playerTracker;
        this.storageService = storageService;
        this.rewards = rewards;
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
                // Total AFK time in current session (in the zone)
                String zone = playerTracker.getPlayerZone(id);
                if (zone == null) return "0s";
                Map<String, Integer> progress = playerTracker.getProgress(id);
                int totalSeconds = progress.values().stream().mapToInt(Integer::intValue).sum();
                return MessageUtils.formatDuration(totalSeconds);
            }
            case "next_reward" -> {
                String zone = playerTracker.getPlayerZone(id);
                if (zone == null) return "";
                Map<String, Integer> prog = playerTracker.getProgress(id);
                Set<String> given = playerTracker.getGivenOnce(id);
                List<Reward> zoneRewards = getRewardsForZone(zone);
                NextRewardInfo info = getNearestReward(prog, given, zoneRewards);
                if (info.getRemainingSeconds() <= 0) return "";
                return MessageUtils.formatDuration(info.getRemainingSeconds());
            }
            case "rewards_received" -> {
                return String.valueOf(storageService.getTotalRewardsReceived(id));
            }
            case "afk_time" -> {
                long seconds = storageService.getTotalAfkTime(id);
                return MessageUtils.formatDuration(seconds);
            }
            default -> {
                // Handle zone-specific placeholders: zone_time_<zone>
                if (params.startsWith("zone_time_")) {
                    String zoneName = params.substring("zone_time_".length());
                    long seconds = storageService.getZoneAfkTime(id, zoneName);
                    return MessageUtils.formatDuration(seconds);
                }
                return "";
            }
        }
    }

    private List<Reward> getRewardsForZone(String zoneName) {
        List<String> zoneRewardNames = zoneService.getZoneRewards(zoneName);
        if (zoneRewardNames == null || zoneRewardNames.isEmpty()) {
            return rewards.values().stream()
                    .filter(Reward::isEnabled)
                    .collect(Collectors.toList());
        }
        return zoneRewardNames.stream()
                .map(rewards::get)
                .filter(r -> r != null && r.isEnabled())
                .collect(Collectors.toList());
    }

    private NextRewardInfo getNearestReward(Map<String, Integer> prog, Set<String> given, List<Reward> zoneRewards) {
        long nearest = Long.MAX_VALUE;
        long total = 0;
        for (Reward r : zoneRewards) {
            if (!r.isEnabled()) continue;
            int current = prog.getOrDefault(r.getName(), 0);
            if (r.getOnceAfterSeconds() > 0 && !given.contains(r.getName())) {
                long remaining = r.getOnceAfterSeconds() - current;
                if (remaining >= 0 && remaining < nearest) {
                    nearest = remaining;
                    total = r.getOnceAfterSeconds();
                }
            }
            if (r.getIntervalSeconds() > 0) {
                long mod = current % r.getIntervalSeconds();
                long remaining = r.getIntervalSeconds() - mod;
                if (remaining >= 0 && remaining < nearest) {
                    nearest = remaining;
                    total = r.getIntervalSeconds();
                }
            }
        }
        return nearest == Long.MAX_VALUE ? new NextRewardInfo(0, 0) : new NextRewardInfo(nearest, total);
    }
}