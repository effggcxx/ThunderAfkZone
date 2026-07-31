package me.ehsan.afkzone.managers;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.config.MessagesConfig;
import me.ehsan.afkzone.models.NextRewardInfo;
import me.ehsan.afkzone.models.Reward;
import me.ehsan.afkzone.service.*;
import me.ehsan.afkzone.storage.StorageService;
import me.ehsan.afkzone.util.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages rewards and player tracking with a single global scheduler.
 * Rewards are stored as individual YAML files in the rewards/ folder.
 */
public class RewardManager {

    private final Main plugin;
    private final ZoneService zoneService;
    private final PlayerTracker playerTracker;
    private final TimerService timerService;
    private final RewardDispatcher rewardDispatcher;
    private final RewardEvaluationService rewardEvaluationService;
    private final RewardPersistenceService rewardPersistenceService;
    private final StorageService storageService;
    private final MessagesConfig messagesConfig;

    private Map<String, Reward> rewards = new HashMap<>();

    private BukkitTask globalTask;

    // Counter for periodic progress persistence (every 30 seconds)
    private int progressSaveCounter = 0;
    private static final int PROGRESS_SAVE_INTERVAL = 30; // seconds

    private String onMultiple = "all";
    private boolean resetProgressOnLeave = true;
    /** Seconds of inactivity required before reward progress ticks. 0 = no threshold (presence only). */
    private int afkThresholdSeconds = 0;
    private Sound enterSound = null;
    private Sound exitSound = null;
    private Sound rewardSound = null;
    private float soundVolume = 1.0f;
    private float soundPitch = 1.0f;

    public RewardManager(Main plugin, ZoneService zoneService, PlayerTracker playerTracker,
                         TimerService timerService, RewardDispatcher rewardDispatcher,
                         RewardEvaluationService rewardEvaluationService,
                         RewardPersistenceService rewardPersistenceService,
                         StorageService storageService, MessagesConfig messagesConfig) {
        this.plugin = plugin;
        this.zoneService = zoneService;
        this.playerTracker = playerTracker;
        this.timerService = timerService;
        this.rewardDispatcher = rewardDispatcher;
        this.rewardEvaluationService = rewardEvaluationService;
        this.rewardPersistenceService = rewardPersistenceService;
        this.storageService = storageService;
        this.messagesConfig = messagesConfig;
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
    // Rewards folder
    // -------------------------------------------------------------------------

    /**
     * Returns the rewards data folder, creating it if it doesn't exist.
     */
    public File getRewardsFolder() {
        File folder = new File(plugin.getDataFolder(), "rewards");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    /**
     * Returns the file for a given reward name.
     */
    public File getRewardFile(String name) {
        return new File(getRewardsFolder(), name.toLowerCase(Locale.ROOT) + ".yml");
    }

    // -------------------------------------------------------------------------
    // Configuration loading
    // -------------------------------------------------------------------------

    public void loadRewards() {
        rewards.clear();
        File folder = getRewardsFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().info("No reward files found in rewards/ folder");
            return;
        }
        int loaded = 0;
        int warnings = 0;
        for (File file : files) {
            String fileName = file.getName();
            String rewardName = fileName.substring(0, fileName.length() - 4); // remove .yml
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            // Check for empty/malformed reward file
            if (config.getKeys(false).isEmpty()) {
                plugin.getLogger().warning("Reward file '" + fileName + "' is empty or malformed - skipping");
                warnings++;
                continue;
            }

            Reward r = new Reward(rewardName);
            r.loadFromConfig(config);

            // Validate amount
            if (r.getAmount() < 1) {
                plugin.getLogger().warning("Reward '" + rewardName + "' has invalid amount: " + r.getAmount() + " (must be >= 1). Setting to 1.");
                r.setAmount(1);
                warnings++;
            }

            // Validate interval
            if (r.getIntervalSeconds() < 0) {
                plugin.getLogger().warning("Reward '" + rewardName + "' has negative interval_seconds: " + r.getIntervalSeconds() + ". Setting to 0.");
                r.setIntervalSeconds(0);
                warnings++;
            }

            // Validate once_after
            if (r.getOnceAfterSeconds() < 0) {
                plugin.getLogger().warning("Reward '" + rewardName + "' has negative once_after_seconds: " + r.getOnceAfterSeconds() + ". Setting to 0.");
                r.setOnceAfterSeconds(0);
                warnings++;
            }

            // Validate priority
            if (r.getPriority() < 0) {
                plugin.getLogger().warning("Reward '" + rewardName + "' has negative priority: " + r.getPriority() + ". Setting to 0.");
                r.setPriority(0);
                warnings++;
            }

            // Load ItemStack via Bukkit's ConfigurationSerializable path (not manual
            // serialize + getValues(true), which flattens nested meta and corrupts items).
            if (config.contains("item")) {
                ItemStack item = config.getItemStack("item");
                if (item == null) {
                    plugin.getLogger().warning("Reward '" + rewardName + "' has corrupted item data in the 'item' section - reward will not give any item.");
                    warnings++;
                }
                r.setItemStack(item);
            } else {
                plugin.getLogger().warning("Reward '" + rewardName + "' has no 'item' section. Use /afkzone reward save " + rewardName + " while holding an item to fix this.");
                warnings++;
            }

            // Warn if both interval and once_after are 0 (reward never triggers automatically)
            if (r.getIntervalSeconds() == 0 && r.getOnceAfterSeconds() == 0) {
                plugin.getLogger().warning("Reward '" + rewardName + "' has both interval_seconds=0 and once_after_seconds=0. It will never trigger automatically (only via /afkzone reward give).");
            }

            rewards.put(rewardName, r);
            loaded++;
        }
        plugin.getLogger().info("Loaded " + loaded + " rewards from rewards/ folder" + (warnings > 0 ? " (" + warnings + " warnings)" : ""));
    }

    /**
     * Saves a reward to its individual YAML file in the rewards/ folder.
     * Includes the serialized item stack data.
     */
    public void saveReward(Reward reward) {
        File file = getRewardFile(reward.getName());
        YamlConfiguration config = new YamlConfiguration();

        // Save config fields
        reward.saveToConfig(config);

        // Save ItemStack natively — preserves nested meta/NBT across restarts
        if (reward.getItemStack() != null) {
            config.set("item", reward.getItemStack());
        }

        try {
            config.save(file);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save reward '" + reward.getName() + "': " + e.getMessage());
        }
    }

    /**
     * Deletes a reward file from the rewards/ folder and removes it from memory.
     */
    public boolean deleteReward(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        // Try both original and lowercased keys in memory
        rewards.remove(name);
        rewards.remove(key);
        File file = getRewardFile(name);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    /**
     * Enables or disables a reward and persists the change.
     */
    public boolean setRewardEnabled(String name, boolean enabled) {
        Reward r = rewards.get(name);
        if (r == null) {
            // Case-insensitive fallback
            for (Map.Entry<String, Reward> e : rewards.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    r = e.getValue();
                    break;
                }
            }
        }
        if (r == null) return false;
        r.setEnabled(enabled);
        saveReward(r);
        return true;
    }

    /**
     * Creates or updates a reward from an item in hand, saving it to the rewards/ folder.
     */
    public Reward saveRewardFromItem(String name, ItemStack item, String description,
                                      int amount, int intervalSeconds, int onceAfterSeconds,
                                      int priority, boolean enabled) {
        Reward reward = new Reward(name);
        reward.setDescription(description != null ? description : "");
        reward.setAmount(amount);
        reward.setIntervalSeconds(intervalSeconds);
        reward.setOnceAfterSeconds(onceAfterSeconds);
        reward.setPriority(priority);
        reward.setEnabled(enabled);
        reward.setItemStack(item.clone());
        // Set the amount on the item stack to match the configured amount
        reward.getItemStack().setAmount(amount);

        // Save to file
        saveReward(reward);
        // Add to in-memory map
        rewards.put(name, reward);
        return reward;
    }

    public void loadGlobalConfig() {
        FileConfiguration cfg = plugin.getConfig();

        // Validate on_multiple
        String rawOnMultiple = cfg.getString("global.on_multiple", "all");
        if (!"all".equalsIgnoreCase(rawOnMultiple) && !"highest".equalsIgnoreCase(rawOnMultiple)) {
            plugin.getLogger().warning("Invalid global.on_multiple value: '" + rawOnMultiple + "'. Expected 'all' or 'highest'. Using default: 'all'.");
            this.onMultiple = "all";
        } else {
            this.onMultiple = rawOnMultiple;
        }

        this.resetProgressOnLeave = cfg.getBoolean("global.reset_progress_on_leave", true);

        // Validate afk_threshold_seconds
        int rawThreshold = cfg.getInt("global.afk_threshold_seconds", 0);
        if (rawThreshold < 0) {
            plugin.getLogger().warning("Invalid global.afk_threshold_seconds: " + rawThreshold + " (negative). Setting to 0.");
            this.afkThresholdSeconds = 0;
        } else {
            this.afkThresholdSeconds = rawThreshold;
        }

        // Validate storage backend
        String storageType = cfg.getString("global.storage", "sqlite");
        if (!"sqlite".equalsIgnoreCase(storageType) && !"memory".equalsIgnoreCase(storageType)) {
            plugin.getLogger().warning("Invalid global.storage: '" + storageType + "'. Expected 'sqlite' or 'memory'. Using default: 'sqlite'.");
        }
        this.enterSound = MessageUtils.parseSound(cfg.getString("global.enter_sound", "ENTITY_PLAYER_LEVELUP"),
                plugin.getLogger(), "global.enter_sound");
        this.exitSound = MessageUtils.parseSound(cfg.getString("global.exit_sound", "ENTITY_ITEM_BREAK"),
                plugin.getLogger(), "global.exit_sound");
        this.soundVolume = (float) cfg.getDouble("global.sound_volume", 1.0);
        this.soundPitch = (float) cfg.getDouble("global.sound_pitch", 1.0);

        // Timer config from messages.yml
        if (messagesConfig != null && messagesConfig.getTimer() != null) {
            timerService.setTimerConfig(messagesConfig.getTimer());
        }

        // Messages config from messages.yml
        if (messagesConfig != null) {
            if (messagesConfig.getRewardReceived() != null) {
                rewardDispatcher.setMsgRewardReceived(messagesConfig.getRewardReceived());
            }
            if (messagesConfig.getRewardFailed() != null) {
                rewardDispatcher.setMsgRewardFailed(messagesConfig.getRewardFailed());
            }
            if (messagesConfig.getInventoryFull() != null) {
                rewardDispatcher.setMsgInventoryFull(messagesConfig.getInventoryFull());
            }
        }

        // Reward dispatcher sound config (still from config.yml)
        this.rewardSound = MessageUtils.parseSound(cfg.getString("global.reward_sound", "ENTITY_EXPERIENCE_ORB_PICKUP"),
                plugin.getLogger(), "global.reward_sound");
        rewardDispatcher.setRewardSound(this.rewardSound);
        rewardDispatcher.setSoundVolume(this.soundVolume);
        rewardDispatcher.setSoundPitch(this.soundPitch);
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
        // Save all tracked players' progress before shutdown
        if (!resetProgressOnLeave && storageService.isPersistent()) {
            for (UUID id : playerTracker.getTrackedPlayers().keySet()) {
                savePlayerProgress(id);
            }
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
            try {
                tickOnePlayer(id);
            } catch (Exception ex) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE,
                        "Error ticking AFK player " + id + " - skipping this player for this tick, others unaffected", ex);
            }
        }
    }

    private void tickOnePlayer(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) {
            // Save progress before clearing if we're preserving it
            if (!resetProgressOnLeave) {
                savePlayerProgress(id);
            }
            playerTracker.stopTrackingSilent(id, resetProgressOnLeave);
            timerService.removePlayer(player);
            return;
        }

        String zoneName = playerTracker.getPlayerZone(id);
        if (zoneName == null) return;

        // Verify player is still inside the zone
        String currentZone = zoneService.findZoneForLocation(player.getLocation());
        if (currentZone == null || !currentZone.equals(zoneName)) {
            // Save progress before leaving if we're preserving it
            if (!resetProgressOnLeave) {
                savePlayerProgress(id);
            }
            Sound effectiveExitSound = resolveZoneSound(zoneName, "exit_sound", exitSound);
            playerTracker.stopTracking(id, messagesConfig.getExitZone(), effectiveExitSound, soundVolume, soundPitch, resetProgressOnLeave);
            timerService.removePlayer(player);
            executeExitCommands(player, zoneName);
            return;
        }

        // Only count time if the player is considered AFK (per-zone override supported).
        // threshold 0 = no idle requirement (presence alone is enough).
        int effectiveThreshold = zoneService.getZoneConfigInt(zoneName, "afk_threshold_seconds", afkThresholdSeconds);
        if (effectiveThreshold > 0) {
            long last = playerTracker.getLastActiveTime(id);
            if ((System.currentTimeMillis() - last) < (effectiveThreshold * 1000L)) {
                return;
            }
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

        // Current-session counter
        playerTracker.incrementSession(id);

        // Track time in storage
        try {
            storageService.addAfkTime(id, 1);
            storageService.addZoneAfkTime(id, zoneName, 1);
        } catch (Exception ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Storage error tracking AFK time for " + id + " - continuing without stats for this tick", ex);
        }

        // Collect due rewards
        Set<Reward> due = rewardEvaluationService.evaluateDueRewards(zoneRewards, prog, given, onMultiple);
        if (!due.isEmpty()) {
            Sound effectiveRewardSound = resolveZoneSound(zoneName, "reward_sound", rewardSound);
            for (Reward r : due) {
                rewardDispatcher.giveRewardToPlayer(r, player, effectiveRewardSound);
                if (r.getOnceAfterSeconds() > 0) {
                    given.add(r.getName());
                }
            }
        }

        // Periodic progress persistence (every 30 seconds) for crash safety
        if (!resetProgressOnLeave) {
            progressSaveCounter++;
            if (progressSaveCounter >= PROGRESS_SAVE_INTERVAL) {
                progressSaveCounter = 0;
                savePlayerProgress(id);
            }
        }

        // Update timer display
        boolean timerEnabledForZone = zoneService.getZoneConfigBoolean(zoneName, "timer.enabled", timerService.isEnabled());
        NextRewardInfo info = rewardEvaluationService.getNearestReward(prog, given, zoneRewards);
        if (timerEnabledForZone && info.getRemainingSeconds() > 0) {
            timerService.sendTimer(player, info.getRemainingSeconds(), info.getTotalSeconds(), zoneName);
        }
    }

    // -------------------------------------------------------------------------
    // Player enter / leave
    // -------------------------------------------------------------------------

    public void startTrackingPlayer(Player player, String zoneName) {
        // If not resetting progress, try to load persisted progress from storage
        if (!resetProgressOnLeave) {
            loadPlayerProgress(player.getUniqueId());
        }
        Sound effectiveEnterSound = resolveZoneSound(zoneName, "enter_sound", enterSound);
        playerTracker.startTracking(player, zoneName, effectiveEnterSound, soundVolume, soundPitch,
                messagesConfig.getEnterZone(), resetProgressOnLeave);
        executeEntryCommands(player, zoneName);
    }

    public void stopTrackingPlayer(UUID id) {
        // Save progress before stopping if we're preserving it
        if (!resetProgressOnLeave) {
            savePlayerProgress(id);
        }
        Player player = Bukkit.getPlayer(id);
        String zone = playerTracker.getPlayerZone(id);
        Sound effectiveExitSound = resolveZoneSound(zone, "exit_sound", exitSound);
        playerTracker.stopTracking(id, messagesConfig.getExitZone(), effectiveExitSound, soundVolume, soundPitch, resetProgressOnLeave);
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

    /**
     * Records player activity so the AFK threshold timer resets.
     * Called from ZoneListener on move/chat/command/interact.
     */
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
    // Progress persistence
    // -------------------------------------------------------------------------

    /**
     * Saves a player's reward progress and once-given set to persistent storage.
     */
    private void savePlayerProgress(UUID id) {
        rewardPersistenceService.savePlayerProgress(id, playerTracker.getProgress(id), playerTracker.getGivenOnce(id));
    }

    /**
     * Loads a player's reward progress and once-given set from persistent storage.
     * Only loads if the player has no existing progress in memory (first time this session).
     */
    private void loadPlayerProgress(UUID id) {
        rewardPersistenceService.loadPlayerProgress(id, playerTracker.getProgress(id), playerTracker.getGivenOnce(id));
    }

    // -------------------------------------------------------------------------
    // Reward helpers (public so PlaceholderAPI expansion can delegate)
    // -------------------------------------------------------------------------

    /**
     * Returns the list of enabled rewards that apply to the given zone.
     * If the zone has no reward restrictions, all enabled global rewards are returned.
     */
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

    public NextRewardInfo getNearestReward(Map<String, Integer> prog, Set<String> given, List<Reward> zoneRewards) {
        return rewardEvaluationService.getNearestReward(prog, given, zoneRewards);
    }

    public void sendEnterMessage(Player player, String zoneName) {
        MessageUtils.sendStyled(player, messagesConfig.getEnterZone(), zoneName, null);
    }

    public void sendExitMessage(Player player, String zoneName) {
        MessageUtils.sendStyled(player, messagesConfig.getExitZone(), zoneName, null);
        MessageUtils.playSound(player, resolveZoneSound(zoneName, "exit_sound", exitSound), soundVolume, soundPitch);
    }

    // -------------------------------------------------------------------------
    // Per-zone config override resolution
    // -------------------------------------------------------------------------

    private Sound resolveZoneSound(String zoneName, String key, Sound globalDefault) {
        if (zoneName == null) return globalDefault;
        String override = zoneService.getZoneConfigString(zoneName, key, null);
        if (override == null || override.isBlank()) return globalDefault;
        if (override.equalsIgnoreCase("none")) return null;
        Sound parsed = MessageUtils.parseSound(override, plugin.getLogger(), "zones." + zoneName + "." + key);
        return parsed != null ? parsed : globalDefault;
    }
}
