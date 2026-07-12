package com.swag.tournaments.engine.engines;

import com.swag.tournaments.engine.FormulaEvaluator;
import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.model.TournamentInstance;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Handles FISHING type tournaments without SwagFishing present.
 * When SwagFishing is loaded, SwagFishingBridge replaces this engine via
 * ScoringEngineRegistry.registerEngine().
 *
 * Uses MONITOR priority so it fires after all other plugins process the catch.
 */
public class FishingScoreEngine implements ScoringEngine, Listener {

    private TournamentInstance instance;
    private ScoreSubmitCallback scoreSubmit;

    @Override
    public void onActivate(TournamentInstance instance, JavaPlugin plugin,
                           ScoreSubmitCallback scoreSubmit) {
        this.instance = instance;
        this.scoreSubmit = scoreSubmit;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void onDeactivate() {
        HandlerList.unregisterAll(this);
        instance = null;
        scoreSubmit = null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (instance == null || scoreSubmit == null) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        Player player = event.getPlayer();

        double size = 0.0;
        if (event.getCaught() instanceof Item) {
            // Vanilla fishing doesn't expose size; default to 1 for SUM mode
            size = 1.0;
        }

        Map<String, Double> vars = Map.of("size", size, "value", size);
        double delta = FormulaEvaluator.evaluate(instance.getTemplate().getScoreFormula(), vars);

        scoreSubmit.submit(player, delta, Map.of("size", size));
    }
}
