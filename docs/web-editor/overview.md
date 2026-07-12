# Web Editor

SwagTournaments ships a full browser-based editor for templates, scheduling, history, and config — but it does not run its own web server. It **mounts onto SwagAPI's shared web service** as a module.

## How It's Wired Up

`WebServerManager` implements `com.sun.net.httpserver.HttpHandler` directly. On startup (last step of `onEnable()`, after every other manager is ready):

1. If `web-editor.enabled` is `false` in `config.yml`, the web editor is skipped entirely.
2. It looks up `IWebService` from Bukkit's `ServicesManager`. If SwagAPI isn't installed (or its service isn't registered), a warning is logged and the web editor is simply unavailable — the rest of the plugin is unaffected.
3. If found, it calls `webService.registerModule(plugin, this)`, mounting SwagTournaments's routes at `/swagapi/swagtournaments/` under SwagAPI's shared HTTP server and thread pool.

```java
public String getUrl() {
    if (!registered || webService == null) return null;
    return webService.getPluginUrl(plugin.getName().toLowerCase());
}
```

`/tournamentadmin webtrust` (see [Admin Commands](../commands/admin-commands.md)) simply prints this URL — it does not mint a token or password anymore.

## Authentication

> **There is no password or token specific to SwagTournaments.** The web editor mount point (`/swagapi/swagtournaments/`) is gated entirely by **SwagAPI's own session-cookie login** before any request ever reaches `WebServerManager.handle()`. An admin logs into the shared SwagAPI panel once, and every registered module — including SwagTournaments — becomes accessible without a second login.
>
> This is a deliberate simplification from an earlier design (visible in some historical planning notes) where the plugin issued its own 30-day trust tokens via a `webtrustees.yml` file and a dedicated `/api/auth` endpoint. That mechanism has been removed; `TournamentAdminCommand.handleWebTrust()` now just surfaces the panel URL and tells the admin to log in with their SwagAPI account.

If `swagtournaments.admin` players report they can't reach the editor, check that SwagAPI itself is installed, its web service started successfully, and the admin has a valid SwagAPI panel login — not a SwagTournaments-specific credential.

## Frontend

A single-page vanilla-JS app (`src/main/resources/web/`, served as static resources through `WebServerManager.serveStatic()`) with five tabs:

| Tab | Purpose |
|---|---|
| **Dashboard** | Live tournament status — polls `GET /api/live`, shows countdown and top standings, with Start/Stop controls |
| **Templates** | Card grid of all loaded templates; click to open a full editor modal covering every YAML field (identity, type/scoring, schedule slots, reward tiers 1–3 + participation, messages, integration flags, cosmetics) |
| **Schedule** | Table of all cron slots across templates, plus a form for the global `auto-schedule.*` settings |
| **History** | Paginated past-tournament table with expandable participant rows |
| **Config** | Auto-generated form from every flattened `config.yml` key, plus an integrations status panel showing soft-dep load state |

## REST API

All endpoints are relative to the plugin's mount point and handled by `WebServerManager.handle()`, which dispatches by path prefix to one of six handler classes:

| Method | Path | Handler | Notes |
|---|---|---|---|
| `GET` | `/api/templates` | `TemplateAPIHandler` | List all templates (id, displayName, type, scoringMode, status) |
| `GET` | `/api/templates/{id}` | `TemplateAPIHandler` | Full template as JSON, 1:1 with the YAML schema |
| `POST` | `/api/templates` | `TemplateAPIHandler` | Create — `409` if the id already exists; writes `{id}.yml` then reloads on the main thread |
| `PUT` | `/api/templates/{id}` | `TemplateAPIHandler` | Update — overwrites the YAML file then reloads on the main thread |
| `DELETE` | `/api/templates/{id}` | `TemplateAPIHandler` | Deletes the file, rebuilds scheduler slots, `204` on success |
| `GET` | `/api/live` | `LiveStatusAPIHandler` | Active tournament state + top-10 leaderboard, or `{"active":false}` |
| `POST` | `/api/live/start` | `LiveStatusAPIHandler` | Body: `{templateId, durationMinutes}`; dispatched to the main thread, returns `202` immediately (fire-and-forget) |
| `POST` | `/api/live/stop` | `LiveStatusAPIHandler` | Stops the active tournament; `202` |
| `GET` | `/api/schedule` | `ScheduleAPIHandler` | Cron slots + auto-schedule config |
| `POST` | `/api/schedule` | `ScheduleAPIHandler` | Updates `auto-schedule.*` config and rebuilds slots |
| `GET` | `/api/history?page=&size=` | `HistoryAPIHandler` | Paginated past tournaments, loaded async from SQLite |
| `GET` | `/api/history/{id}` | `HistoryAPIHandler` | Full participant list for one past tournament |
| `GET` | `/api/config` | `ConfigAPIHandler` | Every `config.yml` key flattened to `key.path: value` |
| `POST` | `/api/config` | `ConfigAPIHandler` | Sets arbitrary flattened keys and saves `config.yml` |
| `GET` | `/api/integrations` | `IntegrationsAPIHandler` | Soft-dependency load status (SwagFishing/SwagFarming/Discord/Vault) |

Template writes go through `YamlConfiguration` and are saved to `plugins/SwagTournaments/tournaments/{id}.yml` — identical to hand-editing the file, so both routes stay in sync. `/api/templates` POST/PUT/DELETE and `/api/schedule` POST all dispatch their Bukkit-API-touching work to the main thread via a `CountDownLatch` (5-second timeout) since they run on SwagAPI's HTTP handler thread, not the server thread.

## Config

| Key | Default | Description |
|---|---|---|
| `web-editor.enabled` | `true` | Master switch — set `false` to disable the web editor even if SwagAPI is installed |

Next: [Admin Commands](../commands/admin-commands.md)
