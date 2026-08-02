package me.ehsan.afkzone.commands;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.listeners.WandListener;
import me.ehsan.afkzone.managers.BorderManager;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.models.WandSelection;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
import me.ehsan.afkzone.util.NameValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.stream.Collectors;

public class AfkZoneCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final ZoneManager zoneManager;
    private final RewardManager rewardManager;
    private final StorageService storageService;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static final List<String> ROOT_SUBS = Arrays.asList(
            "wand", "sel", "create", "list", "info", "remove", "reload", "reward", "zonereward", "stats", "top", "border"
    );

    // Common number suggestions for tab completion
    private static final List<String> AMOUNT_SUGGESTIONS = Arrays.asList("1", "2", "4", "8", "16", "32", "64");
    private static final List<String> INTERVAL_SUGGESTIONS = Arrays.asList("0", "false", "30", "60", "120", "300", "600", "1800", "3600");
    private static final List<String> ONCE_AFTER_SUGGESTIONS = Arrays.asList("0", "false", "30", "60", "120", "300", "600");
    private static final List<String> PRIORITY_SUGGESTIONS = Arrays.asList("0", "1", "5", "10");

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
                plugin.reloadAll();
                msg(sender, "<green>ThunderAfkZone configuration reloaded (config + zones).</green>");
                return true;
            }
            case "reward" -> {
                return handleRewardCommand(sender, args);
            }
            case "zonereward" -> {
                return handleZoneRewardCommand(sender, args);
            }
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>Only players can use this command.</red>");
                    return true;
                }
                if (!sender.hasPermission("afkzone.wand")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                giveWand(player);
                return true;
            }
            case "sel", "wandsel" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>Only players can use this command.</red>");
                    return true;
                }
                if (!sender.hasPermission("afkzone.wand")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                showWandSelection(player);
                return true;
            }
            case "cancel", "wandcancel" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>Only players can use this command.</red>");
                    return true;
                }
                if (!sender.hasPermission("afkzone.wand")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                cancelWandSelection(player);
                return true;
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
                    msg(sender, "<gray>First select a region with the wand tool (<yellow>/afkzone wand</yellow>).</gray>");
                    return true;
                }
                createZoneFromWandSelection(player, args[1]);
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
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
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
                    msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
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
            case "border" -> {
                if (!(sender instanceof Player player)) {
                    msg(sender, "<red>Only players can use this command.</red>");
                    return true;
                }
                if (!sender.hasPermission("afkzone.border")) {
                    msg(sender, "<red>You don't have permission.</red>");
                    return true;
                }
                BorderManager bm = plugin.getBorderManager();
                if (bm == null) {
                    msg(sender, "<red>Border system is not available.</red>");
                    return true;
                }
                if (args.length >= 2) {
                    String mode = args[1].toLowerCase(Locale.ROOT);
                    if (mode.equals("on") || mode.equals("true") || mode.equals("enable")) {
                        bm.setEnabled(player, true);
                        msg(player, "<green>AFK zone border particles enabled.</green>");
                        msg(player, "<gray>Particles will appear around nearby zones. Run <yellow>/afkzone border off</yellow> to disable.</gray>");
                        return true;
                    } else if (mode.equals("off") || mode.equals("false") || mode.equals("disable")) {
                        bm.setEnabled(player, false);
                        msg(player, "<yellow>AFK zone border particles disabled.</yellow>");
                        return true;
                    }
                }
                // Toggle
                boolean enabled = bm.toggle(player);
                if (enabled) {
                    msg(player, "<green>AFK zone border particles enabled.</green>");
                    msg(player, "<gray>Particles will appear around nearby zones. Run <yellow>/afkzone border</yellow> again to disable.</gray>");
                } else {
                    msg(player, "<yellow>AFK zone border particles disabled.</yellow>");
                }
                return true;
            }
            default -> {
                msg(sender, "<red>Unknown command: <white>" + sub + "</white></red>");
                msg(sender, "<gray>Use <yellow>/afkzone</yellow> to see all available commands.</gray>");
                return true;
            }
        }
    }

    private void sendUsage(CommandSender sender) {
        msg(sender, "<gold><bold>=== ThunderAfkZone Commands ===</bold></gold>");
        msg(sender, "");
        msg(sender, "<yellow><bold>Zone Management:</bold></yellow>");
        msg(sender, "<yellow>/afkzone wand</yellow> <gray>- Get the selection wand tool</gray>");
        msg(sender, "<yellow>/afkzone sel</yellow> <gray>- View your current wand selection</gray>");
        msg(sender, "<yellow>/afkzone cancel</yellow> <gray>- Clear your wand selection</gray>");
        msg(sender, "<yellow>/afkzone create [name]</yellow> <gray>- Create a zone from your wand selection</gray>");
        msg(sender, "<yellow>/afkzone list</yellow> <gray>- List all zones</gray>");
        msg(sender, "<yellow>/afkzone info [name]</yellow> <gray>- Show zone details and assigned rewards</gray>");
        msg(sender, "<yellow>/afkzone remove [name]</yellow> <gray>- Delete a zone</gray>");
        msg(sender, "<yellow>/afkzone reload</yellow> <gray>- Reload config + zones from disk</gray>");
        msg(sender, "<yellow>/afkzone border [on|off]</yellow> <gray>- Toggle zone border particles (only visible to you)</gray>");
        msg(sender, "");
        msg(sender, "<yellow><bold>Rewards:</bold></yellow>");
        msg(sender, "<yellow>/afkzone reward save [name] [amount] [interval] [once_after] [priority]</yellow> <gray>- Save held item as a reward</gray>");
        msg(sender, "<yellow>/afkzone reward list</yellow> <gray>- List all saved rewards</gray>");
        msg(sender, "<yellow>/afkzone reward give [name] [player]</yellow> <gray>- Manually give a reward to a player</gray>");
        msg(sender, "<yellow>/afkzone reward remove [name]</yellow> <gray>- Delete a saved reward</gray>");
        msg(sender, "<yellow>/afkzone reward enable|disable|toggle [name]</yellow> <gray>- Toggle a reward without deleting it</gray>");
        msg(sender, "<yellow>/afkzone zonereward list [zone]</yellow> <gray>- Show rewards assigned to a zone</gray>");
        msg(sender, "<yellow>/afkzone zonereward add [zone] [reward]</yellow> <gray>- Assign a reward to a zone</gray>");
        msg(sender, "<yellow>/afkzone zonereward remove [zone] [reward]</yellow> <gray>- Remove a reward from a zone</gray>");
        msg(sender, "<yellow>/afkzone zonereward clear [zone]</yellow> <gray>- Clear all reward restrictions for a zone</gray>");
        msg(sender, "");
        msg(sender, "<yellow><bold>Statistics:</bold></yellow>");
        msg(sender, "<yellow>/afkzone stats [player]</yellow> <gray>- View AFK statistics for yourself or another player</gray>");
        msg(sender, "<yellow>/afkzone top [time|rewards] [limit]</yellow> <gray>- View top players by AFK time or rewards received</gray>");
    }

    // --- Stats ---

    private void handleStats(CommandSender sender, String[] args) {
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

    private void handleTop(CommandSender sender, String[] args) {
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

    // --- Reward command ---

    private boolean handleRewardCommand(CommandSender sender, String[] args) {
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

    // --- Zone listing ---

    private void listZones(CommandSender sender) {
        FileConfiguration zonesConfig = zoneManager.getZonesConfig();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) {
            msg(sender, "<gray>No zones configured.</gray>");
            msg(sender, "<gray>Use <yellow>/afkzone wand</yellow> to get the selection tool, then <yellow>/afkzone create [name]</yellow> to create a zone.</gray>");
            return;
        }
        Set<String> keys = zonesConfig.getConfigurationSection("zones").getKeys(false);
        if (keys.isEmpty()) {
            msg(sender, "<gray>No zones configured.</gray>");
            msg(sender, "<gray>Use <yellow>/afkzone wand</yellow> to get the selection tool, then <yellow>/afkzone create [name]</yellow> to create a zone.</gray>");
            return;
        }
        msg(sender, "<gold><bold>AFK Zones (" + keys.size() + "):</bold></gold>");
        for (String key : keys) {
            String path = "zones." + key;
            String world = zonesConfig.getString(path + ".world", "unknown");
            int x1 = zonesConfig.getInt(path + ".x1", 0);
            int y1 = zonesConfig.getInt(path + ".y1", 0);
            int z1 = zonesConfig.getInt(path + ".z1", 0);
            int x2 = zonesConfig.getInt(path + ".x2", 0);
            int y2 = zonesConfig.getInt(path + ".y2", 0);
            int z2 = zonesConfig.getInt(path + ".z2", 0);
            List<String> rewards = zoneManager.getZoneRewards(key);
            String rewardInfo = rewards.isEmpty() ? "<green>all rewards</green>" : "<white>" + rewards.size() + " reward(s)</white>";
            msg(sender, " <gray>-</gray> <white>" + key + "</white> <dark_gray>in</dark_gray> " + world
                    + " <gray>(" + x1 + "," + y1 + "," + z1 + ")->(" + x2 + "," + y2 + "," + z2 + ")</gray>"
                    + " <dark_gray>|</dark_gray> " + rewardInfo);
        }
    }

    private void showZoneInfo(CommandSender sender, String name) {
        FileConfiguration zonesConfig = zoneManager.getZonesConfig();
        if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones") || !zonesConfig.isSet("zones." + name)) {
            msg(sender, "<red>Zone '<white>" + name + "</white>' does not exist.</red>");
            msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
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

        msg(sender, "<gold><bold>Zone: " + name + "</bold></gold>");
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
            msg(sender, "  <gray>Rewards: <green>all enabled saved rewards</green></gray>");
            for (Reward r : rewardManager.getRewards().values()) {
                if (!r.isEnabled()) continue;
                String itemInfo = r.getItemStack() != null ? r.getItemStack().getType().name().toLowerCase() : "no item";
                msg(sender, "   <dark_gray>-</dark_gray> <white>" + r.getName() + "</white> <gray>(" + itemInfo + ")</gray>");
            }
        } else {
            msg(sender, "  <gray>Assigned rewards (<white>" + assigned.size() + "</white>):</gray>");
            for (String rn : assigned) {
                Reward r = rewardManager.getRewards().get(rn);
                if (r == null) {
                    msg(sender, "   <dark_gray>-</dark_gray> <red>" + rn + "</red> <gray>(missing from rewards folder!)</gray>");
                } else {
                    String status = r.isEnabled() ? "<green>enabled</green>" : "<red>disabled</red>";
                    String itemInfo = r.getItemStack() != null ? r.getItemStack().getType().name().toLowerCase() : "no item";
                    msg(sender, "   <dark_gray>-</dark_gray> <white>" + rn + "</white> <gray>(" + itemInfo + ", " + status + ")</gray>");
                }
            }
        }
    }

    private void removeZone(CommandSender sender, String name) {
        if (!zoneManager.zoneExists(name)) {
            msg(sender, "<red>Zone '<white>" + name + "</white>' does not exist.</red>");
            msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
            return;
        }
        zoneManager.removeZone(name);
        msg(sender, "<green>Zone '<yellow>" + name + "</yellow>' has been removed.</green>");
    }

    // -------------------------------------------------------------------------
    // Wand selection commands
    // -------------------------------------------------------------------------

    private WandListener getWandListener() {
        return plugin.getWandListener();
    }

    private void giveWand(Player player) {
        Material wandMat = Material.WOODEN_HOE;
        try {
            String matName = plugin.getConfig().getString("wand.item", "WOODEN_HOE");
            wandMat = Material.valueOf(matName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {}

        ItemStack wand = new ItemStack(wandMat);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(MM.deserialize("<gold><bold>Selection Wand</bold></gold>")));
            meta.lore(List.of(
                    noItalic(MM.deserialize("<gray>Used to select AFK zone corners.</gray>")),
                    noItalic(MM.deserialize("<yellow>Left-click</yellow> <dark_gray>-</dark_gray> <gray>set position 1</gray>")),
                    noItalic(MM.deserialize("<yellow>Right-click</yellow> <dark_gray>-</dark_gray> <gray>set position 2</gray>")),
                    noItalic(MM.deserialize("<dark_gray>/afkzone create [name] to save</dark_gray>"))
            ));
            wand.setItemMeta(meta);
        }
        player.getInventory().addItem(wand);
        msg(player, "<green>You received the Selection Wand!</green>");
        msg(player, "<gray>Left-click a block to set position 1, right-click to set position 2.</gray>");
        msg(player, "<gray>Use <yellow>/afkzone create [name]</yellow> to create the zone from your selection.</gray>");
        msg(player, "<gray>Use <yellow>/afkzone sel</yellow> to view your current selection.</gray>");
    }

    /**
     * Item names/lore render italic by default when given a styled Component -
     * this turns that off so gold/gray text doesn't show up slanted, matching
     * how vanilla non-enchanted item names normally look.
     */
    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private void showWandSelection(Player player) {
        WandListener listener = plugin.getWandListener();
        if (listener == null) {
            msg(player, "<red>Wand system is not available.</red>");
            return;
        }
        WandSelection sel = listener.getSelection(player);
        if (sel.getPos1() == null && sel.getPos2() == null) {
            msg(player, "<yellow>You have no active wand selection.</yellow>");
            msg(player, "<gray>Use <yellow>/afkzone wand</yellow> to get the selection tool.</gray>");
            msg(player, "<gray>Left-click a block = position 1, Right-click = position 2.</gray>");
            return;
        }
        msg(player, "<yellow>Current wand selection:</yellow>");
        if (sel.getPos1() != null) {
            msg(player, "  <green>Position 1:</green> <white>" + formatLoc(sel.getPos1()) + "</white>");
        } else {
            msg(player, "  <red>Position 1: not set</red>");
        }
        if (sel.getPos2() != null) {
            msg(player, "  <green>Position 2:</green> <white>" + formatLoc(sel.getPos2()) + "</white>");
        } else {
            msg(player, "  <red>Position 2: not set</red>");
        }
        if (sel.isComplete()) {
            msg(player, "  <gray>Size: <white>" + sel.getDimensions() + "</white></gray>");
            msg(player, "<gray>Use <yellow>/afkzone create [name]</yellow> to save this selection as a zone.</gray>");
        } else {
            msg(player, "<gray>Selection incomplete. Use the wand to set both positions.</gray>");
        }
    }

    private void cancelWandSelection(Player player) {
        WandListener listener = plugin.getWandListener();
        if (listener == null) {
            msg(player, "<red>Wand system is not available.</red>");
            return;
        }
        listener.clearSelection(player);
        msg(player, "<yellow>Wand selection cleared.</yellow>");
    }

    private void createZoneFromWandSelection(Player player, String name) {
        WandListener listener = plugin.getWandListener();
        if (listener == null) {
            msg(player, "<red>Wand system is not available.</red>");
            return;
        }
        WandSelection sel = listener.getSelection(player);
        if (!sel.isComplete()) {
            msg(player, "<red>Incomplete selection! Use the wand to select both corners first.</red>");
            msg(player, "<gray>Use <yellow>/afkzone wand</yellow> to get the selection tool.</gray>");
            msg(player, "<gray>Left-click = pos1, Right-click = pos2.</gray>");
            return;
        }

        Location min = sel.getMin();
        Location max = sel.getMax();
        if (min == null || max == null) {
            msg(player, "<red>Invalid selection bounds.</red>");
            return;
        }

        if (name == null || name.trim().isEmpty()) {
            msg(player, "<red>Zone name cannot be empty.</red>");
            return;
        }

        // Validate zone name
        if (!NameValidator.isValidName(name)) {
            msg(player, "<red>Zone name can only contain letters, numbers, underscores, and hyphens.</red>");
            return;
        }

        // Check if zone name already exists
        if (zoneManager.zoneExists(name)) {
            msg(player, "<red>A zone named '<white>" + name + "</white>' already exists.</red>");
            msg(player, "<gray>Choose a different name or remove the existing zone first.</gray>");
            return;
        }

        String worldName = min.getWorld().getName();
        int x1 = min.getBlockX();
        int y1 = min.getBlockY();
        int z1 = min.getBlockZ();
        int x2 = max.getBlockX();
        int y2 = max.getBlockY();
        int z2 = max.getBlockZ();

        String overlapping = zoneManager.findOverlappingZone(worldName, x1, y1, z1, x2, y2, z2);
        if (overlapping != null) {
            msg(player, "<red>This selection overlaps the existing zone '<yellow>" + overlapping
                    + "</yellow>'.</red>");
            msg(player, "<gray>Choose a non-overlapping area, or remove/resize that zone first.</gray>");
            return;
        }

        var zonesConfig = zoneManager.getZonesConfig();
        String path = "zones." + name;
        zonesConfig.set(path + ".world", worldName);
        zonesConfig.set(path + ".x1", x1);
        zonesConfig.set(path + ".y1", y1);
        zonesConfig.set(path + ".z1", z1);
        zonesConfig.set(path + ".x2", x2);
        zonesConfig.set(path + ".y2", y2);
        zonesConfig.set(path + ".z2", z2);
        zoneManager.saveZonesFile();

        // Add to spatial index
        zoneManager.getSpatialIndex().addZone(name, worldName, x1, y1, z1, x2, y2, z2);

        msg(player, "<green>AFK zone '<yellow>" + name + "</yellow>' created!</green>");
        msg(player, "<gray>  World: <white>" + worldName + "</white></gray>");
        msg(player, "<gray>  Area: <white>(" + x1 + "," + y1 + "," + z1 + ") -> (" + x2 + "," + y2 + "," + z2 + ")</white></gray>");
        msg(player, "<gray>  Size: <white>" + (Math.abs(x2 - x1) + 1) + " x " + (Math.abs(y2 - y1) + 1) + " x " + (Math.abs(z2 - z1) + 1) + "</white></gray>");
        msg(player, "<gray>Tip: Use <yellow>/afkzone zonereward add " + name + " [reward]</yellow> to assign rewards to this zone.</gray>");

        // Clear the selection after successful creation
        listener.clearSelection(player);
    }

    private String formatLoc(Location loc) {
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
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
                    return filter(Arrays.asList(
                            "list", "save", "give", "remove", "enable", "disable", "toggle"
                    ), args[1]);
                }
                case "zonereward" -> {
                    return filter(Arrays.asList("list", "add", "remove", "clear"), args[1]);
                }
                case "top" -> {
                    return filter(Arrays.asList("time", "rewards"), args[1]);
                }
                case "stats" -> {
                    // Suggest online player names for stats
                    return null; // null = Bukkit suggests online players
                }
                case "border" -> {
                    return filter(Arrays.asList("on", "off"), args[1]);
                }
                default -> {
                    return Collections.emptyList();
                }
            }
        }

        if (args.length == 3) {
            if (sub.equals("reward")) {
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
            if (sub.equals("zonereward")) {
                String act = args[1].toLowerCase(Locale.ROOT);
                if (act.equals("list") || act.equals("add") || act.equals("remove") || act.equals("clear")) {
                    return filter(new ArrayList<>(zoneManager.getZoneNames()), args[2]);
                }
            }
            // Suggest zone names for create
            if (sub.equals("create")) {
                return filter(new ArrayList<>(zoneManager.getZoneNames()), args[2]);
            }
        }

        if (args.length == 4) {
            if (sub.equals("reward") && args[1].equalsIgnoreCase("save")) {
                // Suggest amounts
                return filter(AMOUNT_SUGGESTIONS, args[3]);
            }
            if (sub.equals("reward") && args[1].equalsIgnoreCase("give")) {
                return null; // Bukkit suggests online players
            }
            if (sub.equals("zonereward")) {
                String act = args[1].toLowerCase(Locale.ROOT);
                if (act.equals("add")) {
                    // Only suggest rewards NOT already assigned to this zone
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
        }

        if (args.length == 5) {
            if (sub.equals("reward") && args[1].equalsIgnoreCase("save")) {
                // Suggest intervals
                return filter(INTERVAL_SUGGESTIONS, args[4]);
            }
        }

        if (args.length == 6) {
            if (sub.equals("reward") && args[1].equalsIgnoreCase("save")) {
                // Suggest once_after values
                return filter(ONCE_AFTER_SUGGESTIONS, args[5]);
            }
        }

        if (args.length == 7) {
            if (sub.equals("reward") && args[1].equalsIgnoreCase("save")) {
                // Suggest priority values
                return filter(PRIORITY_SUGGESTIONS, args[6]);
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