# PlaceholderAPI

`TournamentsPlaceholders` (identifier: `swagtournaments`) self-registers automatically at startup if PlaceholderAPI is detected — no config flag needed.

## Placeholders

| Placeholder | Returns |
|---|---|
| `%swagtournaments_active%` | `"true"` or `"false"` — whether a tournament is currently running |
| `%swagtournaments_type%` | The active tournament's type (`FISHING`, `FARMING`, etc.), or `"none"` |
| `%swagtournaments_name%` | The active tournament's display name, or `""` |
| `%swagtournaments_time_remaining%` | `MM:SS` countdown, or `"N/A"` if none active |
| `%swagtournaments_my_rank%` | The requesting player's current rank (`1`-based), or `"-"` if not participating/no active tournament |
| `%swagtournaments_my_score%` | The requesting player's current score, or `"0"` |
| `%swagtournaments_wins%` | The player's all-time tournament win count, from `player_tournament_stats`. Cached for 60 seconds per player and refreshed asynchronously — the first read after cache expiry returns the previous cached value while a fresh value loads in the background. |
| `%swagtournaments_top_N_name%` | The player name in rank `N` (1–10) of the current leaderboard, or `"-"` |
| `%swagtournaments_top_N_score%` | The score at rank `N` (1–10), or `"0"` |

`top_N_*` accepts any rank from 1 to 10 inclusive — e.g. `%swagtournaments_top_1_name%`, `%swagtournaments_top_5_score%`. Ranks outside 1–10, or malformed identifiers, return `null` (PlaceholderAPI renders these as empty).

Score values are formatted as a plain integer when the score is a whole number, or to two decimal places otherwise.

## Example

```
&6Tournament: &e%swagtournaments_name% &7(%swagtournaments_type%)
&7Time left: &e%swagtournaments_time_remaining%
&7Your rank: &e#%swagtournaments_my_rank% &7— &e%swagtournaments_my_score%
&7#1: &e%swagtournaments_top_1_name% &7(%swagtournaments_top_1_score%)
```

Next: [Web Editor Overview](../web-editor/overview.md)
