package com.swag.tournaments.engine;

import com.swag.tournaments.model.TournamentInstance;
import org.bukkit.plugin.java.JavaPlugin;

public interface ScoringEngine {

    /**
     * Called when a tournament using this engine becomes active.
     * Implementations should register their Bukkit event listeners here.
     *
     * @param instance    the active tournament instance
     * @param plugin      the plugin instance for listener registration
     * @param scoreSubmit callback to submit a score delta: (player, delta, metadata)
     */
    void onActivate(TournamentInstance instance, JavaPlugin plugin,
                    ScoreSubmitCallback scoreSubmit);

    /**
     * Called when the tournament ends or is cancelled.
     * Implementations must unregister all listeners registered in onActivate.
     */
    void onDeactivate();

    @FunctionalInterface
    interface ScoreSubmitCallback {
        void submit(org.bukkit.entity.Player player, double delta, java.util.Map<String, Object> metadata);
    }
}
