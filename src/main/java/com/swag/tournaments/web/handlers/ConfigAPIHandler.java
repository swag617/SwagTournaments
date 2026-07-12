package com.swag.tournaments.web.handlers;

import com.google.gson.*;
import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.web.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConfigAPIHandler implements HttpHandler {

    private final SwagTournaments plugin;

    public ConfigAPIHandler(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            JsonUtil.sendOptions(ex);
            return;
        }

        switch (ex.getRequestMethod().toUpperCase()) {
            case "GET" -> handleGet(ex);
            case "POST" -> handlePost(ex);
            default -> JsonUtil.send(ex, 405, JsonUtil.error("Method not allowed"));
        }
    }

    private void handleGet(HttpExchange ex) throws IOException {
        Map<String, Object> flat = new LinkedHashMap<>();
        flattenSection(plugin.getConfig(), "", flat);
        JsonUtil.send(ex, 200, JsonUtil.ok(flat));
    }

    private void flattenSection(ConfigurationSection section, String prefix, Map<String, Object> out) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            if (section.isConfigurationSection(key)) {
                flattenSection(section.getConfigurationSection(key), fullKey, out);
            } else {
                out.put(fullKey, section.get(key));
            }
        }
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
                for (Map.Entry<String, JsonElement> entry : req.entrySet()) {
                    String key = entry.getKey();
                    JsonElement val = entry.getValue();
                    if (val.isJsonPrimitive()) {
                        JsonPrimitive prim = val.getAsJsonPrimitive();
                        if (prim.isBoolean()) {
                            plugin.getConfig().set(key, prim.getAsBoolean());
                        } else if (prim.isNumber()) {
                            double d = prim.getAsDouble();
                            if (d == Math.floor(d) && !Double.isInfinite(d)) {
                                plugin.getConfig().set(key, (int) d);
                            } else {
                                plugin.getConfig().set(key, d);
                            }
                        } else {
                            plugin.getConfig().set(key, prim.getAsString());
                        }
                    }
                }
                plugin.saveConfig();
            } finally {
                latch.countDown();
            }
        });

        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                plugin.getLogger().warning("ConfigAPIHandler: timed out waiting for main-thread config save");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        JsonUtil.send(ex, 200, "{\"saved\":true}");
    }
}
