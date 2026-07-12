# Scoring Modes & Engines

## Scoring Modes

Set per-template via `scoring-mode`. Applied in `TournamentInstance.submitScore()`:

| Mode | Behavior |
|---|---|
| `SUM` | Additive — each delta is added to the player's running total. |
| `MAX` | Personal best — the score is only replaced if the new delta is greater than the current score (not added). |
| `UNIQUE_COUNT` | Counts distinct `uniqueKey` values submitted by the player; the score becomes the count of unique keys seen. Requires the scoring engine to pass a non-empty `unique_key` in its submission context — none of the six built-in engines currently do this, so `UNIQUE_COUNT` is primarily intended for `CUSTOM` tournaments driven by `SwagTournamentsAPI.submitCustomScore()` with a context map. |
| `FIRST_TO` | Additive like `SUM`, but `TournamentManager.submitScore()` checks the running total against `target-score` after every submission. Once a player's score reaches the target, the tournament ends automatically (scheduled one tick later so the triggering event finishes cleanly). A `target-score` of `0` disables early-ending — the mode then behaves exactly like `SUM` until the timer runs out. |

Leaderboards (`TournamentInstance.getLeaderboard()`) always sort by score descending, with ties broken by earliest join time.

## Scoring Engines

Each `TournamentType` maps to a `ScoringEngine` in `ScoringEngineRegistry`, activated when a tournament starts and deactivated (unregistering its listeners) when it ends.

| Type | Engine | Trigger | Score Formula Variables |
|---|---|---|---|
| FISHING | `FishingScoreEngine` (or `SwagFishingBridge`'s deferring wrapper) | `PlayerFishEvent` (`CAUGHT_FISH`) | `size`, `value` |
| FARMING | `FarmingScoreEngine` (or `SwagFarmingBridge`'s deferring wrapper) | `BlockBreakEvent` on fully-grown crops | `value`, `quality` |
| COMBAT | `CombatScoreEngine` | `EntityDeathEvent` with a player killer, hostile mob only | `max_health`, `entity_type` (ordinal) |
| MINING | `MiningScoreEngine` | `BlockBreakEvent` on ore blocks | `y_level`, `value` |
| ECONOMY | `EconomyScoreEngine` | Polls each online player's Vault balance every 60s; scores the positive delta since the last poll | — (delta is the raw balance increase) |
| CUSTOM | `CustomScoreEngine` | No automatic hooks — scores are only submitted via `/tadmin score` or `SwagTournamentsAPI.submitCustomScore()` | whatever the caller passes |

All event-driven engines listen at `EventPriority.MONITOR` with `ignoreCancelled = true` — they observe after everything else has processed the event and never act on a cancelled interaction.

### COMBAT details

Only entities considered hostile are counted: `Monster`, `Slime`, `Ghast`, `Phantom`, `Shulker`, `ElderGuardian`, `Guardian`, `Warden`. Player deaths never count. If `combat-entities` is non-empty on the template, only those specific `EntityType` names score.

### MINING details

If `mining-materials` is empty, any of a built-in ore set counts (coal, iron, copper, gold, redstone, lapis, diamond, emerald, quartz, nether gold, ancient debris — including deepslate variants). If the list is non-empty, only those exact materials score. The break must also fall within the template's `conditions.min-y`/`max-y` range.

### FARMING details

Without SwagFarming installed, `FarmingScoreEngine` only scores vanilla crop blocks (wheat, carrots, potatoes, beetroot, nether wart, cocoa, melon, pumpkin, sugar cane, bamboo, sweet berries, kelp, chorus flower) that are fully grown (`Ageable` at max age). With SwagFarming present and `defer-to-swagfarming: true` on the template, `SwagFarmingBridge` takes over and adds a fertilizer-quality bonus to the score value when SwagFarming recognizes the block as a managed crop; it falls back to the vanilla check for locations SwagFarming doesn't track.

### FISHING details

Without SwagFishing installed, `FishingScoreEngine` scores every catch with a fixed `size=1.0`. With SwagFishing present and `defer-to-swagfishing: true` on the template, `SwagFishingBridge` reads the caught item's `fish_size` PDC value (from SwagFishing's own namespace) so `MAX`-mode "biggest catch" templates can score by actual fish size.

### ECONOMY details

Requires Vault with a registered `Economy` provider. If Vault isn't found when the tournament starts, the engine logs a warning and simply never scores anyone (the tournament still runs, just with no participants unless money changes hands after Vault becomes available). Only balance *increases* are scored — spending money never decreases your tournament score.

## Formula Evaluator

`score-formula` is parsed by an in-house recursive-descent evaluator (`FormulaEvaluator`) — no scripting engine, so there's no code-injection surface. Supported grammar:

```
expr   = term ( ('+' | '-') term )*
term   = factor ( ('*' | '/') factor )*
factor = NUMBER | IDENTIFIER | 'Math.max' '(' expr ',' expr ')'
                             | 'Math.min' '(' expr ',' expr ')'
                             | '(' expr ')'
```

- Division by zero returns `0.0` rather than throwing.
- An unknown identifier evaluates to `0.0` (`vars.getOrDefault(name, 0.0)`).
- A blank/null formula evaluates to `1.0`.
- Available variable names depend entirely on which engine is scoring (see the table above) — e.g. `"size * 2"` only makes sense for FISHING, `"Math.max(value, 1)"` works for FARMING/MINING.

Next: [Scheduling](scheduling.md)
