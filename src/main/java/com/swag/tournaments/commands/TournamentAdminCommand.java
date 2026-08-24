package com.swag.tournaments.commands;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.gui.AdminPanelGUI;
import com.swag.tournaments.gui.GUIListener;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentTemplate;
import com.swag.tournaments.model.TournamentType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

@SuppressWarnings("deprecation")
public class TournamentAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of(
            "start", "stop", "reload", "list", "score", "webtrust"
    );

    private final SwagTournaments plugin;
    private final GUIListener guiListener;

    public TournamentAdminCommand(SwagTournaments plugin, GUIListener guiListener) {
        this.plugin = plugin;
        this.guiListener = guiListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("swagtournaments.admin")) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "You don't have permission.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Console cannot open a GUI.");
                return true;
            }
            AdminPanelGUI gui = new AdminPanelGUI(plugin, player, guiListener);
            guiListener.register(player, gui);
            gui.open(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> handleStart(sender, args);
            case "stop" -> handleStop(sender);
            case "reload" -> handleReload(sender, args);
            case "list" -> handleList(sender);
            case "score" -> handleScore(sender, args);
            case "webtrust" -> handleWebTrust(sender);
            default -> sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED
                    + "Unknown subcommand. Usage: /tadmin [start|stop|reload|list|score|webtrust]");
        }
        return true;
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Usage: /tadmin start <templateId> [durationMinutes]");
            return;
        }

        Optional<TournamentTemplate> opt = plugin.getTemplateManager().getTemplate(args[1]);
        if (opt.isEmpty()) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "No template found with ID '" + args[1] + "'.");
            return;
        }

        int duration = 30;
        if (args.length >= 3) {
            try {
                duration = Integer.parseInt(args[2]);
                if (duration <= 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Invalid duration: must be a positive integer.");
                return;
            }
        }

        TournamentTemplate template = opt.get();
        boolean started = plugin.getTournamentManager().startTournament(template, duration, "ADMIN");
        if (started) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.GREEN + "Tournament '"
                    + template.getFormattedDisplayName() + "' started for " + duration + " minutes.");
        } else {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Could not start — another tournament may be active.");
        }
    }

    private void handleStop(CommandSender sender) {
        if (!plugin.getTournamentManager().isActive()) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "No active tournament to stop.");
            return;
        }
        plugin.getTournamentManager().stopTournament(sender);
    }

    private void handleReload(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            plugin.getTemplateManager().reloadTemplate(args[1]);
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.GREEN + "Reloaded template '" + args[1] + "'.");
        } else {
            plugin.getTemplateManager().reloadAll();
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.GREEN + "Reloaded all templates: "
                    + plugin.getTemplateManager().getTemplates().size() + " loaded.");
        }

        // Rebuild scheduler cron slots from fresh templates
        if (plugin.getSchedulerManager() != null) {
            plugin.getSchedulerManager().rebuildSlots();
        }
    }

    private void handleList(CommandSender sender) {
        Collection<TournamentTemplate> templates = plugin.getTemplateManager().getTemplates();
        if (templates.isEmpty()) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.YELLOW + "No templates loaded.");
            return;
        }

        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();

        sender.sendMessage(plugin.getChatPrefix() + ChatColor.GOLD + "=== Tournament Templates (" + templates.size() + ") ===");
        for (TournamentTemplate t : templates) {
            boolean isActive = active != null && active.getTemplate().getId().equals(t.getId());
            String statusTag = isActive ? ChatColor.GREEN + "[ACTIVE]" : ChatColor.GRAY + "[idle]";
            sender.sendMessage(statusTag + ChatColor.WHITE + " " + t.getId()
                    + ChatColor.GRAY + " | " + t.getType().name()
                    + " | " + t.getScoringMode().name()
                    + ChatColor.DARK_GRAY + " — " + t.getFormattedDisplayName());
        }
    }

    private void handleScore(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Usage: /tadmin score <player> <amount>");
            return;
        }

        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
        if (active == null) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "No active tournament.");
            return;
        }
        if (active.getTemplate().getType() != TournamentType.CUSTOM) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Manual score submission is only valid for CUSTOM type tournaments.");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Player '" + args[1] + "' is not online.");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Invalid amount: " + args[2]);
            return;
        }

        plugin.getTournamentManager().submitScore(target, amount, Map.of("source", "ADMIN"));
        sender.sendMessage(plugin.getChatPrefix() + ChatColor.GREEN + "Submitted score " + amount + " for " + target.getName() + ".");
    }

    private void handleWebTrust(CommandSender sender) {
        // The web editor no longer mints its own trust tokens — SwagAPI's shared session
        // login gates the mount point before any request reaches SwagTournaments. This
        // just surfaces the panel URL; the admin authenticates via SwagAPI's /login page.
        String url = plugin.getWebServerManager() != null ? plugin.getWebServerManager().getUrl() : null;
        if (url == null) {
            sender.sendMessage(plugin.getChatPrefix() + ChatColor.RED + "Web editor is not currently available. "
                    + "Check that SwagAPI is installed and web-editor.enabled is true.");
            return;
        }

        sender.sendMessage(plugin.getChatPrefix() + ChatColor.GREEN + "Web editor: " + ChatColor.YELLOW + url);
        sender.sendMessage(ChatColor.GRAY + "Log in with your SwagAPI panel account to access it.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("swagtournaments.admin")) return List.of();

        if (args.length == 1) {
            return SUBS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("start") || sub.equals("reload")) {
                return plugin.getTemplateManager().getTemplates().stream()
                        .map(TournamentTemplate::getId)
                        .filter(id -> id.startsWith(args[1].toLowerCase()))
                        .toList();
            }
            if (sub.equals("score")) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            return List.of("30", "60", "90", "120");
        }

        return List.of();
    }
}
