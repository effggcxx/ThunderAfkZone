package me.ehsan.afkzone.managers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.models.NextRewardInfo;
import me.ehsan.afkzone.models.Reward;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RewardManager {

    private final Main plugin;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    private Map<String, Reward> rewards = new HashMap<>();

    // Runtime tracking
    private Map<UUID, BukkitTask> playerTasks = new ConcurrentHashMap<>();
    private Map<UUID, Map<String, Integer>> playerProgress = new ConcurrentHashMap<>();
    private Map<UUID, Set<String>> playerGivenOnce = new ConcurrentHashMap<>();
    private Map<UUID, String> playerZone = new ConcurrentHashMap<>();
    private Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private Map<UUID, Long> lastActive = new ConcurrentHashMap<>();

    private String onMultiple = "all";
    private int afkThresholdSeconds = 60;
    private Sound enterSound = null;
    private Sound exitSound = null;
    private Sound rewardSound = null;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;

    private boolean timerEnabled = true;
    private String timerTemplate = "<gold><bold>Next reward in <timer></bold></gold>";
    private String timerDisplay = "title";
    private String timerSize = "big";
    private int timerTitleFadeIn = 5;
    private int timerTitleStay = 40;
    private int timerTitleFadeOut = 5;

    // Messages
    private String msgEnterZone = "<green>Entered AFK zone: <yellow><zone></yellow></green>";
    private String msgExitZone = "<gray>You left AFK zone: <yellow><zone></yellow></gray>";
    private String msgRewardReceived = "<gold>You received reward: <yellow><reward></yellow></gold>";
    private String msgRewardFailed = "<red>Reward '<reward>' could not be delivered. Please contact staff.</red>";

    private final ZoneManager zoneManager;

    public RewardManager(Main plugin, ZoneManager zoneManager) {
        this.plugin = plugin;
        this.zoneManager = zoneManager;
    }

    public void loadRewards() {
        rewards.clear();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.isConfigurationSection("rewards")) return;
        for (String key : cfg.getConfigurationSection("rewards").getKeys(false)) {
            String path = "rewards." + key;
            Reward r = new Reward(key);
            r.description = cfg.getString(path + ".description", "");
            r.executor = cfg.getString(path + ".executor", "console");
            r.itemName = cfg.getString(path + ".item", cfg.getString(path + ".command", ""));
            r.amount = cfg.getInt(path + ".amount", 1);
            r.command = cfg.getString(path + ".command", "");
            r.intervalSeconds = cfg.getInt(path + ".interval_seconds", 0);
            r.onceAfterSeconds = cfg.getInt(path + ".once_after_seconds", 0);
            r.priority = cfg.getInt(path + ".priority", 0);
            r.enabled = cfg.getBoolean(path + ".enabled", true);
            rewards.put(key, r);
        }
        plugin.getLogger().info("Loaded " + rewards.size() + " rewards");
    }

    public void loadGlobalConfig() {
        this.onMultiple = plugin.getConfig().getString("global.on_multiple", "all");
        this.afkThresholdSeconds = plugin.getConfig().getInt("global.afk_threshold_seconds", 60);
        this.enterSound = parseSound(plugin.getConfig().getString("global.enter_sound", "ENTITY_PLAYER_LEVELUP"), "global.enter_sound");
        this.exitSound = parseSound(plugin.getConfig().getString("global.exit_sound", "ENTITY_ITEM_BREAK"), "global.exit_sound");
        this.rewardSound = parseSound(plugin.getConfig().getString("global.reward_sound", "ENTITY_EXPERIENCE_ORB_PICKUP"), "global.reward_sound");
        this.soundVolume = (float) plugin.getConfig().getDouble("global.sound_volume", 1.0);
        this.soundPitch = (float) plugin.getConfig().getDouble("global.sound_pitch", 1.0);
        this.timerEnabled = plugin.getConfig().getBoolean("global.timer.enabled", true);
        this.timerTemplate = plugin.getConfig().getString("global.timer.template", "<gold><bold>Next reward in <timer></bold></gold>");
        this.timerDisplay = plugin.getConfig().getString("global.timer.display", "title");
        this.timerSize = plugin.getConfig().getString("global.timer.size", "big");
        this.timerTitleFadeIn = plugin.getConfig().getInt("global.timer.title.fade_in", 5);
        this.timerTitleStay = plugin.getConfig().getInt("global.timer.title.stay", 40);
        this.timerTitleFadeOut = plugin.getConfig().getInt("global.timer.title.fade_out", 5);

        this.msgEnterZone = plugin.getConfig().getString("global.messages.enter_zone", msgEnterZone);
        this.msgExitZone = plugin.getConfig().getString("global.messages.exit_zone", msgExitZone);
        this.msgRewardReceived = plugin.getConfig().getString("global.messages.reward_received", msgRewardReceived);
        this.msgRewardFailed = plugin.getConfig().getString("global.messages.reward_failed", msgRewardFailed);
    }

    private Sound parseSound(String soundName, String configPath) {
        if (soundName == null || soundName.isEmpty()) return null;
        try {
            return Sound.valueOf(soundName.toUpperCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid sound name in config path " + configPath + ": '" + soundName + "'. Sound disabled.");
            return null;
        }
    }

    public Map<String, Reward> getRewards() {
        return rewards;
    }

    public String getPlayerZone(UUID id) {
        return playerZone.get(id);
    }

    public void markActive(UUID id) {
        lastActive.put(id, System.currentTimeMillis());
    }

    public boolean isPlayerInAnyZone(UUID id) {
        return playerZone.containsKey(id);
    }

    // --- Timer / Display utilities ---

    private String formatTime(long seconds) {
        if (seconds <= 0) return "0s";
        if (seconds >= 60) {
            long mins = seconds / 60;
            long secs = seconds % 60;
            return mins + ":" + String.format("%02d", secs);
        }
        return seconds + "s";
    }

    private void sendStyled(Player player, String template, String zoneName, String rewardName) {
        if (template == null || template.isEmpty()) return;
        String text = template;
        if (zoneName != null) text = text.replace("<zone>", zoneName);
        if (rewardName != null) text = text.replace("<reward>", rewardName);
        try {
            player.sendMessage(miniMessage.deserialize(text));
        } catch (Exception ex) {
            player.sendMessage(text.replaceAll("<[^>]+>", ""));
        }
    }

    private Component buildTimerComponent(long secondsRemaining, String zoneName, String playerName) {
        String text = timerTemplate
                .replace("<timer>", formatTime(secondsRemaining))
                .replace("<zone>", zoneName == null ? "" : zoneName)
                .replace("<player>", playerName == null ? "" : playerName);
        if ("mini".equalsIgnoreCase(timerSize)) {
            text = "<gray><italic>" + text + "</italic></gray>";
        } else if ("big".equalsIgnoreCase(timerSize)) {
            text = "<gold><bold>" + text + "</bold></gold>";
        }
        try {
            return miniMessage.deserialize(text);
        } catch (Exception ex) {
            return Component.text("Next reward in " + formatTime(secondsRemaining));
        }
    }

    private float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    private void sendTimer(Player player, long secondsRemaining, long totalSeconds, String zoneName) {
        if (!timerEnabled) return;
        Component component = buildTimerComponent(secondsRemaining, zoneName, player.getName());
        switch (timerDisplay.toLowerCase(Locale.ROOT)) {
            case "actionbar":
                player.sendActionBar(component);
                break;
            case "chat":
                player.sendMessage(component);
                break;
            case "bossbar": {
                float progress = totalSeconds > 0
                        ? clamp01((float) (totalSeconds - secondsRemaining) / (float) totalSeconds)
                        : 0f;
                BossBar bar = activeBossBars.get(player.getUniqueId());
                if (bar == null) {
                    bar = BossBar.bossBar(component, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
                    activeBossBars.put(player.getUniqueId(), bar);
                    player.showBossBar(bar);
                } else {
                    bar.name(component);
                    bar.progress(progress);
                }
                break;
            }
            default:
                player.showTitle(Title.title(component, Component.empty(),
                        Title.Times.times(
                                Duration.ofMillis(timerTitleFadeIn * 50L),
                                Duration.ofMillis(timerTitleStay * 50L),
                                Duration.ofMillis(timerTitleFadeOut * 50L))));
                break;
        }
    }

    private NextRewardInfo getNearestReward(Map<String, Integer> prog, Set<String> given) {
        long nearest = Long.MAX_VALUE;
        long total = 0;
        for (Reward r : rewards.values()) {
            if (!r.enabled) continue;
            int current = prog.getOrDefault(r.name, 0);
            if (r.onceAfterSeconds > 0 && !given.contains(r.name)) {
                long remaining = r.onceAfterSeconds - current;
                if (remaining >= 0 && remaining < nearest) {
                    nearest = remaining;
                    total = r.onceAfterSeconds;
                }
            }
            if (r.intervalSeconds > 0) {
                long remaining = r.intervalSeconds - (current % r.intervalSeconds);
                if (remaining == r.intervalSeconds) remaining = r.intervalSeconds;
                if (remaining >= 0 && remaining < nearest) {
                    nearest = remaining;
                    total = r.intervalSeconds;
                }
            }
        }
        return nearest == Long.MAX_VALUE ? new NextRewardInfo(0, 0) : new NextRewardInfo(nearest, total);
    }

    public void giveRewardToPlayer(Reward r, Player player) {
        if (r == null || player == null) return;
        if ((r.executor == null || r.executor.equalsIgnoreCase("console")) && r.command != null && !r.command.isEmpty()) {
            String consoleCmd = r.command.replace("{player}", player.getName()).replace("{award}", r.name);
            boolean ok = dispatchAndCheck(consoleCmd, r.name);
            if (ok) {
                sendStyled(player, msgRewardReceived, null, r.name);
                if (rewardSound != null) player.playSound(player.getLocation(), rewardSound, soundVolume, soundPitch);
            } else {
                sendStyled(player, msgRewardFailed, null, r.name);
            }
            return;
        }
        try {
            String ex = r.executor == null ? "console" : r.executor.toLowerCase();
            boolean ok = true;
            switch (ex) {
                case "itemedit": {
                    String dispatch = "si give " + player.getName() + " " + r.itemName + " " + r.amount;
                    ok = dispatchAndCheck(dispatch, r.name);
                    break;
                }
                case "item":
                case "itemadder": {
                    String dispatch = "itemadder give " + player.getName() + " " + r.itemName + " " + r.amount;
                    ok = dispatchAndCheck(dispatch, r.name);
                    break;
                }
                case "vanilla":
                case "give": {
                    String giveCmd = "give " + player.getName() + " " + r.itemName + " " + r.amount;
                    ok = dispatchAndCheck(giveCmd, r.name);
                    break;
                }
                default: {
                    if (r.command != null && !r.command.isEmpty()) {
                        String fallback = r.command.replace("{player}", player.getName()).replace("{award}", r.name);
                        ok = dispatchAndCheck(fallback, r.name);
                    }
                    break;
                }
            }
            if (ok) {
                sendStyled(player, msgRewardReceived, null, r.name);
                if (rewardSound != null) {
                    player.playSound(player.getLocation(), rewardSound, soundVolume, soundPitch);
                }
            } else {
                sendStyled(player, msgRewardFailed, null, r.name);
            }
        } catch (Exception ex) {
            plugin.getLogger().severe("Failed to give reward " + r.name + ": " + ex.getMessage());
            sendStyled(player, msgRewardFailed, null, r.name);
        }
    }

    private boolean dispatchAndCheck(String command, String rewardName) {
        try {
            boolean result = plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
            if (!result) {
                plugin.getLogger().warning("Command for reward '" + rewardName + "' returned failure: /" + command);
            }
            return result;
        } catch (Exception ex) {
            plugin.getLogger().severe("Exception dispatching command for reward '" + rewardName + "': /" + command + " - " + ex.getMessage());
            return false;
        }
    }

    // --- Player Tracking ---

    public void startTrackingPlayer(final Player player, final String zoneName) {
        UUID id = player.getUniqueId();
        stopTrackingPlayer(id);
        playerZone.put(id, zoneName);
        playerProgress.put(id, new ConcurrentHashMap<>());
        playerGivenOnce.put(id, ConcurrentHashMap.newKeySet());
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                stopTrackingPlayer(id);
                return;
            }
            String currentZone = zoneManager.findZoneForLocation(player.getLocation());
            if (currentZone == null || !currentZone.equals(zoneName)) {
                stopTrackingPlayer(id);
                sendStyled(player, msgExitZone, zoneName, null);
                return;
            }
            Map<String, Integer> prog = playerProgress.get(id);
            Set<String> given = playerGivenOnce.get(id);
            if (prog == null) return;
            long last = lastActive.getOrDefault(id, System.currentTimeMillis());
            if ((System.currentTimeMillis() - last) < (afkThresholdSeconds * 1000L)) {
                return;
            }
            for (Reward r : rewards.values()) {
                if (!r.enabled) continue;
                prog.putIfAbsent(r.name, 0);
                prog.put(r.name, prog.get(r.name) + 1);
            }
            Set<Reward> due = new HashSet<>();
            for (Reward r : rewards.values()) {
                if (!r.enabled) continue;
                int t = prog.getOrDefault(r.name, 0);
                if (r.onceAfterSeconds > 0 && !given.contains(r.name) && t >= r.onceAfterSeconds) due.add(r);
                if (r.intervalSeconds > 0 && t > 0 && t % r.intervalSeconds == 0) due.add(r);
            }
            if (!due.isEmpty()) {
                if ("highest".equalsIgnoreCase(onMultiple)) {
                    int max = due.stream().mapToInt(x -> x.priority).max().orElse(Integer.MIN_VALUE);
                    due = due.stream().filter(x -> x.priority == max).collect(Collectors.toSet());
                }
                for (Reward r : due) {
                    giveRewardToPlayer(r, player);
                    if (r.onceAfterSeconds > 0) given.add(r.name);
                }
            }
            NextRewardInfo info = getNearestReward(prog, given);
            if (timerEnabled && info.getRemainingSeconds() > 0) {
                sendTimer(player, info.getRemainingSeconds(), info.getTotalSeconds(), zoneName);
            }
        }, 20L, 20L);
        playerTasks.put(id, task);
        sendStyled(player, msgEnterZone, zoneName, null);
        if (enterSound != null) {
            player.playSound(player.getLocation(), enterSound, soundVolume, soundPitch);
        }
    }

    public void stopTrackingPlayer(UUID id) {
        BukkitTask t = playerTasks.remove(id);
        if (t != null) t.cancel();
        playerProgress.remove(id);
        playerGivenOnce.remove(id);
        String zone = playerZone.remove(id);
        Player player = Bukkit.getPlayer(id);
        BossBar bar = activeBossBars.remove(id);
        if (bar != null && player != null) {
            player.hideBossBar(bar);
        }
        if (player != null && zone != null && exitSound != null) {
            player.playSound(player.getLocation(), exitSound, soundVolume, soundPitch);
        }
    }

    public void sendEnterMessage(Player player, String zoneName) {
        sendStyled(player, msgEnterZone, zoneName, null);
    }

    public void sendExitMessage(Player player, String zoneName) {
        sendStyled(player, msgExitZone, zoneName, null);
        if (exitSound != null) {
            player.playSound(player.getLocation(), exitSound, soundVolume, soundPitch);
        }
    }
}