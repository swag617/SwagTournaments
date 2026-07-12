package com.swag.tournaments.engine.engines;

import com.swag.tournaments.engine.FormulaEvaluator;
import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.model.TournamentInstance;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles FARMING type tournaments without SwagFarming present.
 * Counts only fully-grown crop blocks broken by a player.
 * SwagFarmingBridge may replace this engine via ScoringEngineRegistry.registerEngine()
 * to integrate crop quality data.
 */
public class FarmingScoreEngine implements ScoringEngine, Listener {

    private static final Set<Material> CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.MELON, Material.PUMPKIN,
            Material.SUGAR_CANE, Material.BAMBOO, Material.SWEET_BERRY_BUSH,
            Material.KELP_PLANT, Material.CHORUS_FLOWER
    );

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
    public void onBlockBreak(BlockBreakEvent event) {
        if (instance == null || scoreSubmit == null) return;

        Block block = event.getBlock();
        if (!CROPS.contains(block.getType())) return;

        // Only score fully grown crops
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;
        }

        List<String> actionFilter = instance.getTemplate().getFarmingActions();
        if (!actionFilter.isEmpty() && !actionFilter.stream().anyMatch(a -> a.equalsIgnoreCase("HARVEST"))) {
            return;
        }

        Player player = event.getPlayer();
        Map<String, Double> vars = Map.of("value", 1.0);
        double delta = FormulaEvaluator.evaluate(instance.getTemplate().getScoreFormula(), vars);

        scoreSubmit.submit(player, delta, Map.of(
                "material", block.getType().name(),
                "action", "HARVEST"
        ));
    }
}
