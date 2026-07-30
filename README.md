# ThunderAfkZone ( not ready to use yet )

A Paper 1.21+ plugin for AFK reward zones: define cuboid regions with the
built-in selection wand, give players rewards for staying AFK inside them,
and track statistics per player and per zone.

## Features

- **Built-in zone selection wand** — no external dependency needed. Left-click
  sets corner 1, right-click sets corner 2.
- **Flexible rewards** — repeating (`interval_seconds`) or one-time
  (`once_after_seconds`) rewards, given via vanilla `/give`, ItemEdit, or any
  console command. Toggle rewards on/off without deleting their config.
- **Per-zone reward restrictions** — by default every zone shares the same
  global reward pool; optionally restrict specific zones to a subset of rewards.
- **Per-zone enter/exit commands** — run arbitrary console commands when a
  player enters or leaves a zone.
- **Overlap protection** — creating a zone that overlaps an existing one is
  blocked with a clear error naming the conflicting zone.
- **Fully configurable messages** — every player-facing message (enter, exit,
  reward received/failed, timer) has its own text and display mode
  (chat/title/actionbar/boss bar), set independently in `messages.yml`.
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
| ItemEdit | Only if using `executor: "itemedit"` on a reward | Soft dependency |
| PlaceholderAPI | Only if you want the placeholders | Soft dependency |

No external plugin is needed for zone selection — the wand is built in (see
below).

If `global.storage: "sqlite"` is set in `config.yml`, the SQLite JDBC driver is
downloaded automatically by Paper at startup (declared in `plugin.yml`'s
`libraries:` block) — no manual setup needed. Check your server log on first
boot for `SQLite storage initialized`; if you instead see a warning about
failed initialization, switch to `storage: "memory"` until it's resolved.

## Installation

1. Drop the built jar into your server's `plugins/` folder.
2. Start the server once to generate `config.yml` and `zones.yml`.
3. Edit `config.yml` to set up your rewards, then reload or restart.
4. Run `/afkzone wand` to get the selection tool and create your first zone
   (see below).

## Creating a zone with the wand

1. `/afkzone wand` — gives you the selection tool (a wooden hoe by default,
   configurable via `wand.item` in `config.yml`).
2. **Left-click** a block to set corner 1, **right-click** another block to
   set corner 2.
3. `/afkzone sel` — check your current selection at any time (corners +
   dimensions), if you want to confirm before creating.
4. `/afkzone create <name>` — creates the zone from your selection and clears
   it, ready for the next one. If the selection overlaps an existing zone,
   creation is blocked and you'll get an error naming which zone it conflicts
   with — resize your selection or remove/adjust the other zone first.
5. `/afkzone cancel` — clears your current selection without creating anything.

Only players with `afkzone.wand` permission are affected when holding the
wand item — everyone else can use a plain wooden hoe (or whatever material
you've configured) completely normally.

## Commands

All commands are under `/afkzone` (aliases: `/taz`, `/thunderafk`).

| Command | Description |
|---|---|
| `/afkzone wand` | Get the selection wand |
| `/afkzone sel` | Show your current wand selection (corners + dimensions) |
| `/afkzone cancel` | Clear your current wand selection |
| `/afkzone create <name>` | Create a zone from your current wand selection |
| `/afkzone list` | List all configured zones |
| `/afkzone info <name>` | Show a zone's coordinates and which rewards apply there |
| `/afkzone remove <name>` | Delete a zone |
| `/afkzone reload` | Reload config.yml, messages.yml, and zones.yml |
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
| `afkzone.wand` | op | Getting the wand, viewing/clearing a selection |
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

## AFK threshold

By default (`afk_threshold_seconds: 0`), simply being inside a zone counts as
AFK and reward progress starts immediately. Set this above `0` if you want to
require genuine idling instead - progress will only advance once a player has
gone that many seconds without any activity inside the zone.

```yaml
global:
  afk_threshold_seconds: 0    # 0 = default, presence only (no idle required)
```

Activity that resets the idle timer: block movement, chat, commands, and
interacting with blocks/entities. Entering a zone also counts as activity, so
if this is set above `0`, players must wait the threshold after walking in
before timers start.

Per-zone override in `zones.yml`:

```yaml
zones:
  spawn_afk:
    afk_threshold_seconds: 30
```

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

`config.yml` also ships a third example, `itemedit_example` (disabled by
default), showing the `itemedit` executor syntax.

A reward needs **at least one** of `interval_seconds` or `once_after_seconds`
set above `0`, or it can never fire. `priority` only matters if
`global.on_multiple: "highest"` — otherwise every eligible reward is given.

**Executors:**
- `give` (or `vanilla`) → `/give <player> <item> <amount>` (vanilla material name, e.g. `diamond`, `iron_sword`)
- `itemedit` → `/si give <player> <item> <amount>` (item must already be saved in ItemEdit under that exact name)
- `console` → runs the string in `command:`, with `{player}` substituted

**Leaving a zone resets reward progress by default.** Interval/once-after
counters restart from zero every time a player leaves and re-enters, even a
couple of seconds later. If that's too punishing for your setup, set
`global.reset_progress_on_leave: false` in `config.yml` to keep progress
in memory across zone visits (a full restart still clears it). Note that the
current-session timer (`%afkzone_time%`, and the "Current session" line in
`/afkzone stats`) always resets on leaving regardless of this setting — it's
tracking time in the zone right now, not reward progress.

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

## Messages (`messages.yml`)

Every player-facing message - `enter_zone`, `exit_zone`, `reward_received`,
`reward_failed`, and the `timer` countdown - has its own entry here, each
with independent text *and* display mode:

```yaml
enter_zone:
  text: "<green>Entered AFK zone: <yellow><zone></yellow></green>"
  display: "title"          # chat | title | actionbar | bossbar

timer:
  text: "<timer> remaining until next reward"
  display: "title"
  size: "big"                # "big" or "mini"
  title:
    fade_in: 5
    stay: 40
    fade_out: 5
```

Placeholders available: `<zone>`, `<reward>`, `<timer>`, `<player>` (not all
apply to every message - `<timer>` only makes sense in the `timer` entry).
Text is MiniMessage format throughout, same as everywhere else in the plugin.
`display: "title"` and `"bossbar"` both put the message front-and-center on
screen rather than in chat; `enter_zone`/`exit_zone`/`timer` default to
`"title"`, `reward_received`/`reward_failed` default to `"chat"` so they
don't fight with whatever's currently showing as a title.

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

## Wand configuration (`config.yml`)

```yaml
wand:
  item: "WOODEN_HOE"       # any Bukkit Material name
```

Changing `wand.item` to something players don't normally hold (an axe or a
stick, say) avoids any chance of confusion even for staff who forget they're
holding it. Whatever you pick, only players with `afkzone.wand` are affected
by clicks with that item - it never touches normal players' tool use.

The wand itself is named and described in-item (gold "Selection Wand" with
lore explaining left/right-click) so players don't need to check docs to know
what it does.

## Storage

Set `global.storage` in `config.yml` to:
- `"memory"` — no setup required, but AFK time and reward counts reset on every restart.
- `"sqlite"` — persists to `afkzone.db` in the plugin's data folder. Requires the
  bundled SQLite driver to load successfully on startup (see Requirements above).
