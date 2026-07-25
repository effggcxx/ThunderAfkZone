package me.ehsan.afkzone;

import me.ehsan.afkzone.commands.AfkZoneCommand;
import me.ehsan.afkzone.listeners.ActivityListener;
import me.ehsan.afkzone.listeners.ZoneListener;
import me.ehsan.afkzone.managers.RewardManager;
import me.ehsan.afkzone.managers.ZoneManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {

    private ZoneManager zoneManager;
    private RewardManager rewardManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        // Initialize managers
        this.zoneManager = new ZoneManager(this);
        this.rewardManager = new RewardManager(this, zoneManager);

        // Load configuration
        rewardManager.loadRewards();
        rewardManager.loadGlobalConfig();

        // Register listeners
        getServer().getPluginManager().registerEvents(new ZoneListener(this, zoneManager, rewardManager), this);
        getServer().getPluginManager().registerEvents(new ActivityListener(rewardManager), this);

        // Register command
        if (getCommand("afkzone") != null) {
            getCommand("afkzone").setExecutor(new AfkZoneCommand(this, zoneManager, rewardManager));
        }

        getLogger().info("AfkZone enabled");
    }

    @Override
    public void onDisable() {
        getLogger().info("AfkZone disabled");
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }
}