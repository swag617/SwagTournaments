# Permission Nodes

SwagTournaments registers exactly two permission nodes in `plugin.yml`:

| Node | Default | Grants |
|---|---|---|
| `swagtournaments.use` | `true` (all players) | `/tournament` and all of its subcommands (`info`, `top`, `history`, `stats`, `next`) — the entire player-facing GUI and command surface |
| `swagtournaments.admin` | `op` | `/tournamentadmin` and all of its subcommands (`start`, `stop`, `reload`, `list`, `score`, `webtrust`) — the entire admin surface, including the web editor link |

```yaml
permissions:
  swagtournaments.use:
    description: Access player tournament commands and GUI
    default: true
  swagtournaments.admin:
    description: Access admin tournament commands
    default: op
```

## Notes

- There are **no granular sub-permissions** — a player either has full access to `/tournament` or none at all, and an admin either has full access to `/tournamentadmin` or none at all. There's no way to grant, say, `/tournamentadmin list` without also granting `start`/`stop`/`score`.
- Both commands enforce their permission at the very top of `onCommand()` and again in `onTabComplete()`, so a player without `swagtournaments.admin` won't even see tab-completion suggestions for `/tadmin`.
- The web editor does **not** use these permission nodes for access control — it's gated by SwagAPI's own session login, independent of in-game permissions. See [Web Editor Overview](../web-editor/overview.md).
- Each tournament template also supports `conditions.required-permission` in its YAML — this field is parsed and stored on `TournamentTemplate` but is not currently enforced by any scoring engine or GUI as a participation gate; treat it as reserved for future use rather than a working restriction.

Next: [Troubleshooting](../troubleshooting/troubleshooting.md)
