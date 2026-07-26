package me.ehsan.afkzone;

import me.ehsan.afkzone.commands.AfkZoneCommand;
import me.ehsan.afkzone.listeners.ActivityListener;
import me.ehsan.afkzone.listeners.ZoneListener;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import me.ehsan.afkzone.placeholder.AfkZoneExpansion;
import me.ehsan.afkzone.service.*;
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
    private ParticleService particleService;
    private AfkZoneExpansion placeholderExpansion;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize services
        this.spatialIndex = new SpatialZoneIndex();
        this.playerTracker = new PlayerTracker();
        this.timerService = new TimerService();

        // Storage (SQLite if available, otherwise in-memory)
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

        // Particle service for visual zone boundaries
        this.particleService = new ParticleService(this, spatialIndex);
        loadParticleConfig();

        // Register listeners
        getServer().getPluginManager().registerEvents(new ZoneListener(this, zoneManager, rewardManager, particleService), this);
        getServer().getPluginManager().registerEvents(new ActivityListener(rewardManager), this);

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
        if (particleService != null) {
            particleService.stopAll();
        }
        if (storageService != null) {
            storageService.shutdown();
        }
        if (placeholderExpansion != null) {
            placeholderExpansion.unregister();
        }
        getLogger().info("ThunderAfkZone disabled");
    }

  private void loadParticleConfig() {
    if (particleService == null) return;

    particleService.setEnabled(getConfig().getBoolean("global.particles.enabled", true));
    particleService.setParticleCount(getConfig().getInt("global.particles.count", 1));
    particleService.setParticleSpacing(getConfig().getDouble("global.particles.spacing", 2.0));
    particleService.setViewDistance(getConfig().getDouble("global.particles.view_distance", 48.0));
    particleService.setIntervalTicks(getConfig().getInt("global.particles.interval_ticks", 40));

    // Particle type (optional)
    String typeName = getConfig().getString("global.particles.type", "END_ROD");
    try {
        particleService.setParticle(org.bukkit.Particle.valueOf(typeName.toUpperCase(java.util.Locale.ROOT)));
    } catch (IllegalArgumentException ex) {
        getLogger().warning("Invalid particle type in config: '" + typeName + "'. Using END_ROD.");
        particleService.setParticle(org.bukkit.Particle.END_ROD);
    }
}
    /**
 * Full reload of config.yml + zones.yml + particles.
 * Safe to call from /afkzone reload.
 * Note: storage backend is NOT switched at runtime (requires restart).
 */
public void reloadAll() {
    reloadConfig();

    // Rewards + global settings
    rewardManager.loadRewards();
    rewardManager.loadGlobalConfig();

    // Zones + spatial index
    zoneManager.reload();

    // Particles
    loadParticleConfig();
    if (particleService != null) {
        particleService.restartAll();   
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

    public ParticleService getParticleService() {
        return particleService;
    }
}