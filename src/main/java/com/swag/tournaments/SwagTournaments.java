package com.swag.tournaments;

import com.SwagDev.SwagAPI.api.IPrefixService;
import com.swag.tournaments.commands.TournamentAdminCommand;
import com.swag.tournaments.commands.TournamentCommand;
import com.swag.tournaments.database.DatabaseManager;
import com.swag.tournaments.database.TournamentRepository;
import com.swag.tournaments.engine.ScoringEngineRegistry;
import com.swag.tournaments.gui.GUIListener;
import com.swag.tournaments.integration.IntegrationManager;
import com.swag.tournaments.listener.PlayerJoinQuitListener;
import com.swag.tournaments.manager.RewardManager;
import com.swag.tournaments.manager.SchedulerManager;
import com.swag.tournaments.manager.TemplateManager;
import com.swag.tournaments.manager.TournamentManager;
import com.swag.tournaments.placeholder.TournamentsPlaceholders;
import com.swag.tournaments.web.WebServerManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Objects;

public class SwagTournaments extends JavaPlugin {

    private static SwagTournaments instance;

    private DatabaseManager databaseManager;
    private TournamentRepository tournamentRepository;
    private TemplateManager templateManager;
    private TournamentManager tournamentManager;
    private RewardManager rewardManager;
    private ScoringEngineRegistry engineRegistry;
    private IntegrationManager integrationManager;
    private SchedulerManager schedulerManager;
    private GUIListener guiListener;
    private WebServerManager webServerManager;

    private IPrefixService prefixService;
    private String chatPrefix;

    private boolean swagFishingPresent;
    private boolean swagFarmingPresent;
    private boolean discordUtilsPresent;
    private boolean vaultPresent;
    private boolean placeholderApiPresent;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Config
        saveDefaultConfig();

        // 1b. Chat prefix — resolve SwagAPI's IPrefixService (per-plugin or global admin
        // override from the web panel) once at startup, falling back to this plugin's own
        // messages.yml "prefix" value unchanged if SwagAPI/the service isn't present or
        // nothing is configured on the panel.
        ServicesManager sm = getServer().getServicesManager();
        var prefixProvider = sm.getRegistration(IPrefixService.class);
        prefixService = (prefixProvider != null) ? prefixProvider.getProvider() : null;

        String configuredPrefix = loadConfiguredPrefixFromMessagesYml();
        String resolvedPrefix = (prefixService != null)
                ? prefixService.getPrefix("SwagTournaments", configuredPrefix)
                : configuredPrefix;
        // Renders into legacy '&sect;' codes so every call site can just concatenate the result
        // in front of its message without needing to translate it itself. A MiniMessage-tag
        // admin override (e.g. "<gold>[FleaMC] </gold>") is parsed and re-serialized to legacy
        // codes here too — a bare ChatColor.translateAlternateColorCodes call (the previous
        // approach) only understands '&' codes and would leave MiniMessage tags as literal text.
        chatPrefix = toLegacyPrefix(resolvedPrefix);

        // 2. Database — fail fast
        databaseManager = new DatabaseManager(this);
        try {
            databaseManager.initialize();
        } catch (SQLException e) {
            getLogger().severe("Failed to initialise database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        tournamentRepository = new TournamentRepository(databaseManager, getLogger());

        // 3. Templates (copies bundled examples if missing)
        templateManager = new TemplateManager(this);
        templateManager.reloadAll();

        // 4. Detect soft dependencies
        detectSoftDependencies();

        // 5. Scoring engines
        engineRegistry = new ScoringEngineRegistry(swagFishingPresent, swagFarmingPresent);

        // 6. RewardManager
        rewardManager = new RewardManager(this);
        rewardManager.initialize();

        // 7. TournamentManager
        tournamentManager = new TournamentManager(this, tournamentRepository, rewardManager, engineRegistry);

        // 8. IntegrationManager (bridges + Discord; must come after TournamentManager exists)
        integrationManager = new IntegrationManager(this);
        integrationManager.initialize();

        // Wire integration manager back into tournament manager for lifecycle callbacks
        tournamentManager.setIntegrationManager(integrationManager);

        // 9. SchedulerManager
        schedulerManager = new SchedulerManager(this, templateManager, tournamentManager, integrationManager);
        schedulerManager.start();

        // 10. GUIListener + Commands + PlayerJoin/QuitListener
        guiListener = new GUIListener();
        getServer().getPluginManager().registerEvents(guiListener, this);
        getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);

        TournamentCommand tc = new TournamentCommand(this, guiListener);
        TournamentAdminCommand tac = new TournamentAdminCommand(this, guiListener);

        Objects.requireNonNull(getCommand("tournament")).setExecutor(tc);
        Objects.requireNonNull(getCommand("tournament")).setTabCompleter(tc);
        Objects.requireNonNull(getCommand("tournamentadmin")).setExecutor(tac);
        Objects.requireNonNull(getCommand("tournamentadmin")).setTabCompleter(tac);

        // 11. PlaceholderAPI expansion
        if (placeholderApiPresent) {
            new TournamentsPlaceholders(this).register();
        }

        // 12. Web editor (last, after all state is ready)
        webServerManager = new WebServerManager(this);
        webServerManager.start();

        printBanner();
    }

    @Override
    public void onDisable() {
        if (schedulerManager != null) {
            schedulerManager.stop();
        }

        if (tournamentManager != null) {
            // Use synchronous flush so DB writes complete before the scheduler shuts down
            tournamentManager.shutdownFlush();
        }

        if (webServerManager != null) {
            webServerManager.stop();
        }

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("SwagTournaments disabled.");
    }

    /**
     * Reads the {@code prefix} key straight out of the bundled {@code messages.yml} resource
     * (this file is never copied to disk / made user-editable — it's only read here as the
     * fallback source for {@link IPrefixService}), so a server that never touches the SwagAPI
     * web panel keeps exactly today's hardcoded value.
     */
    private String loadConfiguredPrefixFromMessagesYml() {
        String fallback = "&8[&6Tournaments&8] &r";
        try (InputStream in = getResource("messages.yml")) {
            if (in == null) return fallback;
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            return yaml.getString("prefix", fallback);
        } catch (Exception e) {
            getLogger().warning("Failed to read prefix from messages.yml, using default: " + e.getMessage());
            return fallback;
        }
    }

    private void detectSoftDependencies() {
        swagFishingPresent = Bukkit.getPluginManager().getPlugin("SwagFishing") != null;
        swagFarmingPresent = Bukkit.getPluginManager().getPlugin("SwagFarming") != null;
        discordUtilsPresent = Bukkit.getPluginManager().getPlugin("DiscordUtils") != null;
        vaultPresent = Bukkit.getPluginManager().getPlugin("Vault") != null;
        placeholderApiPresent = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    private void printBanner() {
        boolean webEnabled = getConfig().getBoolean("web-editor.enabled", true);
        String webUrl = webServerManager != null ? webServerManager.getUrl() : null;
        getLogger().info("================================================");
        getLogger().info("  SwagTournaments v" + getPluginMeta().getVersion());
        getLogger().info("  Templates loaded : " + templateManager.getTemplates().size());
        getLogger().info("  SwagFishing       : " + (swagFishingPresent ? "YES" : "no"));
        getLogger().info("  SwagFarming       : " + (swagFarmingPresent ? "YES" : "no"));
        getLogger().info("  DiscordUtils      : " + (discordUtilsPresent ? "YES" : "no"));
        getLogger().info("  Vault             : " + (vaultPresent ? "YES" : "no"));
        getLogger().info("  PlaceholderAPI    : " + (placeholderApiPresent ? "YES" : "no"));
        if (webEnabled && webUrl != null) {
            getLogger().info("  Web Editor        : " + webUrl);
        } else if (webEnabled) {
            getLogger().info("  Web Editor        : enabled but unavailable (SwagAPI not found)");
        } else {
            getLogger().info("  Web Editor        : disabled");
        }
        getLogger().info("================================================");
    }

    // ---- Static accessor ----

    public static SwagTournaments getInstance() {
        return instance;
    }

    // ---- Manager getters ----

    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public TournamentRepository getTournamentRepository() { return tournamentRepository; }
    public TemplateManager getTemplateManager() { return templateManager; }
    public TournamentManager getTournamentManager() { return tournamentManager; }
    public RewardManager getRewardManager() { return rewardManager; }
    public ScoringEngineRegistry getEngineRegistry() { return engineRegistry; }
    public IntegrationManager getIntegrationManager() { return integrationManager; }
    public SchedulerManager getSchedulerManager() { return schedulerManager; }
    public GUIListener getGUIListener() { return guiListener; }
    public WebServerManager getWebServerManager() { return webServerManager; }

    /**
     * The effective self-identifying chat prefix for this plugin: the SwagAPI admin panel's
     * per-plugin or global override if one is set, else this plugin's own {@code messages.yml}
     * {@code prefix} value unchanged. Resolved once at startup via {@link IPrefixService}.
     */
    private static final java.util.regex.Pattern LEGACY_HEX =
            java.util.regex.Pattern.compile("(?i)[&§]#([0-9a-f]{6})");
    private static final java.util.regex.Pattern LEGACY_CODE =
            java.util.regex.Pattern.compile("(?i)[&§]([0-9a-fk-or])");
    private static final java.util.Map<Character, String> LEGACY_TAGS = java.util.Map.ofEntries(
            java.util.Map.entry('0', "<black>"), java.util.Map.entry('1', "<dark_blue>"), java.util.Map.entry('2', "<dark_green>"),
            java.util.Map.entry('3', "<dark_aqua>"), java.util.Map.entry('4', "<dark_red>"), java.util.Map.entry('5', "<dark_purple>"),
            java.util.Map.entry('6', "<gold>"), java.util.Map.entry('7', "<gray>"), java.util.Map.entry('8', "<dark_gray>"),
            java.util.Map.entry('9', "<blue>"), java.util.Map.entry('a', "<green>"), java.util.Map.entry('b', "<aqua>"),
            java.util.Map.entry('c', "<red>"), java.util.Map.entry('d', "<light_purple>"), java.util.Map.entry('e', "<yellow>"),
            java.util.Map.entry('f', "<white>"), java.util.Map.entry('k', "<obfuscated>"), java.util.Map.entry('l', "<bold>"),
            java.util.Map.entry('m', "<strikethrough>"), java.util.Map.entry('n', "<underlined>"), java.util.Map.entry('o', "<italic>"),
            java.util.Map.entry('r', "<reset>"));

    /**
     * Renders a stored prefix value from SwagAPI's IPrefixService — which may be MiniMessage
     * tags (admin-typed via the web panel), legacy {@code &}/{@code §} codes (this plugin's own
     * fallback constants), or a mix — into a legacy §-coded string safe for this plugin's
     * ChatColor/sendMessage(String) pipeline. Without this, a MiniMessage-tag override (e.g.
     * {@code <gold>[FleaMC] </gold>}) would show its literal tag text instead of rendering.
     */
    private static String toLegacyPrefix(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        var hex = LEGACY_HEX.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (hex.find()) hex.appendReplacement(sb, "<#" + hex.group(1) + ">");
        hex.appendTail(sb);
        raw = sb.toString();

        var code = LEGACY_CODE.matcher(raw);
        StringBuilder sb2 = new StringBuilder();
        while (code.find()) {
            String tag = LEGACY_TAGS.get(Character.toLowerCase(code.group(1).charAt(0)));
            code.appendReplacement(sb2, tag != null ? java.util.regex.Matcher.quoteReplacement(tag) : "");
        }
        code.appendTail(sb2);

        var component = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(sb2.toString());
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(component);
    }

    public String getChatPrefix() { return chatPrefix; }

    // ---- Integration state ----

    public boolean isSwagFishingPresent() { return swagFishingPresent; }
    public boolean isSwagFarmingPresent() { return swagFarmingPresent; }
    public boolean isDiscordUtilsPresent() { return discordUtilsPresent; }
    public boolean isVaultPresent() { return vaultPresent; }
    public boolean isPlaceholderApiPresent() { return placeholderApiPresent; }
}
