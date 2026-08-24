package com.swag.tournaments.manager;

import com.swag.tournaments.integration.IntegrationManager;
import com.swag.tournaments.model.CronSlot;
import com.swag.tournaments.model.TournamentTemplate;
import com.swag.tournaments.model.TournamentType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.Collections;
import java.util.logging.Logger;

@SuppressWarnings("deprecation")
public class SchedulerManager {

    /**
     * Bundles a template reference with its cron slot for scheduling purposes.
     */
    public record ScheduledSlot(TournamentTemplate template, CronSlot slot) {}

    /**
     * Return type for getNextScheduledStart().
     */
    public record NextSchedule(TournamentTemplate template, LocalDateTime fireTime) {}

    private final JavaPlugin plugin;
    private final Logger log;
    private final TemplateManager templateManager;
    private final TournamentManager tournamentManager;
    private final IntegrationManager integrationManager;

    private List<ScheduledSlot> slots = new ArrayList<>();
    private BukkitTask cronTask;
    private BukkitTask autoTask;

    private long lastFiredEpochMinute = -1;
    private LocalDateTime nextAutoStartAt;
    private TournamentTemplate nextAutoTemplate;

    private final Random random = new Random();

    public SchedulerManager(JavaPlugin plugin,
                            TemplateManager templateManager,
                            TournamentManager tournamentManager,
                            IntegrationManager integrationManager) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.templateManager = templateManager;
        this.tournamentManager = tournamentManager;
        this.integrationManager = integrationManager;
    }

    public void start() {
        rebuildSlots();

        // Per-minute cron check (every 20 ticks = 1 second; check every 60 ticks for responsiveness)
        cronTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cronTick, 60L, 60L);

        syncAutoTask();

        log.info("SchedulerManager started with " + slots.size() + " cron slot(s).");
    }

    public void stop() {
        if (cronTask != null) {
            cronTask.cancel();
            cronTask = null;
        }
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    /**
     * Starts or stops the auto-rotation task to match the current "auto-schedule.enabled"
     * config value. Safe to call repeatedly and from any runtime mutation path (web toggle,
     * template create/update/delete, /tadmin reload) — a no-op if the task is already in the
     * correct state. Without this, flipping the toggle at runtime had no effect until a full
     * server restart because autoTask was only ever created once, inside start().
     */
    public void syncAutoTask() {
        boolean enabled = plugin.getConfig().getBoolean("auto-schedule.enabled", false);
        if (enabled && autoTask == null) {
            int intervalMinutes = plugin.getConfig().getInt("auto-schedule.interval-minutes", 180);
            scheduleNextAuto(intervalMinutes);
            autoTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autoTick, 1200L, 1200L);
            log.info("SchedulerManager: auto-rotation enabled.");
        } else if (!enabled && autoTask != null) {
            autoTask.cancel();
            autoTask = null;
            nextAutoStartAt = null;
            nextAutoTemplate = null;
            log.info("SchedulerManager: auto-rotation disabled.");
        }
    }

    /**
     * Rebuilds the flat slot list from all currently loaded templates.
     * Called on startup and after any reload.
     */
    public void rebuildSlots() {
        slots = new ArrayList<>();
        for (TournamentTemplate template : templateManager.getTemplates()) {
            for (CronSlot slot : template.getCronSlots()) {
                slots.add(new ScheduledSlot(template, slot));
            }
        }
        log.info("SchedulerManager: rebuilt " + slots.size() + " cron slot(s).");
    }

    private void cronTick() {
        LocalDateTime now = LocalDateTime.now();
        // epochMinute guard: only process once per minute regardless of tick rate
        long currentEpochMinute = now.truncatedTo(ChronoUnit.MINUTES)
                .atZone(ZoneId.systemDefault()).toEpochSecond() / 60;
        if (currentEpochMinute == lastFiredEpochMinute) return;

        // Mark this minute as processed unconditionally so we never re-enter
        // for this same minute even if no slots match
        lastFiredEpochMinute = currentEpochMinute;

        int warningMinutes = plugin.getConfig().getInt("auto-schedule.warning-minutes", 5);

        for (ScheduledSlot ss : slots) {
            CronSlot slot = ss.slot();

            // Warning N minutes before
            LocalDateTime warningTime = now.plusMinutes(warningMinutes)
                    .truncatedTo(ChronoUnit.MINUTES);
            if (slot.matchesNow(warningTime)) {
                broadcastWarning(ss.template(), warningMinutes);
            }

            if (!slot.matchesNow(now.truncatedTo(ChronoUnit.MINUTES))) continue;

            if (tournamentManager.isActive()) {
                log.info("Cron slot skipped for '" + ss.template().getId()
                        + "' — a tournament is already active.");
                continue;
            }

            // FISHING type overlap with SwagFishing
            if (ss.template().getType() == TournamentType.FISHING
                    && integrationManager.isSwagFishingTournamentActive()) {
                log.info("Cron slot skipped for '" + ss.template().getId()
                        + "' — SwagFishing tournament is active.");
                continue;
            }

            boolean started = tournamentManager.startTournament(
                    ss.template(), slot.getDurationMinutes(), "CRON");
            if (started) {
                log.info("Cron-fired tournament '" + ss.template().getId() + "'.");
            }
        }
    }

    private void autoTick() {
        if (nextAutoStartAt == null) return;
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(nextAutoStartAt)) return;

        if (tournamentManager.isActive()) {
            // Reschedule to try again after the current tournament ends (check every minute)
            nextAutoStartAt = now.plusMinutes(1);
            return;
        }

        int minPlayers = plugin.getConfig().getInt("auto-schedule.min-players", 2);
        if (Bukkit.getOnlinePlayers().size() < minPlayers) {
            nextAutoStartAt = now.plusMinutes(1);
            return;
        }

        TournamentTemplate picked = nextAutoTemplate;
        if (picked == null) {
            log.warning("Auto-rotation: no templates in rotation pool.");
            scheduleNextAuto(plugin.getConfig().getInt("auto-schedule.interval-minutes", 180));
            return;
        }

        if (picked.getType() == TournamentType.FISHING
                && integrationManager.isSwagFishingTournamentActive()) {
            log.info("Auto-rotation skipped for '" + picked.getId()
                    + "' — SwagFishing tournament is active.");
            scheduleNextAuto(plugin.getConfig().getInt("auto-schedule.interval-minutes", 180));
            return;
        }

        int duration = plugin.getConfig().getInt("auto-schedule.duration-minutes", 30);
        boolean started = tournamentManager.startTournament(picked, duration, "AUTO_ROTATION");
        if (started) {
            log.info("Auto-rotation fired tournament '" + picked.getId() + "'.");
        }
        scheduleNextAuto(plugin.getConfig().getInt("auto-schedule.interval-minutes", 180));
    }

    private void scheduleNextAuto(int intervalMinutes) {
        nextAutoStartAt = LocalDateTime.now().plusMinutes(intervalMinutes);
        // Pick the template ONCE here and carry the same reference through to the actual
        // start in autoTick() — previously the warning broadcast and the real start each
        // independently re-rolled pickWeightedRandom(), so players could see "Tournament X
        // begins in 5m!" and then watch Tournament Y actually start.
        nextAutoTemplate = pickWeightedRandom();

        // Warn players before auto-rotation
        int warningMinutes = plugin.getConfig().getInt("auto-schedule.warning-minutes", 5);
        if (intervalMinutes > warningMinutes && nextAutoTemplate != null) {
            long warningTicks = (long) (intervalMinutes - warningMinutes) * 60 * 20;
            TournamentTemplate warnTemplate = nextAutoTemplate;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // Only warn if auto task hasn't been cancelled, and this pick is still the
                // one scheduled to fire (a reschedule in between — e.g. FISHING overlap skip,
                // empty pool retry — would have replaced nextAutoTemplate with a fresh pick).
                if (autoTask == null) return;
                if (nextAutoTemplate != warnTemplate) return;
                String msg = ChatColor.translateAlternateColorCodes('&',
                        warnTemplate.getMessageWarning().isEmpty()
                                ? "&6★ A &e" + warnTemplate.getType().name()
                                + "&6 tournament begins in &e" + warningMinutes + "m&6!"
                                : warnTemplate.getMessageWarning()
                                .replace("{time}", warningMinutes + "m")
                                .replace("{type}", warnTemplate.getType().name()));
                Bukkit.broadcastMessage(msg);
            }, warningTicks);
        }
    }

    private TournamentTemplate pickWeightedRandom() {
        List<TournamentTemplate> pool = templateManager.getTemplates().stream()
                .filter(TournamentTemplate::isInRotation)
                .toList();

        if (pool.isEmpty()) return null;

        int totalWeight = pool.stream().mapToInt(TournamentTemplate::getRotationWeight).sum();
        if (totalWeight <= 0) return pool.get(random.nextInt(pool.size()));

        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (TournamentTemplate t : pool) {
            cumulative += t.getRotationWeight();
            if (roll < cumulative) return t;
        }
        return pool.get(pool.size() - 1);
    }

    private void broadcastWarning(TournamentTemplate template, int minutesAway) {
        String msg = template.getMessageWarning();
        if (msg == null || msg.isEmpty()) {
            msg = "&6★ A &e" + template.getType().name()
                    + "&6 tournament begins in &e" + minutesAway + "m&6!";
        }
        msg = msg.replace("{time}", minutesAway + "m")
                .replace("{type}", template.getType().name())
                .replace("{name}", template.getDisplayName());
        Bukkit.broadcastMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    public List<ScheduledSlot> getSlots() {
        return Collections.unmodifiableList(slots);
    }

    /**
     * Returns the next upcoming cron-scheduled start across all templates.
     * Looks forward up to 7 days to find the next matching slot.
     */
    public Optional<NextSchedule> getNextScheduledStart() {
        LocalDateTime now = LocalDateTime.now().plusMinutes(1); // skip currently-firing minute
        LocalDateTime searchLimit = now.plusDays(7);

        NextSchedule earliest = null;

        for (ScheduledSlot ss : slots) {
            CronSlot slot = ss.slot();
            LocalDateTime candidate = now.truncatedTo(ChronoUnit.MINUTES);

            while (candidate.isBefore(searchLimit)) {
                if (slot.matchesNow(candidate)) {
                    if (earliest == null || candidate.isBefore(earliest.fireTime())) {
                        earliest = new NextSchedule(ss.template(), candidate);
                    }
                    break;
                }
                candidate = candidate.plusMinutes(1);
            }
        }

        // Also consider auto-rotation if active — reuse the already-picked template rather
        // than re-rolling pickWeightedRandom() again, so this reported "next" template matches
        // what will actually fire (and what the warning broadcast announced).
        if (nextAutoStartAt != null && nextAutoStartAt.isAfter(now)) {
            TournamentTemplate auto = nextAutoTemplate;
            if (auto != null) {
                if (earliest == null || nextAutoStartAt.isBefore(earliest.fireTime())) {
                    earliest = new NextSchedule(auto, nextAutoStartAt);
                }
            }
        }

        return Optional.ofNullable(earliest);
    }
}
