package com.swag.tournaments.engine.engines;

import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.model.TournamentInstance;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CUSTOM type — score is submitted exclusively via the public API
 * (SwagTournamentsAPI.submitCustomScore or /ta score command).
 * No Bukkit event listeners are registered.
 */
public class CustomScoreEngine implements ScoringEngine {

    @Override
    public void onActivate(TournamentInstance instance, JavaPlugin plugin,
                           ScoreSubmitCallback scoreSubmit) {
        // intentionally empty — API-driven only
    }

    @Override
    public void onDeactivate() {
        // intentionally empty
    }
}
