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
 *
 * Thread safety: RewardManager flushes buffered AFK time to this class from
 * an async thread (see RewardManager.flushAfkTimeAsync), while commands like
 * /afkzone stats and /afkzone top read from it on the main thread. The
 * underlying JDBC Connection is not safe for unsynchronized concurrent use,
 * so every method here synchronizes on dbLock before touching it.
 */
public class SqliteStorage implements StorageService {

    private final JavaPlugin plugin;
    private final File dbFile;
    private final Object dbLock = new Object();
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
        synchronized (dbLock) {
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
                    stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS player_progress (" +
                        "  uuid VARCHAR(36) NOT NULL," +
                        "  reward_name VARCHAR(64) NOT NULL," +
                        "  progress INT DEFAULT 0," +
                        "  PRIMARY KEY (uuid, reward_name)" +
                        ")"
                    );
                    stmt.executeUpdate(
                        "CREATE TABLE IF NOT EXISTS player_given_once (" +
                        "  uuid VARCHAR(36) NOT NULL," +
                        "  reward_name VARCHAR(64) NOT NULL," +
                        "  PRIMARY KEY (uuid, reward_name)" +
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
    }

    @Override
    public void shutdown() {
        synchronized (dbLock) {
            if (!isReady()) return;
            try {
                if (!connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error closing SQLite connection", e);
            }
        }
    }

    @Override
    public long getTotalAfkTime(UUID playerId) {
        synchronized (dbLock) {
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
    }

    @Override
    public void addAfkTime(UUID playerId, long seconds) {
        synchronized (dbLock) {
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
    }

    @Override
    public int getTotalRewardsReceived(UUID playerId) {
        synchronized (dbLock) {
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
    }

    @Override
    public void incrementRewardsReceived(UUID playerId) {
        synchronized (dbLock) {
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
    }

    @Override
    public long getZoneAfkTime(UUID playerId, String zoneName) {
        synchronized (dbLock) {
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
    }

    @Override
    public void addZoneAfkTime(UUID playerId, String zoneName, long seconds) {
        synchronized (dbLock) {
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
    }

    @Override
    public List<Map.Entry<UUID, Long>> getTopAfkTime(int limit) {
        synchronized (dbLock) {
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
    }

    @Override
    public List<Map.Entry<UUID, Integer>> getTopRewards(int limit) {
        synchronized (dbLock) {
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
    }

    @Override
    public void savePlayerProgress(UUID playerId, Map<String, Integer> progress) {
        synchronized (dbLock) {
            if (!isReady() || progress == null) return;
            String uuid = playerId.toString();
            try {
                // Clear old progress for this player, then insert new
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM player_progress WHERE uuid = ?")) {
                    ps.setString(1, uuid);
                    ps.executeUpdate();
                }
                if (progress.isEmpty()) return;
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO player_progress (uuid, reward_name, progress) VALUES (?, ?, ?)")) {
                    for (Map.Entry<String, Integer> entry : progress.entrySet()) {
                        ps.setString(1, uuid);
                        ps.setString(2, entry.getKey());
                        ps.setInt(3, entry.getValue());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error saving progress for " + playerId, e);
            }
        }
    }

    @Override
    public Map<String, Integer> loadPlayerProgress(UUID playerId) {
        synchronized (dbLock) {
            Map<String, Integer> result = new HashMap<>();
            if (!isReady()) return result;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT reward_name, progress FROM player_progress WHERE uuid = ?")) {
                ps.setString(1, playerId.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    result.put(rs.getString("reward_name"), rs.getInt("progress"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error loading progress for " + playerId, e);
            }
            return result;
        }
    }

    @Override
    public void savePlayerGivenOnce(UUID playerId, Set<String> givenOnce) {
        synchronized (dbLock) {
            if (!isReady() || givenOnce == null) return;
            String uuid = playerId.toString();
            try {
                // Clear old data for this player, then insert new
                try (PreparedStatement ps = connection.prepareStatement(
                        "DELETE FROM player_given_once WHERE uuid = ?")) {
                    ps.setString(1, uuid);
                    ps.executeUpdate();
                }
                if (givenOnce.isEmpty()) return;
                try (PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO player_given_once (uuid, reward_name) VALUES (?, ?)")) {
                    for (String rewardName : givenOnce) {
                        ps.setString(1, uuid);
                        ps.setString(2, rewardName);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error saving given-once for " + playerId, e);
            }
        }
    }

    @Override
    public Set<String> loadPlayerGivenOnce(UUID playerId) {
        synchronized (dbLock) {
            Set<String> result = new HashSet<>();
            if (!isReady()) return result;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT reward_name FROM player_given_once WHERE uuid = ?")) {
                ps.setString(1, playerId.toString());
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    result.add(rs.getString("reward_name"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Error loading given-once for " + playerId, e);
            }
            return result;
        }
    }

    @Override
    public boolean isPersistent() {
        // Only truly persistent if a live connection was actually established.
        // No lock needed - a simple null check on a reference that's only
        // ever set once during initialize().
        return isReady();
    }
}