package me.ehsan.afkzone.storage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory fallback storage. Data is lost on server restart.
 */
public class MemoryStorage implements StorageService {

    private final Map<UUID, Long> afkTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> rewardsReceived = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> zoneAfkTime = new ConcurrentHashMap<>();

    @Override
    public void initialize() {
        // Nothing to do for in-memory
    }

    @Override
    public void shutdown() {
        afkTime.clear();
        rewardsReceived.clear();
        zoneAfkTime.clear();
    }

    @Override
    public long getTotalAfkTime(UUID playerId) {
        return afkTime.getOrDefault(playerId, 0L);
    }

    @Override
    public void addAfkTime(UUID playerId, long seconds) {
        afkTime.merge(playerId, seconds, Long::sum);
    }

    @Override
    public int getTotalRewardsReceived(UUID playerId) {
        return rewardsReceived.getOrDefault(playerId, 0);
    }

    @Override
    public void incrementRewardsReceived(UUID playerId) {
        rewardsReceived.merge(playerId, 1, Integer::sum);
    }

    @Override
    public long getZoneAfkTime(UUID playerId, String zoneName) {
        Map<String, Long> zones = zoneAfkTime.get(playerId);
        return zones == null ? 0L : zones.getOrDefault(zoneName, 0L);
    }

    @Override
    public void addZoneAfkTime(UUID playerId, String zoneName, long seconds) {
        zoneAfkTime.computeIfAbsent(playerId, k -> new ConcurrentHashMap<>())
                .merge(zoneName, seconds, Long::sum);
    }

    @Override
    public List<Map.Entry<UUID, Long>> getTopAfkTime(int limit) {
        return afkTime.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map.Entry<UUID, Integer>> getTopRewards(int limit) {
        return rewardsReceived.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isPersistent() {
        return false;
    }
}