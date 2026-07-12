package com.swag.tournaments.web.handlers;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.web.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class HistoryAPIHandler implements HttpHandler {

    private final SwagTournaments plugin;

    public HistoryAPIHandler(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            JsonUtil.sendOptions(ex);
            return;
        }
        if (!ex.getRequestMethod().equalsIgnoreCase("GET")) {
            JsonUtil.send(ex, 405, JsonUtil.error("Method not allowed"));
            return;
        }

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");

        if (parts.length >= 4 && !parts[3].isEmpty()) {
            handleInstance(ex, parts[3]);
        } else {
            handleList(ex, ex.getRequestURI().getQuery());
        }
    }

    private void handleList(HttpExchange ex, String query) throws IOException {
        int page = 0;
        int size = 20;

        if (query != null) {
            for (String param : query.split("&")) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2) {
                    try {
                        if (kv[0].equals("page")) page = Integer.parseInt(kv[1]);
                        if (kv[0].equals("size")) size = Math.min(Integer.parseInt(kv[1]), 100);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        int finalPage = page;
        int finalSize = size;
        AtomicReference<List<Map<String, Object>>> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            result.set(plugin.getTournamentRepository().getHistory(finalPage, finalSize));
            latch.countDown();
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        List<Map<String, Object>> rows = result.get();
        if (rows == null) {
            JsonUtil.send(ex, 500, JsonUtil.error("DB query timed out"));
            return;
        }

        JsonUtil.send(ex, 200, JsonUtil.ok(rows));
    }

    private void handleInstance(HttpExchange ex, String idStr) throws IOException {
        long id;
        try {
            id = Long.parseLong(idStr);
        } catch (NumberFormatException e) {
            JsonUtil.send(ex, 400, JsonUtil.error("Invalid instance ID: " + idStr));
            return;
        }

        AtomicReference<List<Map<String, Object>>> result = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            result.set(plugin.getTournamentRepository().getParticipants(id));
            latch.countDown();
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        List<Map<String, Object>> rows = result.get();
        if (rows == null) {
            JsonUtil.send(ex, 500, JsonUtil.error("DB query timed out"));
            return;
        }

        JsonUtil.send(ex, 200, JsonUtil.ok(rows));
    }
}
