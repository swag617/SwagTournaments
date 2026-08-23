package com.swag.tournaments.integration;

import com.swag.farming.SwagFarming;
import com.swag.farming.events.CropHarvestEvent;
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
 *
 * <p>Managed-crop harvests are scored from SwagFarming's {@code CropHarvestEvent}
 * (fired after the harvest is fully resolved, exposing the real rolled
 * {@code CropQuality} and the crop's {@code CropRarity}) rather than the raw
 * {@code BlockBreakEvent}. This isn't just richer — it's necessary:
 * {@code CropHarvestListener} cancels the {@code BlockBreakEvent} for every
 * known managed crop at {@code EventPriority.HIGH}, and this class's own
 * vanilla-fallback {@code onBlockBreak} handler below runs at
 * {@code MONITOR} with {@code ignoreCancelled = true} — so for a managed
 * crop that handler was never actually being invoked at all, regardless of
 * what value it tried to derive. The two handlers stay mutually exclusive:
 * {@code onBlockBreak} bails out whenever SwagFarming still has a
 * {@code PlantedCrop} tracked at the broken block's location, leaving that
 * case entirely to {@link #onCropHarvest(CropHarvestEvent)}.</p>
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

    /**
     * Scores SwagFarming-managed crop harvests using the real committed
     * quality/rarity from {@link CropHarvestEvent}. See the class javadoc for
     * why this must be a separate listener rather than folded into
     * {@link #onBlockBreak(BlockBreakEvent)}.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onCropHarvest(CropHarvestEvent event) {
        if (!active) return;

        TournamentInstance instance = plugin.getTournamentManager().getCurrentInstance();
        if (instance == null) return;
        if (instance.getTemplate().getType() != TournamentType.FARMING) return;
        if (!instance.getTemplate().isDeferToSwagFarming()) return;

        // Check action filter — CropHarvestEvent only ever represents a completed harvest.
        var actionFilter = instance.getTemplate().getFarmingActions();
        if (!actionFilter.isEmpty() && actionFilter.stream().noneMatch(a -> a.equalsIgnoreCase("HARVEST"))) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Real quality/rarity from SwagFarming instead of the crude "1.0 + fertilizer
        // bonus" approximation the vanilla fallback below is stuck using. CropQuality's
        // sellMultiplier (1.0-3.0) and CropRarity's xpMultiplier (1.0-12.0) are both
        // already-established per-tier weights in SwagFarming's own model, so combining
        // them multiplicatively gives a meaningfully wider, richer score spread than a
        // single additive fertilizer term ever could.
        double value = event.getQuality().getSellMultiplier() * event.getRarity().getXpMultiplier();

        Map<String, Double> vars = Map.of("value", value, "quality", value);
        double delta = FormulaEvaluator.evaluate(instance.getTemplate().getScoreFormula(), vars);

        plugin.getTournamentManager().submitScore(player, delta, Map.of(
                "material", block.getType().name(),
                "quality", value,
                "action", "HARVEST"
        ));
    }

    /**
     * Vanilla-crop fallback: scores plain vanilla crop breaks that SwagFarming
     * isn't tracking (or isn't installed at all). Bails out whenever SwagFarming
     * still has a {@code PlantedCrop} tracked at this location — that case is
     * handled exclusively by {@link #onCropHarvest(CropHarvestEvent)} to avoid
     * double-scoring the same harvest.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!active) return;

        TournamentInstance instance = plugin.getTournamentManager().getCurrentInstance();
        if (instance == null) return;
        if (instance.getTemplate().getType() != TournamentType.FARMING) return;
        if (!instance.getTemplate().isDeferToSwagFarming()) return;

        Block block = event.getBlock();
        Player player = event.getPlayer();

        try {
            if (swagFarming.getGrowthManager().getPlantedCrop(block.getLocation()) != null) {
                // Managed crop — already scored (or will be) via onCropHarvest above.
                return;
            }
        } catch (Exception e) {
            // SwagFarming API unavailable for this location; fall through to vanilla check
        }

        // Check action filter
        var actionFilter = instance.getTemplate().getFarmingActions();
        if (!actionFilter.isEmpty() && actionFilter.stream().noneMatch(a -> a.equalsIgnoreCase("HARVEST"))) {
            return;
        }

        // Vanilla fallback: only score fully-grown crops
        if (!VANILLA_CROPS.contains(block.getType())) return;
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() < ageable.getMaximumAge()) return;
        }

        double value = 1.0;
        Map<String, Double> vars = Map.of("value", value, "quality", value);
        double delta = FormulaEvaluator.evaluate(instance.getTemplate().getScoreFormula(), vars);

        plugin.getTournamentManager().submitScore(player, delta, Map.of(
                "material", block.getType().name(),
                "quality", value,
                "action", "HARVEST"
        ));
    }
}
