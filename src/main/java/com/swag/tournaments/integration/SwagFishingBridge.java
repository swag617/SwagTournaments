package com.swag.tournaments.integration;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.engine.FormulaEvaluator;
import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.engine.engines.FishingScoreEngine;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentType;
import com.swagserv.swagfishing.SwagFishing;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

/**
 * Integrates with SwagFishing for FISHING type tournaments.
 *
 * When defer-to-swagfishing=true on the template, this bridge handles scoring at MONITOR
 * priority (after SwagFishing's HIGHEST listener). It replaces the default FishingScoreEngine
 * in the registry with a delegating wrapper so that the vanilla engine doesn't double-score.
 *
 * When defer-to-swagfishing=false, the vanilla FishingScoreEngine runs as normal and
 * this bridge's event handler is a no-op for that tournament.
 */
public class SwagFishingBridge implements Listener {

    private static final String SWAGFISHING_NAMESPACE = "swagfishing";

    private final SwagTournaments plugin;
    private SwagFishing swagFishing;
    private boolean active;

    public SwagFishingBridge(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            swagFishing = (SwagFishing) Bukkit.getPluginManager().getPlugin("SwagFishing");
            if (swagFishing != null) {
                Bukkit.getPluginManager().registerEvents(this, plugin);
                active = true;

                // Replace the vanilla FishingScoreEngine with a delegating engine that defers to
                // this bridge for templates with defer-to-swagfishing=true, and falls through
                // to vanilla logic for templates without it.
                plugin.getEngineRegistry().registerEngine(TournamentType.FISHING,
                        new DeferringFishingEngine(this));
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("SwagFishingBridge: failed to hook SwagFishing: " + t.getMessage());
            active = false;
        }
    }

    /**
     * Wraps the vanilla FishingScoreEngine. When the active template has defer-to-swagfishing=true,
     * this engine does nothing — the bridge's MONITOR listener handles scoring instead.
     * Otherwise, it delegates to vanilla FishingScoreEngine.
     */
    private static class DeferringFishingEngine implements ScoringEngine {

        private final SwagFishingBridge bridge;
        private final FishingScoreEngine vanilla = new FishingScoreEngine();

        DeferringFishingEngine(SwagFishingBridge bridge) {
            this.bridge = bridge;
        }

        @Override
        public void onActivate(TournamentInstance instance, JavaPlugin plugin,
                               ScoreSubmitCallback scoreSubmit) {
            if (!instance.getTemplate().isDeferToSwagFishing()) {
                // Bridge is not taking over — use vanilla engine
                vanilla.onActivate(instance, plugin, scoreSubmit);
            }
            // When defer=true, the bridge's MONITOR listener handles everything; engine is inert.
        }

        @Override
        public void onDeactivate() {
            vanilla.onDeactivate();
        }
    }

    /**
     * Checks whether SwagFishing has an active tournament running.
     */
    public boolean isSwagFishingTournamentActive() {
        if (swagFishing == null) return false;
        try {
            return swagFishing.getTournamentManager().isActive();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * MONITOR priority fires after SwagFishing's HIGHEST listener has already processed the catch.
     * Only submits score when our plugin has an active FISHING tournament with defer-to-swagfishing=true.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        if (!active) return;
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        TournamentInstance instance = plugin.getTournamentManager().getCurrentInstance();
        if (instance == null) return;
        if (instance.getTemplate().getType() != TournamentType.FISHING) return;
        if (!instance.getTemplate().isDeferToSwagFishing()) return;

        Player player = event.getPlayer();
        double size = 0.0;

        if (event.getCaught() instanceof Item item) {
            // Try to read fish_size from SwagFishing PDC
            try {
                NamespacedKey sizeKey = new NamespacedKey(SWAGFISHING_NAMESPACE, "fish_size");
                var meta = item.getItemStack().getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(sizeKey, PersistentDataType.DOUBLE)) {
                    size = meta.getPersistentDataContainer().get(sizeKey, PersistentDataType.DOUBLE);
                } else {
                    size = 1.0;
                }
            } catch (Exception e) {
                size = 1.0;
            }
        } else {
            size = 1.0;
        }

        final double finalSize = size;
        Map<String, Double> vars = Map.of("size", finalSize, "value", finalSize);
        double delta = FormulaEvaluator.evaluate(instance.getTemplate().getScoreFormula(), vars);

        plugin.getTournamentManager().submitScore(player, delta, Map.of("size", finalSize));
    }
}
