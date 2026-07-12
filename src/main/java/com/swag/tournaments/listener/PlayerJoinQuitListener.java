package com.swag.tournaments.listener;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.TournamentInstance;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Re-adds the tournament BossBar for players who join while a tournament is active.
 * Also removes the BossBar reference on quit to prevent memory/BossBar leaks.
 */
public class PlayerJoinQuitListener implements Listener {

    private final SwagTournaments plugin;

    public PlayerJoinQuitListener(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        TournamentInstance inst = plugin.getTournamentManager().getCurrentInstance();
        if (inst == null) return;

        BossBar bar = inst.getBossBar();
        if (bar == null) return;

        Player player = event.getPlayer();
        // BossBar#addPlayer is idempotent — safe to call even if already added
        bar.addPlayer(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        TournamentInstance inst = plugin.getTournamentManager().getCurrentInstance();
        if (inst == null) return;

        BossBar bar = inst.getBossBar();
        if (bar == null) return;

        // Remove the player from the BossBar so it doesn't leak for the offline player
        bar.removePlayer(event.getPlayer());
    }
}
