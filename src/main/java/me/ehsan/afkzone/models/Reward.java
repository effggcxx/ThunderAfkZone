package me.ehsan.afkzone.models;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Reward {

    private final String name;
    private String description;
    private int amount;
    private int intervalSeconds;
    private int onceAfterSeconds;
    private int priority;
    private boolean enabled;
    private ItemStack itemStack;

    public Reward(String name) {
        this.name = Objects.requireNonNull(name, "Reward name must not be null");
    }

    public String getName() { return name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
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
    public ItemStack getItemStack() { return itemStack; }
    public void setItemStack(ItemStack itemStack) { this.itemStack = itemStack; }

    /**
     * Saves this reward's configuration data (excluding the item stack)
     * into the given ConfigurationSection.
     */
    public void saveToConfig(ConfigurationSection section) {
        section.set("description", description);
        section.set("amount", amount);
        section.set("interval_seconds", intervalSeconds);
        section.set("once_after_seconds", onceAfterSeconds);
        section.set("priority", priority);
        section.set("enabled", enabled);
    }

    /**
     * Loads this reward's configuration data (excluding the item stack)
     * from the given ConfigurationSection.
     */
    public void loadFromConfig(ConfigurationSection section) {
        this.description = section.getString("description", "");
        this.amount = section.getInt("amount", 1);
        this.intervalSeconds = section.getInt("interval_seconds", 0);
        this.onceAfterSeconds = section.getInt("once_after_seconds", 0);
        this.priority = section.getInt("priority", 0);
        this.enabled = section.getBoolean("enabled", true);
    }

    /**
     * Serializes the item stack into a Map for storage in a YAML file.
     */
    public Map<String, Object> serializeItem() {
        if (itemStack == null) return new HashMap<>();
        return itemStack.serialize();
    }

    /**
     * Deserializes the item stack from a Map.
     */
    public static ItemStack deserializeItem(Map<String, Object> data) {
        if (data == null || data.isEmpty()) return null;
        try {
            return ItemStack.deserialize(data);
        } catch (Exception e) {
            return null;
        }
    }

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