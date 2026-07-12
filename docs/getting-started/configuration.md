# Configuration

SwagTournaments splits configuration across three files: `config.yml` (plugin-wide settings), `messages.yml` (all player-facing text), and one YAML file per tournament in the `tournaments/` folder (see [Tournament Templates](../core-features/tournaments.md)).

## config.yml

This is the complete default file — every key shown here exists with these defaults:

```yaml
web-editor:
  enabled: true

discord:
  enabled: true
  announcements-server: 1
  announcements-channel-id: ""
  live-updates-enabled: false
  live-updates-interval-minutes: 15
  colors:
    FISHING: "#3399FF"
    FARMING: "#44BB44"
    COMBAT: "#FF4444"
    MINING: "#AAAAAA"
    ECONOMY: "#FFDD00"
    CUSTOM: "#AA44AA"

auto-schedule:
  enabled: false
  interval-minutes: 180
  duration-minutes: 30
  min-players: 2
  warning-minutes: 5

integrations:
  swag-fishing:
    enabled: true
  swag-farming:
    enabled: true
  discord-utils:
    enabled: true
```

### `web-editor`

| Key | Default | Description |
|---|---|---|
| `web-editor.enabled` | `true` | If `false`, `WebServerManager.start()` skips registration entirely — the web editor will not be mounted even if SwagAPI is installed. |

### `discord`

| Key | Default | Description |
|---|---|---|
| `discord.enabled` | `true` | Master switch for all Discord embeds (start, end, live update). |
| `discord.announcements-server` | `1` | Index passed to `DiscordBot.getTextChannel(serverIndex, channelId)` — which configured Discord server/guild to use. |
| `discord.announcements-channel-id` | `""` | Channel ID to post to. Leave blank or `"YOUR_CHANNEL_ID"` to disable posting (checked explicitly before every send). |
| `discord.live-updates-enabled` | `false` | If `true`, periodic live-standings embeds are sent during an active tournament. |
| `discord.live-updates-interval-minutes` | `15` | Interval between live-standings embeds. |
| `discord.colors.<TYPE>` | per-type hex | Embed side color per tournament type. Falls back to the built-in map if the hex fails to parse. |

### `auto-schedule`

Controls the **weighted-random auto-rotation pool** — a second, optional scheduling mode alongside per-template cron slots. See [Scheduling](../core-features/scheduling.md) for the full mechanics.

| Key | Default | Description |
|---|---|---|
| `auto-schedule.enabled` | `false` | Enables the auto-rotation task. |
| `auto-schedule.interval-minutes` | `180` | Minutes between auto-rotation tournament starts. |
| `auto-schedule.duration-minutes` | `30` | Duration of each auto-started tournament. |
| `auto-schedule.min-players` | `2` | Minimum online players required for auto-rotation to fire; otherwise it retries every minute. |
| `auto-schedule.warning-minutes` | `5` | How far in advance (for both cron slots and auto-rotation) a warning broadcast is sent. |

### `integrations`

| Key | Default | Description |
|---|---|---|
| `integrations.swag-fishing.enabled` | `true` | Reserved toggle for the SwagFishing bridge (presence is still auto-detected). |
| `integrations.swag-farming.enabled` | `true` | Reserved toggle for the SwagFarming bridge. |
| `integrations.discord-utils.enabled` | `true` | Reserved toggle for the Discord integration. |

> **Note:** Regardless of these `integrations.*` flags, a bridge only activates if the corresponding plugin is actually installed and detected by `IntegrationManager.initialize()` at startup.

## messages.yml

> **Not currently wired up.** A `messages.yml` resource file ships in the jar with a full set of message keys (`tournament.*`, `leaderboard.*`, `rewards.*`, `stats.*`, `history.*`, `next.*`, `admin.*`), but no code in the current build actually loads or reads it — `SwagTournaments.onEnable()` never calls `saveResource("messages.yml", ...)`, and no manager parses it. All player-facing broadcasts and command feedback are hardcoded directly in the Java source (`TournamentManager`, `SchedulerManager`, `TournamentCommand`, `TournamentAdminCommand`). Per-template messages (`messages.start` / `messages.end` / `messages.warning` in each tournament YAML) **do** work, since those are read straight off the `TournamentTemplate` object — see [Tournament Templates](../core-features/tournaments.md).

## Reloading

`/tadmin reload [templateId]` reloads either one template or all templates from disk and rebuilds the scheduler's cron slot list — use this after editing any file in `tournaments/` by hand. Editing `config.yml` requires a full server restart, since it has no dedicated hot-reload path in the current implementation.

Next: [Tournament Templates](../core-features/tournaments.md)
