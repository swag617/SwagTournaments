# Troubleshooting

## Plugin fails to load / disables itself on startup

`onEnable()` initializes the SQLite database first and calls `getServer().getPluginManager().disablePlugin(this)` immediately if it fails, before anything else is set up. Check the console for `Failed to initialise database: ...` — this is almost always a file-permission issue on `plugins/SwagTournaments/tournaments.db`, or a corrupted database file from an unclean shutdown. Deleting `tournaments.db` (losing history) will let the plugin recreate a fresh one.

## `/tadmin list` shows 0 templates

Check `plugins/SwagTournaments/tournaments/` exists and contains `.yml` files. If the folder is empty, the 6 bundled examples should have been copied in on first startup — check the console log for template-loading warnings. Run `/tadmin reload` to force a re-scan.

## A tournament won't start (`Could not start — another tournament may be active`)

Only one tournament can run at a time, regardless of type. Check `/tadmin list` for an `[ACTIVE]` tag, or `/tournament` to see the status item. If nothing appears active but starts still fail, check the console — `TournamentManager` logs a warning naming the currently-active template.

## A FISHING tournament refuses to start

If SwagFishing is installed, SwagTournaments checks `SwagFishing`'s own tournament system before starting a FISHING-type tournament. If SwagFishing reports a tournament already running, the start is blocked (this is by design — see [Integrations](../core-features/integrations.md)). Stop SwagFishing's tournament first, or wait for it to end.

## FISHING/FARMING scores aren't updating even though `defer-to-*` is `true`

Confirm the corresponding plugin (SwagFishing / SwagFarming) is actually installed and loaded — the bridge only registers its listener if `Bukkit.getPluginManager().getPlugin(...)` finds it at startup. If the soft-dep plugin is installed *after* SwagTournaments has already started, you'll need to restart the server (bridges are wired up once, in `IntegrationManager.initialize()`, not re-checked on reload).

## Money rewards aren't being paid

Vault must be installed with a registered economy provider (e.g. EssentialsX). `RewardManager.initialize()` logs `"Vault Economy not found — money rewards will be skipped."` at startup if none is found — check your console log at boot. Commands and item rewards still work without Vault; only the `money` field of a reward tier is skipped.

## ECONOMY tournaments never score anyone

Same root cause as above — `EconomyScoreEngine` requires Vault and logs a warning if it isn't present when the tournament starts. Also note it only scores balance *increases* polled every 60 seconds; a very short tournament duration may simply not have a poll cycle complete before it ends.

## Discord embeds aren't posting

Check, in order: `discord.enabled: true`, DiscordUtils is installed and its bot reports ready, and `discord.announcements-channel-id` is set to a real channel ID (not blank, not the placeholder `"YOUR_CHANNEL_ID"`). `discord.announcements-server` must also match the correct configured server index in DiscordUtils if you run more than one Discord server.

## Web editor is unreachable

The web editor has no server of its own — it's mounted on **SwagAPI's** shared web service. Checklist:
1. Is SwagAPI installed and its web service actually running?
2. Is `web-editor.enabled: true` in SwagTournaments' `config.yml`?
3. Run `/tadmin webtrust` — it prints the URL if registered, or an explicit error if not.
4. Once you have the URL, log in with your **SwagAPI panel account** — SwagTournaments has no login of its own. See [Web Editor Overview](../web-editor/overview.md).

## Editing a template YAML by hand has no effect

Templates are only re-read from disk on plugin startup or `/tadmin reload [id]`. Hand-editing a file in `tournaments/` does nothing until you reload it.

## `config.yml` / `messages.yml` changes aren't applying

`config.yml` requires a full server restart — there's no dedicated `/tadmin reload` path for it (only templates reload live). `messages.yml` is currently unused by the plugin entirely regardless of restart — see [Configuration](../getting-started/configuration.md).

Next: [FAQ](faq.md)
