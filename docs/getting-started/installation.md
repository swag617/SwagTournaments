# Installation

## Requirements

| Dependency | Required | Purpose |
|---|---|---|
| Paper 1.21+ | Yes | `api-version: '1.21'` in `plugin.yml` |
| Java 21 | Yes | Compiled with `--release 21` |
| SwagAPI | **Yes** | Declared as a hard `depend` in `plugin.yml` — Paper will not enable SwagTournaments without it installed. At runtime, only the web editor module (`WebServerManager`) actually calls into SwagAPI, to register on its shared `IWebService`; tournament data itself is stored in SwagTournaments' own bundled SQLite database, not through SwagAPI. |
| SwagFishing | No | Enables the FISHING scoring bridge |
| SwagFarming | No | Enables the FARMING scoring bridge |
| DiscordUtils | No | Enables tournament start/end Discord embeds |
| Vault | No | Required for money rewards and the ECONOMY tournament type |
| PlaceholderAPI | No | Enables `%swagtournaments_*%` placeholders |

All dependencies above SwagAPI are soft and detected automatically at startup — `IntegrationManager` checks `Bukkit.getPluginManager().getPlugin(...)` for each one and only wires up the corresponding bridge if it's present. You do not need to enable or disable anything for a missing soft-dep; the plugin simply skips that integration.

## Steps

1. Install [SwagAPI](https://github.com/swag617/SwagAPI) first — SwagTournaments is declared with a hard dependency on it in `plugin.yml` and will not enable without it, even though most of the plugin's own functionality doesn't call into it directly.
2. Download or build `SwagTournaments.jar` and drop it into your server's `plugins/` folder.
3. (Optional) Install any of the soft-dependencies above **before** starting the server if you want their integrations active from the first boot.
4. Start the server. On first run, SwagTournaments will:
   - Create `plugins/SwagTournaments/config.yml`, `messages.yml`
   - Copy 6 bundled example templates into `plugins/SwagTournaments/tournaments/`
   - Create `plugins/SwagTournaments/tournaments.db` (SQLite, WAL mode)
5. Verify the plugin loaded: `/tadmin list` should show the 6 bundled templates.
6. (Optional) Confirm the web editor registered with SwagAPI — see [Web Editor Overview](../web-editor/overview.md).

## Bundled Example Templates

Six ready-to-use templates ship in `src/main/resources/tournaments/` and are copied to your data folder on first run:

| File | Type | Scoring Mode | Score Hook |
|---|---|---|---|
| `most_fish.yml` | FISHING | SUM | +1 per catch |
| `biggest_catch.yml` | FISHING | MAX | score = fish size |
| `crop_harvest.yml` | FARMING | SUM | +1 per harvest |
| `monster_slayer.yml` | COMBAT | SUM | +1 per mob kill |
| `diamond_rush.yml` | MINING | FIRST_TO (target 50) | diamond ores only |
| `speed_miner.yml` | MINING | SUM | any ore |

These are real, functioning templates — edit them directly or use them as a starting point for your own. See [Tournament Templates](../core-features/tournaments.md) for the full YAML schema.

## Permissions

Two permission nodes are registered out of the box:

| Node | Default | Grants |
|---|---|---|
| `swagtournaments.use` | `true` (all players) | `/tournament` and its subcommands |
| `swagtournaments.admin` | `op` | `/tournamentadmin` and its subcommands |

See [Permission Nodes](../permissions/permissions.md) for details.

## Commands & Aliases

Registered in `plugin.yml`:

```yaml
commands:
  tournament:
    aliases: [t, tourney]
    permission: swagtournaments.use
  tournamentadmin:
    aliases: [ta, tadmin]
    permission: swagtournaments.admin
```

Next: [Configuration](configuration.md)
