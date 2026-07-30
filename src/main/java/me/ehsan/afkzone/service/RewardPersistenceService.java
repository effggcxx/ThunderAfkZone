package me.ehsan.afkzone.service;

import me.ehsan.afkzone.Main;
import me.ehsan.afkzone.storage.StorageService;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles persistence of reward progress and once-given state for players.
 * Keeps storage concerns separate from reward orchestration.
 */
public class RewardPersistenceService {

    private final Main plugin;
    private final StorageService storageService;

    public RewardPersistenceService(Main plugin, StorageService storageService) {
        this.plugin = plugin;
        this.storageService = storageService;
    }

    public void savePlayerProgress(UUID playerId, Map<String, Integer> progress, Set<String> givenOnce) {
        if (!storageService.isPersistent()) return;
        try {
            storageService.savePlayerProgress(playerId, progress);
            storageService.savePlayerGivenOnce(playerId, givenOnce);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to save reward progress for " + playerId + ": " + ex.getMessage());
        }
    }

    public void loadPlayerProgress(UUID playerId, Map<String, Integer> progress, Set<String> givenOnce) {
        if (!storageService.isPersistent()) return;
        if (progress.isEmpty()) {
            Map<String, Integer> saved = storageService.loadPlayerProgress(playerId);
            if (!saved.isEmpty()) {
                progress.putAll(saved);
            }
        }
        if (givenOnce.isEmpty()) {
            Set<String> saved = storageService.loadPlayerGivenOnce(playerId);
            if (!saved.isEmpty()) {
                givenOnce.addAll(saved);
            }
        }
    }
}
