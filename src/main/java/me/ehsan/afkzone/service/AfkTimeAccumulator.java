package me.ehsan.afkzone.service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Buffers per-tick AFK-time increments in memory so the per-second tick
 * loop in RewardManager never touches storage directly.
 *
 * Previously, RewardManager called storageService.addAfkTime() /
 * addZoneAfkTime() once per tracked player on every tick (every second),
 * synchronously, on the main server thread. With SQLite that meant a
 * blocking disk write per AFK player per second - fine with a couple of
 * players, a real source of tick lag once more than a handful are parked
 * at once.
 *
 * Now the tick loop only calls add() here, which is pure in-memory
 * arithmetic (a LongAdder increment) and safe to call from the main
 * thread every tick with no I/O. RewardManager periodically calls
 * drainAll() and hands the snapshot off to storage on an async thread,
 * so the actual writes are both off the main thread AND batched into one
 * write per player per flush interval instead of one per second.
 */
public class AfkTimeAccumulator {

    private final Map<UUID, LongAdder> pendingTotal = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, LongAdder>> pendingZone = new ConcurrentHashMap<>();

    /** Records one second of AFK time for a player, both total and per-zone. No I/O. */
    public void add(UUID id, String zoneName) {
        pendingTotal.computeIfAbsent(id, k -> new LongAdder()).increment();
        pendingZone.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(zoneName, k -> new LongAdder()).increment();
    }

    /** Seconds buffered for this player that haven't been flushed to storage yet. */
    public long getPendingTotal(UUID id) {
        LongAdder adder = pendingTotal.get(id);
        return adder == null ? 0L : adder.sum();
    }

    /** Seconds buffered for this player in this zone that haven't been flushed yet. */
    public long getPendingZone(UUID id, String zoneName) {
        Map<String, LongAdder> zones = pendingZone.get(id);
        if (zones == null) return 0L;
        LongAdder adder = zones.get(zoneName);
        return adder == null ? 0L : adder.sum();
    }

    /**
     * Atomically snapshots and clears everything currently buffered, returning
     * it so the caller can write it to storage. Safe to call from any thread.
     * Each player's counter is individually removed-then-summed, so an add()
     * racing with a drain never loses data - it either lands in this snapshot
     * or starts a fresh counter for the next one.
     */
    public Snapshot drainAll() {
        Map<UUID, Long> totalSnapshot = new ConcurrentHashMap<>();
        for (UUID id : pendingTotal.keySet()) {
            LongAdder adder = pendingTotal.remove(id);
            if (adder != null) {
                long value = adder.sumThenReset();
                if (value != 0) totalSnapshot.put(id, value);
            }
        }
        Map<UUID, Map<String, Long>> zoneSnapshot = new ConcurrentHashMap<>();
        for (UUID id : pendingZone.keySet()) {
            Map<String, LongAdder> zones = pendingZone.remove(id);
            if (zones == null) continue;
            Map<String, Long> flat = new ConcurrentHashMap<>();
            for (Map.Entry<String, LongAdder> e : zones.entrySet()) {
                long value = e.getValue().sumThenReset();
                if (value != 0) flat.put(e.getKey(), value);
            }
            if (!flat.isEmpty()) zoneSnapshot.put(id, flat);
        }
        return new Snapshot(totalSnapshot, zoneSnapshot);
    }

    public void clear() {
        pendingTotal.clear();
        pendingZone.clear();
    }

    public record Snapshot(Map<UUID, Long> total, Map<UUID, Map<String, Long>> zone) {
        public boolean isEmpty() {
            return total.isEmpty() && zone.isEmpty();
        }
    }
}
