package me.ehsan.afkzone.models;

public class Reward {

    public String name;
    public String description;
    public String executor;
    public String command;
    public String itemName;
    public int amount;
    public int intervalSeconds;
    public int onceAfterSeconds;
    public int priority;
    public boolean enabled;

    public Reward(String name) {
        this.name = name;
    }
}
