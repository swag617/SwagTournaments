package com.swag.tournaments.model;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.logging.Logger;

@SuppressWarnings("deprecation")
public final class TournamentTemplate {

    private final String id;
    private final String displayName;
    private final String description;
    private final Material iconMaterial;
    private final BarColor barColor;
    private final BarStyle barStyle;
    private final TournamentType type;
    private final ScoringMode scoringMode;
    private final String scoreFormula;
    private final double targetScore;

    private final List<String> farmingActions;
    private final List<String> miningMaterials;
    private final List<String> combatEntities;

    private final List<String> conditionBiomes;
    private final List<String> conditionWorlds;
    private final String conditionTime;
    private final String conditionWeather;
    private final int conditionMinY;
    private final int conditionMaxY;
    private final String conditionRequiredPermission;

    private final List<CronSlot> cronSlots;
    private final boolean inRotation;
    private final int rotationWeight;

    private final String messageStart;
    private final String messageEnd;
    private final String messageWarning;

    private final Map<Integer, RewardTier> rewards;

    private final boolean deferToSwagFishing;
    private final boolean deferToSwagFarming;
    private final String discordChannelKey;

    private final boolean startFireworks;
    private final boolean endFireworks;
    private final String soundStart;
    private final String soundEnd;

    private TournamentTemplate(Builder b) {
        this.id = b.id;
        this.displayName = b.displayName;
        this.description = b.description;
        this.iconMaterial = b.iconMaterial;
        this.barColor = b.barColor;
        this.barStyle = b.barStyle;
        this.type = b.type;
        this.scoringMode = b.scoringMode;
        this.scoreFormula = b.scoreFormula;
        this.targetScore = b.targetScore;
        this.farmingActions = List.copyOf(b.farmingActions);
        this.miningMaterials = List.copyOf(b.miningMaterials);
        this.combatEntities = List.copyOf(b.combatEntities);
        this.conditionBiomes = List.copyOf(b.conditionBiomes);
        this.conditionWorlds = List.copyOf(b.conditionWorlds);
        this.conditionTime = b.conditionTime;
        this.conditionWeather = b.conditionWeather;
        this.conditionMinY = b.conditionMinY;
        this.conditionMaxY = b.conditionMaxY;
        this.conditionRequiredPermission = b.conditionRequiredPermission;
        this.cronSlots = List.copyOf(b.cronSlots);
        this.inRotation = b.inRotation;
        this.rotationWeight = b.rotationWeight;
        this.messageStart = b.messageStart;
        this.messageEnd = b.messageEnd;
        this.messageWarning = b.messageWarning;
        this.rewards = Collections.unmodifiableMap(b.rewards);
        this.deferToSwagFishing = b.deferToSwagFishing;
        this.deferToSwagFarming = b.deferToSwagFarming;
        this.discordChannelKey = b.discordChannelKey;
        this.startFireworks = b.startFireworks;
        this.endFireworks = b.endFireworks;
        this.soundStart = b.soundStart;
        this.soundEnd = b.soundEnd;
    }

    public static TournamentTemplate fromConfig(String id, FileConfiguration config) {
        return fromConfig(id, config, null);
    }

    public static TournamentTemplate fromConfig(String id, FileConfiguration config, Logger log) {
        Builder b = new Builder(id);

        b.displayName = config.getString("display-name", id);
        b.description = config.getString("description", "");

        String matStr = config.getString("icon-material", "PAPER");
        Material mat = Material.matchMaterial(matStr);
        b.iconMaterial = mat != null ? mat : Material.PAPER;

        String barColorStr = config.getString("bar-color", "WHITE");
        try {
            b.barColor = BarColor.valueOf(barColorStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            b.barColor = BarColor.WHITE;
        }

        String barStyleStr = config.getString("bar-style", "SOLID");
        try {
            b.barStyle = BarStyle.valueOf(barStyleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            b.barStyle = BarStyle.SOLID;
        }

        String typeStr = config.getString("type", "CUSTOM");
        try {
            b.type = TournamentType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            if (log != null) log.warning("[SwagTournaments] Unknown type '" + typeStr + "' in template " + id + ", defaulting to CUSTOM");
            b.type = TournamentType.CUSTOM;
        }

        String modeStr = config.getString("scoring-mode", "SUM");
        try {
            b.scoringMode = ScoringMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            b.scoringMode = ScoringMode.SUM;
        }

        b.scoreFormula = config.getString("score-formula", "1");
        b.targetScore = config.getDouble("target-score", 0);

        b.farmingActions = config.getStringList("farming-actions");
        b.miningMaterials = config.getStringList("mining-materials");
        b.combatEntities = config.getStringList("combat-entities");

        ConfigurationSection cond = config.getConfigurationSection("conditions");
        if (cond != null) {
            b.conditionBiomes = cond.getStringList("biomes");
            b.conditionWorlds = cond.getStringList("worlds");
            b.conditionTime = cond.getString("time", "ANY");
            b.conditionWeather = cond.getString("weather", "ANY");
            b.conditionMinY = cond.getInt("min-y", -64);
            b.conditionMaxY = cond.getInt("max-y", 320);
            b.conditionRequiredPermission = cond.getString("required-permission", "");
        }

        ConfigurationSection sched = config.getConfigurationSection("schedule");
        if (sched != null) {
            b.inRotation = sched.getBoolean("in-rotation", false);
            b.rotationWeight = sched.getInt("rotation-weight", 1);

            List<Map<?, ?>> slotMaps = sched.getMapList("slots");
            for (Map<?, ?> slotMap : slotMaps) {
                String dayStr = String.valueOf(slotMap.get("day"));
                String timeStr = String.valueOf(slotMap.get("time"));
                int duration = slotMap.containsKey("duration")
                        ? Integer.parseInt(String.valueOf(slotMap.get("duration")))
                        : 60;

                DayOfWeek day = null;
                if (dayStr != null && !dayStr.equalsIgnoreCase("DAILY") && !dayStr.equalsIgnoreCase("null")) {
                    try {
                        day = DayOfWeek.valueOf(dayStr.toUpperCase());
                    } catch (IllegalArgumentException ignored) {
                        if (log != null) log.warning("[SwagTournaments] Invalid day '" + dayStr + "' in template " + id);
                    }
                }

                LocalTime time = LocalTime.of(0, 0);
                try {
                    time = LocalTime.parse(timeStr);
                } catch (DateTimeParseException e) {
                    if (log != null) log.warning("[SwagTournaments] Invalid time '" + timeStr + "' in template " + id);
                }

                b.cronSlots.add(new CronSlot(day, time, duration, id));
            }
        }

        ConfigurationSection msgs = config.getConfigurationSection("messages");
        if (msgs != null) {
            b.messageStart = msgs.getString("start", "");
            b.messageEnd = msgs.getString("end", "");
            b.messageWarning = msgs.getString("warning", "");
        }

        ConfigurationSection rewardSec = config.getConfigurationSection("rewards");
        if (rewardSec != null) {
            for (String key : rewardSec.getKeys(false)) {
                ConfigurationSection tierSec = rewardSec.getConfigurationSection(key);
                if (tierSec == null) continue;

                // place 0 = participation tier (either string key "participation" or numeric key "0")
                int place;
                if (key.equalsIgnoreCase("participation")) {
                    boolean enabled = tierSec.getBoolean("enabled", false);
                    if (!enabled) continue;
                    place = 0;
                } else {
                    try {
                        place = Integer.parseInt(key);
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    // For numeric key 0 (participation), also check enabled flag
                    if (place == 0) {
                        boolean enabled = tierSec.getBoolean("enabled", false);
                        if (!enabled) continue;
                    }
                }

                double money = tierSec.getDouble("money", 0.0);
                List<String> commands = tierSec.getStringList("commands");
                List<ItemStack> items = new ArrayList<>();

                List<Map<?, ?>> itemMaps = tierSec.getMapList("items");
                for (Map<?, ?> itemMap : itemMaps) {
                    String matName = String.valueOf(itemMap.get("material"));
                    Material itemMat = Material.matchMaterial(matName);
                    if (itemMat == null) continue;

                    int amount = itemMap.containsKey("amount")
                            ? Integer.parseInt(String.valueOf(itemMap.get("amount")))
                            : 1;
                    ItemStack stack = new ItemStack(itemMat, amount);
                    if (itemMap.containsKey("display-name")) {
                        var meta = stack.getItemMeta();
                        if (meta != null) {
                            meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes(
                                    '&', String.valueOf(itemMap.get("display-name"))));
                            stack.setItemMeta(meta);
                        }
                    }
                    items.add(stack);
                }

                b.rewards.put(place, new RewardTier(place, money, commands, items));
            }
        }

        ConfigurationSection integ = config.getConfigurationSection("integration");
        if (integ != null) {
            b.deferToSwagFishing = integ.getBoolean("defer-to-swagfishing", false);
            b.deferToSwagFarming = integ.getBoolean("defer-to-swagfarming", false);
            b.discordChannelKey = integ.getString("discord-channel-key", "tournaments");
        }

        ConfigurationSection cosm = config.getConfigurationSection("cosmetics");
        if (cosm != null) {
            b.startFireworks = cosm.getBoolean("start-fireworks", false);
            b.endFireworks = cosm.getBoolean("end-fireworks", false);
            b.soundStart = cosm.getString("sound-start", "");
            b.soundEnd = cosm.getString("sound-end", "");
        }

        return new TournamentTemplate(b);
    }

    // ---- Getters ----

    public String getId() { return id; }

    /** Raw config value (literal '&amp;' codes) — keep untranslated for round-tripping through
     *  config.yml saves and the web-editor JSON API. Use {@link #getFormattedDisplayName()} for
     *  anything actually shown to a player (boss bar, chat messages). */
    public String getDisplayName() { return displayName; }

    /** {@link #getDisplayName()} with '&amp;' color codes translated — use this for boss bars,
     *  broadcasts, and command output. Not cached: translating a short string is cheap even at the
     *  once-per-second rate the boss-bar title updater calls this at. */
    public String getFormattedDisplayName() { return ChatColor.translateAlternateColorCodes('&', displayName); }
    public String getDescription() { return description; }
    public Material getIconMaterial() { return iconMaterial; }
    public BarColor getBarColor() { return barColor; }
    public BarStyle getBarStyle() { return barStyle; }
    public TournamentType getType() { return type; }
    public ScoringMode getScoringMode() { return scoringMode; }
    public String getScoreFormula() { return scoreFormula; }
    public double getTargetScore() { return targetScore; }
    public List<String> getFarmingActions() { return farmingActions; }
    public List<String> getMiningMaterials() { return miningMaterials; }
    public List<String> getCombatEntities() { return combatEntities; }
    public List<String> getConditionBiomes() { return conditionBiomes; }
    public List<String> getConditionWorlds() { return conditionWorlds; }
    public String getConditionTime() { return conditionTime; }
    public String getConditionWeather() { return conditionWeather; }
    public int getConditionMinY() { return conditionMinY; }
    public int getConditionMaxY() { return conditionMaxY; }
    public String getConditionRequiredPermission() { return conditionRequiredPermission; }
    public List<CronSlot> getCronSlots() { return cronSlots; }
    public boolean isInRotation() { return inRotation; }
    public int getRotationWeight() { return rotationWeight; }
    public String getMessageStart() { return messageStart; }
    public String getMessageEnd() { return messageEnd; }
    public String getMessageWarning() { return messageWarning; }
    public Map<Integer, RewardTier> getRewards() { return rewards; }
    public boolean isDeferToSwagFishing() { return deferToSwagFishing; }
    public boolean isDeferToSwagFarming() { return deferToSwagFarming; }
    public String getDiscordChannelKey() { return discordChannelKey; }
    public boolean isStartFireworks() { return startFireworks; }
    public boolean isEndFireworks() { return endFireworks; }
    public String getSoundStart() { return soundStart; }
    public String getSoundEnd() { return soundEnd; }

    // ---- Builder ----

    private static final class Builder {
        final String id;
        String displayName = "";
        String description = "";
        Material iconMaterial = Material.PAPER;
        BarColor barColor = BarColor.WHITE;
        BarStyle barStyle = BarStyle.SOLID;
        TournamentType type = TournamentType.CUSTOM;
        ScoringMode scoringMode = ScoringMode.SUM;
        String scoreFormula = "1";
        double targetScore = 0;
        List<String> farmingActions = new ArrayList<>();
        List<String> miningMaterials = new ArrayList<>();
        List<String> combatEntities = new ArrayList<>();
        List<String> conditionBiomes = new ArrayList<>();
        List<String> conditionWorlds = new ArrayList<>();
        String conditionTime = "ANY";
        String conditionWeather = "ANY";
        int conditionMinY = -64;
        int conditionMaxY = 320;
        String conditionRequiredPermission = "";
        List<CronSlot> cronSlots = new ArrayList<>();
        boolean inRotation = false;
        int rotationWeight = 1;
        String messageStart = "";
        String messageEnd = "";
        String messageWarning = "";
        Map<Integer, RewardTier> rewards = new HashMap<>();
        boolean deferToSwagFishing = false;
        boolean deferToSwagFarming = false;
        String discordChannelKey = "tournaments";
        boolean startFireworks = false;
        boolean endFireworks = false;
        String soundStart = "";
        String soundEnd = "";

        Builder(String id) {
            this.id = id;
        }
    }
}
