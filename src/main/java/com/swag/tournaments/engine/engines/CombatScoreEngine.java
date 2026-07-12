package com.swag.tournaments.engine.engines;

import com.swag.tournaments.engine.FormulaEvaluator;
import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.model.TournamentInstance;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

public class CombatScoreEngine implements ScoringEngine, Listener {

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
    public void onEntityDeath(EntityDeathEvent event) {
        if (instance == null || scoreSubmit == null) return;

        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        LivingEntity dead = event.getEntity();
        if (dead instanceof Player) return;
        if (!isHostile(dead)) return;

        List<String> filter = instance.getTemplate().getCombatEntities();
        if (!filter.isEmpty()) {
            String typeName = dead.getType().name();
            boolean matched = filter.stream().anyMatch(f -> f.equalsIgnoreCase(typeName));
            if (!matched) return;
        }

        double maxHealth = 0.0;
        var healthAttr = dead.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            maxHealth = healthAttr.getValue();
        }

        Map<String, Double> vars = Map.of(
                "max_health", maxHealth,
                "entity_type", (double) dead.getType().ordinal()
        );

        double delta = FormulaEvaluator.evaluate(instance.getTemplate().getScoreFormula(), vars);

        scoreSubmit.submit(killer, delta, Map.of(
                "entity_type", dead.getType().name(),
                "max_health", maxHealth
        ));
    }

    private boolean isHostile(LivingEntity entity) {
        return entity instanceof Monster
                || entity instanceof Slime
                || entity instanceof Ghast
                || entity instanceof Phantom
                || entity instanceof Shulker
                || entity instanceof ElderGuardian
                || entity instanceof Guardian
                || entity instanceof Warden;
    }
}
