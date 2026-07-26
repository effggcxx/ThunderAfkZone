package me.ehsan.afkzone.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * SQLite-based persistent storage for statistics.
 *
 * Requires the org.xerial:sqlite-jdbc driver to be present on the classpath
 * (declared as a Paper `libraries:` entry in plugin.yml). If the driver can't
 * be loaded or the connection can't be opened, every method here degrades to
 * a safe no-op/default value instead of throwing - a failed connection used
 * to cause a NullPointerException on the first call, which escaped the
 * per-tick loop in RewardManager and silently broke reward delivery for
 * every tracked player, not just this one.
 */
public class SqliteStorage implements StorageService {

    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;

    public SqliteStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), "afkzone.db");
    }

    /**
     * True once a live connection was established. Every read/write method
     * checks this first and no-ops if false, so a failed driver/connection
     * never throws a NullPointerException up into the caller.
     */
    private boolean isReady() {
        return connection != null;
    }

    @Override
    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            plugin.getDataFolder().mkdirs();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS player_stats (" +
                    "  uuid VARCHAR(36) PRIMARY KEY," +
                    "  afk_time BIGINT DEFAULT 0," +
                    "  rewards_received INT DEFAULT 0" +
                    ")"
                );
                stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS zone_stats (" +
                    "  uuid VARCHAR(36) NOT NULL," +
                    "  zone_name VARCHAR(64) NOT NULL," +
                    "  afk_time BIGINT DEFAULT 0," +
                    "  PRIMARY KEY (uuid, zone_name)" +
                    ")"
                );
            }
            plugin.getLogger().info("SQLite storage initialized: " + dbFile.getName());
        } catch (Exception e) {
            connection = null;
            plugin.getLogger().log(Level.SEVERE,
                "Failed to initialize SQLite storage - falling back to no-op storage. " +
                "AFK time and reward counts will NOT be tracked until this is fixed " +
                "(check that plugin.yml's `libraries:` entry for sqlite-jdbc loaded correctly). " +
                "Set global.storage: \"memory\" in config.yml for a working non-persistent fallback.",
                e);
        }
    }

    @Override
    public void shutdown() {
        if (!isReady()) return;
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error closing SQLite connection", e);
        }
    }

    @Override
    public long getTotalAfkTime(UUID playerId) {
        if (!isReady()) return 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT afk_time FROM player_stats WHERE uuid = ?")) {
            ps.setString(1, playerId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("afk_time");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error reading AFK time for " + playerId, e);
        }
        return 0;
    }

    @Override
    public void addAfkTime(UUID playerId, long seconds) {
        if (!isReady()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_stats (uuid, afk_time, rewards_received) VALUES (?, ?, 0) " +
                "ON CONFLICT(uuid) DO UPDATE SET afk_time = afk_time + ?")) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, seconds);
            ps.setLong(3, seconds);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error adding AFK time for " + playerId, e);
        }
    }

    @Override
    public int getTotalRewardsReceived(UUID playerId) {
        if (!isReady()) return 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT rewards_received FROM player_stats WHERE uuid = ?")) {
            ps.setString(1, playerId.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("rewards_received");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error reading rewards for " + playerId, e);
        }
        return 0;
    }

    @Override
    public void incrementRewardsReceived(UUID playerId) {
        if (!isReady()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO player_stats (uuid, afk_time, rewards_received) VALUES (?, 0, 1) " +
                "ON CONFLICT(uuid) DO UPDATE SET rewards_received = rewards_received + 1")) {
            ps.setString(1, playerId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error incrementing rewards for " + playerId, e);
        }
    }

    @Override
    public long getZoneAfkTime(UUID playerId, String zoneName) {
        if (!isReady()) return 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT afk_time FROM zone_stats WHERE uuid = ? AND zone_name = ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, zoneName);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("afk_time");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error reading zone AFK time", e);
        }
        return 0;
    }

    @Override
    public void addZoneAfkTime(UUID playerId, String zoneName, long seconds) {
        if (!isReady()) return;
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO zone_stats (uuid, zone_name, afk_time) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid, zone_name) DO UPDATE SET afk_time = afk_time + ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, zoneName);
            ps.setLong(3, seconds);
            ps.setLong(4, seconds);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error adding zone AFK time", e);
        }
    }

    @Override
    public List<Map.Entry<UUID, Long>> getTopAfkTime(int limit) {
        List<Map.Entry<UUID, Long>> results = new ArrayList<>();
        if (!isReady()) return results;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, afk_time FROM player_stats ORDER BY afk_time DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                long time = rs.getLong("afk_time");
                results.add(new AbstractMap.SimpleEntry<>(uuid, time));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error reading top AFK time", e);
        }
        return results;
    }

    @Override
    public List<Map.Entry<UUID, Integer>> getTopRewards(int limit) {
        List<Map.Entry<UUID, Integer>> results = new ArrayList<>();
        if (!isReady()) return results;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, rewards_received FROM player_stats ORDER BY rewards_received DESC LIMIT ?")) {
            ps.setInt(1, limit);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                int count = rs.getInt("rewards_received");
                results.add(new AbstractMap.SimpleEntry<>(uuid, count));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error reading top rewards", e);
        }
        return results;
    }

    @Override
    public boolean isPersistent() {
        // Only truly persistent if a live connection was actually established.
        return isReady();
    }
}