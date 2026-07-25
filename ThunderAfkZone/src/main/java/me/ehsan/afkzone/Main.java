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
import java.time.Duration;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;

import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.Sound;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.Bukkit;

public class Main extends JavaPlugin {
	private File zonesFile;
	private FileConfiguration zonesConfig;

	private Map<String, Reward> rewards = new HashMap<>();

	// runtime tracking (moved out of Reward)
	private Map<UUID, BukkitTask> playerTasks = new ConcurrentHashMap<>();
	private Map<UUID, Map<String, Integer>> playerProgress = new ConcurrentHashMap<>();
	private Map<UUID, Set<String>> playerGivenOnce = new ConcurrentHashMap<>();
	private Map<UUID, String> playerZone = new ConcurrentHashMap<>();
	private Map<UUID, BossBar> activeBossBars = new ConcurrentHashMap<>();
	private String onMultiple = "all";
	private Map<UUID, Long> lastActive = new ConcurrentHashMap<>();
	private int afkThresholdSeconds = 60;
	private Sound enterSound = null;
	private Sound exitSound = null;
	private Sound rewardSound = null;
	private float soundVolume = 1.0f;
	private float soundPitch = 1.0f;

	private boolean timerEnabled = true;
	private String timerTemplate = "<gold><bold>Next reward in <timer></bold></gold>";
	private String timerDisplay = "title";
	private String timerSize = "big";
	private int timerTitleFadeIn = 5;
	private int timerTitleStay = 40;
	private int timerTitleFadeOut = 5;
	private final MiniMessage miniMessage = MiniMessage.miniMessage();

	// Configurable, MiniMessage-styled player-facing messages (support <zone> / <reward> placeholders)
	private String msgEnterZone = "<green>Entered AFK zone: <yellow><zone></yellow></green>";
	private String msgExitZone = "<gray>You left AFK zone: <yellow><zone></yellow></gray>";
	private String msgRewardReceived = "<gold>You received reward: <yellow><reward></yellow></gold>";
	private String msgRewardFailed = "<red>Reward '<reward>' could not be delivered. Please contact staff.</red>";

	@Override
	public void onEnable() {
		saveDefaultConfig();
		loadRewards();
		loadGlobalConfig();
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
			r.enabled = cfg.getBoolean(path + ".enabled", true);
			rewards.put(key, r);
		}
		getLogger().info("Loaded " + rewards.size() + " rewards");
	}

	private void loadGlobalConfig() {
		this.onMultiple = getConfig().getString("global.on_multiple", "all");
		this.afkThresholdSeconds = getConfig().getInt("global.afk_threshold_seconds", 60);
		this.enterSound = parseSound(getConfig().getString("global.enter_sound", "ENTITY_PLAYER_LEVELUP"), "global.enter_sound");
		this.exitSound = parseSound(getConfig().getString("global.exit_sound", "ENTITY_ITEM_BREAK"), "global.exit_sound");
		this.rewardSound = parseSound(getConfig().getString("global.reward_sound", "ENTITY_EXPERIENCE_ORB_PICKUP"), "global.reward_sound");
		this.soundVolume = (float) getConfig().getDouble("global.sound_volume", 1.0);
		this.soundPitch = (float) getConfig().getDouble("global.sound_pitch", 1.0);
		this.timerEnabled = getConfig().getBoolean("global.timer.enabled", true);
		this.timerTemplate = getConfig().getString("global.timer.template", "<gold><bold>Next reward in <timer></bold></gold>");
		this.timerDisplay = getConfig().getString("global.timer.display", "title");
		this.timerSize = getConfig().getString("global.timer.size", "big");
		this.timerTitleFadeIn = getConfig().getInt("global.timer.title.fade_in", 5);
		this.timerTitleStay = getConfig().getInt("global.timer.title.stay", 40);
		this.timerTitleFadeOut = getConfig().getInt("global.timer.title.fade_out", 5);

		this.msgEnterZone = getConfig().getString("global.messages.enter_zone", msgEnterZone);
		this.msgExitZone = getConfig().getString("global.messages.exit_zone", msgExitZone);
		this.msgRewardReceived = getConfig().getString("global.messages.reward_received", msgRewardReceived);
		this.msgRewardFailed = getConfig().getString("global.messages.reward_failed", msgRewardFailed);
	}

	private Sound parseSound(String soundName, String configPath) {
		if (soundName == null || soundName.isEmpty()) return null;
		try {
			return Sound.valueOf(soundName.toUpperCase(Locale.ROOT).trim());
		} catch (IllegalArgumentException ex) {
			getLogger().warning("Invalid sound name in config path " + configPath + ": '" + soundName + "'. Sound disabled.");
			return null;
		}
	}

	private String formatTime(long seconds) {
		if (seconds <= 0) return "0s";
		if (seconds >= 60) {
			long mins = seconds / 60;
			long secs = seconds % 60;
			return mins + ":" + String.format("%02d", secs);
		}
		return seconds + "s";
	}

	/**
	 * Sends a MiniMessage-styled message to a player, substituting <zone> and/or
	 * <reward> placeholders with plain values before parsing. Falls back to a
	 * tag-stripped plain message if the template fails to parse.
	 */
	private void sendStyled(Player player, String template, String zoneName, String rewardName) {
		if (template == null || template.isEmpty()) return;
		String text = template;
		if (zoneName != null) text = text.replace("<zone>", zoneName);
		if (rewardName != null) text = text.replace("<reward>", rewardName);
		try {
			player.sendMessage(miniMessage.deserialize(text));
		} catch (Exception ex) {
			player.sendMessage(text.replaceAll("<[^>]+>", ""));
		}
	}

	private Component buildTimerComponent(long secondsRemaining, String zoneName, String playerName) {
		String text = timerTemplate
				.replace("<timer>", formatTime(secondsRemaining))
				.replace("<zone>", zoneName == null ? "" : zoneName)
				.replace("<player>", playerName == null ? "" : playerName);
		if ("mini".equalsIgnoreCase(timerSize)) {
			text = "<gray><italic>" + text + "</italic></gray>";
		} else if ("big".equalsIgnoreCase(timerSize)) {
			text = "<gold><bold>" + text + "</bold></gold>";
		}
		try {
			return miniMessage.deserialize(text);
		} catch (Exception ex) {
			return Component.text("Next reward in " + formatTime(secondsRemaining));
		}
	}

	private float clamp01(float v) {
		if (v < 0f) return 0f;
		if (v > 1f) return 1f;
		return v;
	}

	private void sendTimer(Player player, long secondsRemaining, long totalSeconds, String zoneName) {
		if (!timerEnabled) return;
		Component component = buildTimerComponent(secondsRemaining, zoneName, player.getName());
		switch (timerDisplay.toLowerCase(Locale.ROOT)) {
			case "actionbar":
				player.sendActionBar(component);
				break;
			case "chat":
				player.sendMessage(component);
				break;
			case "bossbar": {
				float progress = totalSeconds > 0
						? clamp01((float) (totalSeconds - secondsRemaining) / (float) totalSeconds)
						: 0f;
				BossBar bar = activeBossBars.get(player.getUniqueId());
				if (bar == null) {
					bar = BossBar.bossBar(component, progress, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
					activeBossBars.put(player.getUniqueId(), bar);
					player.showBossBar(bar);
				} else {
					bar.name(component);
					bar.progress(progress);
				}
				break;
			}
			default:
				player.showTitle(Title.title(component, Component.empty(), Title.Times.times(Duration.ofMillis(timerTitleFadeIn * 50L), Duration.ofMillis(timerTitleStay * 50L), Duration.ofMillis(timerTitleFadeOut * 50L))));
				break;
		}
	}

	private NextRewardInfo getNearestReward(Map<String, Integer> prog, Set<String> given) {
		long nearest = Long.MAX_VALUE;
		long total = 0;
		for (Reward r : rewards.values()) {
			if (!r.enabled) continue;
			int current = prog.getOrDefault(r.name, 0);
			if (r.onceAfterSeconds > 0 && !given.contains(r.name)) {
				long remaining = r.onceAfterSeconds - current;
				if (remaining >= 0 && remaining < nearest) {
					nearest = remaining;
					total = r.onceAfterSeconds;
				}
			}
			if (r.intervalSeconds > 0) {
				long remaining = r.intervalSeconds - (current % r.intervalSeconds);
				if (remaining == r.intervalSeconds) remaining = r.intervalSeconds;
				if (remaining >= 0 && remaining < nearest) {
					nearest = remaining;
					total = r.intervalSeconds;
				}
			}
		}
		return nearest == Long.MAX_VALUE ? new NextRewardInfo(0, 0) : new NextRewardInfo(nearest, total);
	}

	private void giveRewardToPlayer(Reward r, Player player) {
		if (r == null || player == null) return;
		// If executor is console and a raw command is provided, run that (with {player} placeholder)
		if ((r.executor == null || r.executor.equalsIgnoreCase("console")) && r.command != null && !r.command.isEmpty()) {
			String consoleCmd = r.command.replace("{player}", player.getName()).replace("{award}", r.name);
			boolean ok = dispatchAndCheck(consoleCmd, r.name);
			if (ok) {
				sendStyled(player, msgRewardReceived, null, r.name);
				if (rewardSound != null) player.playSound(player.getLocation(), rewardSound, soundVolume, soundPitch);
			} else {
				sendStyled(player, msgRewardFailed, null, r.name);
			}
			return;
		}
		try {
			String ex = r.executor == null ? "console" : r.executor.toLowerCase();
			boolean ok = true;
			switch (ex) {
				case "itemedit": {
					String dispatch = "si give " + player.getName() + " " + r.itemName + " " + r.amount;
					ok = dispatchAndCheck(dispatch, r.name);
					break;
				}
				case "item":
				case "itemadder": {
					// legacy support
					String dispatch = "itemadder give " + player.getName() + " " + r.itemName + " " + r.amount;
					ok = dispatchAndCheck(dispatch, r.name);
					break;
				}
				case "vanilla":
				case "give": {
					// vanilla give: give <player> <command>
					String giveCmd = "give " + player.getName() + " " + r.itemName + " " + r.amount;
					ok = dispatchAndCheck(giveCmd, r.name);
					break;
				}
				default: {
					// run arbitrary console command
					if (r.command != null && !r.command.isEmpty()) {
						String fallback = r.command.replace("{player}", player.getName()).replace("{award}", r.name);
						ok = dispatchAndCheck(fallback, r.name);
					}
					break;
				}
			}
			if (ok) {
				sendStyled(player, msgRewardReceived, null, r.name);
				if (rewardSound != null) {
					player.playSound(player.getLocation(), rewardSound, soundVolume, soundPitch);
				}
			} else {
				sendStyled(player, msgRewardFailed, null, r.name);
			}
		} catch (Exception ex) {
			getLogger().severe("Failed to give reward " + r.name + ": " + ex.getMessage());
			sendStyled(player, msgRewardFailed, null, r.name);
		}
	}

	/**
	 * Dispatches a console command and logs a warning if the command handler
	 * reports failure (returns false), instead of silently swallowing it.
	 */
	private boolean dispatchAndCheck(String command, String rewardName) {
		try {
			boolean result = getServer().dispatchCommand(getServer().getConsoleSender(), command);
			if (!result) {
				getLogger().warning("Command for reward '" + rewardName + "' returned failure: /" + command);
			}
			return result;
		} catch (Exception ex) {
			getLogger().severe("Exception dispatching command for reward '" + rewardName + "': /" + command + " - " + ex.getMessage());
			return false;
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
			sender.sendMessage("Usage: /afkzone create <name> | list | info <name> | remove <name> | reload | reward list|give <reward> [player]");
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
			loadGlobalConfig();
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
					if (!sender.hasPermission("afkzone.reward.list")) {
						sender.sendMessage("You don't have permission to list rewards.");
						return true;
					}
					if (rewards.isEmpty()) {
						sender.sendMessage("No rewards configured.");
						return true;
					}
					sender.sendMessage("Rewards:");
					for (Reward r : rewards.values()) {
						String status = r.enabled ? "enabled" : "disabled";
						sender.sendMessage("- " + r.name + " (" + status + ", priority=" + r.priority + ") - " + r.description);
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
			case "info": {
				if (args.length < 2) {
					sender.sendMessage("Usage: /afkzone info <name>");
					return true;
				}
				showZoneInfo(sender, args[1]);
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
				sender.sendMessage("Usage: /afkzone create <name> | list | info <name> | remove <name>");
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

	/**
	 * Shows a single zone's coordinates plus the rewards that apply there.
	 * Rewards are global (not bound per-zone), so this lists every enabled
	 * reward along with the overall enabled/disabled count.
	 */
	private void showZoneInfo(CommandSender sender, String name) {
		if (zonesConfig == null || !zonesConfig.isConfigurationSection("zones") || !zonesConfig.isSet("zones." + name)) {
			sender.sendMessage("Zone '" + name + "' does not exist.");
			return;
		}
		String path = "zones." + name;
		String world = zonesConfig.getString(path + ".world", "unknown");
		int x1 = zonesConfig.getInt(path + ".x1", 0);
		int y1 = zonesConfig.getInt(path + ".y1", 0);
		int z1 = zonesConfig.getInt(path + ".z1", 0);
		int x2 = zonesConfig.getInt(path + ".x2", 0);
		int y2 = zonesConfig.getInt(path + ".y2", 0);
		int z2 = zonesConfig.getInt(path + ".z2", 0);

		sender.sendMessage("Zone '" + name + "':");
		sender.sendMessage("  World: " + world);
		sender.sendMessage("  Corners: (" + x1 + "," + y1 + "," + z1 + ") -> (" + x2 + "," + y2 + "," + z2 + ")");
		sender.sendMessage("  Size: " + (Math.abs(x2 - x1) + 1) + " x " + (Math.abs(y2 - y1) + 1) + " x " + (Math.abs(z2 - z1) + 1));

		if (rewards.isEmpty()) {
			sender.sendMessage("  Rewards: none configured.");
			return;
		}
		long enabledCount = rewards.values().stream().filter(r -> r.enabled).count();
		sender.sendMessage("  Rewards (" + enabledCount + "/" + rewards.size() + " enabled, apply to all zones):");
		for (Reward r : rewards.values()) {
			String status = r.enabled ? "enabled" : "disabled";
			sender.sendMessage("   - " + r.name + " (" + status + ", priority=" + r.priority + ")");
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
				sendStyled(player, msgExitZone, zoneName, null);
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
				if (!r.enabled) continue;
				prog.putIfAbsent(r.name, 0);
				prog.put(r.name, prog.get(r.name) + 1);
			}
			// collect due rewards
			Set<Reward> due = new HashSet<>();
			for (Reward r : rewards.values()) {
				if (!r.enabled) continue;
				int t = prog.getOrDefault(r.name, 0);
				if (r.onceAfterSeconds > 0 && !given.contains(r.name) && t >= r.onceAfterSeconds) due.add(r);
				if (r.intervalSeconds > 0 && t > 0 && t % r.intervalSeconds == 0) due.add(r);
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
			NextRewardInfo info = getNearestReward(prog, given);
			if (timerEnabled && info.remainingSeconds > 0) {
				sendTimer(player, info.remainingSeconds, info.totalSeconds, zoneName);
			}
		}, 20L, 20L);
		playerTasks.put(id, task);
		sendStyled(player, msgEnterZone, zoneName, null);
		if (enterSound != null) {
			player.playSound(player.getLocation(), enterSound, soundVolume, soundPitch);
		}
	}

	private void stopTrackingPlayer(UUID id) {
		BukkitTask t = playerTasks.remove(id);
		if (t != null) t.cancel();
		playerProgress.remove(id);
		playerGivenOnce.remove(id);
		String zone = playerZone.remove(id);
		Player player = Bukkit.getPlayer(id);
		BossBar bar = activeBossBars.remove(id);
		if (bar != null && player != null) {
			player.hideBossBar(bar);
		}
		if (player != null && zone != null && exitSound != null) {
			player.playSound(player.getLocation(), exitSound, soundVolume, soundPitch);
		}
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
				sendStyled(p, msgExitZone, prev, null);
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
