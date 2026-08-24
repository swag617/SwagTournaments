package com.swag.tournaments.integration;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentTemplate;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.logging.Logger;

public class IntegrationManager {

    private final SwagTournaments plugin;
    private final Logger log;

    private SwagFishingBridge swagFishingBridge;
    private SwagFarmingBridge swagFarmingBridge;
    private DiscordIntegration discordIntegration;
    private Economy economy;

    private boolean swagFishingPresent;
    private boolean swagFarmingPresent;
    private boolean discordPresent;
    private boolean vaultPresent;

    public IntegrationManager(SwagTournaments plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void initialize() {
        swagFishingPresent = Bukkit.getPluginManager().getPlugin("SwagFishing") != null;
        swagFarmingPresent = Bukkit.getPluginManager().getPlugin("SwagFarming") != null;
        discordPresent = Bukkit.getPluginManager().getPlugin("DiscordUtils") != null;
        vaultPresent = Bukkit.getPluginManager().getPlugin("Vault") != null;

        if (vaultPresent) {
            RegisteredServiceProvider<Economy> rsp =
                    Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }

        if (swagFishingPresent) {
            swagFishingBridge = new SwagFishingBridge(plugin);
            swagFishingBridge.initialize();
        }

        if (swagFarmingPresent) {
            swagFarmingBridge = new SwagFarmingBridge(plugin);
            swagFarmingBridge.initialize();
        }

        // Always constructed — DiscordIntegration publishes on SwagAPI's event bus rather
        // than depending on DiscordUtils directly, so there's no plugin-presence gate here.
        // It's a safe no-op per send if the event bus or a matching webhook isn't available.
        discordIntegration = new DiscordIntegration(plugin);

        log.info("Integrations active:"
                + " SwagFishing=" + swagFishingPresent
                + " SwagFarming=" + swagFarmingPresent
                + " Discord=" + discordPresent
                + " Vault=" + vaultPresent);
    }

    /**
     * Called when a tournament starts. Routes to Discord announce.
     */
    public void onTournamentStart(TournamentInstance instance) {
        if (discordIntegration != null) {
            discordIntegration.sendTournamentStart(instance);
        }
    }

    /**
     * Called when a tournament ends. Routes to Discord announce.
     */
    public void onTournamentEnd(TournamentInstance instance) {
        if (discordIntegration != null) {
            discordIntegration.sendTournamentEnd(instance);
        }
    }

    /**
     * Called when a tournament's winner sets a genuine new all-time-best score for its
     * template (Feature 2 — Hall of Fame). Routes to Discord announce.
     */
    public void onHallOfFameRecord(TournamentTemplate template, String playerName, double score) {
        if (discordIntegration != null) {
            discordIntegration.sendHallOfFameRecord(template, playerName, score);
        }
    }

    /**
     * Checks whether SwagFishing has an active tournament, to prevent overlap.
     * Returns false if SwagFishing is not present or the check fails.
     */
    public boolean isSwagFishingTournamentActive() {
        if (swagFishingBridge == null) return false;
        return swagFishingBridge.isSwagFishingTournamentActive();
    }

    // ---- Getters ----

    public boolean isSwagFishingPresent() { return swagFishingPresent; }
    public boolean isSwagFarmingPresent() { return swagFarmingPresent; }
    public boolean isDiscordPresent() { return discordPresent; }
    public boolean isVaultPresent() { return vaultPresent; }
    public Economy getEconomy() { return economy; }
    public SwagFishingBridge getSwagFishingBridge() { return swagFishingBridge; }
    public SwagFarmingBridge getSwagFarmingBridge() { return swagFarmingBridge; }
    public DiscordIntegration getDiscordIntegration() { return discordIntegration; }
}
