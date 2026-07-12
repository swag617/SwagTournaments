package com.swag.tournaments.web.handlers;

import com.google.gson.JsonObject;
import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.web.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public class IntegrationsAPIHandler implements HttpHandler {

    private final SwagTournaments plugin;

    public IntegrationsAPIHandler(SwagTournaments plugin) {
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

        JsonObject obj = new JsonObject();
        obj.addProperty("swagFishing", plugin.isSwagFishingPresent());
        obj.addProperty("swagFarming", plugin.isSwagFarmingPresent());
        obj.addProperty("discordUtils", plugin.isDiscordUtilsPresent());
        obj.addProperty("vault", plugin.isVaultPresent());
        obj.addProperty("placeholderApi", plugin.isPlaceholderApiPresent());

        JsonUtil.send(ex, 200, JsonUtil.GSON.toJson(obj));
    }
}
