package com.swag.tournaments.database;

import com.swag.tournaments.model.TournamentParticipant;
import com.swag.tournaments.model.TournamentStatus;

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
                       best_score_ever, last_seen, first_seen
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
}
