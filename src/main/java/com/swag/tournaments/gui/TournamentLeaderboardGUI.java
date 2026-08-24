package com.swag.tournaments.gui;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentParticipant;
import com.swag.tournaments.model.TournamentTemplate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@SuppressWarnings("deprecation")
public class TournamentLeaderboardGUI extends GUIBase {

    private final SwagTournaments plugin;
    private final Player player;
    private final GUIListener guiListener;
    private final TournamentTemplate template;
    private BukkitTask refreshTask;

    public TournamentLeaderboardGUI(SwagTournaments plugin, Player player,
                                    GUIListener guiListener, TournamentTemplate template) {
        this.plugin = plugin;
        this.player = player;
        this.guiListener = guiListener;
        this.template = template;
    }

    @Override
    public void buildGUI() {
        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
        boolean isActive = active != null
                && active.getTemplate().getId().equals(template.getId());

        String title = ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Leaderboard";
        if (isActive) {
            long rem = active.getTimeRemainingSeconds();
            title = ChatColor.GOLD + "" + ChatColor.BOLD + template.getFormattedDisplayName()
                    + ChatColor.YELLOW + " — " + formatTime(rem);
        }
        inventory = Bukkit.createInventory(null, 54, title);

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        populateLeaderboard(active);

        setItem(45, createItem(Material.ARROW, "&eBack"));
        setItem(49, createItem(Material.BARRIER, "&cClose"));

        if (isActive && refreshTask == null) {
            refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 100L, 100L);
        }
    }

    private void populateLeaderboard(TournamentInstance active) {
        // Clear content slots 9–43
        for (int i = 9; i <= 43; i++) {
            inventory.setItem(i, null);
        }

        List<TournamentParticipant> ranked;
        if (active != null && active.getTemplate().getId().equals(template.getId())) {
            ranked = active.getLeaderboard();
        } else {
            ranked = List.of();
        }

        Material[] medalMaterials = {
                Material.GOLD_INGOT,
                Material.IRON_INGOT,
                Material.COPPER_INGOT
        };

        int slot = 10;
        for (int i = 0; i < ranked.size() && slot <= 43; i++) {
            TournamentParticipant p = ranked.get(i);
            int rank = i + 1;

            ItemStack item;
            if (rank <= 3) {
                // Medal item for top 3
                item = createItem(medalMaterials[rank - 1],
                        "&6#" + rank + " &e" + p.getPlayerName(),
                        "&7Score: &f" + formatScore(p.getScore()),
                        "&7Rank: &f#" + rank);
            } else {
                // Player head for others
                item = buildSkull(p.getPlayerUuid(), p.getPlayerName(), rank, p.getScore());
            }

            setItem(slot, item);
            slot++;
            // Skip border columns (multiples of 9)
            if ((slot + 1) % 9 == 0) slot += 2;
        }

        if (ranked.isEmpty()) {
            setItem(22, createItem(Material.PAPER, "&7No participants yet",
                    "&8Be the first to score!"));
        }
    }

    private ItemStack buildSkull(UUID uuid, String name, int rank, double score) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            meta.setOwningPlayer(op);
            meta.setDisplayName(ChatColor.YELLOW + "#" + rank + " " + ChatColor.WHITE + name);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Score: " + ChatColor.WHITE + formatScore(score));
            lore.add(ChatColor.GRAY + "Rank: " + ChatColor.WHITE + "#" + rank);
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private void refresh() {
        if (player.getOpenInventory().getTopInventory() != inventory) {
            cancelRefresh();
            return;
        }
        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
        populateLeaderboard(active);

        // Update title via rebuilding isn't possible without reopening — just update status slot
        if (active != null && active.getTemplate().getId().equals(template.getId())) {
            long rem = active.getTimeRemainingSeconds();
            setItem(4, createItem(Material.CLOCK,
                    "&e" + template.getDisplayName(),
                    "&7Time Left: &f" + formatTime(rem),
                    "&7Players: &f" + active.getParticipantCount()));
        } else {
            cancelRefresh();
        }
    }

    public void cancelRefresh() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == 45) {
            cancelRefresh();
            TournamentInfoGUI infoGui = new TournamentInfoGUI(plugin, player, guiListener, template);
            guiListener.register(player, infoGui);
            infoGui.open(player);
            return;
        }

        if (slot == 49) {
            cancelRefresh();
            player.closeInventory();
        }
    }

    private String formatTime(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }

    private String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) return String.valueOf((long) score);
        return String.format("%.2f", score);
    }
}
