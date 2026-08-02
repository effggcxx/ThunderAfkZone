package me.ehsan.afkzone.commands.handlers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.storage.StorageService;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Base for per-domain subcommand handlers.
 *
 * <p>Holds the shared dependencies every subcommand group needs and the common
 * {@code msg()} helper, so {@link me.ehsan.afkzone.commands.AfkZoneCommand}
 * stays a thin router instead of embedding all command logic.
 */
public abstract class AbstractSubcommandHandler {

    protected final Main plugin;
    protected final ZoneManager zoneManager;
    protected final RewardManager rewardManager;
    protected final StorageService storageService;

    protected static final MiniMessage MM = MiniMessage.miniMessage();

    protected AbstractSubcommandHandler(Main plugin, ZoneManager zoneManager,
                                        RewardManager rewardManager, StorageService storageService) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
        this.rewardManager = rewardManager;
        this.storageService = storageService;
    }

    /**
     * Sends a MiniMessage-formatted message, falling back to a stripped plain
     * text version if deserialization fails.
     */
    protected void msg(CommandSender sender, String miniText) {
        try {
            sender.sendMessage(MM.deserialize(miniText));
        } catch (Exception ex) {
            sender.sendMessage(miniText.replaceAll("<[^>]+>", ""));
        }
    }

    protected List<String> filter(List<String> options, String partial) {
        if (partial == null || partial.isEmpty()) {
            return options;
        }
        String lower = partial.toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(lower))
                .toList();
    }
}