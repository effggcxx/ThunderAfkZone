# ThunderAfkZone

A Paper 1.21+ plugin for AFK reward zones: define cuboid regions with WorldEdit,
give players rewards for staying AFK inside them, and track statistics per player
and per zone.

## Features

- **WorldEdit-based zone creation** — select a region with the WorldEdit wand,
  run one command, done.
- **Flexible rewards** — repeating (`interval_seconds`) or one-time
  (`once_after_seconds`) rewards, given via vanilla `/give`, ItemEdit, or any
  console command. Toggle rewards on/off without deleting their config.
- **Per-zone reward restrictions** — by default every zone shares the same
  global reward pool; optionally restrict specific zones to a subset of rewards.
- **Per-zone enter/exit commands** — run arbitrary console commands when a
  player enters or leaves a zone.
- **Countdown display** — title, action bar, chat, or a persistent boss bar
  showing time until the next reward, fully styled via MiniMessage.
- **Zone boundary particles** — optional visual outline shown to nearby players.
- **Persistent or in-memory storage** — SQLite for stats that survive restarts,
  or plain in-memory tracking if you don't need persistence.
- **Player statistics** — lifetime AFK time, per-zone AFK time, and rewards
  received, plus `/afkzone top` leaderboards.
- **PlaceholderAPI support** — expose AFK status and stats to scoreboards,
  holograms, or anywhere else placeholders are read.

## Requirements

| Dependency | Required? | Notes |
|---|---|---|
| Paper 1.21+ | Yes | Built against `paper-api:1.21.8` |
| WorldEdit | Yes | Used to select zone corners (`/afkzone create`). **Version matters** - WorldEdit ships Minecraft-patch-specific builds, and its supported range shifts almost every patch. Install whichever WorldEdit build officially supports your server's exact Minecraft version (check [Hangar](https://hangar.papermc.io/EngineHub/WorldEdit/versions)), not necessarily the version pinned in `build.gradle.kts` - that pin is only for compiling against WorldEdit's API, which has stayed stable across the 7.3.x series. |
| ItemEdit | Only if using `executor: "itemedit"` on a reward | Soft dependency |
| PlaceholderAPI | Only if you want the placeholders | Soft dependency |

If `global.storage: "sqlite"` is set in `config.yml`, the SQLite JDBC driver is
downloaded automatically by Paper at startup (declared in `plugin.yml`'s
`libraries:` block) — no manual setup needed. Check your server log on first
boot for `SQLite storage initialized`; if you instead see a warning about
failed initialization, switch to `storage: "memory"` until it's resolved.

## Installation

1. Drop the built jar into your server's `plugins/` folder.
2. Make sure WorldEdit is installed (required to create zones).
3. Start the server once to generate `config.yml` and `zones.yml`.
4. Edit `config.yml` to set up your rewards, then reload or restart.

## Commands

All commands are under `/afkzone` (aliases: `/taz`, `/thunderafk`).

| Command | Description |
|---|---|
| `/afkzone create <name>` | Create a zone from your current WorldEdit selection |
| `/afkzone list` | List all configured zones |
| `/afkzone info <name>` | Show a zone's coordinates and which rewards apply there |
| `/afkzone remove <name>` | Delete a zone |
| `/afkzone reload` | Reload config, zones, and particle settings |
| `/afkzone reward list` | List all configured rewards and their enabled state |
| `/afkzone reward give <reward> [player]` | Manually give a reward |
| `/afkzone zonereward list <zone>` | Show which rewards are restricted to a zone |
| `/afkzone zonereward add <zone> <reward>` | Restrict a reward to a specific zone |
| `/afkzone zonereward remove <zone> <reward>` | Remove a reward restriction |
| `/afkzone zonereward clear <zone>` | Clear all restrictions (zone uses all global rewards again) |
| `/afkzone stats [player]` | Show current session + lifetime AFK time and rewards received |
| `/afkzone top [time\|rewards]` | Show the leaderboard for AFK time or rewards received |

## Permissions

| Permission | Default | Covers |
|---|---|---|
| `afkzone.create` | op | Creating zones |
| `afkzone.list` | op | Listing zones |
| `afkzone.info` | op | Viewing zone info |
| `afkzone.remove` | op | Removing zones |
| `afkzone.reload` | op | Reloading config |
| `afkzone.reward.list` | op | Listing rewards |
| `afkzone.reward.give` | op | Manually giving rewards |
| `afkzone.zonereward.*` | op | Per-zone reward assignment (`list`/`add`/`remove`/`clear`) |
| `afkzone.zonereward` | op | All of the above `zonereward.*` sub-permissions |
| `afkzone.stats` | op | Viewing AFK statistics |
| `afkzone.top` | op | Viewing leaderboards |
| `afkzone.admin` | op | Every permission above, bundled |

## Configuring rewards (`config.yml`)

```yaml
rewards:
  welcome_diamond:
    description: "A diamond every 5 minutes of AFK"
    executor: "give"          # give | itemedit | console
    item: "diamond"
    amount: 1
    interval_seconds: 300     # repeats every 5 minutes
    once_after_seconds: 0     # 0 = disabled
    priority: 5
    enabled: true

  starter_kit:
    description: "One-time iron sword after 60 seconds of AFK"
    executor: "console"
    command: "give {player} iron_sword 1"
    interval_seconds: 0
    once_after_seconds: 60    # given once, 60s after going AFK
    priority: 10
    enabled: true
```

A reward needs **at least one** of `interval_seconds` or `once_after_seconds`
set above `0`, or it can never fire. `priority` only matters if
`global.on_multiple: "highest"` — otherwise every eligible reward is given.

**Executors:**
- `give` (or `vanilla`) → `/give <player> <item> <amount>` (vanilla material name, e.g. `diamond`, `iron_sword`)
- `itemedit` → `/si give <player> <item> <amount>` (item must already be saved in ItemEdit under that exact name)
- `console` → runs the string in `command:`, with `{player}` substituted

## Zones and per-zone options (`zones.yml`)

Zones are created via `/afkzone create`, but you can also hand-edit `zones.yml`.
Each zone supports optional `rewards:` (restrict to specific reward names),
`on_enter.commands:` and `on_exit.commands:` (console commands run on
entry/exit, with `{player}`, `{zone}`, and `{uuid}` placeholders available):

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
      - "welcome_diamond"
    on_enter:
      commands:
        - "say {player} entered {zone}"
    on_exit:
      commands:
        - "say {player} left {zone}"
```

## PlaceholderAPI

Requires PlaceholderAPI installed. Placeholders:

| Placeholder | Returns |
|---|---|
| `%afkzone_zone%` | Name of the zone the player is currently in (blank if none) |
| `%afkzone_in_zone%` | `yes` / `no` |
| `%afkzone_time%` | Current session AFK time (resets when the player leaves a zone) |
| `%afkzone_next_reward%` | Time until the next reward in the current zone |
| `%afkzone_rewards_received%` | Lifetime count of rewards received |
| `%afkzone_afk_time%` | Lifetime total AFK time (persists across zone visits and restarts) |
| `%afkzone_zone_time_<zone>%` | Lifetime AFK time in a specific named zone |

## Building from source

Requires Java 21.

```bash
./gradlew build
```

The built jar is output to `build/libs/`.

## Storage

Set `global.storage` in `config.yml` to:
- `"memory"` — no setup required, but AFK time and reward counts reset on every restart.
- `"sqlite"` — persists to `afkzone.db` in the plugin's data folder. Requires the
  bundled SQLite driver to load successfully on startup (see Requirements above).