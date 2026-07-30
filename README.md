# ThunderAfkZone

[![Paper 1.21+](https://img.shields.io/badge/Paper-1.21%2B-6A5ACD?logo=papermc)](https://papermc.io/downloads/paper)
[![Java 21](https://img.shields.io/badge/Java-21-007396?logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![License MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![PlaceholderAPI Supported](https://img.shields.io/badge/PlaceholderAPI-Supported-2E8B57)](https://github.com/PlaceholderAPI/PlaceholderAPI)
[![SQLite](https://img.shields.io/badge/SQLite-Supported-003B57?logo=sqlite)](https://www.sqlite.org/)

A Paper 1.21+ plugin for AFK reward zones: define cuboid regions with the
built-in selection wand, give players rewards for staying AFK inside them,
and track statistics per player and per zone.

Rewards are created in-game by holding an item and saving it — no YAML
editing required for normal use.

## Features

- **Built-in zone selection wand** — no external dependency needed. Left-click
  sets corner 1, right-click sets corner 2.
- **In-game reward saving** — hold any item and run `/afkzone reward save`.
  The exact ItemStack (name, lore, enchants, custom data) is stored and given
  later. No material-name guessing, no ItemEdit required.
- **Repeating or one-time rewards** — interval timers, or one-time after a
  delay (via the reward YAML if needed).
- **Per-zone reward restrictions** — by default every zone uses all saved
  rewards; optionally restrict a zone to a subset.
- **Per-zone enter/exit commands** — run arbitrary console commands when a
  player enters or leaves a zone.
- **Overlap protection** — creating a zone that overlaps an existing one is
  blocked with a clear error naming the conflicting zone.
- **Fully configurable messages** — enter, exit, reward received/failed,
  inventory full, and the timer each have their own text and display mode
  (chat / title / actionbar / bossbar) in `messages.yml`.
- **Persistent or in-memory storage** — SQLite for stats that survive
  restarts, or plain in-memory tracking if you don't need persistence.
- **Player statistics** — lifetime AFK time, per-zone AFK time, and rewards
  received, plus `/afkzone top` leaderboards.
- **PlaceholderAPI support** — expose AFK status and stats to scoreboards,
  holograms, or anywhere else placeholders are read.

## Requirements

| Dependency       | Required? | Notes                                      |
|------------------|-----------|--------------------------------------------|
| Paper 1.21+      | Yes       | Built against `paper-api:1.21.8`           |
| PlaceholderAPI   | No        | Soft dependency — only if you want placeholders |

No external plugin is needed for zone selection or rewards. The wand and
item-save system are built in.

If `global.storage: "sqlite"` is set in `config.yml`, the SQLite JDBC driver
is downloaded automatically by Paper at startup (declared in `plugin.yml`'s
`libraries:` block). Check your server log on first boot for
`SQLite storage initialized`. If you see a failure warning instead, switch
to `storage: "memory"` until it is resolved.

## Installation

1. Drop the built jar into your server's `plugins/` folder.
2. Start the server once to generate `config.yml`, `messages.yml`, and
   `zones.yml`.
3. Create rewards with `/afkzone reward save` (see below).
4. Run `/afkzone wand`, select two corners, then `/afkzone create <name>`.

## Creating rewards

Rewards live as individual YAML files in `plugins/ThunderAfkZone/rewards/`.
You create them in-game:

1. Hold the item you want to give (any ItemStack — custom name, lore,
   enchants, etc. are preserved).
2. Run:

```
/afkzone reward save <name> [amount] [interval]
```

| Argument   | Default | Meaning |
|------------|---------|---------|
| `name`     | required | Unique reward id (filename becomes `<name>.yml`) |
| `amount`   | `1`      | How many of the item to give |
| `interval` | `0`      | Seconds between repeats. `0` = no interval (see note below) |

Examples:

```
/afkzone reward save diamond_reward 1 300
# Gives 1 of the held item every 5 minutes of AFK progress

/afkzone reward save starter_kit 1 0
# Saves the item; set once_after_seconds in the YAML for a one-time reward
```

3. `/afkzone reward list` — see all saved rewards and their state.
4. `/afkzone reward give <name> [player]` — manually give a reward (for testing).

**One-time rewards:** the save command always writes `once_after_seconds: 0`.
For a reward that should fire once after N seconds of AFK, open the file
`plugins/ThunderAfkZone/rewards/<name>.yml` after saving and set:

```yaml
once_after_seconds: 60   # give once after 60s of progress
interval_seconds: 0
```

You can also edit `priority`, `enabled`, `amount`, and `description` there.
Reload with `/afkzone reload` after hand-edits.

**Inventory full:** if the player's inventory cannot hold the item, it is
dropped at their feet and the `inventory_full` message is shown.

## Creating a zone with the wand

1. `/afkzone wand` — gives the selection tool (wooden hoe by default,
   configurable via `wand.item` in `config.yml`).
2. **Left-click** a block to set corner 1, **right-click** another block to
   set corner 2.
3. `/afkzone sel` — check your current selection (corners + dimensions).
4. `/afkzone create <name>` — creates the zone from the selection and clears
   it. Overlap with an existing zone is blocked; the error names the
   conflicting zone.
5. `/afkzone cancel` — clears the selection without creating anything.

Only players with `afkzone.wand` are affected while holding the wand item —
everyone else can use a normal wooden hoe (or whatever material you
configured) as usual.

## Commands

All commands are under `/afkzone` (aliases: `/taz`, `/thunderafk`).

| Command | Description |
|---------|-------------|
| `/afkzone wand` | Get the selection wand |
| `/afkzone sel` | Show current wand selection (corners + dimensions) |
| `/afkzone cancel` | Clear current wand selection |
| `/afkzone create <name>` | Create a zone from the current wand selection |
| `/afkzone list` | List all configured zones |
| `/afkzone info <name>` | Zone coordinates and which rewards apply |
| `/afkzone remove <name>` | Delete a zone |
| `/afkzone reload` | Reload config.yml, messages.yml, zones.yml, and rewards/ |
| `/afkzone reward list` | List all saved rewards |
| `/afkzone reward save <name> [amount] [interval]` | Save the held item as a reward |
| `/afkzone reward give <name> [player]` | Manually give a reward |
| `/afkzone zonereward list <zone>` | Show reward restrictions for a zone |
| `/afkzone zonereward add <zone> <reward>` | Restrict a reward to a zone |
| `/afkzone zonereward remove <zone> <reward>` | Remove a restriction |
| `/afkzone zonereward clear <zone>` | Clear restrictions (zone uses all saved rewards) |
| `/afkzone stats [player]` | Session + lifetime AFK time and rewards received |
| `/afkzone top [time\|rewards]` | Leaderboard for AFK time or rewards received |

## Permissions

| Permission | Default | Covers |
|------------|---------|--------|
| `afkzone.wand` | op | Wand, selection view/clear |
| `afkzone.create` | op | Creating zones |
| `afkzone.list` | op | Listing zones |
| `afkzone.info` | op | Viewing zone info |
| `afkzone.remove` | op | Removing zones |
| `afkzone.reload` | op | Reloading config |
| `afkzone.reward.list` | op | Listing rewards |
| `afkzone.reward.save` | op* | Saving a held item as a reward |
| `afkzone.reward.give` | op | Manually giving a reward |
| `afkzone.zonereward.*` | op | Per-zone reward assignment |
| `afkzone.zonereward` | op | All `zonereward.*` sub-permissions |
| `afkzone.stats` | op | Viewing AFK statistics |
| `afkzone.top` | op | Viewing leaderboards |
| `afkzone.admin` | op | Bundle of the above |

\* `afkzone.reward.save` is checked by the plugin. Ensure it is granted (ops
have it by default; permission plugins may need the node added explicitly).

## AFK threshold

By default (`afk_threshold_seconds: 0`), simply being inside a zone counts as
AFK and reward progress starts immediately. Set this above `0` to require
genuine idling — progress only advances after that many seconds without
activity.

```yaml
global:
  afk_threshold_seconds: 0    # 0 = presence only (no idle required)
```

Activity that resets the idle timer: block movement, chat, commands, and
interacting with blocks/entities. Entering a zone also counts as activity.

Per-zone override in `zones.yml`:

```yaml
zones:
  spawn_afk:
    afk_threshold_seconds: 30
```

## Reward progress and leaving a zone

**Leaving a zone resets reward progress by default.** Interval and
once-after counters restart from zero on re-entry. To keep progress in
memory across brief leaves:

```yaml
global:
  reset_progress_on_leave: false
```

Progress is still only held in memory — a full restart clears it either way.
The current-session timer (`%afkzone_time%` and the "Current session" line
in `/afkzone stats`) always resets on leave.

When multiple rewards become due in the same second:

```yaml
global:
  on_multiple: "all"      # give every eligible reward
  # on_multiple: "highest"  # only the highest-priority reward(s)
```

## Zones (`zones.yml`)

Zones are created with `/afkzone create`, or you can hand-edit `zones.yml`.
Each zone supports optional `rewards:` (restrict to specific reward names),
`on_enter.commands:` / `on_exit.commands:`, and per-zone overrides:

```yaml
zones:
  spawn_afk:
    world: "world"
    x1: 100
    y1: 64
    z1: 100
    x2: 110
    y2: 70
    z2: 110
    rewards:
      - "diamond_reward"
    afk_threshold_seconds: 30
    enter_sound: "BLOCK_NOTE_BLOCK_PLING"
    exit_sound: "none"
    reward_sound: "ENTITY_PLAYER_LEVELUP"
    timer.enabled: true
    on_enter:
      commands:
        - "say {player} entered {zone}"
    on_exit:
      commands:
        - "say {player} left {zone}"
```

Placeholders in enter/exit commands: `{player}`, `{zone}`, `{uuid}`.

## Messages (`messages.yml`)

Every player-facing message has its own text and display mode:

```yaml
enter_zone:
  text: "<green>Entered AFK zone: <yellow><zone></yellow></green>"
  display: "title"          # chat | title | actionbar | bossbar

exit_zone:
  text: "<gray>You left AFK zone: <yellow><zone></yellow></gray>"
  display: "title"

reward_received:
  text: "<gold>You received reward: <yellow><reward></yellow></gold>"
  display: "chat"

reward_failed:
  text: "<red>Reward '<reward>' could not be delivered. Please contact staff.</red>"
  display: "chat"

inventory_full:
  text: "<red>Your inventory was full! Reward '<reward>' fell on the ground.</red>"
  display: "chat"

timer:
  text: "<timer> remaining until next reward"
  display: "bossbar"
  size: "big"               # "big" or "mini"
  title:
    fade_in: 5
    stay: 40
    fade_out: 5
```

Placeholders: `<zone>`, `<reward>`, `<timer>`, `<player>` (not all apply to
every message). Text uses MiniMessage format.

## PlaceholderAPI

Requires PlaceholderAPI. Placeholders:

| Placeholder | Returns |
|-------------|---------|
| `%afkzone_zone%` | Name of the zone the player is in (blank if none) |
| `%afkzone_in_zone%` | `yes` / `no` |
| `%afkzone_time%` | Current session time in the zone (resets on leave) |
| `%afkzone_next_reward%` | Time until the next reward in the current zone |
| `%afkzone_rewards_received%` | Lifetime count of rewards received |
| `%afkzone_afk_time%` | Lifetime total AFK time |
| `%afkzone_zone_time_<zone>%` | Lifetime AFK time in a specific zone |

## Wand configuration

```yaml
wand:
  item: "WOODEN_HOE"       # any Bukkit Material name
```

Only players with `afkzone.wand` are affected by clicks with that item.
The wand is named and described in-item so the controls are clear without
checking docs.

## Storage

Set `global.storage` in `config.yml` to:

- `"memory"` — no setup; AFK time and reward counts reset on every restart.
- `"sqlite"` — persists to `afkzone.db` in the plugin data folder. Requires
  the driver in `plugin.yml`'s `libraries:` block to load successfully.

## Building from source

Requires Java 21.

```bash
./gradlew build
```

The built jar is written to `build/libs/`.
