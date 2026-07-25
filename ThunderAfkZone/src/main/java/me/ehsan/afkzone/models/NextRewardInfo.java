package me.ehsan.afkzone.models;

public class NextRewardInfo {

    private final long remainingSeconds;
    private final long totalSeconds;

    public NextRewardInfo(long remainingSeconds, long totalSeconds) {
        this.remainingSeconds = remainingSeconds;
        this.totalSeconds = totalSeconds;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }

    public long getTotalSeconds() {
        return totalSeconds;
    }
}