# ThunderAfkZone It is incomplete right now !!!

Custom AFK-zone plugin for Paper 1.21+ servers.  
Define zones (via a WorldEdit selection) and configure per-reward interval and one-time timers, with a MiniMessage-based countdown display.

## Features

- Create rectangular AFK zones from a WorldEdit selection (official WorldEdit API)
- **Single global scheduler** (efficient with many AFK players)
- **Per-zone reward configuration** – each zone can use a subset of global rewards
- Configurable rewards with interval / once-after timers + priority system
- MiniMessage support for all messages and the countdown timer
- Timer display modes: title, actionbar, chat, bossbar
- Full tab completion for every subcommand
- Soft-depend on WorldEdit (only needed for `/afkzone create`)

## Commands

| Command | Description |
|---------|-------------|
| `/afkzone create <name>` | Create a zone from your current WorldEdit selection |
| `/afkzone list` | List all zones |
| `/afkzone info <name>` | Show zone details + assigned rewards |
| `/afkzone remove <name>` | Remove a zone |
| `/afkzone reload` | Reload config.yml and zones.yml |
| `/afkzone reward list` | List all global rewards |
| `/afkzone reward give <reward> [player]` | Manually give a reward |
| `/afkzone zonereward list <zone>` | Show rewards assigned to a zone |
| `/afkzone zonereward add <zone> <reward>` | Assign a reward to a zone |
| `/afkzone zonereward remove <zone> <reward>` | Unassign a reward from a zone |
| `/afkzone zonereward clear <zone>` | Clear restrictions (zone uses all global rewards again) |

## Per-zone rewards

Rewards are still defined globally in `config.yml`.  
By default a zone uses **all enabled global rewards**.

To restrict a zone:

```
/afkzone zonereward add spawn_afk welcome_diamond
/afkzone zonereward add spawn_afk starter_kit
```

Or edit `zones.yml` directly:

```yaml
zones:
  spawn_afk:
    world: world
    x1: 0
    y1: 64
    z1: 0
    x2: 10
    y2: 70
    z2: 10
    rewards:
      - welcome_diamond
      - starter_kit
```

An empty / missing `rewards:` list = use every enabled global reward.

## Permissions

| Permission | Default |
|------------|---------|
| `afkzone.create` | op |
| `afkzone.list` | op |
| `afkzone.info` | op |
| `afkzone.remove` | op |
| `afkzone.reload` | op |
| `afkzone.reward.list` | op |
| `afkzone.reward.give` | op |
| `afkzone.zonereward` | op |

## Dependencies

- **Paper 1.21+**
- **WorldEdit** (soft – required only for `/afkzone create`)
- **ItemEdit** (optional – only if you use the `itemedit` executor)

## Building

```bash
cd ThunderAfkZone
./gradlew build
```

Jar ends up in `build/libs/`.
