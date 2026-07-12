# Installation

## Requirements

| Dependency | Required | Purpose |
|---|---|---|
| Paper 1.21+ | Yes | `api-version: '1.21'` in `plugin.yml` |
| Java 21 | Yes | Compiled with `--release 21` |
| SwagAPI | Only for the web editor | Provides the shared `IWebService` the web editor mounts on |
| SwagFishing | No | Enables the FISHING scoring bridge |
| SwagFarming | No | Enables the FARMING scoring bridge |
| DiscordUtils | No | Enables tournament start/end Discord embeds |
| Vault | No | Required for money rewards and the ECONOMY tournament type |
| PlaceholderAPI | No | Enables `%swagtournaments_*%` placeholders |

All soft dependencies are detected automatically at startup — `IntegrationManager` checks `Bukkit.getPluginManager().getPlugin(...)` for each one and only wires up the corresponding bridge if it's present. You do not need to enable or disable anything for a missing soft-dep; the plugin simply skips that integration.

## Steps

1. Download or build `SwagTournaments.jar` and drop it into your server's `plugins/` folder.
2. (Optional) Install any of the soft-dependencies above **before** starting the server if you want their integrations active from the first boot.
3. Start the server. On first run, SwagTournaments will:
   - Create `plugins/SwagTournaments/config.yml`, `messages.yml`
   - Copy 6 bundled example templates into `plugins/SwagTournaments/tournaments/`
   - Create `plugins/SwagTournaments/tournaments.db` (SQLite, WAL mode)
4. Verify the plugin loaded: `/tadmin list` should show the 6 bundled templates.
5. (Optional) Register with SwagAPI for the web editor — see [Web Editor Overview](../web-editor/overview.md).

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
