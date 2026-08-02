package me.ehsan.afkzone.commands.handlers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.storage.StorageService;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Handles the {@code zonereward} subcommand group
 * ({@code list/add/remove/clear}).
 *
 * <p>Split out from {@link me.ehsan.afkzone.commands.AfkZoneCommand} so the
 * router stays small. Method bodies are unchanged from the original.
 */
public class ZoneRewardSubcommands extends AbstractSubcommandHandler {

    public ZoneRewardSubcommands(Main plugin, ZoneManager zoneManager,
                                 RewardManager rewardManager, StorageService storageService) {
        super(plugin, zoneManager, rewardManager, storageService);
    }

    public boolean handleZoneRewardCommand(CommandSender sender, String[] args) {
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
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                    return true;
                }
                String zone = args[2];
                if (!zoneManager.zoneExists(zone)) {
                    msg(sender, "<red>Zone '<white>" + zone + "</white>' does not exist.</red>");
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                    return true;
                }
                List<String> assigned = zoneManager.getZoneRewards(zone);
                if (assigned.isEmpty()) {
                    msg(sender, "<yellow>Zone <white>" + zone + "</white></yellow>");
                    msg(sender, "<gray>  Reward restriction: <green>none</green> - uses all enabled saved rewards.</gray>");
                } else {
                    msg(sender, "<yellow>Zone <white>" + zone + "</white> - assigned rewards (<white>" + assigned.size() + "</white>):</yellow>");
                    for (String name : assigned) {
                        Reward r = rewardManager.getRewards().get(name);
                        String status = (r != null && r.isEnabled()) ? "<green>ok</green>" : "<red>missing/disabled</red>";
                        String itemInfo = (r != null && r.getItemStack() != null)
                                ? " <gray>(" + r.getItemStack().getType().name().toLowerCase() + ")</gray>"
                                : "";
                        msg(sender, "  <gray>-</gray> <white>" + name + "</white>" + itemInfo + " <gray>(" + status + "<gray>)");
                    }
                }
                return true;
            }
            case "add" -> {
                if (args.length < 4) {
                    msg(sender, "<red>Usage: /afkzone zonereward add [zone] [reward]</red>");
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                    if (!rewardManager.getRewards().isEmpty()) {
                        msg(sender, "<gray>Available rewards: <white>" + String.join("</white>, <white>", rewardManager.getRewards().keySet()) + "</white></gray>");
                    }
                    return true;
                }
                String zone = args[2];
                String reward = args[3];
                if (!zoneManager.zoneExists(zone)) {
                    msg(sender, "<red>Zone '<white>" + zone + "</white>' does not exist.</red>");
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                    return true;
                }
                if (!rewardManager.getRewards().containsKey(reward)) {
                    msg(sender, "<red>Unknown reward: <white>" + reward + "</white></red>");
                    if (!rewardManager.getRewards().isEmpty()) {
                        msg(sender, "<gray>Available rewards: <white>" + String.join("</white>, <white>", rewardManager.getRewards().keySet()) + "</white></gray>");
                    } else {
                        msg(sender, "<gray>No rewards saved yet. Use <yellow>/afkzone reward save [name]</yellow> to create one.</gray>");
                    }
                    return true;
                }
                if (zoneManager.addZoneReward(zone, reward)) {
                    msg(sender, "<green>Added reward <yellow>" + reward + "</yellow> to zone <yellow>" + zone + "</yellow></green>");
                } else {
                    msg(sender, "<yellow>Reward <white>" + reward + "</white> is already assigned to zone <white>" + zone + "</white>.</yellow>");
                }
                return true;
            }
            case "remove" -> {
                if (args.length < 4) {
                    msg(sender, "<red>Usage: /afkzone zonereward remove [zone] [reward]</red>");
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                    return true;
                }
                String zone = args[2];
                String reward = args[3];
                if (!zoneManager.zoneExists(zone)) {
                    msg(sender, "<red>Zone '<white>" + zone + "</white>' does not exist.</red>");
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                    return true;
                }
                if (zoneManager.removeZoneReward(zone, reward)) {
                    msg(sender, "<green>Removed reward <yellow>" + reward + "</yellow> from zone <yellow>" + zone + "</yellow></green>");
                } else {
                    msg(sender, "<yellow>Reward <white>" + reward + "</white> was not assigned to zone <white>" + zone + "</white>.</yellow>");
                    List<String> assigned = zoneManager.getZoneRewards(zone);
                    if (!assigned.isEmpty()) {
                        msg(sender, "<gray>Assigned rewards for <white>" + zone + "</white>: <white>" + String.join("</white>, <white>", assigned) + "</white></gray>");
                    }
                }
                return true;
            }
            case "clear" -> {
                if (args.length < 3) {
                    msg(sender, "<red>Usage: /afkzone zonereward clear [zone]</red>");
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                    return true;
                }
                String zone = args[2];
                if (!zoneManager.zoneExists(zone)) {
                    msg(sender, "<red>Zone '<white>" + zone + "</white>' does not exist.</red>");
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                    return true;
                }
                zoneManager.setZoneRewards(zone, Collections.emptyList());
                msg(sender, "<green>Cleared reward restrictions for <yellow>" + zone + "</yellow>.</green>");
                msg(sender, "<gray>Zone <white>" + zone + "</white> now uses all enabled saved rewards.</gray>");
                return true;
            }
            default -> {
                msg(sender, "<red>Unknown zonereward action: <white>" + act + "</white></red>");
                msg(sender, "<gray>Available actions: list, add, remove, clear</gray>");
                return true;
            }
        }
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return filter(List.of("list", "add", "remove", "clear"), args[1]);
        }
        if (args.length == 3) {
            String act = args[1].toLowerCase(Locale.ROOT);
            if (act.equals("list") || act.equals("add") || act.equals("remove") || act.equals("clear")) {
                return filter(new ArrayList<>(zoneManager.getZoneNames()), args[2]);
            }
        }
        if (args.length == 4) {
            String act = args[1].toLowerCase(Locale.ROOT);
            if (act.equals("add")) {
                String zone = args[2];
                List<String> assigned = zoneManager.getZoneRewards(zone);
                List<String> available = new ArrayList<>(rewardManager.getRewards().keySet());
                available.removeAll(assigned);
                return filter(available, args[3]);
            }
            if (act.equals("remove")) {
                String zone = args[2];
                return filter(zoneManager.getZoneRewards(zone), args[3]);
            }
        }
        return Collections.emptyList();
    }
}