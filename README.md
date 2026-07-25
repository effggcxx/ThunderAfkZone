# ThunderAfkZone
Custom AFK-zone plugin for Paper 1.21+ servers. Define zones (via a WorldEdit selection)
and configure per-reward interval and one-time timers, with a MiniMessage-based countdown
display.

## Commands
- `/afkzone create <name>` — create a zone from your current WorldEdit selection
- `/afkzone list` — list configured zones
- `/afkzone remove <name>` — remove a zone
- `/afkzone reload` — reload config.yml and zones.yml
- `/afkzone reward list` — list configured rewards
- `/afkzone reward give <reward> [player]` — manually give a reward

## Configuration
See `config.yml` for the reward schema (`executor`, `item`, `amount`, `interval_seconds`,
`once_after_seconds`, `priority`) and global settings (sounds, AFK threshold, timer display).

## Dependencies
- Paper 1.21+
- WorldEdit (soft dependency, required only for `/afkzone create`)
- ItemEdit (optional, only if using the `itemedit` reward executor)
