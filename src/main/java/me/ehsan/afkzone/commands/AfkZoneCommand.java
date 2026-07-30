package me.ehsan.afkzone.commands;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.listeners.WandListener;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.models.WandSelection;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
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
            "wand", "sel", "create", "list", "info", "remove", "reload", "reward", "zonereward", "stats", "top"
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
        msg(sender, "<yellow>/afkzone wand <gray>- get the selection tool (wooden hoe)</gray></yellow>");
        msg(sender, "<yellow>/afkzone sel <gray>- view current wand selection</gray></yellow>");
        msg(sender, "<yellow>/afkzone cancel <gray>- clear current wand selection</gray></yellow>");
        msg(sender, "<yellow>/afkzone create [name] <gray>- create zone from wand selection</gray></yellow>");
        msg(sender, "<yellow>/afkzone list <gray>- list zones</gray></yellow>");
        msg(sender, "<yellow>/afkzone info [name] <gray>- zone details + rewards</gray></yellow>");
        msg(sender, "<yellow>/afkzone remove [name] <gray>- remove a zone</gray></yellow>");
        msg(sender, "<yellow>/afkzone reload <gray>- reload config</gray></yellow>");
        msg(sender, "<yellow>/afkzone reward list|save|give [name] [player]</yellow>");
        msg(sender, "<yellow>  save [name] [amount] [interval] <gray>- save held item as a reward</gray></yellow>");
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
        if (rewardManager.getPlayerTracker().isPlayerInAnyZone(targetId)) {
            int sessionSeconds = rewardManager.getPlayerTracker().getSessionSeconds(targetId);
            String zone = rewardManager.getPlayerTracker().getPlayerZone(targetId);
            msg(sender, "  <gray>Current session: <white>" + MessageUtils.formatDuration(sessionSeconds)
                    + "</white> <dark_gray>(in " + zone + ")</dark_gray></gray>");
        }
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
            msg(sender, "<red>Usage: /afkzone reward list | save [name] [amount] [interval] | give [reward] [player]</red>");
            return true;
        }
        String act = args[1].toLowerCase(Locale.ROOT);
        if (act.equals("list")) {
            return handleRewardList(sender);
        } else if (act.equals("save")) {
            return handleRewardSave(sender, args);
        } else if (act.equals("give")) {
            return handleRewardGive(sender, args);
        }
        msg(sender, "<red>Usage: /afkzone reward list | save [name] [amount] [interval] | give [reward] [player]</red>");
        return true;
    }

    private boolean handleRewardList(CommandSender sender) {
        if (!sender.hasPermission("afkzone.reward.list")) {
            msg(sender, "<red>You don't have permission.</red>");
            return true;
        }
        if (rewardManager.getRewards().isEmpty()) {
            msg(sender, "<gray>No rewards saved. Use <yellow>/afkzone reward save [name]</yellow> while holding an item to create one.</gray>");
            return true;
        }
        msg(sender, "<yellow>Saved rewards:</yellow>");
        for (Reward r : rewardManager.getRewards().values()) {
            String status = r.isEnabled() ? "<green>enabled</green>" : "<red>disabled</red>";
            String itemInfo = r.getItemStack() != null ? "<gray>" + r.getItemStack().getType().name().toLowerCase() + "</gray>" : "<red>no item</red>";
            String interval = r.getIntervalSeconds() > 0 ? "every " + MessageUtils.formatDuration(r.getIntervalSeconds()) : "once";
            msg(sender, " <gray>- <white>" + r.getName() + "</white> " + itemInfo
                    + " <gray>x" + r.getAmount() + "</gray>"
                    + " <dark_gray>|</dark_gray> " + interval
                    + " <dark_gray>|</dark_gray> " + status
                    + " <dark_gray>|</dark_gray> priority=" + r.getPriority()
                    + " <dark_gray>- " + (r.getDescription().isEmpty() ? "" : r.getDescription()));
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
            msg(sender, "<red>Usage: /afkzone reward save [name] [amount] [interval]</red>");
            msg(sender, "<gray>Hold the item you want to save as the reward in your hand.</gray>");
            msg(sender, "<gray>  amount    - how many items to give (default: 1)</gray>");
            msg(sender, "<gray>  interval  - give every X seconds (0 = once only, default: 0)</gray>");
            return true;
        }

        String name = args[2];
        if (name == null || name.trim().isEmpty()) {
            msg(sender, "<red>Reward name cannot be empty.</red>");
            return true;
        }

        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (heldItem == null || heldItem.getType() == Material.AIR) {
            msg(sender, "<red>You must be holding an item to save as a reward.</red>");
            return true;
        }

        int amount = args.length >= 4 ? parseInt(args[3], 1) : 1;
        int interval = args.length >= 5 ? parseInt(args[4], 0) : 0;

        if (amount < 1) amount = 1;
        if (interval < 0) interval = 0;

        // Build description from item type
        String description = heldItem.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');

        boolean overwrite = rewardManager.getRewards().containsKey(name);
        rewardManager.saveRewardFromItem(name, heldItem, description, amount, interval, 0, 0, true);

        if (overwrite) {
            msg(sender, "<green>Updated reward <yellow>" + name + "</yellow> with your held item (" + description + ").</green>");
        } else {
            msg(sender, "<green>Saved reward <yellow>" + name + "</yellow> from your held item (" + description + ").</green>");
        }
        msg(sender, "<gray>Amount: <white>" + amount + "</white> | Interval: <white>" + (interval > 0 ? "every " + MessageUtils.formatDuration(interval) : "once") + "</white></gray>");
        return true;
    }

    private boolean handleRewardGive(CommandSender sender, String[] args) {
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
                    msg(sender, "<yellow>Zone <white>" + zone + "</white> uses <green>all enabled saved rewards</green> (no restriction).</yellow>");
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
                msg(sender, "<green>Cleared reward restrictions for <yellow>" + zone + "</yellow>. It now uses all saved rewards.</green>");
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
            String rewardInfo = rewards.isEmpty() ? "<gray>all saved</gray>" : "<white>" + rewards.size() + " reward(s)</white>";
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
            msg(sender, "  <gray>Rewards: <green>all enabled saved rewards</green></gray>");
            for (Reward r : rewardManager.getRewards().values()) {
                if (!r.isEnabled()) continue;
                String itemInfo = r.getItemStack() != null ? r.getItemStack().getType().name().toLowerCase() : "no item";
                msg(sender, "   <dark_gray>- <white>" + r.getName() + "</white> <gray>(" + itemInfo + ", priority=" + r.getPriority() + ")</gray>");
            }
        } else {
            msg(sender, "  <gray>Rewards (<white>" + assigned.size() + "</white> assigned):</gray>");
            for (String rn : assigned) {
                Reward r = rewardManager.getRewards().get(rn);
                if (r == null) {
                    msg(sender, "   <dark_gray>- <red>" + rn + "</red> <gray>(missing from rewards folder!)</gray>");
                } else {
                    String status = r.isEnabled() ? "<green>enabled</green>" : "<red>disabled</red>";
                    String itemInfo = r.getItemStack() != null ? r.getItemStack().getType().name().toLowerCase() : "no item";
                    msg(sender, "   <dark_gray>- <white>" + rn + "</white> <gray>(" + itemInfo + ", " + status + "<gray>, priority=" + r.getPriority() + ")");
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

        // Check if zone name already exists
        if (zoneManager.zoneExists(name)) {
            msg(player, "<red>A zone named '" + name + "' already exists.</red>");
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
                    + "</yellow>'. Choose a non-overlapping area, or remove/resize that zone first.</red>");
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

        msg(player, "<green>AFK zone '" + name + "' created: " + worldName
                + " (" + x1 + "," + y1 + "," + z1 + ") -> (" + x2 + "," + y2 + "," + z2 + ")</green>");
        msg(player, "<gray>Tip: Use <yellow>/afkzone zonereward add " + name + " [reward]</yellow> to restrict rewards for this zone.</gray>");

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
                    return filter(Arrays.asList("list", "save", "give"), args[1]);
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
            if (sub.equals("reward") && args[1].equalsIgnoreCase("save")) {
                return filter(new ArrayList<>(rewardManager.getRewards().keySet()), args[2]);
            }
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
            if (sub.equals("reward") && (args[1].equalsIgnoreCase("give") || args[1].equalsIgnoreCase("save"))) {
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