# Leaderboards & GUIs

All in-game GUIs are 54-slot chest inventories built on a shared `GUIBase` (`buildGUI()`, `handleClick()`, `open()`). `GUIListener` tracks the currently open GUI per player in a `Map<UUID, GUIBase>` and routes `InventoryClickEvent`/`InventoryCloseEvent` to it — every click on a tracked GUI is cancelled automatically so items can never be taken out.

| GUI | Opened By | Purpose |
|---|---|---|
| `TournamentListGUI` | `/tournament` (no args) | Paginated browse of all loaded templates (7 per page), with a status item showing the active tournament if any. Click a template to open its info page. |
| `TournamentInfoGUI` | `/tournament info [id]`, clicking a template in the list | Full detail view for one template — info, rewards summary, and (for admins in some flows) start/stop. |
| `TournamentLeaderboardGUI` | `/tournament top [id]` | Live standings for the active or specified tournament. Auto-refreshes; the refresh task is cancelled automatically when the GUI closes. |
| `PastTournamentsGUI` | `/tournament history` | Paginated tournament history loaded **async** from SQLite (28 rows/page) — the GUI is only opened once the DB query completes and results are marshalled back to the main thread. |
| `AdminPanelGUI` | `/tournamentadmin` (no args) | Admin control surface — up to 21 template slots, an active-tournament status item, stop button, and a "Reload Templates" button that rebuilds the GUI in place after reloading. |

## Player Commands That Drive These GUIs

- `/tournament` — opens `TournamentListGUI`.
- `/tournament info [id]` — opens `TournamentInfoGUI` for the given template, or the currently active tournament's template if no ID is given; falls back to the list GUI if nothing is active and no ID was given.
- `/tournament top [id]` — opens `TournamentLeaderboardGUI`; requires either an active tournament or an explicit template ID.
- `/tournament history` — opens `PastTournamentsGUI` (async).
- `/tournament stats [player]` — chat-only, not a GUI (see below).
- `/tournament next` — chat-only, shows the next scheduled start.

## `/tournament stats`

Prints a player's all-time tournament record from `player_tournament_stats` directly to chat: tournaments entered, tournaments won, 1st/2nd/3rd place counts, and best score ever. Defaults to the command sender if no player name is given.

## Live Standings & PlaceholderAPI

`TournamentLeaderboardGUI` refreshes its own in-game view periodically while open. For scoreboards or chat formatting outside of a GUI, see [PlaceholderAPI](placeholders.md) — the `my_rank`, `my_score`, and `top_N_name`/`top_N_score` placeholders expose the same leaderboard data (`TournamentInstance.getLeaderboard()`) without opening any inventory.

Next: [Integrations](integrations.md)
