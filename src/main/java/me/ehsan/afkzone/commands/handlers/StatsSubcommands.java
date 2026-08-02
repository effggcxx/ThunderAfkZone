package me.ehsan.afkzone.commands.handlers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the {@code stats} and {@code top} subcommands.
 *
 * <p>Split out from {@link me.ehsan.afkzone.commands.AfkZoneCommand} so the
 * router stays small. Method bodies are unchanged from the original.
 */
public class StatsSubcommands extends AbstractSubcommandHandler {

    public StatsSubcommands(Main plugin, ZoneManager zoneManager,
                            RewardManager rewardManager, StorageService storageService) {
        super(plugin, zoneManager, rewardManager, storageService);
    }

    // --- Stats ---

    public void handleStats(CommandSender sender, String[] args) {
        UUID targetId;
        String targetName;
        boolean isOtherPlayer = false;

        if (args.length >= 2) {
            // Viewing another player's stats - requires afkzone.stats.others permission
            if (!sender.hasPermission("afkzone.stats.others")) {
                msg(sender, "<red>You don't have permission to view other players' stats.</red>");
                msg(sender, "<gray>Permission required: <white>afkzone.stats.others</white></gray>");
                return;
            }

            // Try online player first, then fall back to offline.
            // Use Paper's getOfflinePlayerIfCached(name) instead of
            // Bukkit.getOfflinePlayer(name): the by-name Bukkit overload can
            // trigger a synchronous Mojang API lookup when the player isn't in
            // the local UUID cache, which blocks the main thread. The cached
            // variant returns null instead of doing a network call, and the
            // "must have logged in at least once" error path below already
            // handles that case.
            Player onlineTarget = Bukkit.getPlayerExact(args[1]);
            if (onlineTarget != null) {
                targetId = onlineTarget.getUniqueId();
                targetName = onlineTarget.getName();
            } else {
                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (offlineTarget == null || (!offlineTarget.hasPlayedBefore() && !offlineTarget.isOnline())) {
                    msg(sender, "<red>Player not found: <white>" + args[1] + "</white></red>");
                    msg(sender, "<gray>The player must have logged in at least once.</gray>");
                    return;
                }
                targetId = offlineTarget.getUniqueId();
                targetName = offlineTarget.getName() != null ? offlineTarget.getName() : args[1];
            }
            isOtherPlayer = true;
        } else if (sender instanceof Player p) {
            targetId = p.getUniqueId();
            targetName = p.getName();
        } else {
            msg(sender, "<red>Usage: /afkzone stats [player]</red>");
            msg(sender, "<gray>If run from console, specify a player name.</gray>");
            return;
        }

        // AFK time is flushed to storage periodically (not every second - see
        // RewardManager.flushAfkTimeAsync), so add back what's currently
        // buffered in memory to keep this display accurate to the second.
        long totalAfkTime = storageService.getTotalAfkTime(targetId) + rewardManager.getPendingAfkSeconds(targetId);
        int rewardsReceived = storageService.getTotalRewardsReceived(targetId);

        msg(sender, "<yellow>Statistics for <white>" + targetName + "</white>:</yellow>");
        if (rewardManager.getPlayerTracker().isPlayerInAnyZone(targetId)) {
            int sessionSeconds = rewardManager.getPlayerTracker().getSessionSeconds(targetId);
            String zone = rewardManager.getPlayerTracker().getPlayerZone(targetId);
            msg(sender, "  <gray>Current session: <white>" + MessageUtils.formatDuration(sessionSeconds)
                    + "</white> <dark_gray>(in " + zone + ")</dark_gray></gray>");
        } else if (isOtherPlayer) {
            msg(sender, "  <gray>Current session: <dark_gray>not in a zone</dark_gray></gray>");
        }
        msg(sender, "  <gray>Total AFK time: <white>" + MessageUtils.formatDuration(totalAfkTime) + "</white></gray>");
        msg(sender, "  <gray>Rewards received: <white>" + rewardsReceived + "</white></gray>");

        // Show per-zone stats
        msg(sender, "  <gray>Per-zone AFK time:</gray>");
        boolean hasZoneStats = false;
        for (String zone : zoneManager.getZoneNames()) {
            long zoneTime = storageService.getZoneAfkTime(targetId, zone) + rewardManager.getPendingZoneAfkSeconds(targetId, zone);
            if (zoneTime > 0) {
                hasZoneStats = true;
                msg(sender, "   <dark_gray>- <white>" + zone + "</white>: <gray>" + MessageUtils.formatDuration(zoneTime) + "</gray>");
            }
        }
        if (!hasZoneStats) {
            msg(sender, "   <dark_gray>No zone-specific AFK time recorded.</dark_gray>");
        }
    }

    // --- Top ---

    public void handleTop(CommandSender sender, String[] args) {
        String type = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "time";
        int limit = args.length >= 3 ? parseInt(args[2], 10) : 10;

        if (limit < 1) limit = 1;
        if (limit > 100) limit = 100;

        if ("rewards".equals(type) || "reward".equals(type)) {
            msg(sender, "<yellow>Top " + limit + " players by rewards received:</yellow>");
            List<Map.Entry<UUID, Integer>> top = storageService.getTopRewards(limit);
            int i = 1;
            for (Map.Entry<UUID, Integer> entry : top) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = entry.getKey().toString().substring(0, 8) + "...";
                msg(sender, "  <white>" + i + ".</white> <gray>" + name + "</gray> - <yellow>" + entry.getValue() + "</yellow> rewards");
                i++;
            }
            if (top.isEmpty()) {
                msg(sender, "  <gray>No data yet. Players need to spend time in AFK zones to appear here.</gray>");
            }
        } else if ("time".equals(type)) {
            msg(sender, "<yellow>Top " + limit + " players by AFK time:</yellow>");
            List<Map.Entry<UUID, Long>> top = storageService.getTopAfkTime(limit);
            int i = 1;
            for (Map.Entry<UUID, Long> entry : top) {
                String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = entry.getKey().toString().substring(0, 8) + "...";
                msg(sender, "  <white>" + i + ".</white> <gray>" + name + "</gray> - <yellow>" + MessageUtils.formatDuration(entry.getValue()) + "</yellow>");
                i++;
            }
            if (top.isEmpty()) {
                msg(sender, "  <gray>No data yet. Players need to spend time in AFK zones to appear here.</gray>");
            }
        } else {
            msg(sender, "<red>Unknown type: <white>" + type + "</white></red>");
            msg(sender, "<gray>Use <yellow>time</yellow> for AFK time or <yellow>rewards</yellow> for rewards received.</gray>");
        }
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("stats")) {
                return null;
            }
            if (args[0].equalsIgnoreCase("top")) {
                return filter(List.of("time", "rewards"), args[1]);
            }
        }
        return Collections.emptyList();
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}