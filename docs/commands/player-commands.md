# Player Commands

## `/tournament`

Aliases: `/t`, `/tourney`
Permission: `swagtournaments.use` (default: `true` for all players)
Player-only — cannot be run from console.

```
/tournament                 → opens the Tournament List GUI
/tournament info [id]       → opens the Tournament Info GUI
/tournament top [id]        → opens the live Leaderboard GUI
/tournament history         → opens the Past Tournaments GUI (async-loaded)
/tournament stats [player]  → prints all-time stats to chat
/tournament next            → prints the next scheduled tournament to chat
```

### `/tournament` (no args)

Opens `TournamentListGUI` — a paginated browse of every loaded template, 7 per page, with a status item showing the currently active tournament (if any).

### `/tournament info [id]`

Opens `TournamentInfoGUI` for the given template ID. If no ID is given:
- If a tournament is currently active, opens info for that tournament's template.
- Otherwise, falls back to opening the Tournament List GUI.

### `/tournament top [id]`

Opens the live `TournamentLeaderboardGUI` for the given template ID, or the active tournament if no ID is given. If no tournament is active and no ID is given, you get an error message — you must specify a template.

### `/tournament history`

Opens `PastTournamentsGUI`, which loads history data from SQLite asynchronously before opening — there's a brief delay while the query runs off the main thread.

### `/tournament stats [player]`

Prints directly to chat (not a GUI): tournaments entered, tournaments won, 1st/2nd/3rd place counts, and best score ever, pulled from `player_tournament_stats`. Defaults to yourself if no player name is given. Works for offline players (looked up via `Bukkit.getOfflinePlayer`).

### `/tournament next`

Prints the next upcoming cron-scheduled tournament (template name, fire time, type) by searching up to 7 days ahead across all templates' cron slots. If auto-rotation is enabled and its next fire time is sooner than any cron slot, that's shown instead. Prints a message if the scheduler isn't running or nothing is scheduled.

## Tab Completion

- Arg 1: `info`, `top`, `history`, `stats`, `next`
- Arg 2 for `info`/`top`: loaded template IDs
- Arg 2 for `stats`: online player names

Next: [Admin Commands](admin-commands.md)
