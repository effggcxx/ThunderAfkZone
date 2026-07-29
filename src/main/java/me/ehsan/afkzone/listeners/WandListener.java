package me.ehsan.afkzone.listeners;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.models.WandSelection;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles the wooden hoe wand for selecting cuboid regions.
 * Left-click = position 1, Right-click = position 2.
 */
public class WandListener implements Listener {

    private final Main plugin;
    private final Map<UUID, WandSelection> selections = new HashMap<>();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Configurable
    private Material wandMaterial = Material.WOODEN_HOE;

    public WandListener(Main plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        var cfg = plugin.getConfig();
        String matName = cfg.getString("wand.item", "WOODEN_HOE");
        try {
            wandMaterial = Material.valueOf(matName.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid wand material '" + matName + "'. Using WOODEN_HOE.");
            wandMaterial = Material.WOODEN_HOE;
        }
    }

    public WandSelection getSelection(Player player) {
        return selections.computeIfAbsent(player.getUniqueId(), k -> new WandSelection());
    }

    public void clearSelection(Player player) {
        UUID id = player.getUniqueId();
        selections.remove(id);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();

        // Only intercept clicks for players actually permitted to use the wand -
        // otherwise this would cancel normal tool use (e.g. tilling farmland)
        // for any player who happens to be holding the same material, since the
        // wand defaults to a plain WOODEN_HOE.
        if (!player.hasPermission("afkzone.wand")) return;

        // Check if holding the wand
        if (player.getInventory().getItemInMainHand().getType() != wandMaterial) return;

        // Only handle block clicks
        if (event.getClickedBlock() == null) return;

        Location loc = event.getClickedBlock().getLocation();
        WandSelection sel = getSelection(player);

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Position 1 — cancel so the block isn't broken
            event.setCancelled(true);
            sel.setPos1(loc);
            msg(player, "<green>Set position 1 to: <white>" + formatLoc(loc) + "</white></green>");
            if (sel.isComplete()) {
                msg(player, "<gray>Selection size: <white>" + sel.getDimensions() + "</white></gray>");
                msg(player, "<gray>Use <yellow>/afkzone create [name]</yellow> to create the zone.</gray>");
            } else {
                msg(player, "<gray>Now select position 2 with right-click.</gray>");
            }
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            // Position 2 — do NOT cancel so the block interaction (open door,
            // place block, etc.) still happens. The wand is still detected
            // and the position is recorded.
            sel.setPos2(loc);
            msg(player, "<green>Set position 2 to: <white>" + formatLoc(loc) + "</white></green>");
            if (sel.isComplete()) {
                msg(player, "<gray>Selection size: <white>" + sel.getDimensions() + "</white></gray>");
                msg(player, "<gray>Use <yellow>/afkzone create [name]</yellow> to create the zone.</gray>");
            } else {
                msg(player, "<gray>Now select position 1 with left-click.</gray>");
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        selections.remove(id);
    }

    // --- Helpers ---

    private String formatLoc(Location loc) {
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private void msg(Player player, String miniText) {
        try {
            player.sendMessage(MM.deserialize(miniText));
        } catch (Exception ex) {
            player.sendMessage(miniText.replaceAll("<[^>]+>", ""));
        }
    }
}