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

        this.zoneManager = new ZoneManager(this);
        this.rewardManager = new RewardManager(this, zoneManager);

        rewardManager.loadRewards();
        rewardManager.loadGlobalConfig();

        // Single global scheduler that ticks all tracked players every second
        rewardManager.startGlobalScheduler();

        getServer().getPluginManager().registerEvents(new ZoneListener(this, zoneManager, rewardManager), this);
        getServer().getPluginManager().registerEvents(new ActivityListener(rewardManager), this);

        if (getCommand("afkzone") != null) {
            getCommand("afkzone").setExecutor(new AfkZoneCommand(this, zoneManager, rewardManager));
        }

        getLogger().info("ThunderAfkZone enabled");
    }

    @Override
    public void onDisable() {
        if (rewardManager != null) {
            rewardManager.stopGlobalScheduler();
        }
        getLogger().info("ThunderAfkZone disabled");
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }
}
