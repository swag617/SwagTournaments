# SwagTournaments

Flexible tournament and competition framework for Paper 1.21+. Every tournament is a YAML **template** — display name, scoring mode, scoring formula, schedule, rewards, and cosmetics all live in one file, so new tournament types are new YAML files, not new code. Six built-in tournament types cover fishing, farming, combat, mining, and economy, plus an API-driven `CUSTOM` type for anything else.

📖 **[Live Documentation](https://swag617.github.io/SwagTournaments/)** · 📦 **[Releases](https://github.com/swag617/SwagTournaments/releases)**

## Features

- **Template-driven** — new tournament types are new YAML files in `tournaments/`; changes apply with `/tadmin reload`, no restart
- **Four scoring modes** — `SUM` (additive), `MAX` (personal best), `UNIQUE_COUNT` (distinct items/locations), `FIRST_TO` (race to a target, ends early when reached)
- **Six tournament types** — `FISHING`, `FARMING`, `COMBAT`, `MINING`, `ECONOMY`, `CUSTOM` — each backed by its own `ScoringEngine`
- **In-house formula evaluator** — `score-formula` supports `+ - * /`, `Math.max()`, `Math.min()`, and named variables — no scripting engine, no security surface
- **Two scheduling modes** — fixed weekly/daily cron slots per template, and a weighted-random auto-rotation pool
- **Soft-dependency bridges** — SwagFishing and SwagFarming bridges add richer scoring context and suppress overlapping tournaments
- **Web editor** — a full template/schedule/history/config SPA mounted on SwagAPI's shared panel, gated by SwagAPI's own login
- **Discord announcements** — start/end embeds (and optional live standings), published via SwagAPI's shared event bus for DiscordUtils to pick up
- **PlaceholderAPI expansion** — 8+ dynamic placeholders for scoreboards and chat, including per-rank `top_N_name`/`top_N_score`
- **Admin tools** — `/tournamentadmin` for starting/stopping/scoring tournaments and reloading templates live

## Requirements

| Dependency | Required | Notes |
|---|---|---|
| Paper 1.21+ | Yes | Built against Paper API `1.21.4-R0.1-SNAPSHOT`; `plugin.yml` declares `api-version: '1.21'` |
| Java 21 | Yes | |
| [SwagAPI](https://github.com/swag617/SwagAPI) | **Yes** | Hard dependency (`depend: [SwagAPI]`) — Paper will not enable SwagTournaments without it. At runtime, only the web editor module calls into SwagAPI's `IWebService`; tournament data itself lives in SwagTournaments' own bundled SQLite database. |
| SwagFishing | No | Enables the FISHING scoring bridge (richer scoring via fish size) |
| SwagFarming | No | Enables the FARMING scoring bridge (richer scoring via crop quality) |
| DiscordUtils | No | Enables tournament start/end Discord embeds, via SwagAPI's shared event bus |
| Vault | No | Required for money rewards and the ECONOMY tournament type |
| PlaceholderAPI | No | Enables `%swagtournaments_*%` placeholders |

## Storage

Tournament instances, participants, and per-player stats are stored in the plugin's own bundled **SQLite database** (`plugins/SwagTournaments/tournaments.db`, WAL mode) — not through SwagAPI's shared database service. SwagAPI is only used for the optional web editor's shared HTTP service and login.

## Building from Source

### Prerequisites
- Java JDK 21
- Maven 3.6+

### Build Command

```bash
mvn clean package
```

The shade plugin relocates its bundled Gson and SQLite JDBC driver and outputs the packaged JAR to `target/`.

> `pom.xml` references `libs/SwagAPI-1.0.0.jar` (system-scoped, required) and `libs/SwagFishing-1.0.0-SNAPSHOT.jar` / `libs/SwagFarming-1.0.0.jar` (system-scoped, optional bridges) — make sure the jars you need are present at those paths before building.

## Installation

1. Install [SwagAPI](https://github.com/swag617/SwagAPI) first — SwagTournaments will not enable without it.
2. Drop `SwagTournaments.jar` into your server's `plugins/` folder.
3. (Optional) Install SwagFishing, SwagFarming, DiscordUtils, Vault, and/or PlaceholderAPI before starting the server if you want their integrations active from first boot.
4. Start the server, then run `/tadmin list` to confirm the 6 bundled example templates loaded.

Full setup walkthrough: [Installation guide](https://swag617.github.io/SwagTournaments/#/getting-started/installation).

## Project Structure

```
SwagTournaments/
├── pom.xml                                              # Maven build configuration
├── src/main/
│   ├── java/com/swag/tournaments/
│   │   ├── SwagTournaments.java                         # Main plugin class
│   │   ├── api/SwagTournamentsAPI.java                  # Public API for CUSTOM scoring
│   │   ├── commands/                                    # /tournament and /tournamentadmin
│   │   ├── database/                                    # SQLite persistence layer
│   │   ├── engine/                                      # Scoring engines + formula evaluator
│   │   │   └── engines/                                 # Combat/Custom/Economy/Farming/Fishing/Mining
│   │   ├── gui/                                         # List/Info/Leaderboard/Admin GUIs
│   │   ├── integration/                                 # SwagFishing/SwagFarming/Discord bridges
│   │   ├── listener/                                    # Join/quit listener
│   │   ├── manager/                                     # Reward/Scheduler/Template/Tournament managers
│   │   ├── model/                                       # Templates, instances, participants, enums
│   │   ├── placeholder/                                 # PlaceholderAPI expansion
│   │   └── web/                                         # Web editor (mounted on SwagAPI's IWebService)
│   └── resources/
│       ├── plugin.yml                                   # Plugin metadata
│       ├── config.yml                                   # Main configuration
│       ├── messages.yml                                 # Player-facing message strings
│       ├── tournaments/                                 # 6 bundled example templates
│       └── web/                                         # Web editor frontend (HTML/CSS/JS)
└── docs/                                                # Docsify documentation site
```

## Documentation

Full docs (installation, configuration, tournament templates, scoring, scheduling, rewards, integrations, web editor, commands, permissions) are published at **https://swag617.github.io/SwagTournaments/**.

## License

Proprietary — SwagDev internal use.

---

**SwagTournaments** v1.2.0 · Built for Paper 1.21+ · Java 21
