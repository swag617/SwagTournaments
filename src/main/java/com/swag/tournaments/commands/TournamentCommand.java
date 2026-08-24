package com.swag.tournaments.commands;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.database.TournamentRepository;
import com.swag.tournaments.gui.*;
import com.swag.tournaments.manager.SchedulerManager;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentTemplate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;

@SuppressWarnings("deprecation")
public class TournamentCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("info", "top", "history", "stats", "next", "profile", "halloffame", "records");
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("EEE MM/dd HH:mm");

    private final SwagTournaments plugin;
    private final GUIListener guiListener;

    public TournamentCommand(SwagTournaments plugin, GUIListener guiListener) {
        this.plugin = plugin;
        this.guiListener = guiListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "This command is player-only.");
            return true;
        }

        if (!player.hasPermission("swagtournaments.use")) {
            player.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "You don't have permission to use this.");
            return true;
        }

        if (args.length == 0) {
            TournamentListGUI gui = new TournamentListGUI(plugin, player, guiListener);
            guiListener.register(player, gui);
            gui.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info" -> handleInfo(player, args);
            case "top" -> handleTop(player, args);
            case "history" -> handleHistory(player);
            case "stats" -> handleStats(player, args);
            case "next" -> handleNext(player);
            case "profile" -> handleProfile(player, args);
            case "halloffame", "records" -> handleHallOfFame(player);
            default -> {
                player.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Unknown subcommand. Usage: /tournament [info|top|history|stats|next|profile|halloffame]");
            }
        }
        return true;
    }

    private void handleInfo(Player player, String[] args) {
        TournamentTemplate template;

        if (args.length >= 2) {
            Optional<TournamentTemplate> opt = plugin.getTemplateManager().getTemplate(args[1]);
            if (opt.isEmpty()) {
                player.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "No template found with ID '" + args[1] + "'.");
                return;
            }
            template = opt.get();
        } else {
            TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
            if (active != null) {
                template = active.getTemplate();
            } else {
                // Default to list if no active and no ID given
                TournamentListGUI gui = new TournamentListGUI(plugin, player, guiListener);
                guiListener.register(player, gui);
                gui.open(player);
                return;
            }
        }

        TournamentInfoGUI gui = new TournamentInfoGUI(plugin, player, guiListener, template);
        guiListener.register(player, gui);
        gui.open(player);
    }

    private void handleTop(Player player, String[] args) {
        TournamentTemplate template;

        if (args.length >= 2) {
            Optional<TournamentTemplate> opt = plugin.getTemplateManager().getTemplate(args[1]);
            if (opt.isEmpty()) {
                player.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "No template found with ID '" + args[1] + "'.");
                return;
            }
            template = opt.get();
        } else {
            TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
            if (active != null) {
                template = active.getTemplate();
            } else {
                player.sendMessage(plugin.getChatPrefix() + ChatColor.YELLOW + "No active tournament. Specify a template ID.");
                return;
            }
        }

        TournamentLeaderboardGUI gui = new TournamentLeaderboardGUI(plugin, player, guiListener, template);
        guiListener.register(player, gui);
        gui.open(player);
    }

    private void handleHistory(Player player) {
        PastTournamentsGUI gui = new PastTournamentsGUI(plugin, player, guiListener);
        gui.openAsync();
    }

    private void handleStats(Player player, String[] args) {
        UUID targetUuid;
        String targetName;

        if (args.length >= 2) {
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            targetUuid = op.getUniqueId();
            targetName = args[1];
        } else {
            targetUuid = player.getUniqueId();
            targetName = player.getName();
        }

        final String finalName = targetName;
        TournamentRepository repo = plugin.getTournamentRepository();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Object> stats = repo.getPlayerStats(targetUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (stats == null) {
                    player.sendMessage(plugin.getChatPrefix() + ChatColor.YELLOW + "No tournament stats found for " + finalName + ".");
                    return;
                }
                player.sendMessage(plugin.getChatPrefix() + ChatColor.GOLD + "=== Tournament Stats: " + ChatColor.YELLOW + finalName + ChatColor.GOLD + " ===");
                player.sendMessage(ChatColor.GRAY + "Entered:    " + ChatColor.WHITE + stats.get("tournaments_entered"));
                player.sendMessage(ChatColor.GRAY + "Won:        " + ChatColor.WHITE + stats.get("tournaments_won"));
                player.sendMessage(ChatColor.GRAY + "1st Places: " + ChatColor.WHITE + stats.get("total_first_places"));
                player.sendMessage(ChatColor.GRAY + "2nd Places: " + ChatColor.WHITE + stats.get("total_second_places"));
                player.sendMessage(ChatColor.GRAY + "3rd Places: " + ChatColor.WHITE + stats.get("total_third_places"));
                double bestScore = ((Number) stats.get("best_score_ever")).doubleValue();
                player.sendMessage(ChatColor.GRAY + "Best Score: " + ChatColor.WHITE + formatScore(bestScore));
            });
        });
    }

    private void handleProfile(Player player, String[] args) {
        OfflinePlayer target;

        if (args.length >= 2) {
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            target = op;
        } else {
            target = player;
        }

        PlayerProfileGUI gui = new PlayerProfileGUI(plugin, player, guiListener, target);
        gui.openAsync();
    }

    private void handleHallOfFame(Player player) {
        HallOfFameGUI gui = new HallOfFameGUI(plugin, player, guiListener);
        gui.openAsync();
    }

    private void handleNext(Player player) {
        SchedulerManager scheduler = plugin.getSchedulerManager();
        if (scheduler == null) {
            player.sendMessage(plugin.getChatPrefix() + ChatColor.YELLOW + "Scheduler is not active.");
            return;
        }

        Optional<SchedulerManager.NextSchedule> next = scheduler.getNextScheduledStart();
        if (next.isEmpty()) {
            player.sendMessage(plugin.getChatPrefix() + ChatColor.YELLOW + "No scheduled tournaments found.");
            return;
        }

        SchedulerManager.NextSchedule ns = next.get();
        String when = DATE_FMT.format(new Date(
                ns.fireTime().atZone(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()));

        player.sendMessage(plugin.getChatPrefix() + ChatColor.GOLD + "Next Tournament: "
                + ChatColor.YELLOW + ns.template().getFormattedDisplayName()
                + ChatColor.GRAY + " at " + ChatColor.WHITE + when
                + ChatColor.GRAY + " (" + ns.template().getType().name() + ")");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("info")
                || args[0].equalsIgnoreCase("top"))) {
            return plugin.getTemplateManager().getTemplates().stream()
                    .map(TournamentTemplate::getId)
                    .filter(id -> id.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("profile"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    private String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) return String.valueOf((long) score);
        return String.format("%.2f", score);
    }
}
