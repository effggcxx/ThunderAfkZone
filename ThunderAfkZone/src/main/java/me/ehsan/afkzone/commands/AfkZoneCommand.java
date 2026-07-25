package me.ehsan.afkzone.commands;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.models.Reward;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class AfkZoneCommand implements CommandExecutor {

    private final Main plugin;
    private final ZoneManager zoneManager;
    private final RewardManager rewardManager;

    public AfkZoneCommand(Main plugin, ZoneManager zoneManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.rewardManager = rewardManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("afkzone")) return false;
        if (args == null || args.length == 0) {
            sender.sendMessage("Usage: /afkzone create <name> | list | info <name> | remove <name> | reload | reward list|give <reward> [player]");
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("afkzone.reload")) {
                    sender.sendMessage("You don't have permission to reload AfkZone.");
                    return true;
                }
                plugin.reloadConfig();
                rewardManager.loadRewards();
                rewardManager.loadGlobalConfig();
                zoneManager.loadZonesFile();
                sender.sendMessage("AfkZone configuration reloaded.");
                return true;
            }
            case "reward" -> {
                return handleRewardCommand(sender, args);
            }
            case "create" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Only players can create zones");
                    return true;
                }
                if (!sender.hasPermission("afkzone.create")) {
                    sender.sendMessage("You don't have permission to create zones.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /afkzone create <name>");
                    return true;
                }
                zoneManager.createZoneFromWorldEditSelection(player, args[1]);
                return true;
            }
            case "list" -> {
                if (!sender.hasPermission("afkzone.list")) {
                    sender.sendMessage("You don't have permission to list zones.");
                    return true;
                }
                listZones(sender);
                return true;
            }
            case "info" -> {
                if (!sender.hasPermission("afkzone.info")) {
                    sender.sendMessage("You don't have permission to view zone info.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /afkzone info <name>");
                    return true;
                }
                showZoneInfo(sender, args[1]);
                return true;
            }
            case "remove", "delete" -> {
                if (!sender.hasPermission("afkzone.remove")) {
                    sender.sendMessage("You don't have permission to remove zones.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /afkzone remove <name>");
                    return true;
                }
                removeZone(sender, args[1]);
                return true;
            }
            default -> {
                sender.sendMessage("Usage: /afkzone create <name> | list | info <name> | remove <name> | reload | reward list|give <reward> [player]");
                return true;
            }
        }
    }

    private boolean handleRewardCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Usage: /afkzone reward give <reward> [player] | list");
            return true;
        }
        String act = args[1].toLowerCase();
        if (act.equals("list")) {
            if (!sender.hasPermission("afkzone.reward.list")) {
                sender.sendMessage("You don't have permission to list rewards.");
                return true;
            }
            if (rewardManager.getRewards().isEmpty()) {
                sender.sendMessage("No rewards configured.");
                return true;
            }
            sender.sendMessage("Rewards:");
            for (Reward r : rewardManager.getRewards().values()) {
                String status = r.enabled ? "enabled" : "disabled";
                sender.sendMessage("- " + r.name + " (" + status + ", priority=" + r.priority + ") - " + r.description);
            }
            return true;
        } else if (act.equals("give")) {
            if (args.length < 3) {
                sender.sendMessage("Usage: /afkzone reward give <reward> [player]");
                return true;
            }
            if (!sender.hasPermission("afkzone.reward.give")) {
                sender.sendMessage("You don't have permission to give rewards.");
                return true;
            }
            String rewardName = args[2];
            Reward r = rewardManager.getRewards().get(rewardName);
            if (r == null) {
                sender.sendMessage("Unknown reward: " + rewardName);
                return true;
            }
            Player target = null;
            if (args.length >= 4) {
                target = plugin.getServer().getPlayerExact(args[3]);
                if (target == null) {
                    sender.sendMessage("Player not found: " + args[3]);
                    return true;
                }
            } else if (sender instanceof Player p) {
                target = p;
            }
            if (target == null) {
                sender.sendMessage("No target player specified and console cannot be target.");
                return true;
            }
            rewardManager.giveRewardToPlayer(r, target);
            return true;
        }
        return true;
    }

    private void listZones(CommandSender sender) {
        FileConfiguration zonesConfig = zoneManager.getZonesConfig();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) {
            sender.sendMessage("No zones configured.");
            return;
        }
        for (String key : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
            String path = "zones." + key;
            String world = zonesConfig.getString(path + ".world", "unknown");
            int x1 = zonesConfig.getInt(path + ".x1", 0);
            int y1 = zonesConfig.getInt(path + ".y1", 0);
            int z1 = zonesConfig.getInt(path + ".z1", 0);
            int x2 = zonesConfig.getInt(path + ".x2", 0);
            int y2 = zonesConfig.getInt(path + ".y2", 0);
            int z2 = zonesConfig.getInt(path + ".z2", 0);
            sender.sendMessage(key + ": " + world + " (" + x1 + "," + y1 + "," + z1 + ") → (" + x2 + "," + y2 + "," + z2 + ")");
        }
    }

    private void showZoneInfo(CommandSender sender, String name) {
        FileConfiguration zonesConfig = zoneManager.getZonesConfig();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones") || !zonesConfig.isSet("zones." + name)) {
            sender.sendMessage("Zone '" + name + "' does not exist.");
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

        sender.sendMessage("Zone '" + name + "':");
        sender.sendMessage("  World: " + world);
        sender.sendMessage("  Corners: (" + x1 + "," + y1 + "," + z1 + ") → (" + x2 + "," + y2 + "," + z2 + ")");
        sender.sendMessage("  Size: " + (Math.abs(x2 - x1) + 1) + " x " + (Math.abs(y2 - y1) + 1) + " x " + (Math.abs(z2 - z1) + 1));

        if (rewardManager.getRewards().isEmpty()) {
            sender.sendMessage("  Rewards: none configured.");
            return;
        }
        long enabledCount = rewardManager.getRewards().values().stream().filter(r -> r.enabled).count();
        sender.sendMessage("  Rewards (" + enabledCount + "/" + rewardManager.getRewards().size() + " enabled, apply to all zones):");
        for (Reward r : rewardManager.getRewards().values()) {
            String status = r.enabled ? "enabled" : "disabled";
            sender.sendMessage("   - " + r.name + " (" + status + ", priority=" + r.priority + ")");
        }
    }

    private void removeZone(CommandSender sender, String name) {
        FileConfiguration zonesConfig = zoneManager.getZonesConfig();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones") || !zonesConfig.isSet("zones." + name)) {
            sender.sendMessage("Zone '" + name + "' does not exist.");
            return;
        }
        zoneManager.removeZone(name);
        sender.sendMessage("Zone '" + name + "' removed.");
    }
}
