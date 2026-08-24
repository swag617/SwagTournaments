package com.swag.tournaments.web.handlers;

import com.google.gson.*;
import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentTemplate;
import com.swag.tournaments.web.JsonUtil;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TemplateAPIHandler implements HttpHandler {

    private final SwagTournaments plugin;

    public TemplateAPIHandler(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            JsonUtil.sendOptions(ex);
            return;
        }

        String path = ex.getRequestURI().getPath();
        String[] parts = path.split("/");
        String templateId = parts.length >= 4 ? parts[3] : null;
        String method = ex.getRequestMethod().toUpperCase();

        switch (method) {
            case "GET" -> {
                if (templateId == null || templateId.isEmpty()) {
                    handleList(ex);
                } else {
                    handleGet(ex, templateId);
                }
            }
            case "POST" -> handleCreate(ex);
            case "PUT" -> {
                if (templateId == null || templateId.isEmpty()) {
                    JsonUtil.send(ex, 400, JsonUtil.error("Template ID required for PUT"));
                } else {
                    handleUpdate(ex, templateId);
                }
            }
            case "DELETE" -> {
                if (templateId == null || templateId.isEmpty()) {
                    JsonUtil.send(ex, 400, JsonUtil.error("Template ID required for DELETE"));
                } else {
                    handleDelete(ex, templateId);
                }
            }
            default -> JsonUtil.send(ex, 405, JsonUtil.error("Method not allowed"));
        }
    }

    private void handleList(HttpExchange ex) throws IOException {
        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
        JsonArray arr = new JsonArray();
        for (TournamentTemplate t : plugin.getTemplateManager().getTemplates()) {
            JsonObject obj = new JsonObject();
            obj.addProperty("id", t.getId());
            obj.addProperty("displayName", t.getDisplayName());
            obj.addProperty("type", t.getType().name());
            obj.addProperty("scoringMode", t.getScoringMode().name());
            boolean isActive = active != null && active.getTemplate().getId().equals(t.getId());
            obj.addProperty("status", isActive ? "ACTIVE" : "IDLE");
            arr.add(obj);
        }
        JsonUtil.send(ex, 200, JsonUtil.GSON.toJson(arr));
    }

    private void handleGet(HttpExchange ex, String id) throws IOException {
        Optional<TournamentTemplate> opt = plugin.getTemplateManager().getTemplate(id);
        if (opt.isEmpty()) {
            JsonUtil.send(ex, 404, JsonUtil.error("Template not found: " + id));
            return;
        }
        JsonUtil.send(ex, 200, templateToJson(opt.get()));
    }

    private void handleCreate(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject json;
        try {
            json = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            JsonUtil.send(ex, 400, JsonUtil.error("Invalid JSON"));
            return;
        }

        if (!json.has("id") || json.get("id").getAsString().isBlank()) {
            JsonUtil.send(ex, 400, JsonUtil.error("Field 'id' is required"));
            return;
        }

        String id = json.get("id").getAsString().trim();
        boolean exists = plugin.getTemplateManager().hasTemplate(id);
        if (exists) {
            JsonUtil.send(ex, 409, JsonUtil.error("Template with id '" + id + "' already exists. Use PUT to update."));
            return;
        }

        String writeError = writeTemplateFromJson(id, json);
        if (writeError != null) {
            JsonUtil.send(ex, 500, JsonUtil.error(writeError));
            return;
        }

        reloadTemplateOnMainThread(id);
        JsonUtil.send(ex, 201, "{\"created\":\"" + id + "\"}");
    }

    private void handleUpdate(HttpExchange ex, String id) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        JsonObject json;
        try {
            json = JsonParser.parseString(body).getAsJsonObject();
        } catch (Exception e) {
            JsonUtil.send(ex, 400, JsonUtil.error("Invalid JSON"));
            return;
        }

        String writeError = writeTemplateFromJson(id, json);
        if (writeError != null) {
            JsonUtil.send(ex, 500, JsonUtil.error(writeError));
            return;
        }

        reloadTemplateOnMainThread(id);
        JsonUtil.send(ex, 200, "{\"updated\":\"" + id + "\"}");
    }

    private void handleDelete(HttpExchange ex, String id) throws IOException {
        if (!plugin.getTemplateManager().hasTemplate(id)) {
            JsonUtil.send(ex, 404, JsonUtil.error("Template not found: " + id));
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getTemplateManager().deleteTemplate(id);
            if (plugin.getSchedulerManager() != null) {
                plugin.getSchedulerManager().rebuildSlots();
                plugin.getSchedulerManager().syncAutoTask();
            }
            latch.countDown();
        });

        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                plugin.getLogger().warning("TemplateAPIHandler: timed out waiting for main-thread delete of '" + id + "'");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        JsonUtil.send(ex, 204, "");
    }

    private String writeTemplateFromJson(String id, JsonObject json) {
        File tournamentsFolder = new File(plugin.getDataFolder(), "tournaments");
        if (!tournamentsFolder.exists()) tournamentsFolder.mkdirs();

        File file = new File(tournamentsFolder, id + ".yml");
        boolean isNewTemplate = !file.exists();

        YamlConfiguration cfg = new YamlConfiguration();
        if (!isNewTemplate) {
            try {
                cfg.load(file);
            } catch (Exception e) {
                return "Failed to read existing template file before saving: " + e.getMessage();
            }
        }

        cfg.set("id", id);
        cfg.set("display-name", getString(json, "display-name", id));
        cfg.set("description", getString(json, "description", ""));
        cfg.set("icon-material", getString(json, "icon-material", "PAPER"));
        cfg.set("bar-color", getString(json, "bar-color", "WHITE"));
        cfg.set("type", getString(json, "type", "CUSTOM"));
        cfg.set("scoring-mode", getString(json, "scoring-mode", "SUM"));
        cfg.set("score-formula", getString(json, "score-formula", "1"));
        cfg.set("target-score", getDouble(json, "target-score", 0.0));

        // The web editor's modal has no fields for these — the JS always posts
        // placeholder defaults for them (empty lists, "SOLID", etc). Only seed
        // them on first creation; on every later save, leave whatever is
        // already in the file alone so hand-authored data (ore/action lists,
        // spawn conditions, cron slots, warning message, reward items) survives
        // instead of being clobbered by the placeholder on every web save.
        if (isNewTemplate) {
            cfg.set("bar-style", getString(json, "bar-style", "SOLID"));
            cfg.set("farming-actions", getStringList(json, "farming-actions"));
            cfg.set("mining-materials", getStringList(json, "mining-materials"));
            cfg.set("combat-entities", getStringList(json, "combat-entities"));

            JsonObject cond = getObj(json, "conditions");
            cfg.set("conditions.biomes", getStringListFromObj(cond, "biomes"));
            cfg.set("conditions.worlds", getStringListFromObj(cond, "worlds"));
            cfg.set("conditions.time", getStringFromObj(cond, "time", "ANY"));
            cfg.set("conditions.weather", getStringFromObj(cond, "weather", "ANY"));
            cfg.set("conditions.min-y", getIntFromObj(cond, "min-y", -64));
            cfg.set("conditions.max-y", getIntFromObj(cond, "max-y", 320));
            cfg.set("conditions.required-permission", getStringFromObj(cond, "required-permission", ""));

            cfg.set("schedule.slots", new ArrayList<Map<String, Object>>());
            cfg.set("messages.warning", getStringFromObj(getObj(json, "messages"), "warning", ""));
            cfg.set("integration.discord-channel-key", getStringFromObj(getObj(json, "integration"), "discord-channel-key", "tournaments"));
        }

        JsonObject sched = getObj(json, "schedule");
        cfg.set("schedule.in-rotation", getBoolFromObj(sched, "in-rotation", false));
        cfg.set("schedule.rotation-weight", getIntFromObj(sched, "rotation-weight", 1));

        JsonObject msgs = getObj(json, "messages");
        cfg.set("messages.start", getStringFromObj(msgs, "start", ""));
        cfg.set("messages.end", getStringFromObj(msgs, "end", ""));

        JsonObject rewards = getObj(json, "rewards");
        if (rewards != null) {
            for (String key : rewards.keySet()) {
                JsonObject tier = rewards.getAsJsonObject(key);
                if (tier == null) continue;
                String yamlKey = "rewards." + key;
                cfg.set(yamlKey + ".money", tier.has("money") ? tier.get("money").getAsDouble() : 0.0);
                List<String> cmds = new ArrayList<>();
                if (tier.has("commands") && tier.get("commands").isJsonArray()) {
                    for (JsonElement c : tier.getAsJsonArray("commands")) cmds.add(c.getAsString());
                }
                cfg.set(yamlKey + ".commands", cmds);

                // Only write "items" when the incoming JSON actually included it for this tier.
                // The web UI has no items editor yet and never sends the key at all — if we wrote
                // an empty list here on every save, that would silently wipe out hand-authored
                // item rewards already on disk (see diamond_rush.yml etc). Leave whatever is
                // already present in cfg (loaded from the existing file above) untouched instead.
                if (tier.has("items") && tier.get("items").isJsonArray()) {
                    List<Map<String, Object>> items = new ArrayList<>();
                    for (JsonElement ie : tier.getAsJsonArray("items")) {
                        if (!ie.isJsonObject()) continue;
                        JsonObject itemObj = ie.getAsJsonObject();
                        Map<String, Object> itemMap = new LinkedHashMap<>();
                        itemMap.put("material", getString(itemObj, "material", "STONE"));
                        itemMap.put("amount", getIntFromObj(itemObj, "amount", 1));
                        if (itemObj.has("display-name") && !itemObj.get("display-name").isJsonNull()) {
                            itemMap.put("display-name", itemObj.get("display-name").getAsString());
                        }
                        items.add(itemMap);
                    }
                    cfg.set(yamlKey + ".items", items);
                }

                if (key.equals("participation")) {
                    cfg.set(yamlKey + ".enabled", getBoolFromObj(tier, "enabled", false));
                }
            }
        }

        JsonObject integ = getObj(json, "integration");
        cfg.set("integration.defer-to-swagfishing", getBoolFromObj(integ, "defer-to-swagfishing", false));
        cfg.set("integration.defer-to-swagfarming", getBoolFromObj(integ, "defer-to-swagfarming", false));

        JsonObject cosm = getObj(json, "cosmetics");
        cfg.set("cosmetics.start-fireworks", getBoolFromObj(cosm, "start-fireworks", false));
        cfg.set("cosmetics.end-fireworks", getBoolFromObj(cosm, "end-fireworks", false));
        cfg.set("cosmetics.sound-start", getStringFromObj(cosm, "sound-start", ""));
        cfg.set("cosmetics.sound-end", getStringFromObj(cosm, "sound-end", ""));

        try {
            cfg.save(file);
            return null;
        } catch (IOException e) {
            return "Failed to save template file: " + e.getMessage();
        }
    }

    private void reloadTemplateOnMainThread(String id) {
        CountDownLatch latch = new CountDownLatch(1);
        Bukkit.getScheduler().runTask(plugin, () -> {
            plugin.getTemplateManager().reloadTemplate(id);
            if (plugin.getSchedulerManager() != null) {
                plugin.getSchedulerManager().rebuildSlots();
                plugin.getSchedulerManager().syncAutoTask();
            }
            latch.countDown();
        });
        try {
            boolean completed = latch.await(5, TimeUnit.SECONDS);
            if (!completed) {
                plugin.getLogger().warning("TemplateAPIHandler: timed out waiting for main-thread reload of '" + id + "'");
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private String templateToJson(TournamentTemplate t) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", t.getId());
        obj.addProperty("display-name", t.getDisplayName());
        obj.addProperty("description", t.getDescription());
        obj.addProperty("icon-material", t.getIconMaterial().name());
        obj.addProperty("bar-color", t.getBarColor().name());
        obj.addProperty("bar-style", t.getBarStyle().name());
        obj.addProperty("type", t.getType().name());
        obj.addProperty("scoring-mode", t.getScoringMode().name());
        obj.addProperty("score-formula", t.getScoreFormula());
        obj.addProperty("target-score", t.getTargetScore());

        obj.add("farming-actions", strListToJson(t.getFarmingActions()));
        obj.add("mining-materials", strListToJson(t.getMiningMaterials()));
        obj.add("combat-entities", strListToJson(t.getCombatEntities()));

        JsonObject cond = new JsonObject();
        cond.add("biomes", strListToJson(t.getConditionBiomes()));
        cond.add("worlds", strListToJson(t.getConditionWorlds()));
        cond.addProperty("time", t.getConditionTime());
        cond.addProperty("weather", t.getConditionWeather());
        cond.addProperty("min-y", t.getConditionMinY());
        cond.addProperty("max-y", t.getConditionMaxY());
        cond.addProperty("required-permission", t.getConditionRequiredPermission());
        obj.add("conditions", cond);

        JsonObject sched = new JsonObject();
        sched.addProperty("in-rotation", t.isInRotation());
        sched.addProperty("rotation-weight", t.getRotationWeight());
        JsonArray slots = new JsonArray();
        for (var slot : t.getCronSlots()) {
            JsonObject s = new JsonObject();
            s.addProperty("day", slot.getDay() == null ? "DAILY" : slot.getDay().name());
            s.addProperty("time", slot.getTime().toString());
            s.addProperty("duration", slot.getDurationMinutes());
            slots.add(s);
        }
        sched.add("slots", slots);
        obj.add("schedule", sched);

        JsonObject msgs = new JsonObject();
        msgs.addProperty("start", t.getMessageStart());
        msgs.addProperty("end", t.getMessageEnd());
        msgs.addProperty("warning", t.getMessageWarning());
        obj.add("messages", msgs);

        JsonObject rewards = new JsonObject();
        for (var entry : t.getRewards().entrySet()) {
            String key = entry.getKey() == 0 ? "participation" : String.valueOf(entry.getKey());
            JsonObject tier = new JsonObject();
            tier.addProperty("money", entry.getValue().getMoney());
            tier.add("commands", strListToJson(entry.getValue().getCommands()));
            tier.add("items", itemsToJson(entry.getValue().getItems()));
            if (entry.getKey() == 0) tier.addProperty("enabled", true);
            rewards.add(key, tier);
        }
        obj.add("rewards", rewards);

        JsonObject integ = new JsonObject();
        integ.addProperty("defer-to-swagfishing", t.isDeferToSwagFishing());
        integ.addProperty("defer-to-swagfarming", t.isDeferToSwagFarming());
        integ.addProperty("discord-channel-key", t.getDiscordChannelKey());
        obj.add("integration", integ);

        JsonObject cosm = new JsonObject();
        cosm.addProperty("start-fireworks", t.isStartFireworks());
        cosm.addProperty("end-fireworks", t.isEndFireworks());
        cosm.addProperty("sound-start", t.getSoundStart());
        cosm.addProperty("sound-end", t.getSoundEnd());
        obj.add("cosmetics", cosm);

        return JsonUtil.GSON.toJson(obj);
    }

    private JsonArray strListToJson(List<String> list) {
        JsonArray arr = new JsonArray();
        for (String s : list) arr.add(s);
        return arr;
    }

    /**
     * Serializes reward-tier ItemStacks back into the same {material, amount, display-name}
     * shape writeTemplateFromJson()/TournamentTemplate#fromConfig() read from the YAML "items"
     * list, so the web editor round-trips item rewards instead of silently losing them.
     * Display names are un-translated back from '&sect;' to '&' to match how every other
     * raw-string field (display-name, messages.*) is kept literal for round-tripping.
     */
    private JsonArray itemsToJson(List<org.bukkit.inventory.ItemStack> items) {
        JsonArray arr = new JsonArray();
        for (org.bukkit.inventory.ItemStack stack : items) {
            if (stack == null) continue;
            JsonObject itemObj = new JsonObject();
            itemObj.addProperty("material", stack.getType().name());
            itemObj.addProperty("amount", stack.getAmount());
            if (stack.hasItemMeta() && stack.getItemMeta().hasDisplayName()) {
                itemObj.addProperty("display-name",
                        stack.getItemMeta().getDisplayName().replace(org.bukkit.ChatColor.COLOR_CHAR, '&'));
            }
            arr.add(itemObj);
        }
        return arr;
    }

    // ---- JSON helper getters ----

    private String getString(JsonObject o, String key, String def) {
        return (o != null && o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : def;
    }

    private double getDouble(JsonObject o, String key, double def) {
        try { return (o != null && o.has(key)) ? o.get(key).getAsDouble() : def; }
        catch (Exception e) { return def; }
    }

    private List<String> getStringList(JsonObject o, String key) {
        List<String> list = new ArrayList<>();
        if (o != null && o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) list.add(e.getAsString());
        }
        return list;
    }

    private JsonObject getObj(JsonObject o, String key) {
        if (o != null && o.has(key) && o.get(key).isJsonObject()) return o.getAsJsonObject(key);
        return null;
    }

    private String getStringFromObj(JsonObject o, String key, String def) {
        return (o != null && o.has(key) && !o.get(key).isJsonNull()) ? o.get(key).getAsString() : def;
    }

    private List<String> getStringListFromObj(JsonObject o, String key) {
        List<String> list = new ArrayList<>();
        if (o != null && o.has(key) && o.get(key).isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray(key)) list.add(e.getAsString());
        }
        return list;
    }

    private int getIntFromObj(JsonObject o, String key, int def) {
        try { return (o != null && o.has(key)) ? o.get(key).getAsInt() : def; }
        catch (Exception e) { return def; }
    }

    private boolean getBoolFromObj(JsonObject o, String key, boolean def) {
        try { return (o != null && o.has(key)) ? o.get(key).getAsBoolean() : def; }
        catch (Exception e) { return def; }
    }
}
