# Admin Commands

## `/tournamentadmin`

Aliases: `/ta`, `/tadmin`
Permission: `swagtournaments.admin` (default: `op`)
Works from console for most subcommands — only the no-args GUI form requires a player.

```
/tournamentadmin                       → opens the Admin Panel GUI (player only)
/tournamentadmin start <id> [minutes]  → starts a tournament immediately
/tournamentadmin stop                  → stops the active tournament
/tournamentadmin reload [id]           → reloads one template, or all templates
/tournamentadmin list                  → lists all loaded templates in chat
/tournamentadmin score <player> <amt>  → adds score (CUSTOM type tournaments only)
/tournamentadmin webtrust              → prints the web editor URL
```

### `/tadmin` (no args)

Opens `AdminPanelGUI` — up to 21 template slots (left-click to start for 30 minutes, right-click to view info), an active-tournament status item, a Stop button (dimmed when nothing is active), and a Reload Templates button that reloads and rebuilds the GUI in place. Requires a player; console gets an error message.

### `/tadmin start <templateId> [durationMinutes]`

Starts the named template immediately. `durationMinutes` defaults to `30` if omitted, must be a positive integer. Fails with an error if another tournament is already active, or (for FISHING templates) if SwagFishing's own tournament is currently running.

### `/tadmin stop`

Stops the currently active tournament — same as it running out the clock, except triggered manually. Errors if nothing is active.

### `/tadmin reload [templateId]`

With an ID: reloads just that one template file from disk. Without: reloads every file in `tournaments/` and reports how many loaded. Either way, the scheduler's cron slot list is rebuilt afterward so schedule changes take effect immediately.

### `/tadmin list`

Prints every loaded template to chat with an `[ACTIVE]`/`[idle]` tag, its type, scoring mode, and display name.

### `/tadmin score <player> <amount>`

Manually submits a score delta for an online player. **Only works while a `CUSTOM`-type tournament is active** — attempting this during any other tournament type is rejected. This is the primary way to award points in a `CUSTOM` tournament outside of `SwagTournamentsAPI.submitCustomScore()`.

### `/tadmin webtrust`

Prints the web editor's URL (from `WebServerManager.getUrl()`) if the web editor is registered and available. If SwagAPI isn't installed or `web-editor.enabled` is `false`, prints an error instead. Despite the name, this command **no longer generates a token** — see [Web Editor Overview](../web-editor/overview.md) for why. Access is controlled entirely by logging into the shared SwagAPI panel.

## Tab Completion

- Arg 1: `start`, `stop`, `reload`, `list`, `score`, `webtrust`
- Arg 2 for `start`/`reload`: loaded template IDs
- Arg 2 for `score`: online player names
- Arg 3 for `start`: suggested durations `30`, `60`, `90`, `120`

Next: [Permission Nodes](../permissions/permissions.md)
