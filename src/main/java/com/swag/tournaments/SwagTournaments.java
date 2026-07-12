package com.swag.tournaments;

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
import org.bukkit.plugin.java.JavaPlugin;

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

    // ---- Integration state ----

    public boolean isSwagFishingPresent() { return swagFishingPresent; }
    public boolean isSwagFarmingPresent() { return swagFarmingPresent; }
    public boolean isDiscordUtilsPresent() { return discordUtilsPresent; }
    public boolean isVaultPresent() { return vaultPresent; }
    public boolean isPlaceholderApiPresent() { return placeholderApiPresent; }
}
