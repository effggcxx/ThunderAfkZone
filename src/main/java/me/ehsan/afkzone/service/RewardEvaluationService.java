package me.ehsan.afkzone.service;

import me.ehsan.afkzone.models.NextRewardInfo;
import me.ehsan.afkzone.models.Reward;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Evaluates which rewards are due for a player and which reward is next.
 * Keeps reward logic separate from zone management and persistence concerns.
 */
public class RewardEvaluationService {

    public Set<Reward> evaluateDueRewards(List<Reward> zoneRewards, Map<String, Integer> progress,
                                          Set<String> given, String onMultiple) {
        Set<Reward> due = new HashSet<>();
        for (Reward reward : zoneRewards) {
            if (!reward.isEnabled()) continue;

            int currentProgress = progress.getOrDefault(reward.getName(), 0);
            if (reward.getOnceAfterSeconds() > 0 && !given.contains(reward.getName())
                    && currentProgress >= reward.getOnceAfterSeconds()) {
                due.add(reward);
            }
            if (reward.getIntervalSeconds() > 0 && currentProgress > 0
                    && currentProgress % reward.getIntervalSeconds() == 0) {
                due.add(reward);
            }
        }

        if (!due.isEmpty() && "highest".equalsIgnoreCase(onMultiple)) {
            int maxPriority = due.stream().mapToInt(Reward::getPriority).max().orElse(Integer.MIN_VALUE);
            return due.stream().filter(reward -> reward.getPriority() == maxPriority).collect(Collectors.toSet());
        }

        return due;
    }

    public NextRewardInfo getNearestReward(Map<String, Integer> progress, Set<String> given, List<Reward> zoneRewards) {
        long nearest = Long.MAX_VALUE;
        long total = 0;
        for (Reward reward : zoneRewards) {
            if (!reward.isEnabled()) continue;

            int current = progress.getOrDefault(reward.getName(), 0);
            if (reward.getOnceAfterSeconds() > 0 && !given.contains(reward.getName())) {
                long remaining = reward.getOnceAfterSeconds() - current;
                if (remaining >= 0 && remaining < nearest) {
                    nearest = remaining;
                    total = reward.getOnceAfterSeconds();
                }
            }
            if (reward.getIntervalSeconds() > 0) {
                long remainder = current % reward.getIntervalSeconds();
                long remaining = reward.getIntervalSeconds() - remainder;
                if (remaining >= 0 && remaining < nearest) {
                    nearest = remaining;
                    total = reward.getIntervalSeconds();
                }
            }
        }

        return nearest == Long.MAX_VALUE ? new NextRewardInfo(0, 0) : new NextRewardInfo(nearest, total);
    }
}
