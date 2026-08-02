package me.ehsan.afkzone.commands;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.commands.handlers.RewardSubcommands;
import me.ehsan.afkzone.commands.handlers.StatsSubcommands;
import me.ehsan.afkzone.commands.handlers.ZoneRewardSubcommands;
import me.ehsan.afkzone.commands.handlers.ZoneSubcommands;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.storage.StorageService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Top-level {@code /afkzone} command: a thin router that dispatches to
 * per-domain handler classes and provides tab-completion.
 *
 * <p>Command logic lives in the {@code handlers} subpackage:
 * <ul>
 *   <li>{@link StatsSubcommands} - stats, top</li>
 *   <li>{@link RewardSubcommands} - reward save/list/give/remove/enable/disable/toggle</li>
 *   <li>{@link ZoneRewardSubcommands} - zonereward list/add/remove/clear</li>
 *   <li>{@link ZoneSubcommands} - wand, sel, cancel, create, list, info, remove, border</li>
 * </ul>
 *
 * <p>Every subcommand name (including aliases) is registered exactly once in
 * {@link #registerSubcommands()}: execution and tab-completion for a given
 * name live together in one {@link Subcommand} entry, instead of being kept
 * in sync by hand across a root-name list, an execute switch, and a
 * tab-complete switch. Adding a subcommand means adding one {@code register()}
 * call here - nowhere else in this file needs to change.
 */
public class AfkZoneCommand implements CommandExecutor, TabCompleter {

    private final Main plugin;
    private final ZoneManager zoneManager;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final StatsSubcommands statsHandler;
    private final RewardSubcommands rewardHandler;
    private final ZoneRewardSubcommands zoneRewardHandler;
    private final ZoneSubcommands zoneHandler;

    @FunctionalInterface
    private interface Executor {
        boolean execute(CommandSender sender, String[] args);
    }

    @FunctionalInterface
    private interface Completer {
        List<String> complete(CommandSender sender, String[] args);
    }

    /**
     * One registered subcommand name. {@code showInRootTabComplete} is false
     * for aliases (e.g. "wandsel", "delete") so {@code /afkzone <tab>} only
     * suggests the canonical spelling, while the alias still works when typed.
     */
    private record Subcommand(Executor executor, Completer completer, boolean showInRootTabComplete) {
        Subcommand(Executor executor, Completer completer) {
            this(executor, completer, true);
        }
        Subcommand(Executor executor, Completer completer, boolean showInRootTabComplete) {
            this.executor = executor;
            this.completer = completer == null ? (s, a) -> Collections.emptyList() : completer;
            this.showInRootTabComplete = showInRootTabComplete;
        }
    }

    private final Map<String, Subcommand> subcommands = new LinkedHashMap<>();

    public AfkZoneCommand(Main plugin, ZoneManager zoneManager, RewardManager rewardManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        StorageService storageService = plugin.getStorageService();

        this.statsHandler = new StatsSubcommands(plugin, zoneManager, rewardManager, storageService);
        this.rewardHandler = new RewardSubcommands(plugin, zoneManager, rewardManager, storageService);
        this.zoneRewardHandler = new ZoneRewardSubcommands(plugin, zoneManager, rewardManager, storageService);
        this.zoneHandler = new ZoneSubcommands(plugin, zoneManager, rewardManager, storageService);

        registerSubcommands();
    }

    private void register(String name, Executor executor, Completer completer) {
        subcommands.put(name, new Subcommand(executor, completer));
    }

    private void register(String name, Executor executor) {
        register(name, executor, null);
    }

    /** Registers an alias for an already-registered subcommand, hidden from root tab-complete. */
    private void registerAlias(String alias, String canonicalName) {
        Subcommand target = subcommands.get(canonicalName);
        subcommands.put(alias, new Subcommand(target.executor(), target.completer(), false));
    }

    private void registerSubcommands() {
        register("reload", (sender, args) -> {
            if (!sender.hasPermission("afkzone.reload")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            plugin.reloadAll();
            msg(sender, "<green>ThunderAfkZone configuration reloaded (config + zones).</green>");
            return true;
        });

        register("reward", rewardHandler::handleRewardCommand, rewardHandler::tabComplete);
        register("zonereward", zoneRewardHandler::handleZoneRewardCommand, zoneRewardHandler::tabComplete);

        register("wand", (sender, args) -> {
            if (!(sender instanceof Player player)) {
                msg(sender, "<red>Only players can use this command.</red>");
                return true;
            }
            if (!sender.hasPermission("afkzone.wand")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            zoneHandler.giveWand(player);
            return true;
        });

        register("sel", (sender, args) -> {
            if (!(sender instanceof Player player)) {
                msg(sender, "<red>Only players can use this command.</red>");
                return true;
            }
            if (!sender.hasPermission("afkzone.wand")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            zoneHandler.showWandSelection(player);
            return true;
        });
        registerAlias("wandsel", "sel");

        register("cancel", (sender, args) -> {
            if (!(sender instanceof Player player)) {
                msg(sender, "<red>Only players can use this command.</red>");
                return true;
            }
            if (!sender.hasPermission("afkzone.wand")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            zoneHandler.cancelWandSelection(player);
            return true;
        });
        registerAlias("wandcancel", "cancel");

        register("create", (sender, args) -> {
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
            zoneHandler.createZoneFromWandSelection(player, args[1]);
            return true;
        }, zoneHandler::tabComplete);

        register("list", (sender, args) -> {
            if (!sender.hasPermission("afkzone.list")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            zoneHandler.listZones(sender);
            return true;
        });

        register("info", (sender, args) -> {
            if (!sender.hasPermission("afkzone.info")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            if (args.length < 2) {
                msg(sender, "<red>Usage: /afkzone info [name]</red>");
                msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                return true;
            }
            zoneHandler.showZoneInfo(sender, args[1]);
            return true;
        }, zoneHandler::tabComplete);

        register("remove", (sender, args) -> {
            if (!sender.hasPermission("afkzone.remove")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            if (args.length < 2) {
                msg(sender, "<red>Usage: /afkzone remove [name]</red>");
                msg(sender, "<gray>Available zones: <white>" + String.join("</white>, <white>", zoneManager.getZoneNames()) + "</white></gray>");
                return true;
            }
            zoneHandler.removeZone(sender, args[1]);
            return true;
        }, zoneHandler::tabComplete);
        registerAlias("delete", "remove");

        register("stats", (sender, args) -> {
            if (!sender.hasPermission("afkzone.stats")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            statsHandler.handleStats(sender, args);
            return true;
        }, statsHandler::tabComplete);

        register("top", (sender, args) -> {
            if (!sender.hasPermission("afkzone.top")) {
                msg(sender, "<red>You don't have permission.</red>");
                return true;
            }
            statsHandler.handleTop(sender, args);
            return true;
        }, statsHandler::tabComplete);

        register("border", (sender, args) -> {
            if (!(sender instanceof Player player)) {
                msg(sender, "<red>Only players can use this command.</red>");
                return true;
            }
            zoneHandler.handleBorder(player, args);
            return true;
        }, zoneHandler::tabComplete);
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
        Subcommand subcommand = subcommands.get(sub);
        if (subcommand == null) {
            msg(sender, "<red>Unknown command: <white>" + sub + "</white></red>");
            msg(sender, "<gray>Use <yellow>/afkzone</yellow> to see all available commands.</gray>");
            return true;
        }
        return subcommand.executor().execute(sender, args);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> rootNames = subcommands.entrySet().stream()
                    .filter(e -> e.getValue().showInRootTabComplete())
                    .map(Map.Entry::getKey)
                    .toList();
            return filter(rootNames, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Subcommand subcommand = subcommands.get(sub);
        if (subcommand == null) return Collections.emptyList();
        List<String> result = subcommand.completer().complete(sender, args);
        return result == null ? null : result;
    }

    private List<String> filter(List<String> options, String partial) {
        if (partial == null || partial.isEmpty()) {
            return options;
        }
        String lower = partial.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}