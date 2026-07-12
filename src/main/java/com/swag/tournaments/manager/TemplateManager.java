package com.swag.tournaments.manager;

import com.swag.tournaments.model.TournamentTemplate;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class TemplateManager {

    private static final String[] BUNDLED = {
            "tournaments/most_fish.yml",
            "tournaments/biggest_catch.yml",
            "tournaments/crop_harvest.yml",
            "tournaments/monster_slayer.yml",
            "tournaments/diamond_rush.yml",
            "tournaments/speed_miner.yml"
    };

    private final JavaPlugin plugin;
    private final Logger log;
    private final File tournamentsFolder;
    private final Map<String, TournamentTemplate> templates = new ConcurrentHashMap<>();

    public TemplateManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.tournamentsFolder = new File(plugin.getDataFolder(), "tournaments");
    }

    public void reloadAll() {
        if (!tournamentsFolder.exists()) {
            tournamentsFolder.mkdirs();
            copyBundledTemplates();
        }

        templates.clear();
        File[] files = tournamentsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            log.warning("No tournament templates found in " + tournamentsFolder.getPath());
            return;
        }

        int loaded = 0;
        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            TournamentTemplate template = loadFromFile(id, file);
            if (template != null) {
                templates.put(id, template);
                loaded++;
            }
        }
        log.info("Loaded " + loaded + " tournament template(s).");
    }

    public void reloadTemplate(String id) {
        File file = new File(tournamentsFolder, id + ".yml");
        if (!file.exists()) {
            log.warning("Cannot reload template '" + id + "': file not found.");
            return;
        }
        TournamentTemplate template = loadFromFile(id, file);
        if (template != null) {
            templates.put(id, template);
        }
    }

    private TournamentTemplate loadFromFile(String id, File file) {
        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            return TournamentTemplate.fromConfig(id, config, log);
        } catch (Exception e) {
            log.severe("Failed to load template '" + id + "': " + e.getMessage());
            return null;
        }
    }

    public void saveTemplate(TournamentTemplate template) throws IOException {
        File file = new File(tournamentsFolder, template.getId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        config.set("id", template.getId());
        config.set("display-name", template.getDisplayName());
        config.set("description", template.getDescription());
        config.set("icon-material", template.getIconMaterial().name());
        config.set("bar-color", template.getBarColor().name());
        config.set("bar-style", template.getBarStyle().name());
        config.set("type", template.getType().name());
        config.set("scoring-mode", template.getScoringMode().name());
        config.set("score-formula", template.getScoreFormula());
        config.set("target-score", template.getTargetScore());
        config.set("farming-actions", template.getFarmingActions());
        config.set("mining-materials", template.getMiningMaterials());
        config.set("combat-entities", template.getCombatEntities());

        config.set("conditions.biomes", template.getConditionBiomes());
        config.set("conditions.worlds", template.getConditionWorlds());
        config.set("conditions.time", template.getConditionTime());
        config.set("conditions.weather", template.getConditionWeather());
        config.set("conditions.min-y", template.getConditionMinY());
        config.set("conditions.max-y", template.getConditionMaxY());
        config.set("conditions.required-permission", template.getConditionRequiredPermission());

        config.set("schedule.in-rotation", template.isInRotation());
        config.set("schedule.rotation-weight", template.getRotationWeight());

        List<Map<String, Object>> slots = new ArrayList<>();
        for (var slot : template.getCronSlots()) {
            Map<String, Object> slotMap = new LinkedHashMap<>();
            slotMap.put("day", slot.getDay() == null ? "DAILY" : slot.getDay().name());
            slotMap.put("time", slot.getTime().toString());
            slotMap.put("duration", slot.getDurationMinutes());
            slots.add(slotMap);
        }
        config.set("schedule.slots", slots);

        config.set("messages.start", template.getMessageStart());
        config.set("messages.end", template.getMessageEnd());
        config.set("messages.warning", template.getMessageWarning());

        for (Map.Entry<Integer, com.swag.tournaments.model.RewardTier> entry : template.getRewards().entrySet()) {
            String key = entry.getKey() == 0 ? "rewards.participation" : "rewards." + entry.getKey();
            var tier = entry.getValue();
            config.set(key + ".money", tier.getMoney());
            config.set(key + ".commands", tier.getCommands());
            if (entry.getKey() == 0) config.set(key + ".enabled", true);
        }

        config.set("integration.defer-to-swagfishing", template.isDeferToSwagFishing());
        config.set("integration.defer-to-swagfarming", template.isDeferToSwagFarming());
        config.set("integration.discord-channel-key", template.getDiscordChannelKey());

        config.set("cosmetics.start-fireworks", template.isStartFireworks());
        config.set("cosmetics.end-fireworks", template.isEndFireworks());
        config.set("cosmetics.sound-start", template.getSoundStart());
        config.set("cosmetics.sound-end", template.getSoundEnd());

        config.save(file);
        templates.put(template.getId(), template);
    }

    public void deleteTemplate(String id) {
        File file = new File(tournamentsFolder, id + ".yml");
        if (file.exists()) {
            file.delete();
        }
        templates.remove(id);
    }

    private void copyBundledTemplates() {
        for (String resource : BUNDLED) {
            File dest = new File(plugin.getDataFolder(), resource);
            if (!dest.exists()) {
                try {
                    plugin.saveResource(resource, false);
                } catch (Exception e) {
                    log.warning("Could not save bundled template " + resource + ": " + e.getMessage());
                }
            }
        }
    }

    public Optional<TournamentTemplate> getTemplate(String id) {
        return Optional.ofNullable(templates.get(id));
    }

    public Collection<TournamentTemplate> getTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    public boolean hasTemplate(String id) {
        return templates.containsKey(id);
    }
}
