package me.ehsan.afkzone.commands.handlers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.listeners.WandListener;
import me.ehsan.afkzone.managers.BorderManager;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.models.WandSelection;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.NameValidator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles zone-management subcommands: {@code wand}, {@code sel},
 * {@code cancel}, {@code create}, {@code list}, {@code info}, {@code remove},
 * and {@code border}.
 *
 * <p>Split out from {@link me.ehsan.afkzone.commands.AfkZoneCommand} so the
 * router stays small. Method bodies are unchanged from the original.
 */
public class ZoneSubcommands extends AbstractSubcommandHandler {

    public ZoneSubcommands(Main plugin, ZoneManager zoneManager,
                           RewardManager rewardManager, StorageService storageService) {
        super(plugin, zoneManager, rewardManager, storageService);
    }

    // --- Zone listing ---

    public void listZones(CommandSender sender) {
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

    public void showZoneInfo(CommandSender sender, String name) {
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

    public void removeZone(CommandSender sender, String name) {
        if (!zoneManager.zoneExists(name)) {
            msg(sender, "<red>Zone '<white>" + name + "</white>' does not exist.</red>");
            msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
            return;
        }
        zoneManager.removeZone(name);
        msg(sender, "<green>Zone '<yellow>" + name + "</yellow>' has been removed.</green>");
    }

    // --- Wand selection commands ---

    public void giveWand(Player player) {
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

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    public void showWandSelection(Player player) {
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

    public void cancelWandSelection(Player player) {
        WandListener listener = plugin.getWandListener();
        if (listener == null) {
            msg(player, "<red>Wand system is not available.</red>");
            return;
        }
        listener.clearSelection(player);
        msg(player, "<yellow>Wand selection cleared.</yellow>");
    }

    public void createZoneFromWandSelection(Player player, String name) {
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

        if (!NameValidator.isValidName(name)) {
            msg(player, "<red>Zone name can only contain letters, numbers, underscores, and hyphens.</red>");
            return;
        }

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
            msg(player, "<red>This selection overlaps the existing zone '<yellow>" + overlapping + "</yellow>'.</red>");
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

        zoneManager.getSpatialIndex().addZone(name, worldName, x1, y1, z1, x2, y2, z2);

        msg(player, "<green>AFK zone '<yellow>" + name + "</yellow>' created!</green>");
        msg(player, "<gray>  World: <white>" + worldName + "</white></gray>");
        msg(player, "<gray>  Area: <white>(" + x1 + "," + y1 + "," + z1 + ") -> (" + x2 + "," + y2 + "," + z2 + ")</white></gray>");
        msg(player, "<gray>  Size: <white>" + (Math.abs(x2 - x1) + 1) + " x " + (Math.abs(y2 - y1) + 1) + " x " + (Math.abs(z2 - z1) + 1) + "</white></gray>");
        msg(player, "<gray>Tip: Use <yellow>/afkzone zonereward add " + name + " [reward]</yellow> to assign rewards to this zone.</gray>");

        listener.clearSelection(player);
    }

    private String formatLoc(Location loc) {
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    // --- Border ---

    public void handleBorder(Player player, String[] args) {
        if (!player.hasPermission("afkzone.border")) {
            msg(player, "<red>You don't have permission.</red>");
            return;
        }
        BorderManager bm = plugin.getBorderManager();
        if (bm == null) {
            msg(player, "<red>Border system is not available.</red>");
            return;
        }
        if (args.length >= 2) {
            String mode = args[1].toLowerCase(Locale.ROOT);
            if (mode.equals("on") || mode.equals("true") || mode.equals("enable")) {
                bm.setEnabled(player, true);
                msg(player, "<green>AFK zone border particles enabled.</green>");
                msg(player, "<gray>Particles will appear around nearby zones. Run <yellow>/afkzone border off</yellow> to disable.</gray>");
                return;
            } else if (mode.equals("off") || mode.equals("false") || mode.equals("disable")) {
                bm.setEnabled(player, false);
                msg(player, "<yellow>AFK zone border particles disabled.</yellow>");
                return;
            }
        }
        boolean enabled = bm.toggle(player);
        if (enabled) {
            msg(player, "<green>AFK zone border particles enabled.</green>");
            msg(player, "<gray>Particles will appear around nearby zones. Run <yellow>/afkzone border</yellow> again to disable.</gray>");
        } else {
            msg(player, "<yellow>AFK zone border particles disabled.</yellow>");
        }
    }

    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("border")) {
                return filter(Arrays.asList("on", "off"), args[1]);
            }
            if (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete")) {
                return filter(new ArrayList<>(zoneManager.getZoneNames()), args[1]);
            }
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("create")) {
                return filter(new ArrayList<>(zoneManager.getZoneNames()), args[2]);
            }
        }
        return Collections.emptyList();
    }
}
