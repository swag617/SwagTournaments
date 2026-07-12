package com.swag.tournaments.engine.engines;

import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.model.TournamentInstance;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Polls each online participant's Vault balance every 60 seconds and scores
 * the delta (increase only) since the last poll. Requires Vault to be present.
 */
public class EconomyScoreEngine implements ScoringEngine {

    private TournamentInstance instance;
    private ScoreSubmitCallback scoreSubmit;
    private Economy economy;
    private BukkitTask pollTask;

    private final Map<UUID, Double> lastBalance = new HashMap<>();

    @Override
    public void onActivate(TournamentInstance instance, JavaPlugin plugin,
                           ScoreSubmitCallback scoreSubmit) {
        this.instance = instance;
        this.scoreSubmit = scoreSubmit;

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("[SwagTournaments] EconomyScoreEngine: Vault Economy not found — ECONOMY tournament will not score.");
            return;
        }
        economy = rsp.getProvider();

        // Snapshot starting balances for all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            lastBalance.put(p.getUniqueId(), economy.getBalance(p));
        }

        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollBalances, 20L * 60, 20L * 60);
    }

    @Override
    public void onDeactivate() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        lastBalance.clear();
        instance = null;
        scoreSubmit = null;
    }

    private void pollBalances() {
        if (instance == null || economy == null || scoreSubmit == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            double current = economy.getBalance(player);
            double last = lastBalance.getOrDefault(player.getUniqueId(), current);
            double delta = current - last;

            if (delta > 0) {
                scoreSubmit.submit(player, delta, Map.of("balance_delta", delta));
            }
            lastBalance.put(player.getUniqueId(), current);
        }
    }
}
