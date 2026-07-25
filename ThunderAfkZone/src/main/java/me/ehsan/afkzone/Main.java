package me.ehsan.afkzone;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

public class Main extends JavaPlugin {
	private File zonesFile;
	private FileConfiguration zonesConfig;
    
	private Map<String, Reward> rewards = new HashMap<>();

	private static class Reward {
		String name;
		String description;
		String executor; // console | itemedit | give
		String command; // fallback console command (optional)
		String itemName; // item identifier or award name
		int amount;
		int intervalSeconds;
		int onceAfterSeconds;
		int priority;

		Reward(String name) {
			this.name = name;
		}
	}

	// runtime tracking (moved out of Reward)
	private Map<UUID, BukkitTask> playerTasks = new ConcurrentHashMap<>();
	private Map<UUID, Map<String, Integer>> playerProgress = new ConcurrentHashMap<>();
	private Map<UUID, Set<String>> playerGivenOnce = new ConcurrentHashMap<>();
	private Map<UUID, String> playerZone = new ConcurrentHashMap<>();
	private String onMultiple = "all";
	private Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
	private int afkThresholdSeconds = 60;

	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadRewards();
		this.onMultiple = getConfig().getString("global.on_multiple", "all");
		this.afkThresholdSeconds = getConfig().getInt("global.afk_threshold_seconds", 60);
		getServer().getPluginManager().registerEvents(new ZoneListener(), this);
		getServer().getPluginManager().registerEvents(new ActivityListener(), this);
		loadZonesFile();
		if (getCommand("afkzone") != null) getCommand("afkzone").setExecutor(this);
		getLogger().info("AfkZone enabled");
	}

	private void loadRewards() {
		rewards.clear();
		FileConfiguration cfg = getConfig();
		if (!cfg.isConfigurationSection("rewards")) return;
		for (String key : cfg.getConfigurationSection("rewards").getKeys(false)) {
			String path = "rewards." + key;
			Reward r = new Reward(key);
			r.description = cfg.getString(path + ".description", "");
			r.executor = cfg.getString(path + ".executor", "console");
			// new fields
			r.itemName = cfg.getString(path + ".item", cfg.getString(path + ".command", ""));
			r.amount = cfg.getInt(path + ".amount", 1);
			// keep fallback console command field
			r.command = cfg.getString(path + ".command", "");
			r.intervalSeconds = cfg.getInt(path + ".interval_seconds", 0);
			r.onceAfterSeconds = cfg.getInt(path + ".once_after_seconds", 0);
			r.priority = cfg.getInt(path + ".priority", 0);
			rewards.put(key, r);
		}
		getLogger().info("Loaded " + rewards.size() + " rewards");
	}

	private void giveRewardToPlayer(Reward r, Player player) {
		if (r == null || player == null) return;
		// If executor is console and a raw command is provided, run that (with {player} placeholder)
		if ((r.executor == null || r.executor.equalsIgnoreCase("console")) && r.command != null && !r.command.isEmpty()) {
			String consoleCmd = r.command.replace("{player}", player.getName()).replace("{award}", r.name);
			getServer().dispatchCommand(getServer().getConsoleSender(), consoleCmd);
			player.sendMessage("You received reward: " + r.name);
			return;
		}
		try {
			String ex = r.executor == null ? "console" : r.executor.toLowerCase();
			switch (ex) {
				case "itemedit": {
					String dispatch = "si give " + player.getName() + " " + r.itemName + " " + r.amount;
					getServer().dispatchCommand(getServer().getConsoleSender(), dispatch);
					break;
				}
				case "item":
				case "itemadder": {
					// legacy support
					String dispatch = "itemadder give " + player.getName() + " " + r.itemName + " " + r.amount;
					getServer().dispatchCommand(getServer().getConsoleSender(), dispatch);
					break;
				}
				case "vanilla":
				case "give": {
					// vanilla give: give <player> <command>
					String giveCmd = "give " + player.getName() + " " + r.itemName + " " + r.amount;
					getServer().dispatchCommand(getServer().getConsoleSender(), giveCmd);
					break;
				}
				default: {
					// run arbitrary console command
					if (r.command != null && !r.command.isEmpty()) {
						String fallback = r.command.replace("{player}", player.getName()).replace("{award}", r.name);
						getServer().dispatchCommand(getServer().getConsoleSender(), fallback);
					}
					break;
				}
			}
			player.sendMessage("You received reward: " + r.name);
		} catch (Exception ex) {
			getLogger().severe("Failed to give reward " + r.name + ": " + ex.getMessage());
		}
	}

	private void loadZonesFile() {
		zonesFile = new File(getDataFolder(), "zones.yml");
		if (!zonesFile.exists()) {
			try {
				getDataFolder().mkdirs();
				zonesFile.createNewFile();
			} catch (Exception e) {
				getLogger().severe("Could not create zones.yml: " + e.getMessage());
			}
		}
		zonesConfig = YamlConfiguration.loadConfiguration(zonesFile);
	}

	private void saveZonesFile() {
		try {
			zonesConfig.save(zonesFile);
		} catch (Exception e) {
			getLogger().severe("Could not save zones.yml: " + e.getMessage());
		}
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if (!command.getName().equalsIgnoreCase("afkzone")) return false;
		if (args == null || args.length == 0) {
			sender.sendMessage("Usage: /afkzone create <name> | list | remove <name>");
			return true;
		}
		String sub = args[0].toLowerCase();
		switch (sub) {
		case "reload": {
			if (!sender.hasPermission("afkzone.reload")) {
				sender.sendMessage("You don't have permission to reload AfkZone.");
				return true;
			}
			reloadConfig();
			loadRewards();
			this.onMultiple = getConfig().getString("global.on_multiple", "all");
			this.afkThresholdSeconds = getConfig().getInt("global.afk_threshold_seconds", 60);
			loadZonesFile();
			sender.sendMessage("AfkZone configuration reloaded.");
			return true;
		}
			case "reward": {
				if (args.length < 2) {
					sender.sendMessage("Usage: /afkzone reward give <reward> [player] | list");
					return true;
				}
				String act = args[1].toLowerCase();
				if (act.equals("list")) {
					if (rewards.isEmpty()) {
						sender.sendMessage("No rewards configured.");
						return true;
					}
					if (!sender.hasPermission("afkzone.reward.list")) {
						sender.sendMessage("You don't have permission to list rewards.");
						return true;
					}
					sender.sendMessage("Rewards:");
					for (Reward r : rewards.values()) {
						sender.sendMessage("- " + r.name + " (priority=" + r.priority + ") - " + r.description);
					}
					return true;
				} else if (act.equals("give")) {
					if (args.length < 3) {
						sender.sendMessage("Usage: /afkzone reward give <reward> [player]");
						return true;
					}
					String rewardName = args[2];
					Reward r = rewards.get(rewardName);
					if (r == null) {
						sender.sendMessage("Unknown reward: " + rewardName);
						return true;
					}
					Player target = null;
					if (args.length >= 4) {
						String p = args[3];
						target = getServer().getPlayerExact(p);
						if (target == null) {
							sender.sendMessage("Player not found: " + p);
							return true;
						}
					} else {
						if (sender instanceof Player) target = (Player) sender;
					}
					if (!sender.hasPermission("afkzone.reward.give")) {
						sender.sendMessage("You don't have permission to give rewards.");
						return true;
					}
					if (target == null) {
						sender.sendMessage("No target player specified and console cannot be target.");
						return true;
					}
					giveRewardToPlayer(r, target);
					return true;
				}
				return true;
			}
			case "create": {
				if (!(sender instanceof Player)) {
					sender.sendMessage("Only players can create zones");
					return true;
				}
				if (!sender.hasPermission("afkzone.create")) {
					sender.sendMessage("You don't have permission to create zones.");
					return true;
				}
				if (args.length < 2) {
					sender.sendMessage("Usage: /afkzone create <name>");
					return true;
				}
				Player player = (Player) sender;
				String name = args[1];
				createZoneFromWorldEditSelection(player, name);
				return true;
			}
			case "list": {
				listZones(sender);
				return true;
			}
			case "remove":
			case "delete": {
				if (args.length < 2) {
					sender.sendMessage("Usage: /afkzone remove <name>");
					return true;
				}
				removeZone(sender, args[1]);
				return true;
			}
			default: {
				sender.sendMessage("Usage: /afkzone create <name> | list | remove <name>");
				return true;
			}
		}
	}

	private void listZones(CommandSender sender) {
		if (zonesConfig == null) {
			sender.sendMessage("No zones configured.");
			return;
		}
		if (!zonesConfig.isConfigurationSection("zones")) {
			sender.sendMessage("No zones configured.");
			return;
		}
		for (String key : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
			String path = "zones." + key;
			String world = zonesConfig.getString(path + ".world", "unknown");
			int x1 = zonesConfig.getInt(path + ".x1", 0);
			int y1 = zonesConfig.getInt(path + ".y1", 0);
			int z1 = zonesConfig.getInt(path + ".z1", 0);
			int x2 = zonesConfig.getInt(path + ".x2", 0);
			int y2 = zonesConfig.getInt(path + ".y2", 0);
			int z2 = zonesConfig.getInt(path + ".z2", 0);
			sender.sendMessage(key + ": " + world + " (" + x1 + "," + y1 + "," + z1 + ") -> (" + x2 + "," + y2 + "," + z2 + ")");
		}
	}

	private String findZoneForLocation(Location loc) {
		if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones")) return null;
		for (String key : zonesConfig.getConfigurationSection("zones").getKeys(false)) {
			String path = "zones." + key;
			String world = zonesConfig.getString(path + ".world", "");
			if (!loc.getWorld().getName().equals(world)) continue;
			int x1 = zonesConfig.getInt(path + ".x1", Integer.MIN_VALUE);
			int y1 = zonesConfig.getInt(path + ".y1", Integer.MIN_VALUE);
			int z1 = zonesConfig.getInt(path + ".z1", Integer.MIN_VALUE);
			int x2 = zonesConfig.getInt(path + ".x2", Integer.MAX_VALUE);
			int y2 = zonesConfig.getInt(path + ".y2", Integer.MAX_VALUE);
			int z2 = zonesConfig.getInt(path + ".z2", Integer.MAX_VALUE);
			int x = loc.getBlockX();
			int y = loc.getBlockY();
			int z = loc.getBlockZ();
			if (x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2) return key;
		}
		return null;
	}

	private void startTrackingPlayer(final Player player, final String zoneName) {
		UUID id = player.getUniqueId();
		stopTrackingPlayer(id);
		playerZone.put(id, zoneName);
		playerProgress.put(id, new ConcurrentHashMap<>());
		playerGivenOnce.put(id, ConcurrentHashMap.newKeySet());
		BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
			if (!player.isOnline()) {
				stopTrackingPlayer(id);
				return;
			}
			String currentZone = findZoneForLocation(player.getLocation());
			if (currentZone == null || !currentZone.equals(zoneName)) {
				stopTrackingPlayer(id);
				player.sendMessage("You left AFK zone: " + zoneName);
				return;
			}
			Map<String, Integer> prog = playerProgress.get(id);
			Set<String> given = playerGivenOnce.get(id);
			if (prog == null) return;
			// increment counters only if player is AFK
			long last = lastActive.getOrDefault(id, System.currentTimeMillis());
			if ((System.currentTimeMillis() - last) < (afkThresholdSeconds * 1000L)) {
				return; // not AFK yet
			}
			for (Reward r : rewards.values()) {
				prog.putIfAbsent(r.name, 0);
				prog.put(r.name, prog.get(r.name) + 1);
			}
			// collect due rewards
			Set<Reward> due = new HashSet<>();
			for (Reward r : rewards.values()) {
				int t = prog.getOrDefault(r.name, 0);
				if (r.onceAfterSeconds > 0 && !given.contains(r.name) && t >= r.onceAfterSeconds) due.add(r);
				if (r.intervalSeconds > 0 && r.intervalSeconds > 0 && t > 0 && t % r.intervalSeconds == 0) due.add(r);
			}
			if (!due.isEmpty()) {
				if ("highest".equalsIgnoreCase(onMultiple)) {
					int max = due.stream().mapToInt(x -> x.priority).max().orElse(Integer.MIN_VALUE);
					due = due.stream().filter(x -> x.priority == max).collect(Collectors.toSet());
				}
				for (Reward r : due) {
					giveRewardToPlayer(r, player);
					if (r.onceAfterSeconds > 0) given.add(r.name);
				}
			}
		}, 20L, 20L);
		playerTasks.put(id, task);
		player.sendMessage("Entered AFK zone: " + zoneName);
	}

	private void stopTrackingPlayer(UUID id) {
		BukkitTask t = playerTasks.remove(id);
		if (t != null) t.cancel();
		playerProgress.remove(id);
		playerGivenOnce.remove(id);
		playerZone.remove(id);
	}

	private class ZoneListener implements Listener {
		@EventHandler
		public void onPlayerMove(PlayerMoveEvent e) {
			if (e.getFrom().getBlockX() == e.getTo().getBlockX() && e.getFrom().getBlockY() == e.getTo().getBlockY() && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
			Player p = e.getPlayer();
			String zone = findZoneForLocation(p.getLocation());
			UUID id = p.getUniqueId();
			String prev = playerZone.get(id);
			if (zone != null && (prev == null || !prev.equals(zone))) {
				startTrackingPlayer(p, zone);
			} else if (zone == null && prev != null) {
				stopTrackingPlayer(id);
				p.sendMessage("You left AFK zone: " + prev);
			}
		}

		@EventHandler
		public void onQuit(PlayerQuitEvent e) {
			stopTrackingPlayer(e.getPlayer().getUniqueId());
		}
	}

	private class ActivityListener implements Listener {
		private void markActive(Player p) {
			lastActive.put(p.getUniqueId(), System.currentTimeMillis());
		}

		@EventHandler
		public void onPlayerInteract(PlayerInteractEvent e) {
			markActive(e.getPlayer());
		}

		@EventHandler
		public void onChat(AsyncPlayerChatEvent e) {
			markActive(e.getPlayer());
		}

		@EventHandler
		public void onCommand(PlayerCommandPreprocessEvent e) {
			markActive(e.getPlayer());
		}
	}

	private void removeZone(CommandSender sender, String name) {
		if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones") || !zonesConfig.isSet("zones." + name)) {
			sender.sendMessage("Zone '" + name + "' does not exist.");
			return;
		}
		zonesConfig.set("zones." + name, null);
		saveZonesFile();
		sender.sendMessage("Zone '" + name + "' removed.");
	}

	@SuppressWarnings({"unchecked","rawtypes"})
	private void createZoneFromWorldEditSelection(Player player, String name) {
		Plugin wePlugin = getServer().getPluginManager().getPlugin("WorldEdit");
		if (wePlugin == null) {
			player.sendMessage("WorldEdit is not installed on this server.");
			return;
		}

		try {
			Method getSelection = wePlugin.getClass().getMethod("getSelection", Player.class);
			Object selection = getSelection.invoke(wePlugin, player);
			if (selection == null) {
				player.sendMessage("You must make a WorldEdit selection with the wand first.");
				return;
			}

			// Try common Selection API: getMinimumPoint/getMaximumPoint returning Bukkit Location
			Location minLoc = null;
			Location maxLoc = null;
			try {
				Method getMinimumPoint = selection.getClass().getMethod("getMinimumPoint");
				Method getMaximumPoint = selection.getClass().getMethod("getMaximumPoint");
				Object min = getMinimumPoint.invoke(selection);
				Object max = getMaximumPoint.invoke(selection);
				if (min instanceof Location && max instanceof Location) {
					minLoc = (Location) min;
					maxLoc = (Location) max;
				}
			} catch (NoSuchMethodException ignore) {
			}

			// If not Bukkit Location, try alternative method names (older/newer APIs)
			if (minLoc == null || maxLoc == null) {
				try {
					Method getMinimumPoint = selection.getClass().getMethod("getMinimum");
					Method getMaximumPoint = selection.getClass().getMethod("getMaximum");
					Object min = getMinimumPoint.invoke(selection);
					Object max = getMaximumPoint.invoke(selection);
					// These might be BlockVector or similar; attempt to extract int x,y,z and world via selection
					Method getBlockX = min.getClass().getMethod("getBlockX");
					Method getBlockY = min.getClass().getMethod("getBlockY");
					Method getBlockZ = min.getClass().getMethod("getBlockZ");
					int x1 = ((Number) getBlockX.invoke(min)).intValue();
					int y1 = ((Number) getBlockY.invoke(min)).intValue();
					int z1 = ((Number) getBlockZ.invoke(min)).intValue();

					Method getBlockX2 = max.getClass().getMethod("getBlockX");
					Method getBlockY2 = max.getClass().getMethod("getBlockY");
					Method getBlockZ2 = max.getClass().getMethod("getBlockZ");
					int x2 = ((Number) getBlockX2.invoke(max)).intValue();
					int y2 = ((Number) getBlockY2.invoke(max)).intValue();
					int z2 = ((Number) getBlockZ2.invoke(max)).intValue();

					// Try to get world from selection
					World world = null;
					try {
						Method getWorld = selection.getClass().getMethod("getWorld");
						Object w = getWorld.invoke(selection);
						if (w instanceof World) world = (World) w;
					} catch (NoSuchMethodException ignored) {
					}

					if (world == null) world = player.getWorld();

					minLoc = new Location(world, Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2));
					maxLoc = new Location(world, Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2));
				} catch (Exception ex) {
					// fall through
				}
			}

			if (minLoc == null || maxLoc == null) {
				player.sendMessage("Could not determine selection corners from WorldEdit selection.");
				return;
			}

			World world = minLoc.getWorld();
			int x1 = Math.min(minLoc.getBlockX(), maxLoc.getBlockX());
			int y1 = Math.min(minLoc.getBlockY(), maxLoc.getBlockY());
			int z1 = Math.min(minLoc.getBlockZ(), maxLoc.getBlockZ());
			int x2 = Math.max(minLoc.getBlockX(), maxLoc.getBlockX());
			int y2 = Math.max(minLoc.getBlockY(), maxLoc.getBlockY());
			int z2 = Math.max(minLoc.getBlockZ(), maxLoc.getBlockZ());

			// Save to zones.yml
			String path = "zones." + name;
			zonesConfig.set(path + ".world", world.getName());
			zonesConfig.set(path + ".x1", x1);
			zonesConfig.set(path + ".y1", y1);
			zonesConfig.set(path + ".z1", z1);
			zonesConfig.set(path + ".x2", x2);
			zonesConfig.set(path + ".y2", y2);
			zonesConfig.set(path + ".z2", z2);
			saveZonesFile();

			player.sendMessage("AFK zone '" + name + "' created: " + world.getName() + " (" + x1 + "," + y1 + "," + z1 + ") -> (" + x2 + "," + y2 + "," + z2 + ")");

		} catch (NoSuchMethodException e) {
			player.sendMessage("Incompatible WorldEdit version or API not available.");
		} catch (Exception e) {
			player.sendMessage("Error reading WorldEdit selection: " + e.getMessage());
			getLogger().severe("Error reading WorldEdit selection: " + e);
		}
	}
}

