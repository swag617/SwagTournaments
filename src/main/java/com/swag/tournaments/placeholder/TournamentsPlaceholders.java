package com.swag.tournaments.placeholder;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentParticipant;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TournamentsPlaceholders extends PlaceholderExpansion {

    private final SwagTournaments plugin;
    private final Map<UUID, Integer> winsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> winsCacheTime = new ConcurrentHashMap<>();
    private static final long WINS_CACHE_TTL = 60_000L;

    public TournamentsPlaceholders(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "swagtournaments";
    }

    @Override
    public @NotNull String getAuthor() {
        return "swag617";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String identifier) {
        TournamentInstance inst = plugin.getTournamentManager().getCurrentInstance();
        boolean active = plugin.getTournamentManager().isActive();

        switch (identifier) {
            case "active" -> {
                return active ? "true" : "false";
            }
            case "type" -> {
                if (!active || inst == null) return "none";
                return inst.getTemplate().getType().name();
            }
            case "name" -> {
                if (!active || inst == null) return "";
                return inst.getTemplate().getDisplayName();
            }
            case "time_remaining" -> {
                if (!active || inst == null) return "N/A";
                long rem = inst.getTimeRemainingSeconds();
                return String.format("%02d:%02d", rem / 60, rem % 60);
            }
            case "my_rank" -> {
                if (player == null || !active || inst == null) return "-";
                List<TournamentParticipant> lb = inst.getLeaderboard();
                for (int i = 0; i < lb.size(); i++) {
                    if (lb.get(i).getPlayerUuid().equals(player.getUniqueId())) {
                        return String.valueOf(i + 1);
                    }
                }
                return "-";
            }
            case "my_score" -> {
                if (player == null || !active || inst == null) return "0";
                TournamentParticipant p = inst.getParticipant(player.getUniqueId());
                if (p == null) return "0";
                return formatScore(p.getScore());
            }
            case "wins" -> {
                if (player == null) return "0";
                return String.valueOf(getWins(player));
            }
        }

        if (identifier.startsWith("top_") && identifier.length() > 4) {
            String[] parts = identifier.split("_");
            if (parts.length >= 3) {
                int rank;
                try {
                    rank = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    return null;
                }
                if (rank < 1 || rank > 10) return null;
                String field = parts[2];

                if (!active || inst == null) {
                    return field.equals("name") ? "-" : "0";
                }

                List<TournamentParticipant> lb = inst.getLeaderboard();
                if (lb.size() < rank) {
                    return field.equals("name") ? "-" : "0";
                }

                TournamentParticipant p = lb.get(rank - 1);
                return switch (field) {
                    case "name" -> p.getPlayerName();
                    case "score" -> formatScore(p.getScore());
                    default -> null;
                };
            }
        }

        return null;
    }

    private int getWins(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long cacheTime = winsCacheTime.get(uuid);

        if (cacheTime != null && (now - cacheTime) < WINS_CACHE_TTL) {
            return winsCache.getOrDefault(uuid, 0);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Object> stats = plugin.getTournamentRepository().getPlayerStats(uuid);
            int wins = stats != null ? ((Number) stats.get("tournaments_won")).intValue() : 0;
            winsCache.put(uuid, wins);
            winsCacheTime.put(uuid, System.currentTimeMillis());
        });

        return winsCache.getOrDefault(uuid, 0);
    }

    private String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) {
            return String.valueOf((long) score);
        }
        return String.format("%.2f", score);
    }
}
