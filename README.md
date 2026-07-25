# ThunderAfkZone

Custom AFK-zone plugin for Paper 1.21+ servers.  
Define zones (via a WorldEdit selection) and configure per-reward interval and one-time timers, with a MiniMessage-based countdown display.

## Features

- Create rectangular AFK zones from a WorldEdit selection (proper WorldEdit API, no reflection)
- Single global scheduler (efficient even with many AFK players)
- Configurable rewards with interval / once-after timers
- Priority system when multiple rewards trigger at once
- MiniMessage support for all messages and the countdown timer
- Timer display modes: title, actionbar, chat, bossbar
- Soft-depend on WorldEdit (only needed for `/afkzone create`)

## Commands

| Command | Description |
|---------|-------------|
| `/afkzone create <name>` | Create a zone from your current WorldEdit selection |
| `/afkzone list` | List configured zones |
| `/afkzone info <name>` | Show details of a zone |
| `/afkzone remove <name>` | Remove a zone |
| `/afkzone reload` | Reload config.yml and zones.yml |
| `/afkzone reward list` | List configured rewards |
| `/afkzone reward give <reward> [player]` | Manually give a reward |

## Permissions

- `afkzone.create` (default: op)
- `afkzone.list` (default: op)
- `afkzone.info` (default: op)
- `afkzone.remove` (default: op)
- `afkzone.reload` (default: op)
- `afkzone.reward.list` (default: op)
- `afkzone.reward.give` (default: op)

## Dependencies

- **Paper 1.21+**
- **WorldEdit** (soft dependency – required only for `/afkzone create`)
- **ItemEdit** (optional – only if you use the `itemedit` reward executor)

## Configuration

See `config.yml` for the full reward schema and global settings (sounds, AFK threshold, timer display, messages).

## Building

```bash
cd ThunderAfkZone
./gradlew build
```

The jar will be in `build/libs/`.
