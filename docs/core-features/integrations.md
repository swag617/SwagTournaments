# Integrations

`IntegrationManager.initialize()` detects each soft-dependency at startup with `Bukkit.getPluginManager().getPlugin(...)`. Nothing is required — every integration below degrades gracefully to "not present" when the target plugin isn't installed, and the corresponding tournament types/rewards simply run with vanilla behavior instead.

## SwagFishing Bridge

Active when the `SwagFishing` plugin is detected. `SwagFishingBridge` registers a `MONITOR`-priority `PlayerFishEvent` listener and replaces the registry's default `FishingScoreEngine` with a **deferring wrapper**:

- If a FISHING template has `integration.defer-to-swagfishing: true`, the bridge's own listener handles scoring (reading `fish_size` from SwagFishing's PDC) and the vanilla engine does nothing for that tournament — this avoids double-scoring a catch.
- If the template has it set to `false` (or omitted), the vanilla `FishingScoreEngine` runs as normal, scoring every catch as `size = 1.0`.

**Overlap suppression:** before starting any FISHING tournament (from `/tadmin start`, cron slots, or auto-rotation), `IntegrationManager.isSwagFishingTournamentActive()` checks SwagFishing's own `TournamentManager.isActive()`. If SwagFishing already has a tournament running, the SwagTournaments FISHING start is blocked.

SwagFishing itself is never modified — this is a one-directional, read-only integration.

## SwagFarming Bridge

Active when the `SwagFarming` plugin is detected. `SwagFarmingBridge` listens to `BlockBreakEvent` at `MONITOR` priority and, for FARMING templates with `integration.defer-to-swagfarming: true`, calls `SwagFarming`'s growth manager to check whether the broken block is a tracked crop. If it is, and it's fully grown, the score value gets a bonus derived from the crop's fertilizer quality (`1.0 + fertilizerQualityBonus`) instead of the flat `1.0` vanilla crops receive. Locations SwagFarming doesn't track fall back to the vanilla fully-grown-crop check.

SwagFarming has no tournament system of its own, so there is no overlap suppression here — only richer scoring context.

## DiscordUtils Integration

Active when the `DiscordUtils` plugin is detected and its JDA bot reports ready. `DiscordIntegration` sends `MessageEmbed`s via `DiscordUtils`' `CleanEmbedBuilder`:

| Event | Trigger | Content |
|---|---|---|
| Start | Tournament starts | Title, description, type, scoring mode, duration |
| End | Tournament ends | Top-3 with medal emoji, or "No participants" |
| Live Update | Every `discord.live-updates-interval-minutes` while active, only if `discord.live-updates-enabled: true` | Top-5 standings + time remaining |

Controlled entirely by `config.yml`'s `discord.*` keys (see [Configuration](../getting-started/configuration.md)). The embed's side color comes from `discord.colors.<TYPE>`, falling back to a built-in default per type if the configured hex is invalid. No embed is sent at all if `discord.announcements-channel-id` is blank or left as the placeholder `"YOUR_CHANNEL_ID"`.

## Vault (Economy)

Active when the `Vault` plugin is detected with a registered `Economy` provider. Two independent uses:

1. **Money rewards** — `RewardManager` deposits `money` amounts from reward tiers via Vault. Skipped (with a startup log) if Vault isn't found.
2. **ECONOMY tournament type** — `EconomyScoreEngine` polls every online player's balance every 60 seconds and scores the positive delta since the last poll.

## PlaceholderAPI

Active when `PlaceholderAPI` is detected — `TournamentsPlaceholders` self-registers in `onEnable()`. See [PlaceholderAPI](placeholders.md) for the full placeholder list.

## SwagAPI (Web Editor)

Not soft-detected the same way as the others — `WebServerManager.start()` looks up `IWebService` from Bukkit's services manager. If it isn't registered (SwagAPI not installed, or installed but its web service failed to start), the web editor logs a warning and simply doesn't register a mount point; nothing else about the plugin is affected. See [Web Editor Overview](../web-editor/overview.md).

Next: [PlaceholderAPI](placeholders.md)
