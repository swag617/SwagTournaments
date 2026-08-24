package com.swag.tournaments.database;

import com.swag.tournaments.model.PlayerTypeStats;
import com.swag.tournaments.model.TournamentParticipant;
import com.swag.tournaments.model.TournamentStatus;
import com.swag.tournaments.model.TournamentType;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

public class TournamentRepository {

    private final DatabaseManager db;
    private final Logger log;

    public TournamentRepository(DatabaseManager db, Logger log) {
        this.db = db;
        this.log = log;
    }

    public long insertInstance(String templateId, long startedAt, String source) {
        String sql = "INSERT INTO tournament_instances (template_id, status, started_at, source) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, templateId);
            ps.setString(2, TournamentStatus.ACTIVE.name());
            ps.setLong(3, startedAt);
            ps.setString(4, source);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
        } catch (SQLException e) {
            log.severe("Failed to insert tournament instance: " + e.getMessage());
        }
        return -1;
    }

    public void finalizeInstance(long id, long endedAt, String winnerId, double winnerScore,
                                 int participantCount, TournamentStatus status) {
        String sql = """
                UPDATE tournament_instances
                SET ended_at=?, winner_uuid=?, winner_score=?, participant_count=?, status=?
                WHERE id=?
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, endedAt);
            ps.setString(2, winnerId);
            ps.setDouble(3, winnerScore);
            ps.setInt(4, participantCount);
            ps.setString(5, status.name());
            ps.setLong(6, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Failed to finalize tournament instance " + id + ": " + e.getMessage());
        }
    }

    public void upsertScore(long instanceId, UUID uuid, String name, double score) {
        String sql = """
                INSERT INTO tournament_scores (instance_id, player_uuid, player_name, score, joined_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(instance_id, player_uuid) DO UPDATE SET
                    player_name=excluded.player_name,
                    score=excluded.score
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, instanceId);
            ps.setString(2, uuid.toString());
            ps.setString(3, name);
            ps.setDouble(4, score);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Failed to upsert score for " + uuid + ": " + e.getMessage());
        }
    }

    public void bulkFinalizeRanks(long instanceId, List<TournamentParticipant> rankedList) {
        String sql = "UPDATE tournament_scores SET final_rank=? WHERE instance_id=? AND player_uuid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            for (int i = 0; i < rankedList.size(); i++) {
                ps.setInt(1, i + 1);
                ps.setLong(2, instanceId);
                ps.setString(3, rankedList.get(i).getPlayerUuid().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            log.severe("Failed to finalize ranks for instance " + instanceId + ": " + e.getMessage());
        }
    }

    public void markRewardGiven(long instanceId, UUID uuid) {
        String sql = "UPDATE tournament_scores SET reward_given=1 WHERE instance_id=? AND player_uuid=?";
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, instanceId);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.warning("Failed to mark reward given: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> getHistory(int page, int pageSize) {
        String sql = """
                SELECT i.id, i.template_id, i.status, i.started_at, i.ended_at,
                       i.winner_uuid, i.winner_score, i.participant_count, i.source
                FROM tournament_instances i
                ORDER BY i.started_at DESC
                LIMIT ? OFFSET ?
                """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setInt(1, pageSize);
            ps.setInt(2, page * pageSize);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("template_id", rs.getString("template_id"));
                    row.put("status", rs.getString("status"));
                    row.put("started_at", rs.getLong("started_at"));
                    row.put("ended_at", rs.getLong("ended_at"));
                    row.put("winner_uuid", rs.getString("winner_uuid"));
                    row.put("winner_score", rs.getDouble("winner_score"));
                    row.put("participant_count", rs.getInt("participant_count"));
                    row.put("source", rs.getString("source"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to fetch history: " + e.getMessage());
        }
        return results;
    }

    public List<Map<String, Object>> getParticipants(long instanceId) {
        String sql = """
                SELECT player_uuid, player_name, score, final_rank, reward_given, joined_at
                FROM tournament_scores
                WHERE instance_id=?
                ORDER BY final_rank ASC, score DESC
                """;
        List<Map<String, Object>> results = new ArrayList<>();
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setLong(1, instanceId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("player_uuid", rs.getString("player_uuid"));
                    row.put("player_name", rs.getString("player_name"));
                    row.put("score", rs.getDouble("score"));
                    row.put("final_rank", rs.getInt("final_rank"));
                    row.put("reward_given", rs.getInt("reward_given") == 1);
                    row.put("joined_at", rs.getLong("joined_at"));
                    results.add(row);
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to fetch participants for instance " + instanceId + ": " + e.getMessage());
        }
        return results;
    }

    public Map<String, Object> getPlayerStats(UUID uuid) {
        String sql = """
                SELECT player_name, tournaments_entered, tournaments_won,
                       total_first_places, total_second_places, total_third_places,
                       best_score_ever, last_seen, first_seen, lifetime_rewards_money
                FROM player_tournament_stats
                WHERE player_uuid=?
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("player_name", rs.getString("player_name"));
                    row.put("tournaments_entered", rs.getInt("tournaments_entered"));
                    row.put("tournaments_won", rs.getInt("tournaments_won"));
                    row.put("total_first_places", rs.getInt("total_first_places"));
                    row.put("total_second_places", rs.getInt("total_second_places"));
                    row.put("total_third_places", rs.getInt("total_third_places"));
                    row.put("best_score_ever", rs.getDouble("best_score_ever"));
                    row.put("last_seen", rs.getLong("last_seen"));
                    row.put("first_seen", rs.getLong("first_seen"));
                    row.put("lifetime_rewards_money", rs.getDouble("lifetime_rewards_money"));
                    return row;
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to fetch player stats for " + uuid + ": " + e.getMessage());
        }
        return null;
    }

    public void incrementPlayerStats(UUID uuid, String playerName, int place, double score) {
        long now = System.currentTimeMillis();
        String sql = """
                INSERT INTO player_tournament_stats
                    (player_uuid, player_name, tournaments_entered, tournaments_won,
                     total_first_places, total_second_places, total_third_places,
                     best_score_ever, last_seen, first_seen)
                VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    player_name=excluded.player_name,
                    tournaments_entered=tournaments_entered + 1,
                    tournaments_won=tournaments_won + excluded.tournaments_won,
                    total_first_places=total_first_places + excluded.total_first_places,
                    total_second_places=total_second_places + excluded.total_second_places,
                    total_third_places=total_third_places + excluded.total_third_places,
                    best_score_ever=MAX(best_score_ever, excluded.best_score_ever),
                    last_seen=excluded.last_seen
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, playerName);
            ps.setInt(3, place == 1 ? 1 : 0);
            ps.setInt(4, place == 1 ? 1 : 0);
            ps.setInt(5, place == 2 ? 1 : 0);
            ps.setInt(6, place == 3 ? 1 : 0);
            ps.setDouble(7, score);
            ps.setLong(8, now);
            ps.setLong(9, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Failed to increment player stats for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Upserts the per-{@link TournamentType} breakdown row for a player, mirroring
     * {@link #incrementPlayerStats(UUID, String, int, double)}'s place-based counting
     * (won/first_places both count place==1; second/third_places count place==2/3;
     * entered always increments; best_score tracks the max seen).
     * playerName is accepted for signature parity with incrementPlayerStats but isn't stored
     * here — the canonical player_name lives on player_tournament_stats.
     */
    public void incrementPlayerTypeStats(UUID uuid, String playerName, TournamentType type, int place, double score) {
        String sql = """
                INSERT INTO player_type_stats
                    (player_uuid, type, entered, won, first_places, second_places, third_places, best_score)
                VALUES (?, ?, 1, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, type) DO UPDATE SET
                    entered=entered + 1,
                    won=won + excluded.won,
                    first_places=first_places + excluded.first_places,
                    second_places=second_places + excluded.second_places,
                    third_places=third_places + excluded.third_places,
                    best_score=MAX(best_score, excluded.best_score)
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, type.name());
            ps.setInt(3, place == 1 ? 1 : 0);
            ps.setInt(4, place == 1 ? 1 : 0);
            ps.setInt(5, place == 2 ? 1 : 0);
            ps.setInt(6, place == 3 ? 1 : 0);
            ps.setDouble(7, score);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Failed to increment player type stats for " + uuid + " (" + type + "): " + e.getMessage());
        }
    }

    /**
     * Returns the per-type breakdown for a player across all {@link TournamentType} values.
     * Types the player has never played come back zeroed rather than being omitted, so callers
     * (e.g. the profile GUI) can render a full 6-row grid without null-checking.
     */
    public Map<TournamentType, PlayerTypeStats> getPlayerTypeStats(UUID uuid) {
        Map<TournamentType, PlayerTypeStats> result = new EnumMap<>(TournamentType.class);
        for (TournamentType type : TournamentType.values()) {
            result.put(type, new PlayerTypeStats(type, 0, 0, 0, 0, 0, 0.0));
        }

        String sql = """
                SELECT type, entered, won, first_places, second_places, third_places, best_score
                FROM player_type_stats
                WHERE player_uuid=?
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    TournamentType type;
                    try {
                        type = TournamentType.valueOf(rs.getString("type"));
                    } catch (IllegalArgumentException e) {
                        continue; // unknown/stale type string — skip rather than fail the whole load
                    }
                    result.put(type, new PlayerTypeStats(
                            type,
                            rs.getInt("entered"),
                            rs.getInt("won"),
                            rs.getInt("first_places"),
                            rs.getInt("second_places"),
                            rs.getInt("third_places"),
                            rs.getDouble("best_score")
                    ));
                }
            }
        } catch (SQLException e) {
            log.severe("Failed to fetch player type stats for " + uuid + ": " + e.getMessage());
        }
        return result;
    }

    /**
     * Adds to a player's lifetime tournament-reward money total on player_tournament_stats.
     * Upsert-safe: if the row doesn't exist yet (e.g. a very first reward racing ahead of
     * incrementPlayerStats's own async flush — see TournamentManager#finishTournament, where
     * RewardManager.distribute() runs, and schedules this, before the score-flush block that
     * calls incrementPlayerStats), a placeholder player_name (the UUID string) is inserted;
     * incrementPlayerStats will overwrite it with the real name on its next upsert.
     */
    public void addLifetimeReward(UUID uuid, double moneyAmount) {
        String sql = """
                INSERT INTO player_tournament_stats (player_uuid, player_name, lifetime_rewards_money)
                VALUES (?, ?, ?)
                ON CONFLICT(player_uuid) DO UPDATE SET
                    lifetime_rewards_money = lifetime_rewards_money + excluded.lifetime_rewards_money
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, uuid.toString());
            ps.setDouble(3, moneyAmount);
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("Failed to add lifetime reward for " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Feature 2 (Hall of Fame): conditionally upserts the all-time-best score for a
     * tournament template. The {@code WHERE excluded.score > template_records.score} clause
     * makes the {@code DO UPDATE} a no-op when {@code score} isn't a genuine improvement
     * (including ties — strictly greater only), so a fresh insert (no existing row) or a
     * real improvement both return {@code true}; an equal-or-lower score returns {@code false}
     * and leaves the existing record row untouched. Verified against SQLite JDBC 3.45.3.0's
     * actual {@code executeUpdate()} affected-row semantics via a standalone probe: 1 for a
     * genuine insert/update, 0 when the WHERE guard blocks the conditional update — see
     * commit notes for Feature 2. Callers use the return value to decide whether to
     * broadcast/announce a new record.
     */
    public boolean upsertTemplateRecord(String templateId, UUID uuid, String playerName,
                                        double score, long instanceId, long achievedAt) {
        String sql = """
                INSERT INTO template_records (template_id, player_uuid, player_name, score, instance_id, achieved_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(template_id) DO UPDATE SET
                    player_uuid = excluded.player_uuid,
                    player_name = excluded.player_name,
                    score = excluded.score,
                    instance_id = excluded.instance_id,
                    achieved_at = excluded.achieved_at
                WHERE excluded.score > template_records.score
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, templateId);
            ps.setString(2, uuid.toString());
            ps.setString(3, playerName);
            ps.setDouble(4, score);
            ps.setLong(5, instanceId);
            ps.setLong(6, achievedAt);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            log.severe("Failed to upsert template record for " + templateId + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the all-time-best record row for a single template, or {@code null} if the
     * template has never been completed with at least one participant.
     */
    public Map<String, Object> getTemplateRecord(String templateId) {
        String sql = """
                SELECT template_id, player_uuid, player_name, score, instance_id, achieved_at
                FROM template_records
                WHERE template_id=?
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql)) {
            ps.setString(1, templateId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapTemplateRecordRow(rs);
            }
        } catch (SQLException e) {
            log.severe("Failed to fetch template record for " + templateId + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns all template record rows (small table — at most one row per template, currently
     * 6 bundled templates). Used by the Hall of Fame GUI; templates with no completed
     * tournament simply have no entry in the returned map.
     */
    public Map<String, Map<String, Object>> getAllTemplateRecords() {
        Map<String, Map<String, Object>> results = new LinkedHashMap<>();
        String sql = """
                SELECT template_id, player_uuid, player_name, score, instance_id, achieved_at
                FROM template_records
                """;
        try (PreparedStatement ps = db.getConnection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.put(rs.getString("template_id"), mapTemplateRecordRow(rs));
            }
        } catch (SQLException e) {
            log.severe("Failed to fetch template records: " + e.getMessage());
        }
        return results;
    }

    private Map<String, Object> mapTemplateRecordRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("template_id", rs.getString("template_id"));
        row.put("player_uuid", rs.getString("player_uuid"));
        row.put("player_name", rs.getString("player_name"));
        row.put("score", rs.getDouble("score"));
        row.put("instance_id", rs.getLong("instance_id"));
        row.put("achieved_at", rs.getLong("achieved_at"));
        return row;
    }
}
