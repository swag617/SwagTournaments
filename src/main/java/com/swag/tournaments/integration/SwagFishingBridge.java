package com.swag.tournaments.integration;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.engine.FormulaEvaluator;
import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.engine.engines.FishingScoreEngine;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentType;
import com.swagserv.swagfishing.SwagFishing;
import com.swagserv.swagfishing.events.FishCatchEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Integrates with SwagFishing for FISHING type tournaments.
 *
 * When defer-to-swagfishing=true on the template, this bridge handles scoring at MONITOR
 * priority (after SwagFishing's own listener has fully resolved and delivered the catch and
 * fired {@link FishCatchEvent}). It replaces the default FishingScoreEngine in the registry
 * with a delegating wrapper so that the vanilla engine doesn't double-score.
 *
 * When defer-to-swagfishing=false, the vanilla FishingScoreEngine runs as normal and
 * this bridge's event handler is a no-op for that tournament.
 *
 * <p>Previously this bridge listened to the raw Bukkit {@code PlayerFishEvent} and tried to read
 * {@code catch_size}/{@code fish_rarity} PDC data off {@code event.getCaught()} — but SwagFishing's
 * {@code FishingListener#onPlayerFish} always calls {@code event.getCaught().remove()} and builds
 * a completely separate custom item delivered straight to the player, so that entity never carried
 * real data to begin with. Every catch silently scored as a flat 1.0/1.0 as a result. Fixed
 * 2026-08-24 by switching to SwagFishing's new {@link FishCatchEvent}, fired once the catch has
 * actually been resolved and delivered, which carries the real rolled size and species rarity
 * directly — no PDC guesswork needed.
 */
public class SwagFishingBridge implements Listener {

    // Rarity score multiplier, ordered rarest-to-lowest chance to highest-to-lowest.
    // SwagFishing's Fish.FishRarity carries a "baseWeight" per tier (spawn-chance weight, NOT a
    // scoring multiplier — QUARTZ=1.0 down to PRISMATIC=0.005), so it isn't reused directly here.
    // Instead this is a simple ordinal-based scale: each tier up is worth meaningfully more,
    // mirroring the tiers' relative rarity without hard-coupling to spawn-weight semantics.
    private static final Map<com.swagserv.swagfishing.models.Fish.FishRarity, Double> RARITY_MULTIPLIERS =
            new EnumMap<>(com.swagserv.swagfishing.models.Fish.FishRarity.class);
    static {
        RARITY_MULTIPLIERS.put(com.swagserv.swagfishing.models.Fish.FishRarity.QUARTZ, 1.0);
        RARITY_MULTIPLIERS.put(com.swagserv.swagfishing.models.Fish.FishRarity.EMERALD, 1.5);
        RARITY_MULTIPLIERS.put(com.swagserv.swagfishing.models.Fish.FishRarity.SAPPHIRE, 2.0);
        RARITY_MULTIPLIERS.put(com.swagserv.swagfishing.models.Fish.FishRarity.RUBY, 3.0);
        RARITY_MULTIPLIERS.put(com.swagserv.swagfishing.models.Fish.FishRarity.AMETHYST, 5.0);
        RARITY_MULTIPLIERS.put(com.swagserv.swagfishing.models.Fish.FishRarity.PRISMATIC, 10.0);
    }

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
     * MONITOR priority fires after SwagFishing has fully resolved, delivered, and fired
     * {@link FishCatchEvent} for the catch. Only submits score when our plugin has an active
     * FISHING tournament with defer-to-swagfishing=true.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onFishCatch(FishCatchEvent event) {
        if (!active) return;

        TournamentInstance instance = plugin.getTournamentManager().getCurrentInstance();
        if (instance == null) return;
        if (instance.getTemplate().getType() != TournamentType.FISHING) return;
        if (!instance.getTemplate().isDeferToSwagFishing()) return;

        Player player = event.getPlayer();

        // Real rolled size straight off the event — no more PDC guesswork on a deleted entity.
        // A species with no configured min/max size range rolls catchSize == 0.0 (see
        // FishingListener#onPlayerFish), so fall back to 1.0 the same way the old PDC path did
        // when the key was absent, to avoid zeroing out every formula that multiplies by size.
        double size = event.getCatchSize() > 0 ? event.getCatchSize() : 1.0;

        double rarityMultiplier = 1.0;
        com.swagserv.swagfishing.models.Fish.FishRarity rarity = event.getRarity();
        if (rarity != null) {
            rarityMultiplier = RARITY_MULTIPLIERS.getOrDefault(rarity, 1.0);
        }

        // "size" and "value" are preserved for existing score-formula templates; "rarity" and
        // "rarityMultiplier" are additive so new templates can weight by fish rarity tier without
        // breaking formulas that only reference size/value. "xp"/"essence"/"money" are newly
        // available now that FishCatchEvent carries real post-multiplier reward totals, for
        // templates that want to score off actual catch value instead of/alongside size+rarity.
        Map<String, Double> vars = Map.of(
                "size", size,
                "value", size,
                "rarity", rarityMultiplier,
                "rarityMultiplier", rarityMultiplier,
                "xp", (double) event.getXpAwarded(),
                "essence", (double) event.getEssenceAwarded(),
                "money", event.getMoneyValue()
        );
        double delta = FormulaEvaluator.evaluate(instance.getTemplate().getScoreFormula(), vars);

        plugin.getTournamentManager().submitScore(player, delta, Map.of(
                "size", size,
                "rarityMultiplier", rarityMultiplier
        ));
    }
}
