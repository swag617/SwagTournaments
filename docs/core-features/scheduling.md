# Scheduling

`SchedulerManager` runs two independent scheduling mechanisms side by side: fixed **cron slots** defined per-template, and an optional **auto-rotation pool** configured globally.

## Cron Slots

Each template can define one or more slots under `schedule.slots`:

```yaml
schedule:
  slots:
    - day: SATURDAY    # a DayOfWeek name, or omit/DAILY for every day
      time: "18:00"    # HH:mm wall-clock time
      duration: 60     # minutes
```

At startup and after every `/tadmin reload`, `SchedulerManager.rebuildSlots()` flattens every loaded template's slots into one list. A `BukkitRunnable` checks every second (every 60 ticks) whether the current minute matches any slot:

- An `epochMinute` guard ensures each minute is only processed once, even though the check runs every second.
- `warning-minutes` (from `auto-schedule.warning-minutes`, shared with the auto-rotation config) minutes before a slot fires, the template's `messages.warning` (or a generic fallback) is broadcast.
- When a slot's time arrives: if a tournament is already active, or if it's a `FISHING` template and SwagFishing has its own tournament active, the fire is skipped and logged — it does **not** queue for later.
- Otherwise `TournamentManager.startTournament(template, slot.duration, "CRON")` is called.

`/tournament next` (or `/t next`) shows the next upcoming cron-scheduled start (searching up to 7 days ahead) by calling `SchedulerManager.getNextScheduledStart()`.

## Auto-Rotation Pool

A second, independent mechanism controlled entirely by `config.yml`:

```yaml
auto-schedule:
  enabled: false
  interval-minutes: 180
  duration-minutes: 30
  min-players: 2
  warning-minutes: 5
```

When enabled, `SchedulerManager` schedules the next auto-start `interval-minutes` from now and checks every minute (`runTaskTimer`, 1200 ticks) whether it's time to fire:

1. If a tournament is already active, the check is deferred by 1 minute and retried.
2. If fewer than `min-players` are online, deferred by 1 minute and retried.
3. Otherwise, a template is picked via **weighted random selection** from all templates with `in-rotation: true`, weighted by `rotation-weight` (higher weight = more likely to be picked; ties/zero-weight pools fall back to uniform random).
4. If the picked template is `FISHING` and SwagFishing has an active tournament, the pick is skipped for this cycle and the next interval is scheduled.
5. Otherwise `TournamentManager.startTournament(picked, duration-minutes, "AUTO_ROTATION")` is called, and the next interval is scheduled from now.

A warning broadcast is scheduled `warning-minutes` before each auto-rotation fire (only if `interval-minutes > warning-minutes`), using the picked template's `messages.warning` or a generic fallback naming its type.

## Overlap Rules

Both scheduling modes — and `/tadmin start` — share the same overlap checks in `TournamentManager.startTournament()`:

1. **Only one tournament active at a time.** A start is rejected outright if `currentInstance` is already `ACTIVE`.
2. **FISHING vs. SwagFishing.** If SwagFishing is installed, any attempt to start a `FISHING`-type tournament while SwagFishing's own tournament system reports active is blocked (checked both by the cron/auto-rotation scheduler *before* calling `startTournament`, and implicitly avoided by the SwagFishing bridge's defer logic during scoring).

There is no cross-type overlap suppression beyond "one tournament total" — you cannot run a MINING and a COMBAT tournament simultaneously either, by design (`TournamentManager` only tracks a single `currentInstance`).

## `SchedulerManager.getSlots()`

Returns an unmodifiable view of the current flattened cron slot list — used by the web editor's `/api/schedule` endpoint to render the Schedule tab.

Next: [Rewards](rewards.md)
