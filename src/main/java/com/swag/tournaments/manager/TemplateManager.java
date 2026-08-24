package com.swag.tournaments.manager;

import com.swag.tournaments.model.TournamentTemplate;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
