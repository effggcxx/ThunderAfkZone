package me.ehsan.afkzone.commands;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.models.Reward;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class AfkZoneCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final ZoneManager zoneManager;
    private final RewardManager rewardManager;

    private static final List<String> ROOT_SUBS = Arrays.asList(
            "create", "list", "info", "remove", "reload", "reward", "zonereward"
    );

    public AfkZoneCommand(Main plugin, ZoneManager zoneManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.rewardManager = rewardManager;
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
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                plugin.reloadConfig();
                rewardManager.loadRewards();
                rewardManager.loadGlobalConfig();
                zoneManager.loadZonesFile();
                sender.sendMessage("§aAfkZone configuration reloaded.");
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
                    sender.sendMessage("§cOnly players can create zones.");
                    return true;
                }
                if (!sender.hasPermission("afkzone.create")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /afkzone create <name>");
                    return true;
                }
                zoneManager.createZoneFromWorldEditSelection(player, args[1]);
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("afkzone.list")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                listZones(sender);
                return true;
            }
            case "info" -> {
                if (!sender.hasPermission("afkzone.info")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /afkzone info <name>");
                    return true;
                }
                showZoneInfo(sender, args[1]);
                return true;
            }
            case "remove", "delete" -> {
                if (!sender.hasPermission("afkzone.remove")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /afkzone remove <name>");
                    return true;
                }
                removeZone(sender, args[1]);
                return true;
            }
            default -> {
                sendUsage(sender);
                return true;
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§e/afkzone create <name> §7- create zone from WorldEdit selection");
        sender.sendMessage("§e/afkzone list §7- list zones");
        sender.sendMessage("§e/afkzone info <name> §7- zone details + rewards");
        sender.sendMessage("§e/afkzone remove <name> §7- remove a zone");
        sender.sendMessage("§e/afkzone reload §7- reload config");
        sender.sendMessage("§e/afkzone reward list|give <reward> [player]");
        sender.sendMessage("§e/afkzone zonereward list|add|remove <zone> [reward]");
    }

    private boolean handleRewardCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /afkzone reward list | give <reward> [player]");
            return true;
        }
        String act = args[1].toLowerCase(Locale.ROOT);
        if (act.equals("list")) {
            if (!sender.hasPermission("afkzone.reward.list")) {
                sender.sendMessage("§cYou don't have permission.");
                return true;
            }
            if (rewardManager.getRewards().isEmpty()) {
                sender.sendMessage("§7No rewards configured.");
                return true;
            }
            sender.sendMessage("§eGlobal rewards:");
            for (Reward r : rewardManager.getRewards().values()) {
                String status = r.enabled ? "§aenabled" : "§cdisabled";
                sender.sendMessage(" §7- §f" + r.name + " §7(" + status + "§7, priority=" + r.priority + ") §8- " + r.description);
            }
            return true;
        } else if (act.equals("give")) {
            if (!sender.hasPermission("afkzone.reward.give")) {
                sender.sendMessage("§cYou don't have permission.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /afkzone reward give <reward> [player]");
                return true;
            }
            String rewardName = args[2];
            Reward r = rewardManager.getRewards().get(rewardName);
            if (r == null) {
                sender.sendMessage("§cUnknown reward: " + rewardName);
                return true;
            }
            Player target = null;
            if (args.length >= 4) {
                target = plugin.getServer().getPlayerExact(args[3]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found: " + args[3]);
                    return true;
                }
            } else if (sender instanceof Player p) {
                target = p;
            }
            if (target == null) {
                sender.sendMessage("§cNo target player specified.");
                return true;
            }
            rewardManager.giveRewardToPlayer(r, target);
            sender.sendMessage("§aGave reward §e" + rewardName + " §ato §e" + target.getName());
            return true;
        }
        sender.sendMessage("§cUsage: /afkzone reward list | give <reward> [player]");
        return true;
    }

    private boolean handleZoneRewardCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /afkzone zonereward list <zone> | add <zone> <reward> | remove <zone> <reward> | clear <zone>");
            return true;
        }
        String act = args[1].toLowerCase(Locale.ROOT);

        switch (act) {
            case "list" -> {
                if (!sender.hasPermission("afkzone.zonereward.list")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /afkzone zonereward list <zone>");
                    return true;
                }
                String zone = args[2];
                if (!zoneManager.zoneExists(zone)) {
                    sender.sendMessage("§cZone '" + zone + "' does not exist.");
                    return true;
                }
                List<String> assigned = zoneManager.getZoneRewards(zone);
                if (assigned.isEmpty()) {
                    sender.sendMessage("§eZone §f" + zone + " §euses §aall enabled global rewards§e (no restriction).");
                } else {
                    sender.sendMessage("§eZone §f" + zone + " §erewards:");
                    for (String name : assigned) {
                        Reward r = rewardManager.getRewards().get(name);
                        String status = (r != null && r.enabled) ? "§aok" : "§cmissing/disabled";
                        sender.sendMessage(" §7- §f" + name + " §7(" + status + "§7)");
                    }
                }
                return true;
            }
            case "add" -> {
                if (!sender.hasPermission("afkzone.zonereward.add")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /afkzone zonereward add <zone> <reward>");
                    return true;
                }
                String zone = args[2];
                String reward = args[3];
                if (!zoneManager.zoneExists(zone)) {
                    sender.sendMessage("§cZone '" + zone + "' does not exist.");
                    return true;
                }
                if (!rewardManager.getRewards().containsKey(reward)) {
                    sender.sendMessage("§cUnknown reward: " + reward);
                    return true;
                }
                if (zoneManager.addZoneReward(zone, reward)) {
                    sender.sendMessage("§aAdded reward §e" + reward + " §ato zone §e" + zone);
                } else {
                    sender.sendMessage("§eReward §f" + reward + " §eis already assigned to §f" + zone);
                }
                return true;
            }
            case "remove" -> {
                if (!sender.hasPermission("afkzone.zonereward.remove")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                if (args.length < 4) {
                    sender.sendMessage("§cUsage: /afkzone zonereward remove <zone> <reward>");
                    return true;
                }
                String zone = args[2];
                String reward = args[3];
                if (!zoneManager.zoneExists(zone)) {
                    sender.sendMessage("§cZone '" + zone + "' does not exist.");
                    return true;
                }
                if (zoneManager.removeZoneReward(zone, reward)) {
                    sender.sendMessage("§aRemoved reward §e" + reward + " §afrom zone §e" + zone);
                } else {
                    sender.sendMessage("§eReward §f" + reward + " §ewas not assigned to §f" + zone);
                }
                return true;
            }
            case "clear" -> {
                if (!sender.hasPermission("afkzone.zonereward.clear")) {
                    sender.sendMessage("§cYou don't have permission.");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /afkzone zonereward clear <zone>");
                    return true;
                }
                String zone = args[2];
                if (!zoneManager.zoneExists(zone)) {
                    sender.sendMessage("§cZone '" + zone + "' does not exist.");
                    return true;
                }
                zoneManager.setZoneRewards(zone, Collections.emptyList());
                sender.sendMessage("§aCleared reward restrictions for §e" + zone + "§a. It now uses all global rewards.");
                return true;
            }
            default -> {
                sender.sendMessage("§cUsage: /afkzone zonereward list|add|remove|clear ...");
                return true;
            }
        }
    }

    private void listZones(CommandSender sender) {
        FileConfiguration zonesConfig = zoneManager.getZonesConfig();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) {
            sender.sendMessage("§7No zones configured.");
            return;
        }
        sender.sendMessage("§eAFK Zones:");
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
            String rewardInfo = rewards.isEmpty() ? "§7all global" : "§f" + rewards.size() + " reward(s)";
            sender.sendMessage(" §f" + key + " §7→ " + world + " (" + x1 + "," + y1 + "," + z1 + ")→(" + x2 + "," + y2 + "," + z2 + ") §8| " + rewardInfo);
        }
    }

    private void showZoneInfo(CommandSender sender, String name) {
        FileConfiguration zonesConfig = zoneManager.getZonesConfig();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones") || !zonesConfig.isSet("zones." + name)) {
            sender.sendMessage("§cZone '" + name + "' does not exist.");
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

        sender.sendMessage("§eZone §f'" + name + "'§e:");
        sender.sendMessage("  §7World: §f" + world);
        sender.sendMessage("  §7Corners: §f(" + x1 + "," + y1 + "," + z1 + ") → (" + x2 + "," + y2 + "," + z2 + ")");
        sender.sendMessage("  §7Size: §f" + (Math.abs(x2 - x1) + 1) + " x " + (Math.abs(y2 - y1) + 1) + " x " + (Math.abs(z2 - z1) + 1));

        List<String> assigned = zoneManager.getZoneRewards(name);
        if (assigned.isEmpty()) {
            sender.sendMessage("  §7Rewards: §aall enabled global rewards");
            for (Reward r : rewardManager.getRewards().values()) {
                if (!r.enabled) continue;
                sender.sendMessage("   §8- §f" + r.name + " §7(priority=" + r.priority + ")");
            }
        } else {
            sender.sendMessage("  §7Rewards (§f" + assigned.size() + "§7 assigned):");
            for (String rn : assigned) {
                Reward r = rewardManager.getRewards().get(rn);
                if (r == null) {
                    sender.sendMessage("   §8- §c" + rn + " §7(missing from config!)");
                } else {
                    String status = r.enabled ? "§aenabled" : "§cdisabled";
                    sender.sendMessage("   §8- §f" + rn + " §7(" + status + "§7, priority=" + r.priority + ")");
                }
            }
        }
    }

    private void removeZone(CommandSender sender, String name) {
        if (!zoneManager.zoneExists(name)) {
            sender.sendMessage("§cZone '" + name + "' does not exist.");
            return;
        }
        zoneManager.removeZone(name);
        sender.sendMessage("§aZone '" + name + "' removed.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(ROOT_SUBS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2) {
            switch (sub) {
                case "info", "remove", "delete", "create" -> {
                    return filter(new ArrayList<>(zoneManager.getZoneNames()), args[1]);
                }
                case "reward" -> {
                    return filter(Arrays.asList("list", "give"), args[1]);
                }
                case "zonereward" -> {
                    return filter(Arrays.asList("list", "add", "remove", "clear"), args[1]);
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
