# Tournament Templates

Every tournament in SwagTournaments is defined by a single YAML file in `plugins/SwagTournaments/tournaments/<id>.yml`. `TemplateManager.reloadAll()` loads every file in that folder at startup and on `/tadmin reload`; each file becomes a `TournamentTemplate` via `TournamentTemplate.fromConfig(id, config, log)`.

There is no template editor built into the in-game GUI for *creating* new templates from scratch — new templates are created either by hand-editing YAML or through the [Web Editor](../web-editor/overview.md), which has a full template form.

## Full Schema

This is the exact structure `TournamentTemplate.fromConfig()` parses, with the real defaults applied when a key is missing:

```yaml
id: "most_fish"                      # matches the filename (not read from inside the file)
display-name: "&6Most Fish Caught"   # default: the id itself
description: "Catch as many fish as possible!"   # default: ""
icon-material: COD                   # default: PAPER; falls back to PAPER if invalid
bar-color: BLUE                      # default: WHITE; BossBar color, falls back to WHITE if invalid
bar-style: SOLID                     # default: SOLID; falls back to SOLID if invalid

type: FISHING          # FISHING | FARMING | COMBAT | MINING | ECONOMY | CUSTOM — default CUSTOM
scoring-mode: SUM      # SUM | MAX | UNIQUE_COUNT | FIRST_TO — default SUM
score-formula: "1"     # passed to FormulaEvaluator — default "1"
target-score: 0        # only meaningful for FIRST_TO; 0 = no early-end target — default 0

# Type-specific filters — empty list = no filter (all apply)
farming-actions: [HARVEST]          # currently only HARVEST is checked by the bridge
mining-materials: []                # reserved for MiningScoreEngine filtering
combat-entities: []                 # reserved for CombatScoreEngine filtering

conditions:
  biomes: []                        # default [] — stored/editable, not currently enforced by any engine
  worlds: []                        # default [] — stored/editable, not currently enforced by any engine
  time: ANY                         # default "ANY" — stored/editable, not currently enforced by any engine
  weather: ANY                      # default "ANY" — stored/editable, not currently enforced by any engine
  min-y: -64                        # default -64 — enforced by MiningScoreEngine only
  max-y: 320                        # default 320 — enforced by MiningScoreEngine only
  required-permission: ""           # default "" — stored/editable, not currently enforced anywhere

schedule:
  slots:
    - day: SATURDAY    # DayOfWeek name, or DAILY/omitted for every day
      time: "18:00"    # HH:mm, parsed with LocalTime.parse — falls back to 00:00 if invalid
      duration: 60     # minutes — default 60 if omitted
  in-rotation: true     # eligible for the auto-rotation pool — default false
  rotation-weight: 10   # relative weight in the pool — default 1

messages:
  start: "&6★ Tournament &e{type}&6 has begun! Duration: &e{duration}m"
  end: "&6★ Tournament ended! Winner: &e{winner} &7({score})"
  warning: "&6★ A &e{type}&6 tournament begins in &e{time}&6!"
  # all three default to "" — SwagTournaments falls back to a built-in generic message when empty

rewards:
  1:
    money: 5000.0
    commands: ["eco give {player} 500"]
    items:
      - material: DIAMOND
        amount: 3
        display-name: "&bFirst Place Diamond"
  2:
    money: 2500.0
  3:
    money: 1000.0
  0:                    # place 0 = participation tier
    enabled: true        # must be true or the tier is skipped entirely
    commands: ["eco give {player} 50"]

integration:
  defer-to-swagfishing: true    # default false
  defer-to-swagfarming: true    # default false
  discord-channel-key: "tournaments"   # default "tournaments" (currently unused by DiscordIntegration, which posts to the single configured channel)

cosmetics:
  start-fireworks: true    # default false
  end-fireworks: true      # default false
  sound-start: ENTITY_PLAYER_LEVELUP   # Bukkit Sound enum name — default ""
  sound-end: UI_TOAST_CHALLENGE_COMPLETE
```

> Reward tiers use the **numeric placement** as the map key (`1` = 1st place, `2` = 2nd, etc.). Key `0` is the special participation tier and is only applied if it has `enabled: true` — every participant receives it regardless of final rank, in addition to their placement reward if they placed.

## Bundled Templates

| File | Type | Mode | Score Hook |
|---|---|---|---|
| `most_fish.yml` | FISHING | SUM | +1 per catch (defers to SwagFishing bridge) |
| `biggest_catch.yml` | FISHING | MAX | score = fish size |
| `crop_harvest.yml` | FARMING | SUM | +1 per harvest |
| `monster_slayer.yml` | COMBAT | SUM | +1 per mob kill |
| `diamond_rush.yml` | MINING | FIRST_TO (target 50) | diamond ores only |
| `speed_miner.yml` | MINING | SUM | any ore |

> **Conditions block:** `biomes`, `worlds`, `time`, `weather`, and `required-permission` are all parsed from the YAML, stored on `TournamentTemplate`, and fully editable through the web editor — but no scoring engine currently checks them. Only `min-y`/`max-y` are actually enforced, and only by `MiningScoreEngine`. Treat the rest as reserved fields rather than working restrictions until a future update wires them into the event handlers.

## Lifecycle

A template only becomes a running tournament when it is **started** — by `/tadmin start <id> [minutes]`, by a matching cron slot, by the auto-rotation pool, or via the web editor's "Start Now" action. Starting creates a `TournamentInstance` (in-memory state: participants, scores, BossBar, timers) and inserts a row into `tournament_instances`. Only one tournament can be active at a time — `TournamentManager.startTournament()` rejects a new start if `currentInstance` is already `ACTIVE`.

On start:
1. The `ScoringEngine` for the template's type is activated with a score-submit callback.
2. A `BossBar` is created and added to all online players, updating every second with time remaining.
3. The configured start message is broadcast, plus optional sound and fireworks.
4. A delayed task ends the tournament automatically after the configured duration.

On end (duration elapsed, `/tadmin stop`, or `FIRST_TO` target reached):
1. The scoring engine is deactivated (`HandlerList.unregisterAll` where applicable) and the BossBar removed.
2. Final standings are broadcast (winner + top 3).
3. `RewardManager.distribute()` pays out placement and participation rewards.
4. Scores and final ranks are flushed to SQLite, and `player_tournament_stats` is updated per participant.
5. `IntegrationManager.onTournamentEnd()` fires the Discord "ended" embed if configured.

See [Scoring Modes & Engines](scoring.md) for how score deltas are computed and applied, and [Rewards](rewards.md) for the payout details.
