package com.swag.tournaments.engine;

import com.swag.tournaments.engine.engines.*;
import com.swag.tournaments.model.TournamentType;

import java.util.EnumMap;
import java.util.Map;

public class ScoringEngineRegistry {

    private final Map<TournamentType, ScoringEngine> engines = new EnumMap<>(TournamentType.class);

    public ScoringEngineRegistry(boolean swagFishingPresent, boolean swagFarmingPresent) {
        engines.put(TournamentType.FISHING, new FishingScoreEngine());
        engines.put(TournamentType.FARMING, new FarmingScoreEngine());
        engines.put(TournamentType.COMBAT, new CombatScoreEngine());
        engines.put(TournamentType.MINING, new MiningScoreEngine());
        engines.put(TournamentType.ECONOMY, new EconomyScoreEngine());
        engines.put(TournamentType.CUSTOM, new CustomScoreEngine());
    }

    public ScoringEngine getEngine(TournamentType type) {
        return engines.get(type);
    }

    /**
     * Allows other ecosystem plugins (or a SwagFishingBridge / SwagFarmingBridge) to replace
     * the default engine for a given type after startup.
     */
    public void registerEngine(TournamentType type, ScoringEngine engine) {
        engines.put(type, engine);
    }
}
