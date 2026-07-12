# FAQ

**Do I need SwagFishing or SwagFarming installed to use SwagTournaments?**
No. All six tournament types work standalone. FISHING and FARMING templates simply score with vanilla Bukkit events (`PlayerFishEvent`, `BlockBreakEvent` on vanilla crops) when their respective plugin isn't present — see [Scoring Modes & Engines](../core-features/scoring.md).

**Can two tournaments run at the same time?**
No. `TournamentManager` tracks a single `currentInstance` — starting a new tournament while one is active is always rejected, regardless of type.

**Can players create their own tournaments?**
No. Only `swagtournaments.admin` holders can start tournaments (`/tadmin start`), whether manually, via cron, or via the web editor. Players can only browse, view leaderboards, and check stats/history.

**How do I add a new tournament type without editing Java?**
You can't add a genuinely new *type* — the six `TournamentType` values (`FISHING`, `FARMING`, `COMBAT`, `MINING`, `ECONOMY`, `CUSTOM`) are fixed in code. But you can create unlimited **templates** of any existing type with different formulas, filters, schedules, and rewards. For anything the six types can't express, use `CUSTOM` and drive scoring via `/tadmin score` or `SwagTournamentsAPI.submitCustomScore()`.

**What happens to an active tournament if the server restarts?**
`onDisable()` calls `TournamentManager.shutdownFlush()`, which synchronously (not async, since scheduled tasks won't run after `onDisable` returns) persists the current scores and marks the tournament `ENDED` in the database — but it does **not** distribute rewards or send broadcasts. The tournament is simply cut short; there's no resume-on-restart.

**Is there a way to grant partial admin access (e.g. `/tadmin list` but not `/tadmin start`)?**
No — `swagtournaments.admin` is all-or-nothing across every admin subcommand. See [Permission Nodes](../permissions/permissions.md).

**Does the web editor require its own password?**
No — it's mounted on SwagAPI's shared web service and gated by SwagAPI's own session login. See [Web Editor Overview](../web-editor/overview.md).

**Why does `/tadmin webtrust` not give me a token anymore?**
An earlier design minted its own 30-day trust tokens; the current build removed that in favor of SwagAPI's shared login, so the command just prints the panel URL now.

Next: [Troubleshooting](troubleshooting.md)
