package com.swag.tournaments.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GUIListener implements Listener {

    private final Map<UUID, GUIBase> activeGUIs = new HashMap<>();

    public void register(Player player, GUIBase gui) {
        activeGUIs.put(player.getUniqueId(), gui);
    }

    public void unregister(Player player) {
        activeGUIs.remove(player.getUniqueId());
    }

    public GUIBase getActive(Player player) {
        return activeGUIs.get(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        GUIBase gui = activeGUIs.get(player.getUniqueId());
        if (gui == null) return;
        // Verify they're clicking the right inventory (top inventory = the GUI itself)
        if (!event.getInventory().equals(gui.inventory)) return;

        event.setCancelled(true);
        gui.handleClick(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        GUIBase gui = activeGUIs.get(player.getUniqueId());
        if (gui == null) return;
        // Only unregister if the closed inventory is our GUI's inventory
        if (event.getInventory().equals(gui.inventory)) {
            activeGUIs.remove(player.getUniqueId());
            if (gui instanceof TournamentLeaderboardGUI leaderboard) {
                leaderboard.cancelRefresh();
            }
        }
    }
}
