package com.swag.tournaments.api;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentParticipant;
import com.swag.tournaments.model.TournamentType;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class SwagTournamentsAPI {

    private SwagTournamentsAPI() {}

    private static SwagTournaments plugin() {
        return SwagTournaments.getInstance();
    }

    public static boolean isTournamentActive() {
        SwagTournaments p = plugin();
        if (p == null) return false;
        return p.getTournamentManager().isActive();
    }

    public static Optional<String> getActiveTemplateId() {
        SwagTournaments p = plugin();
        if (p == null) return Optional.empty();
        TournamentInstance inst = p.getTournamentManager().getCurrentInstance();
        if (inst == null) return Optional.empty();
        return Optional.of(inst.getTemplate().getId());
    }

    public static Optional<TournamentType> getActiveType() {
        SwagTournaments p = plugin();
        if (p == null) return Optional.empty();
        TournamentInstance inst = p.getTournamentManager().getCurrentInstance();
        if (inst == null) return Optional.empty();
        return Optional.of(inst.getTemplate().getType());
    }

    public static boolean submitCustomScore(Player player, double delta) {
        if (player == null) return false;
        SwagTournaments p = plugin();
        if (p == null) return false;
        TournamentInstance inst = p.getTournamentManager().getCurrentInstance();
        if (inst == null) return false;
        if (inst.getTemplate().getType() != TournamentType.CUSTOM) return false;
        p.getTournamentManager().submitScore(player, delta, Collections.emptyMap());
        return true;
    }

    public static List<TournamentParticipant> getLeaderboard(int topN) {
        SwagTournaments p = plugin();
        if (p == null) return Collections.emptyList();
        TournamentInstance inst = p.getTournamentManager().getCurrentInstance();
        if (inst == null) return Collections.emptyList();
        List<TournamentParticipant> lb = inst.getLeaderboard();
        if (topN <= 0 || topN >= lb.size()) return lb;
        return lb.subList(0, topN);
    }

    public static void registerExternalEngine(TournamentType type, ScoringEngine engine) {
        SwagTournaments p = plugin();
        if (p == null) return;
        p.getEngineRegistry().registerEngine(type, engine);
    }
}
