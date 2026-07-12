package com.swag.tournaments.web.handlers;

import com.google.gson.*;
import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentParticipant;
import com.swag.tournaments.model.TournamentTemplate;
import com.swag.tournaments.web.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class LiveStatusAPIHandler implements HttpHandler {

    private final SwagTournaments plugin;

    public LiveStatusAPIHandler(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            JsonUtil.sendOptions(ex);
            return;
        }

        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod().toUpperCase();

        if (path.equals("/api/live") && method.equals("GET")) {
            handleStatus(ex);
        } else if (path.equals("/api/live/start") && method.equals("POST")) {
            handleStart(ex);
        } else if (path.equals("/api/live/stop") && method.equals("POST")) {
            handleStop(ex);
        } else {
            JsonUtil.send(ex, 404, JsonUtil.error("Not found"));
        }
    }

    private void handleStatus(HttpExchange ex) throws IOException {
        TournamentInstance inst = plugin.getTournamentManager().getCurrentInstance();
        if (inst == null || !plugin.getTournamentManager().isActive()) {
            JsonUtil.send(ex, 200, "{\"active\":false}");
            return;
        }

        TournamentTemplate tmpl = inst.getTemplate();
        List<TournamentParticipant> lb = inst.getLeaderboard();

        JsonObject obj = new JsonObject();
        obj.addProperty("active", true);
        obj.addProperty("templateId", tmpl.getId());
        obj.addProperty("displayName", tmpl.getDisplayName());
        obj.addProperty("type", tmpl.getType().name());
        obj.addProperty("timeRemainingSeconds", inst.getTimeRemainingSeconds());
        obj.addProperty("participantCount", inst.getParticipantCount());

        JsonArray leaderboard = new JsonArray();
        int limit = Math.min(10, lb.size());
        for (int i = 0; i < limit; i++) {
            TournamentParticipant p = lb.get(i);
            JsonObject entry = new JsonObject();
            entry.addProperty("rank", i + 1);
            entry.addProperty("playerName", p.getPlayerName());
            entry.addProperty("score", p.getScore());
            leaderboard.add(entry);
        }
        obj.add("leaderboard", leaderboard);

        JsonUtil.send(ex, 200, JsonUtil.GSON.toJson(obj));
    }

    private void handleStart(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject req;
        try {
            req = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            JsonUtil.send(ex, 400, JsonUtil.error("Invalid JSON"));
            return;
        }

        if (!req.has("templateId")) {
            JsonUtil.send(ex, 400, JsonUtil.error("Missing 'templateId'"));
            return;
        }

        String templateId = req.get("templateId").getAsString();
        int durationMinutes = req.has("durationMinutes") ? req.get("durationMinutes").getAsInt() : 30;

        Optional<com.swag.tournaments.model.TournamentTemplate> opt =
                plugin.getTemplateManager().getTemplate(templateId);
        if (opt.isEmpty()) {
            JsonUtil.send(ex, 404, JsonUtil.error("Template not found: " + templateId));
            return;
        }

        int finalDuration = durationMinutes;
        com.swag.tournaments.model.TournamentTemplate template = opt.get();

        Bukkit.getScheduler().runTask(plugin,
                () -> plugin.getTournamentManager().startTournament(template, finalDuration, "WEB"));

        JsonUtil.send(ex, 202, "{\"dispatched\":true}");
    }

    private void handleStop(HttpExchange ex) throws IOException {
        Bukkit.getScheduler().runTask(plugin,
                () -> plugin.getTournamentManager().stopTournament(null));
        JsonUtil.send(ex, 202, "{\"dispatched\":true}");
    }
}
