package com.swag.tournaments.integration;

import com.swag.farming.SwagFarming;
import com.swag.farming.models.PlantedCrop;
import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.engine.FormulaEvaluator;
import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.engine.engines.FarmingScoreEngine;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;

/**
 * Integrates with SwagFarming to get richer crop quality context when present.
 * Falls back to vanilla crop detection when SwagFarming doesn't know the location.
 * Does NOT replace FarmingScoreEngine — instead adds richer scoring context on top.
 */
public class SwagFarmingBridge implements Listener {

    private static final Set<Material> VANILLA_CROPS = Set.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA, Material.MELON, Material.PUMPKIN,
            Material.SUGAR_CANE, Material.BAMBOO, Material.SWEET_BERRY_BUSH,
            Material.KELP_PLANT, Material.CHORUS_FLOWER
    );

    private final SwagTournaments plugin;
    private SwagFarming swagFarming;
    private boolean active;

    public SwagFarmingBridge(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            swagFarming = (SwagFarming) Bukkit.getPluginManager().getPlugin("SwagFarming");
            if (swagFarming != null) {
                Bukkit.getPluginManager().registerEvents(this, plugin);
                active = true;

                // Replace vanilla FarmingScoreEngine with a deferring wrapper (same pattern as fishing bridge)
                plugin.getEngineRegistry().registerEngine(TournamentType.FARMING,
                        new DeferringFarmingEngine(this));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("SwagFarmingBridge: failed to hook SwagFarming: " + t.getMessage());
            active = false;
        }
    }

    private static class DeferringFarmingEngine implements ScoringEngine {

        private final SwagFarmingBridge bridge;
        private final FarmingScoreEngine vanilla = new FarmingScoreEngine();

        DeferringFarmingEngine(SwagFarmingBridge bridge) {
            this.bridge = bridge;
        }

        @Override
        public void onActivate(TournamentInstance instance, JavaPlugin plugin,
                               ScoreSubmitCallback scoreSubmit) {
            if (!instance.getTemplate().isDeferToSwagFarming()) {
                vanilla.onActivate(instance, plugin, scoreSubmit);
            }
        }

        @Override
        public void onDeactivate() {
            vanilla.onDeactivate();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!active) return;

        TournamentInstance instance = plugin.getTournamentManager().getCurrentInstance();
        if (instance == null) return;
        if (instance.getTemplate().getType() != TournamentType.FARMING) return;
        if (!instance.getTemplate().isDeferToSwagFarming()) return;

        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Check action filter
        var actionFilter = instance.getTemplate().getFarmingActions();
        if (!actionFilter.isEmpty() && actionFilter.stream().noneMatch(a -> a.equalsIgnoreCase("HARVEST"))) {
            return;
        }

        double value = 1.0;
        boolean isManagedCrop = false;

        try {
            PlantedCrop crop = swagFarming.getGrowthManager().getPlantedCrop(block.getLocation());
            if (crop != null) {
                isManagedCrop = true;
                // Only score if fully grown (growthProgress >= 1.0 means fully mature)
                if (crop.getGrowthProgress() < 1.0) return;
                // Fertilizer quality bonus scales the score value
                double qualityBonus = crop.getFertilizerQualityBonus();
                value = 1.0 + qualityBonus;
            }
        } catch (Exception e) {
            // SwagFarming API unavailable for this location; fall through to vanilla check
        }

        if (!isManagedCrop) {
            // Vanilla fallback: only score fully-grown crops
            if (!VANILLA_CROPS.contains(block.getType())) return;
            if (block.getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() < ageable.getMaximumAge()) return;
            }
        }

        Map<String, Double> vars = Map.of("value", value, "quality", value);
        double delta = FormulaEvaluator.evaluate(instance.getTemplate().getScoreFormula(), vars);

        plugin.getTournamentManager().submitScore(player, delta, Map.of(
                "material", block.getType().name(),
                "quality", value,
                "action", "HARVEST"
        ));
    }
}
