package me.ehsan.afkzone.managers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.models.NextRewardInfo;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.service.*;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages rewards and player tracking with a single global scheduler.
 * Now delegates to extracted services: PlayerTracker, TimerService, RewardDispatcher.
 */
public class RewardManager {

    private final Main plugin;
    private final ZoneService zoneService;
    private final PlayerTracker playerTracker;
    private final TimerService timerService;
    private final RewardDispatcher rewardDispatcher;
    private final StorageService storageService;

    private Map<String, Reward> rewards = new HashMap<>();

    private BukkitTask globalTask;

    private String onMultiple = "all";
    private int afkThresholdSeconds = 60;
    private Sound enterSound = null;
    private Sound exitSound = null;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;

    private String msgEnterZone = "<green>Entered AFK zone: <yellow><zone></yellow></green>";
    private String msgExitZone = "<gray>You left AFK zone: <yellow><zone></yellow></gray>";

    public RewardManager(Main plugin, ZoneService zoneService, PlayerTracker playerTracker,
                         TimerService timerService, RewardDispatcher rewardDispatcher,
                         StorageService storageService) {
        this.plugin = plugin;
        this.zoneService = zoneService;
        this.playerTracker = playerTracker;
        this.timerService = timerService;
        this.rewardDispatcher = rewardDispatcher;
        this.storageService = storageService;
    }

    public Map<String, Reward> getRewards() {
        return rewards;
    }

    public PlayerTracker getPlayerTracker() {
        return playerTracker;
    }

    public TimerService getTimerService() {
        return timerService;
    }

    public RewardDispatcher getRewardDispatcher() {
        return rewardDispatcher;
    }

    // -------------------------------------------------------------------------
    // Configuration loading
    // -------------------------------------------------------------------------

    public void loadRewards() {
        rewards.clear();
        FileConfiguration cfg = plugin.getConfig();
        if (!cfg.isConfigurationSection("rewards")) return;
        for (String key : cfg.getConfigurationSection("rewards").getKeys(false)) {
            String path = "rewards." + key;
            Reward r = new Reward(key);
            r.setDescription(cfg.getString(path + ".description", ""));
            r.setExecutor(cfg.getString(path + ".executor", "console"));
            r.setItemName(cfg.getString(path + ".item", cfg.getString(path + ".command", "")));
            r.setAmount(cfg.getInt(path + ".amount", 1));
            r.setCommand(cfg.getString(path + ".command", ""));
            r.setIntervalSeconds(cfg.getInt(path + ".interval_seconds", 0));
            r.setOnceAfterSeconds(cfg.getInt(path + ".once_after_seconds", 0));
            r.setPriority(cfg.getInt(path + ".priority", 0));
            r.setEnabled(cfg.getBoolean(path + ".enabled", true));
            rewards.put(key, r);
        }
        plugin.getLogger().info("Loaded " + rewards.size() + " rewards");
    }

    public void loadGlobalConfig() {
        FileConfiguration cfg = plugin.getConfig();

        this.onMultiple = cfg.getString("global.on_multiple", "all");
        this.afkThresholdSeconds = cfg.getInt("global.afk_threshold_seconds", 60);
        this.enterSound = MessageUtils.parseSound(cfg.getString("global.enter_sound", "ENTITY_PLAYER_LEVELUP"),
                plugin.getLogger(), "global.enter_sound");
        this.exitSound = MessageUtils.parseSound(cfg.getString("global.exit_sound", "ENTITY_ITEM_BREAK"),
                plugin.getLogger(), "global.exit_sound");
        this.soundVolume = (float) cfg.getDouble("global.sound_volume", 1.0);
        this.soundPitch = (float) cfg.getDouble("global.sound_pitch", 1.0);

        // Timer config
        timerService.setEnabled(cfg.getBoolean("global.timer.enabled", true));
        timerService.setTemplate(cfg.getString("global.timer.template", "<gold><bold>Next reward in <timer></bold></gold>"));
        timerService.setDisplay(cfg.getString("global.timer.display", "title"));
        timerService.setSize(cfg.getString("global.timer.size", "big"));
        timerService.setTitleFadeIn(cfg.getInt("global.timer.title.fade_in", 5));
        timerService.setTitleStay(cfg.getInt("global.timer.title.stay", 40));
        timerService.setTitleFadeOut(cfg.getInt("global.timer.title.fade_out", 5));

        // Messages
        this.msgEnterZone = cfg.getString("global.messages.enter_zone", msgEnterZone);
        this.msgExitZone = cfg.getString("global.messages.exit_zone", msgExitZone);

        // Reward dispatcher config
        Sound rewardSound = MessageUtils.parseSound(cfg.getString("global.reward_sound", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                plugin.getLogger(), "global.reward_sound");
        rewardDispatcher.setRewardSound(rewardSound);
        rewardDispatcher.setSoundVolume(this.soundVolume);
        rewardDispatcher.setSoundPitch(this.soundPitch);
        rewardDispatcher.setMsgRewardReceived(cfg.getString("global.messages.reward_received",
                "<gold>You received reward: <yellow><reward></yellow></gold>"));
        rewardDispatcher.setMsgRewardFailed(cfg.getString("global.messages.reward_failed",
                "<red>Reward '<reward>' could not be delivered. Please contact staff.</red>"));
    }

    // -------------------------------------------------------------------------
    // Global scheduler
    // -------------------------------------------------------------------------

    public void startGlobalScheduler() {
        if (globalTask != null && !globalTask.isCancelled()) {
            return;
        }
        globalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAllPlayers, 20L, 20L);
        plugin.getLogger().info("Global AFK reward scheduler started");
    }

    public void stopGlobalScheduler() {
        if (globalTask != null) {
            globalTask.cancel();
            globalTask = null;
        }
        timerService.hideAllBossBars();
        playerTracker.clear();
    }

    private void tickAllPlayers() {
        Map<UUID, String> tracked = playerTracker.getTrackedPlayers();
        if (tracked.isEmpty()) return;

        // Snapshot to avoid ConcurrentModificationException
        List<UUID> ids = new ArrayList<>(tracked.keySet());

        for (UUID id : ids) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                playerTracker.stopTrackingSilent(id);
                timerService.removePlayer(player);
                continue;
            }

            String zoneName = playerTracker.getPlayerZone(id);
            if (zoneName == null) continue;

            // Verify player is still inside the zone
            String currentZone = zoneService.findZoneForLocation(player.getLocation());
            if (currentZone == null || !currentZone.equals(zoneName)) {
                playerTracker.stopTracking(id, msgExitZone, exitSound, soundVolume, soundPitch);
                timerService.removePlayer(player);
                executeExitCommands(player, zoneName);
                continue;
            }

            // Only count time if the player is considered AFK
            long last = playerTracker.getLastActiveTime(id);
            if ((System.currentTimeMillis() - last) < (afkThresholdSeconds * 1000L)) {
                continue;
            }

            Map<String, Integer> prog = playerTracker.getProgress(id);
            Set<String> given = playerTracker.getGivenOnce(id);

            // Rewards active for this zone (empty list = all global rewards)
            List<Reward> zoneRewards = getRewardsForZone(zoneName);

            // Increment progress only for rewards that apply to this zone
            for (Reward r : zoneRewards) {
                if (!r.isEnabled()) continue;
                prog.merge(r.getName(), 1, Integer::sum);
            }

            // Track AFK time in storage
            storageService.addAfkTime(id, 1);
            storageService.addZoneAfkTime(id, zoneName, 1);

            // Collect due rewards
            Set<Reward> due = new HashSet<>();
            for (Reward r : zoneRewards) {
                if (!r.isEnabled()) continue;
                int t = prog.getOrDefault(r.getName(), 0);
                if (r.getOnceAfterSeconds() > 0 && !given.contains(r.getName()) && t >= r.getOnceAfterSeconds()) {
                    due.add(r);
                }
                if (r.getIntervalSeconds() > 0 && t > 0 && t % r.getIntervalSeconds() == 0) {
                    due.add(r);
                }
            }

            if (!due.isEmpty()) {
                if ("highest".equalsIgnoreCase(onMultiple)) {
                    int max = due.stream().mapToInt(Reward::getPriority).max().orElse(Integer.MIN_VALUE);
                    due = due.stream().filter(x -> x.getPriority() == max).collect(Collectors.toSet());
                }
                for (Reward r : due) {
                    rewardDispatcher.giveRewardToPlayer(r, player);
                    if (r.getOnceAfterSeconds() > 0) {
                        given.add(r.getName());
                    }
                }
            }

            // Update timer display (only considering zone rewards)
            NextRewardInfo info = getNearestReward(prog, given, zoneRewards);
            if (info.getRemainingSeconds() > 0) {
                timerService.sendTimer(player, info.getRemainingSeconds(), info.getTotalSeconds(), zoneName);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Player enter / leave
    // -------------------------------------------------------------------------

    public void startTrackingPlayer(Player player, String zoneName) {
        playerTracker.startTracking(player, zoneName, enterSound, soundVolume, soundPitch, msgEnterZone);
        executeEntryCommands(player, zoneName);
    }

    public void stopTrackingPlayer(UUID id) {
        Player player = Bukkit.getPlayer(id);
        String zone = playerTracker.getPlayerZone(id);
        playerTracker.stopTracking(id, msgExitZone, exitSound, soundVolume, soundPitch);
        timerService.removePlayer(player);
        if (player != null && zone != null) {
            executeExitCommands(player, zone);
        }
    }

    public String getPlayerZone(UUID id) {
        return playerTracker.getPlayerZone(id);
    }

    public boolean isPlayerInAnyZone(UUID id) {
        return playerTracker.isPlayerInAnyZone(id);
    }

    public void markActive(UUID id) {
        playerTracker.markActive(id);
    }

    // -------------------------------------------------------------------------
    // Zone entry/exit commands
    // -------------------------------------------------------------------------

    private void executeEntryCommands(Player player, String zoneName) {
        List<String> commands = zoneService.getZoneEntryCommands(zoneName);
        for (String cmd : commands) {
            String parsed = cmd.replace("{player}", player.getName())
                    .replace("{zone}", zoneName)
                    .replace("{uuid}", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    private void executeExitCommands(Player player, String zoneName) {
        List<String> commands = zoneService.getZoneExitCommands(zoneName);
        for (String cmd : commands) {
            String parsed = cmd.replace("{player}", player.getName())
                    .replace("{zone}", zoneName)
                    .replace("{uuid}", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    // -------------------------------------------------------------------------
    // Reward helpers
    // -------------------------------------------------------------------------

    public List<Reward> getRewardsForZone(String zoneName) {
        List<String> zoneRewardNames = zoneService.getZoneRewards(zoneName);
        if (zoneRewardNames == null || zoneRewardNames.isEmpty()) {
            return rewards.values().stream()
                    .filter(Reward::isEnabled)
                    .collect(Collectors.toList());
        }
        List<Reward> result = new ArrayList<>();
        for (String name : zoneRewardNames) {
            Reward r = rewards.get(name);
            if (r != null && r.isEnabled()) {
                result.add(r);
            }
        }
        return result;
    }

    private NextRewardInfo getNearestReward(Map<String, Integer> prog, Set<String> given, List<Reward> zoneRewards) {
        long nearest = Long.MAX_VALUE;
        long total = 0;
        for (Reward r : zoneRewards) {
            if (!r.isEnabled()) continue;
            int current = prog.getOrDefault(r.getName(), 0);
            if (r.getOnceAfterSeconds() > 0 && !given.contains(r.getName())) {
                long remaining = r.getOnceAfterSeconds() - current;
                if (remaining >= 0 && remaining < nearest) {
                    nearest = remaining;
                    total = r.getOnceAfterSeconds();
                }
            }
            if (r.getIntervalSeconds() > 0) {
                long mod = current % r.getIntervalSeconds();
                long remaining = r.getIntervalSeconds() - mod;
                if (remaining >= 0 && remaining < nearest) {
                    nearest = remaining;
                    total = r.getIntervalSeconds();
                }
            }
        }
        return nearest == Long.MAX_VALUE ? new NextRewardInfo(0, 0) : new NextRewardInfo(nearest, total);
    }

    public void sendEnterMessage(Player player, String zoneName) {
        MessageUtils.sendStyled(player, msgEnterZone, zoneName, null);
    }

    public void sendExitMessage(Player player, String zoneName) {
        MessageUtils.sendStyled(player, msgExitZone, zoneName, null);
        MessageUtils.playSound(player, exitSound, soundVolume, soundPitch);
    }
}