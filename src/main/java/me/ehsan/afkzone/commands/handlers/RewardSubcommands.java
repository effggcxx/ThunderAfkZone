package me.ehsan.afkzone.commands.handlers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
import me.ehsan.afkzone.util.NameValidator;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles the {@code reward} subcommand group
 * ({@code save/list/give/remove/enable/disable/toggle}).
 *
 * <p>Split out from {@link me.ehsan.afkzone.commands.AfkZoneCommand} so the
 * router stays small. Method bodies are unchanged from the original.
 */
public class RewardSubcommands extends AbstractSubcommandHandler {

    public RewardSubcommands(Main plugin, ZoneManager zoneManager,
                             RewardManager rewardManager, StorageService storageService) {
        super(plugin, zoneManager, rewardManager, storageService);
    }

    public boolean handleRewardCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "<red>Usage: /afkzone reward <list|save|give|remove|enable|disable|toggle> …</red>");
            msg(sender, "<gray>Use <yellow>/afkzone reward save [name]</yellow> while holding an item to create a reward.</gray>");
            msg(sender, "<gray>Use <yellow>/afkzone reward list</yellow> to see all saved rewards.</gray>");
            return true;
        }
        String act = args[1].toLowerCase(Locale.ROOT);
        return switch (act) {
            case "list" -> handleRewardList(sender);
            case "save" -> handleRewardSave(sender, args);
            case "give" -> handleRewardGive(sender, args);
            case "remove", "delete" -> handleRewardRemove(sender, args);
            case "enable" -> handleRewardSetEnabled(sender, args, true);
            case "disable" -> handleRewardSetEnabled(sender, args, false);
            case "toggle" -> handleRewardToggle(sender, args);
            default -> {
                msg(sender, "<red>Unknown reward action: <white>" + act + "</white></red>");
                msg(sender, "<gray>Available: list, save, give, remove, enable, disable, toggle</gray>");
                yield true;
            }
        };
    }

    private boolean handleRewardList(CommandSender sender) {
        if (!sender.hasPermission("afkzone.reward.list")) {
            msg(sender, "<red>You don't have permission.</red>");
            return true;
        }
        if (rewardManager.getRewards().isEmpty()) {
            msg(sender, "<gray>No rewards saved yet.</gray>");
            msg(sender, "<gray>Hold an item in your hand and use <yellow>/afkzone reward save [name]</yellow> to create one.</gray>");
            msg(sender, "<gray>Example: hold a diamond, then <yellow>/afkzone reward save welcome_diamond 1 300</yellow></gray>");
            return true;
        }
        msg(sender, "<gold><bold>Saved rewards (" + rewardManager.getRewards().size() + "):</bold></gold>");
        for (Reward r : rewardManager.getRewards().values()) {
            String status = r.isEnabled() ? "<green>enabled</green>" : "<red>disabled</red>";
            String itemInfo = r.getItemStack() != null
                    ? "<white>" + r.getItemStack().getType().name().toLowerCase() + "</white>"
                    : "<red>no item</red>";
            String timing;
            if (r.getIntervalSeconds() > 0 && r.getOnceAfterSeconds() > 0) {
                timing = "every " + MessageUtils.formatDuration(r.getIntervalSeconds())
                        + " + once after " + MessageUtils.formatDuration(r.getOnceAfterSeconds());
            } else if (r.getIntervalSeconds() > 0) {
                timing = "every " + MessageUtils.formatDuration(r.getIntervalSeconds());
            } else if (r.getOnceAfterSeconds() > 0) {
                timing = "once after " + MessageUtils.formatDuration(r.getOnceAfterSeconds());
            } else {
                timing = "manual only";
            }
            msg(sender, " <gray>-</gray> <white>" + r.getName() + "</white> " + itemInfo
                    + " <gray>x" + r.getAmount() + "</gray>"
                    + " <dark_gray>|</dark_gray> " + timing
                    + " <dark_gray>|</dark_gray> " + status
                    + (r.getDescription().isEmpty() ? "" : " <dark_gray>- " + r.getDescription()));
        }
        return true;
    }

    private boolean handleRewardSave(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afkzone.reward.save")) {
            msg(sender, "<red>You don't have permission.</red>");
            return true;
        }
        if (!(sender instanceof Player player)) {
            msg(sender, "<red>Only players can save rewards from held items.</red>");
            return true;
        }
        if (args.length < 3) {
            msg(sender, "<red>Usage: /afkzone reward save [name] [amount] [interval] [once_after] [priority]</red>");
            msg(sender, "<gray>Hold the item you want to save in your hand, then run this command.</gray>");
            msg(sender, "<gray>  [name]       - a name for this reward (e.g. welcome_diamond)</gray>");
            msg(sender, "<gray>  [amount]     - how many items to give each time (default: 1)</gray>");
            msg(sender, "<gray>  [interval]   - give every X seconds, or 0/false to disable (default: 0)</gray>");
            msg(sender, "<gray>  [once_after] - give once after X seconds, or 0/false to disable (default: 0)</gray>");
            msg(sender, "<gray>  [priority]   - higher = preferred when on_multiple=highest (default: 0)</gray>");
            msg(sender, "<gray>Examples:</gray>");
            msg(sender, "<gray>  <yellow>/afkzone reward save welcome_diamond 1 300</yellow> - give 1 diamond every 5 min</gray>");
            msg(sender, "<gray>  <yellow>/afkzone reward save starter_kit 1 false 60</yellow> - give 1 sword once after 60s</gray>");
            msg(sender, "<gray>  <yellow>/afkzone reward save vip_kit 1 300 60 10</yellow> - give once after 60s, then every 5 min</gray>");
            return true;
        }

        String name = args[2];
        if (name == null || name.trim().isEmpty()) {
            msg(sender, "<red>Reward name cannot be empty.</red>");
            msg(sender, "<gray>Choose a name like <white>welcome_diamond</white> or <white>vip_sword</white>.</gray>");
            return true;
        }

        // Validate name has no special characters
        if (!NameValidator.isValidName(name)) {
            msg(sender, "<red>Reward name can only contain letters, numbers, underscores, and hyphens.</red>");
            msg(sender, "<gray>Examples: <white>welcome_diamond</white>, <white>vip-sword</white>, <white>token1</white></gray>");
            return true;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            msg(sender, "<red>You must be holding an item to save as a reward.</red>");
            // Show what's in offhand if main hand is empty
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand != null && offhand.getType() != Material.AIR) {
                msg(sender, "<gray>Tip: You have <white>" + offhand.getType().name().toLowerCase() + "</white> in your off-hand. Use your main hand instead.</gray>");
            }
            return true;
        }

        int amount = args.length >= 4 ? parseInt(args[3], 1) : 1;
        int interval = parseTimeArg(args, 4, 0);
        int onceAfter = parseTimeArg(args, 5, 0);
        int priority = args.length >= 7 ? parseInt(args[6], 0) : 0;

        if (amount < 1) {
            msg(sender, "<red>Amount must be at least 1.</red>");
            return true;
        }
        if (interval < 0) {
            msg(sender, "<red>Interval cannot be negative.</red>");
            return true;
        }
        if (onceAfter < 0) {
            msg(sender, "<red>Once-after cannot be negative.</red>");
            return true;
        }

        // Validate that at least one of interval or once_after is set
        if (interval == 0 && onceAfter == 0) {
            msg(sender, "<red>You must set at least one of [interval] or [once_after].</red>");
            msg(sender, "<gray>Use <white>0</white> or <white>false</white> to disable one, but not both.</gray>");
            msg(sender, "<gray>Example: <yellow>/afkzone reward save welcome_diamond 1 300</yellow> (interval every 5 min)</gray>");
            msg(sender, "<gray>Example: <yellow>/afkzone reward save starter_kit 1 false 60</yellow> (once after 60s)</gray>");
            return true;
        }

        // Build description from item type
        String itemTypeName = heldItem.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String description = itemTypeName;

        // Show item details in the message
        String itemDetail = itemTypeName;
        if (heldItem.getItemMeta() != null && heldItem.getItemMeta().hasDisplayName()) {
            itemDetail = heldItem.getItemMeta().getDisplayName() + " <dark_gray>(" + itemTypeName + ")</dark_gray>";
        }

        boolean overwrite = rewardManager.getRewards().containsKey(name);
        if (overwrite) {
            msg(sender, "<yellow>Reward <white>" + name + "</white> already exists. It will be overwritten.</yellow>");
        }
        rewardManager.saveRewardFromItem(name, heldItem, description, amount, interval, onceAfter, priority, true);

        if (overwrite) {
            msg(sender, "<green>Updated reward <yellow>" + name + "</yellow> with your held " + itemDetail + ".</green>");
        } else {
            msg(sender, "<green>Saved reward <yellow>" + name + "</yellow> from your held " + itemDetail + ".</green>");
        }

        // Build timing description
        StringBuilder timing = new StringBuilder();
        if (interval > 0) {
            timing.append("every ").append(MessageUtils.formatDuration(interval));
        }
        if (onceAfter > 0) {
            if (timing.length() > 0) timing.append(" + ");
            timing.append("once after ").append(MessageUtils.formatDuration(onceAfter));
        }
        if (timing.length() == 0) timing.append("manual only");

        msg(sender, "<gray>Amount: <white>" + amount + "</white> <dark_gray>|</dark_gray> Timing: <white>" + timing.toString() + "</white> <dark_gray>|</dark_gray> Priority: <white>" + priority + "</white></gray>");
        return true;
    }

    private boolean handleRewardGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afkzone.reward.give")) {
            msg(sender, "<red>You don't have permission.</red>");
            return true;
        }
        if (args.length < 3) {
            msg(sender, "<red>Usage: /afkzone reward give [reward] [player]</red>");
            if (!rewardManager.getRewards().isEmpty()) {
                msg(sender, "<gray>Available rewards: <white>" + String.join("</white>, <white>", rewardManager.getRewards().keySet()) + "</white></gray>");
            }
            return true;
        }
        String rewardName = args[2];
        Reward r = rewardManager.getRewards().get(rewardName);
        if (r == null) {
            msg(sender, "<red>Unknown reward: <white>" + rewardName + "</white></red>");
            if (!rewardManager.getRewards().isEmpty()) {
                msg(sender, "<gray>Available rewards: <white>" + String.join("</white>, <white>", rewardManager.getRewards().keySet()) + "</white></gray>");
            } else {
                msg(sender, "<gray>No rewards have been saved yet. Use <yellow>/afkzone reward save [name]</yellow> to create one.</gray>");
            }
            return true;
        }
        Player target = null;
        if (args.length >= 4) {
            target = plugin.getServer().getPlayerExact(args[3]);
            if (target == null) {
                msg(sender, "<red>Player not found: <white>" + args[3] + "</white></red>");
                msg(sender, "<gray>Make sure the player is online and check the spelling.</gray>");
                return true;
            }
        } else if (sender instanceof Player p) {
            target = p;
        }
        if (target == null) {
            msg(sender, "<red>No target player specified.</red>");
            msg(sender, "<gray>Usage: /afkzone reward give [reward] [player]</gray>");
            msg(sender, "<gray>If run from console, you must specify a player name.</gray>");
            return true;
        }
        rewardManager.getRewardDispatcher().giveRewardToPlayer(r, target);
        msg(sender, "<green>Gave reward <yellow>" + rewardName + "</yellow> to <yellow>" + target.getName() + "</yellow></green>");
        return true;
    }

    private boolean handleRewardRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afkzone.reward.remove")) {
            msg(sender, "<red>You don't have permission.</red>");
            return true;
        }
        if (args.length < 3) {
            msg(sender, "<red>Usage: /afkzone reward remove [name]</red>");
            if (!rewardManager.getRewards().isEmpty()) {
                msg(sender, "<gray>Available rewards: <white>"
                        + String.join("</white>, <white>", rewardManager.getRewards().keySet())
                        + "</white></gray>");
            }
            return true;
        }
        String name = args[2];
        boolean known = rewardManager.getRewards().containsKey(name)
                || rewardManager.getRewards().keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name));
        if (!known) {
            msg(sender, "<red>Unknown reward: <white>" + name + "</white></red>");
            if (!rewardManager.getRewards().isEmpty()) {
                msg(sender, "<gray>Available rewards: <white>"
                        + String.join("</white>, <white>", rewardManager.getRewards().keySet())
                        + "</white></gray>");
            }
            return true;
        }
        boolean ok = rewardManager.deleteReward(name);
        if (ok) {
            msg(sender, "<green>Removed reward <yellow>" + name + "</yellow>.</green>");
        } else {
            msg(sender, "<red>Failed to delete reward file for <white>" + name + "</white>.</red>");
        }
        return true;
    }

    private boolean handleRewardSetEnabled(CommandSender sender, String[] args, boolean enabled) {
        if (!sender.hasPermission("afkzone.reward.toggle")) {
            msg(sender, "<red>You don't have permission.</red>");
            return true;
        }
        String verb = enabled ? "enable" : "disable";
        if (args.length < 3) {
            msg(sender, "<red>Usage: /afkzone reward " + verb + " [name]</red>");
            if (!rewardManager.getRewards().isEmpty()) {
                msg(sender, "<gray>Available rewards: <white>"
                        + String.join("</white>, <white>", rewardManager.getRewards().keySet())
                        + "</white></gray>");
            }
            return true;
        }
        String name = args[2];
        Reward r = rewardManager.setRewardEnabled(name, enabled);
        if (r == null) {
            msg(sender, "<red>Unknown reward: <white>" + name + "</white></red>");
            if (!rewardManager.getRewards().isEmpty()) {
                msg(sender, "<gray>Available rewards: <white>"
                        + String.join("</white>, <white>", rewardManager.getRewards().keySet())
                        + "</white></gray>");
            }
            return true;
        }
        if (enabled) {
            msg(sender, "<green>Enabled reward <yellow>" + r.getName() + "</yellow>.</green>");
        } else {
            msg(sender, "<yellow>Disabled reward <white>" + r.getName()
                    + "</white>. It will not fire until re-enabled.</yellow>");
        }
        return true;
    }

    private boolean handleRewardToggle(CommandSender sender, String[] args) {
        if (!sender.hasPermission("afkzone.reward.toggle")) {
            msg(sender, "<red>You don't have permission.</red>");
            return true;
        }
        if (args.length < 3) {
            msg(sender, "<red>Usage: /afkzone reward toggle [name]</red>");
            if (!rewardManager.getRewards().isEmpty()) {
                msg(sender, "<gray>Available rewards: <white>"
                        + String.join("</white>, <white>", rewardManager.getRewards().keySet())
                        + "</white></gray>");
            }
            return true;
        }
        String name = args[2];
        Reward existing = rewardManager.getRewards().get(name);
        if (existing == null) {
            for (Map.Entry<String, Reward> e : rewardManager.getRewards().entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    existing = e.getValue();
                    break;
                }
            }
        }
        if (existing == null) {
            msg(sender, "<red>Unknown reward: <white>" + name + "</white></red>");
            if (!rewardManager.getRewards().isEmpty()) {
                msg(sender, "<gray>Available rewards: <white>"
                        + String.join("</white>, <white>", rewardManager.getRewards().keySet())
                        + "</white></gray>");
            }
            return true;
        }
        boolean newState = !existing.isEnabled();
        rewardManager.setRewardEnabled(existing.getName(), newState);
        if (newState) {
            msg(sender, "<green>Enabled reward <yellow>" + existing.getName() + "</yellow>.</green>");
        } else {
            msg(sender, "<yellow>Disabled reward <white>" + existing.getName() + "</white>.</yellow>");
        }
        return true;
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            return filter(Arrays.asList("list", "save", "give", "remove", "enable", "disable", "toggle"), args[1]);
        }
        if (args.length == 3) {
            String act = args[1].toLowerCase(Locale.ROOT);
            if (act.equals("save")) {
                List<String> suggestions = new ArrayList<>(rewardManager.getRewards().keySet());
                suggestions.add("<name>");
                return filter(suggestions, args[2]);
            }
            if (act.equals("give") || act.equals("remove") || act.equals("delete")
                    || act.equals("enable") || act.equals("disable") || act.equals("toggle")) {
                return filter(new ArrayList<>(rewardManager.getRewards().keySet()), args[2]);
            }
        }
        if (args.length == 4) {
            if (args[1].equalsIgnoreCase("save")) {
                return filter(Arrays.asList("1", "2", "4", "8", "16", "32", "64"), args[3]);
            }
            if (args[1].equalsIgnoreCase("give")) {
                return null;
            }
        }
        if (args.length == 5) {
            if (args[1].equalsIgnoreCase("save")) {
                return filter(Arrays.asList("0", "false", "30", "60", "120", "300", "600", "1800", "3600"), args[4]);
            }
        }
        if (args.length == 6) {
            if (args[1].equalsIgnoreCase("save")) {
                return filter(Arrays.asList("0", "false", "30", "60", "120", "300", "600"), args[5]);
            }
        }
        if (args.length == 7) {
            if (args[1].equalsIgnoreCase("save")) {
                return filter(Arrays.asList("0", "1", "5", "10"), args[6]);
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

    /**
     * Parses a time argument (interval or once_after) that may be "false" or a number.
     * "false" and "0" both return 0 (disabled). Non-numeric values return the default.
     */
    private int parseTimeArg(String[] args, int index, int def) {
        if (args.length <= index) return def;
        String val = args[index].toLowerCase(Locale.ROOT);
        if ("false".equals(val)) return 0;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return def;
        }
    }
}