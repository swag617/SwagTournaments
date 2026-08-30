package com.swag.tournaments.manager;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.database.TournamentRepository;
import com.swag.tournaments.engine.ScoringEngine;
import com.swag.tournaments.engine.ScoringEngineRegistry;
import com.swag.tournaments.integration.IntegrationManager;
import com.swag.tournaments.model.*;
import org.bukkit.*;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.logging.Logger;

@SuppressWarnings("deprecation")
public class TournamentManager {

    private final SwagTournaments plugin;
    private final Logger log;
    private final TournamentRepository repository;
    private final RewardManager rewardManager;
    private final ScoringEngineRegistry engineRegistry;

    private IntegrationManager integrationManager;

    private TournamentInstance currentInstance;
    private ScoringEngine activeEngine;
    private BukkitTask endTask;
    private BukkitTask barTask;

    public TournamentManager(SwagTournaments plugin,
                             TournamentRepository repository,
                             RewardManager rewardManager,
                             ScoringEngineRegistry engineRegistry) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.repository = repository;
        this.rewardManager = rewardManager;
        this.engineRegistry = engineRegistry;
    }

    /**
     * Starts a tournament from the given template. Returns false if blocked.
     * Must be called on the main thread.
     */
    public boolean startTournament(TournamentTemplate template, int durationMinutes, String source) {
        if (currentInstance != null && currentInstance.getStatus() == TournamentStatus.ACTIVE) {
            log.warning("Cannot start tournament '" + template.getId()
                    + "': a tournament is already active (" + currentInstance.getTemplate().getId() + ").");
            return false;
        }

        // Overlap: same type already running (extra guard in case status check race)
        if (currentInstance != null
                && currentInstance.getTemplate().getType() == template.getType()
                && currentInstance.getStatus() != TournamentStatus.ENDED
                && currentInstance.getStatus() != TournamentStatus.CANCELLED) {
            return false;
        }

        ScoringEngine engine = engineRegistry.getEngine(template.getType());
        if (engine == null) {
            log.severe("No scoring engine registered for type " + template.getType());
            return false;
        }

        long startedAt = System.currentTimeMillis();
        TournamentInstance instance = new TournamentInstance(template, startedAt, durationMinutes, source);

        // Insert DB row async, then set instanceId back on main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            long id = repository.insertInstance(template.getId(), startedAt, source, template.getType());
            Bukkit.getScheduler().runTask(plugin, () -> instance.setInstanceId(id));
        });

        // Activate engine with a score callback that routes back to submitScore
        engine.onActivate(instance, plugin,
                (player, delta, metadata) -> submitScore(player, delta, metadata));

        currentInstance = instance;
        activeEngine = engine;

        // BossBar for all online players
        BossBar bar = createBossBar(template, durationMinutes);
        instance.setBossBar(bar);
        for (Player p : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(p);
        }

        // Broadcast start
        String startMsg = buildStartMessage(template, durationMinutes);
        Bukkit.broadcastMessage(plugin.getChatPrefix() + startMsg);

        // Fire start sound + fireworks
        if (!template.getSoundStart().isEmpty()) {
            playSoundAll(template.getSoundStart());
        }
        if (template.isStartFireworks()) {
            spawnFireworksAll();
        }

        // Schedule end
        long endTicks = (long) durationMinutes * 60 * 20;
        endTask = Bukkit.getScheduler().runTaskLater(plugin, () -> finishTournament(null), endTicks);

        // BossBar countdown updater every second — stored so it can be cancelled on end
        barTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentInstance != instance) {
                // Stale task — tournament was replaced; self-cancel
                if (barTask != null) { barTask.cancel(); barTask = null; }
                return;
            }
            long rem = instance.getTimeRemainingSeconds();
            float progress = (float) rem / (durationMinutes * 60);
            bar.setProgress(Math.max(0.0f, Math.min(1.0f, progress)));
            bar.setTitle(ChatColor.GOLD + template.getFormattedDisplayName()
                    + ChatColor.YELLOW + " — " + formatTime(rem));
        }, 20L, 20L);

        if (integrationManager != null) {
            integrationManager.onTournamentStart(instance);
        }

        log.info("Tournament '" + template.getId() + "' started (" + durationMinutes + "m, source=" + source + ").");
        return true;
    }

    /**
     * Stops the active tournament. initiator may be null for scheduled/timed ends.
     */
    public void stopTournament(CommandSender initiator) {
        if (currentInstance == null || currentInstance.getStatus() != TournamentStatus.ACTIVE) {
            if (initiator != null) initiator.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "No active tournament to stop.");
            return;
        }
        if (initiator != null) {
            Bukkit.broadcastMessage(plugin.getChatPrefix() + ChatColor.YELLOW + "Tournament stopped by "
                    + initiator.getName() + ".");
        }
        finishTournament(initiator);
    }

    private void finishTournament(CommandSender initiator) {
        if (currentInstance == null) return;
        TournamentInstance instance = currentInstance;
        if (instance.getStatus() != TournamentStatus.ACTIVE) return;

        if (endTask != null) {
            endTask.cancel();
            endTask = null;
        }
        if (barTask != null) {
            barTask.cancel();
            barTask = null;
        }

        instance.setStatus(TournamentStatus.ENDED);

        // Deactivate engine
        if (activeEngine != null) {
            activeEngine.onDeactivate();
            activeEngine = null;
        }

        // Remove boss bar
        BossBar bar = instance.getBossBar();
        if (bar != null) {
            bar.removeAll();
        }

        List<TournamentParticipant> ranked = instance.getLeaderboard();
        TournamentTemplate template = instance.getTemplate();

        // Broadcast results
        broadcastResults(template, ranked);

        // End fireworks
        if (template.isEndFireworks()) spawnFireworksAll();
        if (!template.getSoundEnd().isEmpty()) playSoundAll(template.getSoundEnd());

        // Distribute rewards (main thread)
        rewardManager.distribute(instance, ranked, repository);

        // Persist to DB async
        long endedAt = System.currentTimeMillis();
        String winnerId = ranked.isEmpty() ? null : ranked.get(0).getPlayerUuid().toString();
        double winnerScore = ranked.isEmpty() ? 0.0 : ranked.get(0).getScore();
        int participantCount = ranked.size();
        long instanceId = instance.getInstanceId();
        TournamentParticipant winner = ranked.isEmpty() ? null : ranked.get(0);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Flush all score rows first
            for (int i = 0; i < ranked.size(); i++) {
                TournamentParticipant p = ranked.get(i);
                repository.upsertScore(instanceId, p.getPlayerUuid(), p.getPlayerName(), p.getScore());
                repository.incrementPlayerStats(p.getPlayerUuid(), p.getPlayerName(), i + 1, p.getScore());
                repository.incrementPlayerTypeStats(p.getPlayerUuid(), p.getPlayerName(), template.getType(), i + 1, p.getScore());
            }
            repository.bulkFinalizeRanks(instanceId, ranked);
            repository.finalizeInstance(instanceId, endedAt, winnerId, winnerScore,
                    participantCount, TournamentStatus.ENDED);

            // Feature 2 (Hall of Fame): conditional per-template record upsert. Only a
            // genuine improvement (or first-ever completion) returns true — see
            // TournamentRepository#upsertTemplateRecord for the verified WHERE-guard semantics.
            if (winner != null) {
                boolean isNewRecord = repository.upsertTemplateRecord(
                        template.getId(), winner.getPlayerUuid(), winner.getPlayerName(),
                        winner.getScore(), instanceId, endedAt);
                if (isNewRecord) {
                    Bukkit.getScheduler().runTask(plugin, () -> announceNewRecord(template, winner));
                }
            }
        });

        if (integrationManager != null) {
            integrationManager.onTournamentEnd(instance);
        }

        currentInstance = null;
        log.info("Tournament '" + template.getId() + "' ended. Participants: " + participantCount);
    }

    /**
     * Submits a score delta for a player. Must be called on the main thread.
     * Engines call this via the ScoreSubmitCallback injected in onActivate.
     */
    public void submitScore(Player player, double delta, Map<String, Object> context) {
        if (currentInstance == null || currentInstance.getStatus() != TournamentStatus.ACTIVE) return;

        TournamentInstance instance = currentInstance;
        double newScore = instance.submitScore(
                player.getUniqueId(),
                player.getName(),
                delta,
                context != null ? String.valueOf(context.getOrDefault("unique_key", "")) : ""
        );

        // FIRST_TO check
        if (instance.getTemplate().getScoringMode() == ScoringMode.FIRST_TO) {
            double target = instance.getTemplate().getTargetScore();
            if (target > 0 && newScore >= target) {
                player.sendMessage(plugin.getChatPrefix() + ChatColor.GOLD + "You reached the target score of "
                        + (int) target + "! Tournament ending...");
                // Schedule to next tick so the event handler finishes cleanly
                Bukkit.getScheduler().runTask(plugin, () -> finishTournament(null));
            }
        }

        // Async score flush to DB (fire-and-forget; final ranks written on end)
        // Guard: instanceId is -1 if the async DB insert hasn't completed yet — skip and
        // rely on the final bulk upsert in finishTournament to persist the score.
        long instanceId = instance.getInstanceId();
        if (instanceId >= 0) {
            UUID uuid = player.getUniqueId();
            String name = player.getName();
            Bukkit.getScheduler().runTaskAsynchronously(plugin,
                    () -> repository.upsertScore(instanceId, uuid, name, newScore));
        }
    }

    public boolean isActive() {
        return currentInstance != null && currentInstance.getStatus() == TournamentStatus.ACTIVE;
    }

    /**
     * Called from onDisable to synchronously flush the current tournament's scores to DB
     * before the plugin shuts down (async tasks won't run after onDisable returns).
     * Does NOT distribute rewards or broadcast — just persists the state.
     */
    public void shutdownFlush() {
        if (currentInstance == null) return;
        TournamentInstance instance = currentInstance;
        if (instance.getStatus() != TournamentStatus.ACTIVE) return;

        // Cancel timers immediately
        if (endTask != null) { endTask.cancel(); endTask = null; }
        if (barTask != null) { barTask.cancel(); barTask = null; }

        instance.setStatus(TournamentStatus.ENDED);

        if (activeEngine != null) { activeEngine.onDeactivate(); activeEngine = null; }
        BossBar bar = instance.getBossBar();
        if (bar != null) bar.removeAll();

        currentInstance = null;

        long instanceId = instance.getInstanceId();
        if (instanceId < 0) return; // DB row was never inserted (race on start)

        List<TournamentParticipant> ranked = instance.getLeaderboard();
        long endedAt = System.currentTimeMillis();
        String winnerId = ranked.isEmpty() ? null : ranked.get(0).getPlayerUuid().toString();
        double winnerScore = ranked.isEmpty() ? 0.0 : ranked.get(0).getScore();

        // Synchronous DB flush — running on main thread during shutdown is acceptable
        for (int i = 0; i < ranked.size(); i++) {
            TournamentParticipant p = ranked.get(i);
            repository.upsertScore(instanceId, p.getPlayerUuid(), p.getPlayerName(), p.getScore());
            repository.incrementPlayerStats(p.getPlayerUuid(), p.getPlayerName(), i + 1, p.getScore());
            repository.incrementPlayerTypeStats(p.getPlayerUuid(), p.getPlayerName(), instance.getTemplate().getType(), i + 1, p.getScore());
        }
        repository.bulkFinalizeRanks(instanceId, ranked);
        repository.finalizeInstance(instanceId, endedAt, winnerId, winnerScore,
                ranked.size(), TournamentStatus.ENDED);

        log.info("Tournament '" + instance.getTemplate().getId() + "' state flushed to DB on shutdown.");
    }

    public TournamentInstance getCurrentInstance() {
        return currentInstance;
    }

    public ScoringEngineRegistry getEngineRegistry() {
        return engineRegistry;
    }

    public void setIntegrationManager(IntegrationManager integrationManager) {
        this.integrationManager = integrationManager;
    }

    // ---- Helpers ----

    private BossBar createBossBar(TournamentTemplate template, int durationMinutes) {
        BossBar bar = Bukkit.createBossBar(
                ChatColor.GOLD + template.getFormattedDisplayName()
                        + ChatColor.YELLOW + " — " + durationMinutes + "m",
                template.getBarColor(),
                template.getBarStyle()
        );
        bar.setVisible(true);
        bar.setProgress(1.0);
        return bar;
    }

    private String buildStartMessage(TournamentTemplate template, int durationMinutes) {
        String msg = template.getMessageStart();
        if (msg == null || msg.isEmpty()) {
            msg = "&6★ Tournament &e" + template.getDisplayName() + "&6 has begun! Duration: &e" + durationMinutes + "m";
        }
        return ChatColor.translateAlternateColorCodes('&', msg)
                .replace("{duration}", String.valueOf(durationMinutes))
                .replace("{type}", template.getType().name())
                .replace("{name}", template.getFormattedDisplayName());
    }

    private void broadcastResults(TournamentTemplate template, List<TournamentParticipant> ranked) {
        String endMsg = template.getMessageEnd();
        if (endMsg == null || endMsg.isEmpty()) {
            endMsg = "&6★ Tournament &e" + template.getDisplayName() + "&6 has ended!";
        }
        endMsg = ChatColor.translateAlternateColorCodes('&', endMsg);

        if (!ranked.isEmpty()) {
            TournamentParticipant winner = ranked.get(0);
            endMsg = endMsg.replace("{winner}", winner.getPlayerName())
                    .replace("{score}", formatScore(winner.getScore()));
        }

        Bukkit.broadcastMessage(plugin.getChatPrefix() + endMsg);

        // Top 3 summary
        int show = Math.min(3, ranked.size());
        for (int i = 0; i < show; i++) {
            TournamentParticipant p = ranked.get(i);
            String medal = switch (i) {
                case 0 -> ChatColor.GOLD + "1st";
                case 1 -> ChatColor.GRAY + "2nd";
                case 2 -> ChatColor.YELLOW + "3rd";
                default -> ChatColor.WHITE + "#" + (i + 1);
            };
            Bukkit.broadcastMessage(medal + ChatColor.WHITE + ": "
                    + ChatColor.YELLOW + p.getPlayerName()
                    + ChatColor.GRAY + " — " + formatScore(p.getScore()));
        }
    }

    /**
     * Feature 2 (Hall of Fame): broadcasts and announces a genuine new all-time-best score
     * for a template. Called only after {@link TournamentRepository#upsertTemplateRecord}
     * reports a real change (main thread, via the runTask hop in {@link #finishTournament}).
     */
    private void announceNewRecord(TournamentTemplate template, TournamentParticipant winner) {
        Bukkit.broadcastMessage(plugin.getChatPrefix() + ChatColor.GOLD + "🏆 New record! "
                + ChatColor.YELLOW + winner.getPlayerName() + ChatColor.GOLD
                + " set a new all-time high in " + ChatColor.YELLOW + template.getFormattedDisplayName()
                + ChatColor.GOLD + ": " + ChatColor.YELLOW + formatScore(winner.getScore()) + ChatColor.GOLD + "!");

        if (integrationManager != null) {
            integrationManager.onHallOfFameRecord(template, winner.getPlayerName(), winner.getScore());
        }
    }

    private void playSoundAll(String soundName) {
        Sound sound;
        try {
            sound = Sound.valueOf(soundName.toUpperCase().replace(".", "_"));
        } catch (IllegalArgumentException e) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    private void spawnFireworksAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Firework fw = p.getWorld().spawn(p.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                    .withColor(Color.YELLOW, Color.ORANGE)
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .trail(true)
                    .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        }
    }

    private String formatTime(long seconds) {
        if (seconds >= 3600) {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        } else if (seconds >= 60) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    private String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) {
            return String.valueOf((long) score);
        }
        return String.format("%.2f", score);
    }
}
