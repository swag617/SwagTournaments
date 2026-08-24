package com.swag.tournaments.web.handlers;

import com.google.gson.*;
import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.manager.SchedulerManager;
import com.swag.tournaments.model.CronSlot;
import com.swag.tournaments.web.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ScheduleAPIHandler implements HttpHandler {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final SwagTournaments plugin;

    public ScheduleAPIHandler(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            JsonUtil.sendOptions(ex);
            return;
        }

        String method = ex.getRequestMethod().toUpperCase();
        switch (method) {
            case "GET" -> handleGet(ex);
            case "POST" -> handlePost(ex);
            default -> JsonUtil.send(ex, 405, JsonUtil.error("Method not allowed"));
        }
    }

    private void handleGet(HttpExchange ex) throws IOException {
        SchedulerManager sm = plugin.getSchedulerManager();

        JsonObject resp = new JsonObject();

        JsonArray slotsArr = new JsonArray();
        if (sm != null) {
            for (SchedulerManager.ScheduledSlot ss : sm.getSlots()) {
                CronSlot slot = ss.slot();
                JsonObject obj = new JsonObject();
                obj.addProperty("templateId", ss.template().getId());
                obj.addProperty("templateName", ss.template().getDisplayName());
                obj.addProperty("day", slot.getDay() == null ? "DAILY" : slot.getDay().name());
                obj.addProperty("time", slot.getTime().toString());
                obj.addProperty("durationMinutes", slot.getDurationMinutes());

                LocalDateTime next = findNext(slot);
                obj.addProperty("nextFire", next != null ? next.format(FMT) : "unknown");
                slotsArr.add(obj);
            }
        }
        resp.add("slots", slotsArr);

        JsonObject autoSchedule = new JsonObject();
        autoSchedule.addProperty("enabled", plugin.getConfig().getBoolean("auto-schedule.enabled", false));
        autoSchedule.addProperty("intervalMinutes", plugin.getConfig().getInt("auto-schedule.interval-minutes", 180));
        autoSchedule.addProperty("durationMinutes", plugin.getConfig().getInt("auto-schedule.duration-minutes", 30));
        autoSchedule.addProperty("minPlayers", plugin.getConfig().getInt("auto-schedule.min-players", 2));
        autoSchedule.addProperty("warningMinutes", plugin.getConfig().getInt("auto-schedule.warning-minutes", 5));

        if (sm != null) {
            sm.getNextScheduledStart().ifPresent(ns -> {
                autoSchedule.addProperty("nextScheduledStart", ns.fireTime().format(FMT));
                autoSchedule.addProperty("nextScheduledTemplate", ns.template().getId());
            });
        }
        resp.add("autoSchedule", autoSchedule);

        JsonUtil.send(ex, 200, JsonUtil.GSON.toJson(resp));
    }

    private void handlePost(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject req;
        try {
            req = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            JsonUtil.send(ex, 400, JsonUtil.error("Invalid JSON"));
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                if (req.has("enabled")) {
                    plugin.getConfig().set("auto-schedule.enabled", req.get("enabled").getAsBoolean());
                }
                if (req.has("intervalMinutes")) {
                    plugin.getConfig().set("auto-schedule.interval-minutes", req.get("intervalMinutes").getAsInt());
                }
                if (req.has("durationMinutes")) {
                    plugin.getConfig().set("auto-schedule.duration-minutes", req.get("durationMinutes").getAsInt());
                }
                if (req.has("minPlayers")) {
                    plugin.getConfig().set("auto-schedule.min-players", req.get("minPlayers").getAsInt());
                }
                if (req.has("warningMinutes")) {
                    plugin.getConfig().set("auto-schedule.warning-minutes", req.get("warningMinutes").getAsInt());
                }
                plugin.saveConfig();

                if (plugin.getSchedulerManager() != null) {
                    plugin.getSchedulerManager().rebuildSlots();
                    plugin.getSchedulerManager().syncAutoTask();
                }
            } finally {
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                plugin.getLogger().warning("ScheduleAPIHandler: timed out waiting for main-thread config save");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        JsonUtil.send(ex, 200, "{\"saved\":true}");
    }

    private LocalDateTime findNext(CronSlot slot) {
        LocalDateTime now = LocalDateTime.now().plusMinutes(1);
        LocalDateTime limit = now.plusDays(7);
        LocalDateTime candidate = now;
        while (candidate.isBefore(limit)) {
            if (slot.matchesNow(candidate)) return candidate;
            candidate = candidate.plusMinutes(1);
        }
        return null;
    }
}
