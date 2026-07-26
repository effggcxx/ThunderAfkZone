package me.ehsan.afkzone.commands;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class AfkZoneCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final ZoneManager zoneManager;
    private final RewardManager rewardManager;
    private final StorageService storageService;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final List<String> ROOT_SUBS = Arrays.asList(
            "create", "list", "info", "remove", "reload", "reward", "zonereward", "stats", "top"
    );

    public AfkZoneCommand(Main plugin, ZoneManager zoneManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.rewardManager = rewardManager;
        this.storageService = plugin.getStorageService();
    }

    private void msg(CommandSender sender, String miniText) {
        try {
            sender.sendMessage(MM.deserialize(miniText));
        } catch (Exception ex) {
            sender.sendMessage(miniText.replaceAll("<[^>]+>", ""));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("afkzone")) return false;
        if (args == null || args.length == 0) {
            sendUsage(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("afkzone.reload")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                plugin.reloadConfig();
                rewardManager.loadRewards();
                rewardManager.loadGlobalConfig();
                zoneManager.reload();
                msg(sender, "<green>AfkZone configuration reloaded.</green>");
                return true;
            }
            case "reward" -> {
                return handleRewardCommand(sender, args);
            }
            case "zonereward" -> {
                return handleZoneRewardCommand(sender, args);
            }
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>Only players can create zones.</red>");
                    return true;
                }
                if (!sender.hasPermission("afkzone.create")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                if (args.length < 2) {
                    msg(sender, "<red>Usage: /afkzone create [name]</red>");
                    return true;
                }
                zoneManager.createZoneFromWorldEditSelection(player, args[1]);
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("afkzone.list")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                listZones(sender);
                return true;
            }
            case "info" -> {
                if (!sender.hasPermission("afkzone.info")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                if (args.length < 2) {
                    msg(sender, "<red>Usage: /afkzone info [name]</red>");
                    return true;
                }
                showZoneInfo(sender, args[1]);
                return true;
            }
            case "remove", "delete" -> {
                if (!sender.hasPermission("afkzone.remove")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                if (args.length < 2) {
                    msg(sender, "<red>Usage: /afkzone remove [name]</red>");
                    return true;
                }
                removeZone(sender, args[1]);
                return true;
            }
            case "stats" -> {
                if (!sender.hasPermission("afkzone.stats")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                handleStats(sender, args);
                return true;
            }
            case "top" -> {
                if (!sender.hasPermission("afkzone.top")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                handleTop(sender, args);
                return true;
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        msg(sender, "<yellow>/afkzone create [name] <gray>- create zone from WorldEdit selection</gray></yellow>");
        msg(sender, "<yellow>/afkzone list <gray>- list zones</gray></yellow>");
        msg(sender, "<yellow>/afkzone info [name] <gray>- zone details + rewards</gray></yellow>");
        msg(sender, "<yellow>/afkzone remove [name] <gray>- remove a zone</gray></yellow>");
        msg(sender, "<yellow>/afkzone reload <gray>- reload config</gray></yellow>");
        msg(sender, "<yellow>/afkzone reward list|give [reward] [player]</yellow>");
        msg(sender, "<yellow>/afkzone zonereward list|add|remove [zone] [reward]</yellow>");
        msg(sender, "<yellow>/afkzone stats [player] <gray>- view AFK statistics</gray></yellow>");
        msg(sender, "<yellow>/afkzone top [time|rewards] <gray>- top players</gray></yellow>");
    }

    // --- Stats ---

    private void handleStats(CommandSender sender, String[] args) {
        UUID targetId;
        String targetName;

        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                // Try offline player by name
                msg(sender, "<red>Player not found: " + args[1] + "</red>");
                return;
            }
            targetId = target.getUniqueId();
            targetName = target.getName();
        } else if (sender instanceof Player p) {
            targetId = p.getUniqueId();
            targetName = p.getName();
        } else {
            msg(sender, "<red>Usage: /afkzone stats [player]</red>");
            return;
        }

        long totalAfkTime = storageService.getTotalAfkTime(targetId);
        int rewardsReceived = storageService.getTotalRewardsReceived(targetId);

        msg(sender, "<yellow>Statistics for <white>" + targetName + "</white>:</yellow>");
        msg(sender, "  <gray>Total AFK time: <white>" + MessageUtils.formatDuration(totalAfkTime) + "</white></gray>");
        msg(sender, "  <gray>Rewards received: <white>" + rewardsReceived + "</white></gray>");

        // Show per-zone stats
        msg(sender, "  <gray>Per-zone AFK time:</gray>");
        for (String zone : zoneManager.getZoneNames()) {
            long zoneTime = storageService.getZoneAfkTime(targetId, zone);
            if (zoneTime > 0) {
                msg(sender, "   <dark_gray>- <white>" + zone + "</white>: <gray>" + MessageUtils.formatDuration(zoneTime) + "</gray>");
            }
        }
    }

    // --- Top ---

    private void handleTop(CommandSender sender, String[] args) {
        String type = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "time";
        int limit = args.length >= 3 ? parseInt(args[2], 10) : 10;

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
                msg(sender, "  <gray>No data yet.</gray>");
            }
        } else {
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
                msg(sender, "  <gray>No data yet.</gray>");
            }
        }
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    // --- Reward command ---

    private boolean handleRewardCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "<red>Usage: /afkzone reward list | give [reward] [player]</red>");
            return true;
        }
        String act = args[1].toLowerCase(Locale.ROOT);
        if (act.equals("list")) {
            if (!sender.hasPermission("afkzone.reward.list")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            if (rewardManager.getRewards().isEmpty()) {
                msg(sender, "<gray>No rewards configured.</gray>");
                return true;
            }
            msg(sender, "<yellow>Global rewards:</yellow>");
            for (Reward r : rewardManager.getRewards().values()) {
                String status = r.isEnabled() ? "<green>enabled</green>" : "<red>disabled</red>";
                msg(sender, " <gray>- <white>" + r.getName() + "</white> <gray>(" + status + "<gray>, priority=" + r.getPriority()
                        + ") <dark_gray>- " + r.getDescription());
            }
            return true;
        } else if (act.equals("give")) {
            if (!sender.hasPermission("afkzone.reward.give")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            if (args.length < 3) {
                msg(sender, "<red>Usage: /afkzone reward give [reward] [player]</red>");
                return true;
            }
            String rewardName = args[2];
            Reward r = rewardManager.getRewards().get(rewardName);
            if (r == null) {
                msg(sender, "<red>Unknown reward: " + rewardName + "</red>");
                return true;
            }
            Player target = null;
            if (args.length >= 4) {
                target = plugin.getServer().getPlayerExact(args[3]);
                if (target == null) {
                    msg(sender, "<red>Player not found: " + args[3] + "</red>");
                    return true;
                }
            } else if (sender instanceof Player p) {
                target = p;
            }
            if (target == null) {
                msg(sender, "<red>No target player specified.</red>");
                return true;
            }
            rewardManager.getRewardDispatcher().giveRewardToPlayer(r, target);
            msg(sender, "<green>Gave reward <yellow>" + rewardName + "</yellow> to <yellow>" + target.getName() + "</yellow></green>");
            return true;
        }
        msg(sender, "<red>Usage: /afkzone reward list | give [reward] [player]</red>");
        return true;
    }

    // --- Zone reward command ---

    private boolean handleZoneRewardCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afkzone.zonereward")) {
            msg(sender, "<red>You don't have permission.</red>");
            return true;
        }
        if (args.length < 2) {
            msg(sender, "<red>Usage: /afkzone zonereward list [zone] | add [zone] [reward] | remove [zone] [reward] | clear [zone]</red>");
            return true;
        }
        String act = args[1].toLowerCase(Locale.ROOT);

        switch (act) {
            case "list" -> {
                if (args.length < 3) {
                    msg(sender, "<red>Usage: /afkzone zonereward list [zone]</red>");
                    return true;
                }
                String zone = args[2];
                if (!zoneManager.zoneExists(zone)) {
                    msg(sender, "<red>Zone '" + zone + "' does not exist.</red>");
                    return true;
                }
                List<String> assigned = zoneManager.getZoneRewards(zone);
                if (assigned.isEmpty()) {
                    msg(sender, "<yellow>Zone <white>" + zone + "</white> uses <green>all enabled global rewards</green> (no restriction).</yellow>");
                } else {
                    msg(sender, "<yellow>Zone <white>" + zone + "</white> rewards:</yellow>");
                    for (String name : assigned) {
                        Reward r = rewardManager.getRewards().get(name);
                        String status = (r != null && r.isEnabled()) ? "<green>ok</green>" : "<red>missing/disabled</red>";
                        msg(sender, " <gray>- <white>" + name + "</white> <gray>(" + status + "<gray>)");
                    }
                }
                return true;
            }
            case "add" -> {
                if (args.length < 4) {
                    msg(sender, "<red>Usage: /afkzone zonereward add [zone] [reward]</red>");
                    return true;
                }
                String zone = args[2];
                String reward = args[3];
                if (!zoneManager.zoneExists(zone)) {
                    msg(sender, "<red>Zone '" + zone + "' does not exist.</red>");
                    return true;
                }
                if (!rewardManager.getRewards().containsKey(reward)) {
                    msg(sender, "<red>Unknown reward: " + reward + "</red>");
                    return true;
                }
                if (zoneManager.addZoneReward(zone, reward)) {
                    msg(sender, "<green>Added reward <yellow>" + reward + "</yellow> to zone <yellow>" + zone + "</yellow></green>");
                } else {
                    msg(sender, "<yellow>Reward <white>" + reward + "</white> is already assigned to <white>" + zone + "</white></yellow>");
                }
                return true;
            }
            case "remove" -> {
                if (args.length < 4) {
                    msg(sender, "<red>Usage: /afkzone zonereward remove [zone] [reward]</red>");
                    return true;
                }
                String zone = args[2];
                String reward = args[3];
                if (!zoneManager.zoneExists(zone)) {
                    msg(sender, "<red>Zone '" + zone + "' does not exist.</red>");
                    return true;
                }
                if (zoneManager.removeZoneReward(zone, reward)) {
                    msg(sender, "<green>Removed reward <yellow>" + reward + "</yellow> from zone <yellow>" + zone + "</yellow></green>");
                } else {
                    msg(sender, "<yellow>Reward <white>" + reward + "</white> was not assigned to <white>" + zone + "</white></yellow>");
                }
                return true;
            }
            case "clear" -> {
                if (args.length < 3) {
                    msg(sender, "<red>Usage: /afkzone zonereward clear [zone]</red>");
                    return true;
                }
                String zone = args[2];
                if (!zoneManager.zoneExists(zone)) {
                    msg(sender, "<red>Zone '" + zone + "' does not exist.</red>");
                    return true;
                }
                zoneManager.setZoneRewards(zone, Collections.emptyList());
                msg(sender, "<green>Cleared reward restrictions for <yellow>" + zone + "</yellow>. It now uses all global rewards.</green>");
                return true;
            }
            default -> {
                msg(sender, "<red>Usage: /afkzone zonereward list|add|remove|clear ...</red>");
                return true;
            }
        }
    }

    // --- Zone listing ---

    private void listZones(CommandSender sender) {
        FileConfiguration zonesConfig = zoneManager.getZonesConfig();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) {
            msg(sender, "<gray>No zones configured.</gray>");
            return;
        }
        msg(sender, "<yellow>AFK Zones:</yellow>");
        for (String key : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
            String path = "zones." + key;
            String world = zonesConfig.getString(path + ".world", "unknown");
            int x1 = zonesConfig.getInt(path + ".x1", 0);
            int y1 = zonesConfig.getInt(path + ".y1", 0);
            int z1 = zonesConfig.getInt(path + ".z1", 0);
            int x2 = zonesConfig.getInt(path + ".x2", 0);
            int y2 = zonesConfig.getInt(path + ".y2", 0);
            int z2 = zonesConfig.getInt(path + ".z2", 0);
            List<String> rewards = zoneManager.getZoneRewards(key);
            String rewardInfo = rewards.isEmpty() ? "<gray>all global</gray>" : "<white>" + rewards.size() + " reward(s)</white>";
            msg(sender, " <white>" + key + "</white> <gray>-></gray> " + world + " (" + x1 + "," + y1 + "," + z1 + ")->(" + x2 + "," + y2 + "," + z2 + ") <dark_gray>|</dark_gray> " + rewardInfo);
        }
    }

    private void showZoneInfo(CommandSender sender, String name) {
        FileConfiguration zonesConfig = zoneManager.getZonesConfig();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones") || !zonesConfig.isSet("zones." + name)) {
            msg(sender, "<red>Zone '" + name + "' does not exist.</red>");
            return;
        }
        String path = "zones." + name;
        String world = zonesConfig.getString(path + ".world", "unknown");
        int x1 = zonesConfig.getInt(path + ".x1", 0);
        int y1 = zonesConfig.getInt(path + ".y1", 0);
        int z1 = zonesConfig.getInt(path + ".z1", 0);
        int x2 = zonesConfig.getInt(path + ".x2", 0);
        int y2 = zonesConfig.getInt(path + ".y2", 0);
        int z2 = zonesConfig.getInt(path + ".z2", 0);

        msg(sender, "<yellow>Zone <white>'" + name + "'</white>:</yellow>");
        msg(sender, "  <gray>World: <white>" + world + "</white></gray>");
        msg(sender, "  <gray>Corners: <white>(" + x1 + "," + y1 + "," + z1 + ") -> (" + x2 + "," + y2 + "," + z2 + ")</white></gray>");
        msg(sender, "  <gray>Size: <white>" + (Math.abs(x2 - x1) + 1) + " x " + (Math.abs(y2 - y1) + 1) + " x " + (Math.abs(z2 - z1) + 1) + "</white></gray>");

        // Show entry/exit commands if configured
        List<String> entryCmds = zoneManager.getZoneEntryCommands(name);
        if (!entryCmds.isEmpty()) {
            msg(sender, "  <gray>Entry commands: <white>" + entryCmds.size() + "</white></gray>");
        }
        List<String> exitCmds = zoneManager.getZoneExitCommands(name);
        if (!exitCmds.isEmpty()) {
            msg(sender, "  <gray>Exit commands: <white>" + exitCmds.size() + "</white></gray>");
        }

        List<String> assigned = zoneManager.getZoneRewards(name);
        if (assigned.isEmpty()) {
            msg(sender, "  <gray>Rewards: <green>all enabled global rewards</green></gray>");
            for (Reward r : rewardManager.getRewards().values()) {
                if (!r.isEnabled()) continue;
                msg(sender, "   <dark_gray>- <white>" + r.getName() + "</white> <gray>(priority=" + r.getPriority() + ")</gray>");
            }
        } else {
            msg(sender, "  <gray>Rewards (<white>" + assigned.size() + "</white> assigned):</gray>");
            for (String rn : assigned) {
                Reward r = rewardManager.getRewards().get(rn);
                if (r == null) {
                    msg(sender, "   <dark_gray>- <red>" + rn + "</red> <gray>(missing from config!)</gray>");
                } else {
                    String status = r.isEnabled() ? "<green>enabled</green>" : "<red>disabled</red>";
                    msg(sender, "   <dark_gray>- <white>" + rn + "</white> <gray>(" + status + "<gray>, priority=" + r.getPriority() + ")");
                }
            }
        }
    }

    private void removeZone(CommandSender sender, String name) {
        if (!zoneManager.zoneExists(name)) {
            msg(sender, "<red>Zone '" + name + "' does not exist.</red>");
            return;
        }
        zoneManager.removeZone(name);
        msg(sender, "<green>Zone '" + name + "' removed.</green>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(ROOT_SUBS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            switch (sub) {
                case "info", "remove", "delete" -> {
                    return filter(new ArrayList<>(zoneManager.getZoneNames()), args[1]);
                }
                case "reward" -> {
                    return filter(Arrays.asList("list", "give"), args[1]);
                }
                case "zonereward" -> {
                    return filter(Arrays.asList("list", "add", "remove", "clear"), args[1]);
                }
                case "top" -> {
                    return filter(Arrays.asList("time", "rewards"), args[1]);
                }
                default -> {
                    return Collections.emptyList();
                }
            }
        }

        if (args.length == 3) {
            if (sub.equals("reward") && args[1].equalsIgnoreCase("give")) {
                return filter(new ArrayList<>(rewardManager.getRewards().keySet()), args[2]);
            }
            if (sub.equals("zonereward")) {
                String act = args[1].toLowerCase(Locale.ROOT);
                if (act.equals("list") || act.equals("add") || act.equals("remove") || act.equals("clear")) {
                    return filter(new ArrayList<>(zoneManager.getZoneNames()), args[2]);
                }
            }
        }

        if (args.length == 4) {
            if (sub.equals("reward") && args[1].equalsIgnoreCase("give")) {
                return null; // Bukkit suggests online players
            }
            if (sub.equals("zonereward")) {
                String act = args[1].toLowerCase(Locale.ROOT);
                if (act.equals("add")) {
                    return filter(new ArrayList<>(rewardManager.getRewards().keySet()), args[3]);
                }
                if (act.equals("remove")) {
                    String zone = args[2];
                    return filter(zoneManager.getZoneRewards(zone), args[3]);
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String partial) {
        if (partial == null || partial.isEmpty()) return options;
        String lower = partial.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .collect(Collectors.toList());
    }
}