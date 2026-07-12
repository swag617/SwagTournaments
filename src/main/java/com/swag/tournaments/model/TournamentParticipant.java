package com.swag.tournaments.model;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class TournamentParticipant {

    private final UUID playerUuid;
    private String playerName;
    private double score;
    private final Set<String> uniqueKeys;
    private final long joinedAt;

    public TournamentParticipant(UUID playerUuid, String playerName) {
        this.playerUuid = playerUuid;
        this.playerName = playerName;
        this.score = 0.0;
        this.uniqueKeys = new HashSet<>();
        this.joinedAt = System.currentTimeMillis();
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public void addScore(double delta) {
        this.score += delta;
    }

    public boolean addUniqueKey(String key) {
        return uniqueKeys.add(key);
    }

    public int getUniqueKeyCount() {
        return uniqueKeys.size();
    }

    public long getJoinedAt() {
        return joinedAt;
    }
}
