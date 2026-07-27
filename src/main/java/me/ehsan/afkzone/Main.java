package me.ehsan.afkzone;

import me.ehsan.afkzone.commands.AfkZoneCommand;
import me.ehsan.afkzone.listeners.WandListener;
import me.ehsan.afkzone.listeners.ZoneListener;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.placeholder.AfkZoneExpansion;
import me.ehsan.afkzone.service.PlayerTracker;
import me.ehsan.afkzone.service.RewardDispatcher;
import me.ehsan.afkzone.service.SpatialZoneIndex;
import me.ehsan.afkzone.service.TimerService;
import me.ehsan.afkzone.service.ZoneService;
import me.ehsan.afkzone.storage.MemoryStorage;
import me.ehsan.afkzone.storage.SqliteStorage;
import me.ehsan.afkzone.storage.StorageService;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private ZoneManager zoneManager;
    private RewardManager rewardManager;
    private SpatialZoneIndex spatialIndex;
    private PlayerTracker playerTracker;
    private TimerService timerService;
    private RewardDispatcher rewardDispatcher;
    private StorageService storageService;
    private WandListener wandListener;
    private AfkZoneExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize services
        this.spatialIndex = new SpatialZoneIndex();
        this.playerTracker = new PlayerTracker();
        this.timerService = new TimerService();

        // Storage backend, chosen by global.storage in config.yml (defaults to
        // "sqlite" here and in the shipped config, matching each other).
        // This does NOT auto-detect and fall back to memory if the SQLite
        // driver fails - that failure is handled inside SqliteStorage itself
        // (degrades to safe no-ops, logs a warning telling the admin to
        // switch to "memory" manually). Anything other than "sqlite" here
        // uses MemoryStorage.
        String storageType = getConfig().getString("global.storage", "sqlite");
        if ("sqlite".equalsIgnoreCase(storageType)) {
            this.storageService = new SqliteStorage(this);
        } else {
            this.storageService = new MemoryStorage();
        }
        storageService.initialize();

        this.rewardDispatcher = new RewardDispatcher(this, storageService);
        this.zoneManager = new ZoneManager(this, spatialIndex);
        this.rewardManager = new RewardManager(this, zoneManager, playerTracker, timerService, rewardDispatcher, storageService);

        rewardManager.loadRewards();
        rewardManager.loadGlobalConfig();

        // Single global scheduler that ticks all tracked players every second
        rewardManager.startGlobalScheduler();

        // Wand selection system
        this.wandListener = new WandListener(this);
        wandListener.loadConfig();

        // Register listeners
        getServer().getPluginManager().registerEvents(new ZoneListener(zoneManager, rewardManager), this);
        getServer().getPluginManager().registerEvents(wandListener, this);

        // Register command
        if (getCommand("afkzone") != null) {
            AfkZoneCommand cmd = new AfkZoneCommand(this, zoneManager, rewardManager);
            getCommand("afkzone").setExecutor(cmd);
            getCommand("afkzone").setTabCompleter(cmd);
        }

        // Register PlaceholderAPI expansion if available
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            this.placeholderExpansion = new AfkZoneExpansion(this, zoneManager, playerTracker, storageService, rewardManager.getRewards());
            placeholderExpansion.register();
            getLogger().info("PlaceholderAPI expansion registered");
        }

        getLogger().info("ThunderAfkZone enabled");
    }

    @Override
    public void onDisable() {
        if (rewardManager != null) {
            rewardManager.stopGlobalScheduler();
        }
        if (storageService != null) {
            storageService.shutdown();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        getLogger().info("ThunderAfkZone disabled");
    }

    /**
     * Full reload of config.yml + zones.yml.
     * Storage backend is NOT switched at runtime (requires restart).
     */
    public void reloadAll() {
        reloadConfig();

        rewardManager.loadRewards();
        rewardManager.loadGlobalConfig();

        zoneManager.reload();

        if (wandListener != null) {
            wandListener.loadConfig();
        }
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public SpatialZoneIndex getSpatialIndex() {
        return spatialIndex;
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

    public StorageService getStorageService() {
        return storageService;
    }

    public WandListener getWandListener() {
        return wandListener;
    }
}