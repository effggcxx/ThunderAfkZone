package me.ehsan.afkzone.models;

import java.util.Objects;

public class Reward {

    private final String name;
    private String description;
    private String executor;
    private String command;
    private String itemName;
    private int amount;
    private int intervalSeconds;
    private int onceAfterSeconds;
    private int priority;
    private boolean enabled;

    public Reward(String name) {
        this.name = Objects.requireNonNull(name, "Reward name must not be null");
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getExecutor() { return executor; }
    public void setExecutor(String executor) { this.executor = executor; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
    public int getIntervalSeconds() { return intervalSeconds; }
    public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
    public int getOnceAfterSeconds() { return onceAfterSeconds; }
    public void setOnceAfterSeconds(int onceAfterSeconds) { this.onceAfterSeconds = onceAfterSeconds; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reward reward)) return false;
        return name.equals(reward.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return "Reward{name='" + name + "', enabled=" + enabled + ", priority=" + priority + "}";
    }
}