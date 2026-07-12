package com.swag.tournaments.engine.engines;

import com.swag.tournaments.engine.FormulaEvaluator;
import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.model.TournamentInstance;
import org.bukkit.Material;
import org.bukkit.block.Block;
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

public class MiningScoreEngine implements ScoringEngine, Listener {

    private static final Set<Material> ALL_ORES = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE, Material.NETHER_GOLD_ORE,
            Material.ANCIENT_DEBRIS
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
        Material mat = block.getType();

        List<String> filter = instance.getTemplate().getMiningMaterials();
        if (!filter.isEmpty()) {
            boolean matched = filter.stream().anyMatch(f -> {
                Material m = Material.matchMaterial(f);
                return m != null && m == mat;
            });
            if (!matched) return;
        } else {
            if (!ALL_ORES.contains(mat)) return;
        }

        int y = block.getY();
        int minY = instance.getTemplate().getConditionMinY();
        int maxY = instance.getTemplate().getConditionMaxY();
        if (y < minY || y > maxY) return;

        Player player = event.getPlayer();
        Map<String, Double> vars = Map.of(
                "y_level", (double) y,
                "value", 1.0
        );
        double delta = FormulaEvaluator.evaluate(instance.getTemplate().getScoreFormula(), vars);

        scoreSubmit.submit(player, delta, Map.of(
                "material", mat.name(),
                "y_level", (double) y
        ));
    }
}
