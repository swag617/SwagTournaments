package com.swag.tournaments.model;

/**
 * Per-{@link TournamentType} breakdown of a player's all-time tournament record.
 * Returned by {@code TournamentRepository#getPlayerTypeStats}, one instance per type
 * (types the player has never played come back zeroed rather than being omitted).
 */
public record PlayerTypeStats(TournamentType type, int entered, int won,
                              int firstPlaces, int secondPlaces, int thirdPlaces,
                              double bestScore) {
}
