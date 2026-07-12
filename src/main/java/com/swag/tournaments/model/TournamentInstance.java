package com.swag.tournaments.model;

import org.bukkit.boss.BossBar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TournamentInstance {

    private long instanceId = -1;
    private final TournamentTemplate template;
    private TournamentStatus status;
    private final long startedAt;
    private final long scheduledEndAt;
    private final String source;

    private final Map<UUID, TournamentParticipant> participants = new ConcurrentHashMap<>();
    private BossBar bossBar;

    public TournamentInstance(TournamentTemplate template, long startedAt, int durationMinutes, String source) {
        this.template = template;
        this.status = TournamentStatus.ACTIVE;
        this.startedAt = startedAt;
        this.scheduledEndAt = startedAt + (long) durationMinutes * 60_000;
        this.source = source;
    }

    public long getTimeRemainingSeconds() {
        long remaining = scheduledEndAt - System.currentTimeMillis();
        return Math.max(0, remaining / 1000);
    }

    /**
     * Returns participants sorted descending by score. For FIRST_TO / SUM / MAX / UNIQUE_COUNT,
     * higher score = better. Ties broken by earliest join time.
     */
    public List<TournamentParticipant> getLeaderboard() {
        return participants.values().stream()
                .sorted(Comparator
                        .comparingDouble(TournamentParticipant::getScore).reversed()
                        .thenComparingLong(TournamentParticipant::getJoinedAt))
                .toList();
    }

    /**
     * Applies a score delta to the given player according to the tournament's scoring mode.
     * Thread-safe via ConcurrentHashMap compute; all scoring logic runs on the main thread
     * because Bukkit events fire there.
     *
     * @param uuid       player uuid
     * @param playerName player name (used to register new participants)
     * @param delta      raw delta from the scoring engine
     * @param uniqueKey  for UNIQUE_COUNT mode; null or empty to skip uniqueness tracking
     * @return the new score after applying the delta, or -1 if the player was not updated
     */
    public double submitScore(UUID uuid, String playerName, double delta, String uniqueKey) {
        TournamentParticipant p = participants.computeIfAbsent(uuid,
                id -> new TournamentParticipant(id, playerName));

        switch (template.getScoringMode()) {
            case SUM -> p.addScore(delta);
            case MAX -> {
                if (delta > p.getScore()) p.setScore(delta);
            }
            case UNIQUE_COUNT -> {
                if (uniqueKey != null && !uniqueKey.isEmpty()) {
                    if (p.addUniqueKey(uniqueKey)) {
                        p.setScore(p.getUniqueKeyCount());
                    }
                }
            }
            case FIRST_TO -> {
                p.addScore(delta);
                // The calling manager checks target score and ends the tournament.
            }
        }

        return p.getScore();
    }

    public TournamentParticipant getParticipant(UUID uuid) {
        return participants.get(uuid);
    }

    public boolean hasParticipant(UUID uuid) {
        return participants.containsKey(uuid);
    }

    public int getParticipantCount() {
        return participants.size();
    }

    // ---- Getters / Setters ----

    public long getInstanceId() { return instanceId; }
    public void setInstanceId(long instanceId) { this.instanceId = instanceId; }

    public TournamentTemplate getTemplate() { return template; }

    public TournamentStatus getStatus() { return status; }
    public void setStatus(TournamentStatus status) { this.status = status; }

    public long getStartedAt() { return startedAt; }
    public long getScheduledEndAt() { return scheduledEndAt; }

    public String getSource() { return source; }

    public Map<UUID, TournamentParticipant> getParticipants() { return participants; }

    public BossBar getBossBar() { return bossBar; }
    public void setBossBar(BossBar bossBar) { this.bossBar = bossBar; }
}
