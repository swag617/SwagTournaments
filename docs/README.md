# ✦ SwagTournaments

> Flexible tournament and competition framework for Paper 1.21+

SwagTournaments unifies tournament management across the Swag Development ecosystem. Instead of hard-coding tournament types, every tournament is a YAML **template** — display name, scoring mode, scoring formula, schedule, rewards, and cosmetics all live in one file. Six built-in tournament types cover fishing, farming, combat, mining, and economy, plus an API-driven `CUSTOM` type for anything else. When SwagFishing, SwagFarming, DiscordUtils, or Vault are installed, SwagTournaments hooks into them automatically — none of them are required.

---

## What makes it special

- **Template-driven, not code-driven** — new tournament types are new YAML files in `tournaments/`, no plugin restart required beyond `/tadmin reload`
- **Four scoring modes** — `SUM` (additive), `MAX` (personal best), `UNIQUE_COUNT` (distinct items/locations), `FIRST_TO` (race to a target score, ends early when reached)
- **Six tournament types** — `FISHING`, `FARMING`, `COMBAT`, `MINING`, `ECONOMY`, `CUSTOM` — each backed by its own `ScoringEngine`
- **In-house formula evaluator** — `score-formula` supports `+ - * /`, `Math.max()`, `Math.min()`, and named variables (`size`, `value`, etc.) — no scripting engine, no security surface
- **Two scheduling modes** — fixed weekly/daily cron slots per template, and a weighted-random auto-rotation pool that only fires when enough players are online
- **Soft-dependency bridges** — SwagFishing and SwagFarming bridges add richer scoring context (fish size, crop quality) and suppress overlap so two fishing tournaments never run at once
- **Web editor** — a full template/schedule/history/config SPA mounted on SwagAPI's shared panel, with no login of its own
- **Discord announcements** — start/end embeds (and optional live standings) via DiscordUtils, per-type colors
- **PlaceholderAPI expansion** — 8+ dynamic placeholders for scoreboards and chat, including per-rank `top_N_name`/`top_N_score`

---

## Quick Links

| | |
|---|---|
| [Installation](getting-started/installation.md) | Get SwagTournaments running on your server |
| [Configuration](getting-started/configuration.md) | config.yml and template YAML reference |
| [Tournament Templates](core-features/tournaments.md) | Creating and managing templates |
| [Web Editor](web-editor/overview.md) | Browser-based template/schedule/history editor |
| [Commands](commands/player-commands.md) | Full command reference |
| [Permissions](permissions/permissions.md) | Permission nodes |

---

## Requirements

| Dependency | Required |
|---|---|
| Paper 1.21+ | Yes |
| Java 21 | Yes |
| SwagAPI | **Yes** — declared as a hard `depend` in `plugin.yml`; Paper will refuse to enable SwagTournaments without it. At runtime only the web editor module actually calls into SwagAPI's `IWebService` (tournament data itself lives in the plugin's own bundled SQLite database) |
| SwagFishing | No — enables FISHING bridge integration |
| SwagFarming | No — enables FARMING bridge integration |
| DiscordUtils | No — enables Discord announcements |
| Vault | No — required for money rewards and the ECONOMY tournament type |
| PlaceholderAPI | No — enables `%swagtournaments_*%` placeholders |

> **Download:** [GitHub](https://github.com/swag617/SwagTournaments)
