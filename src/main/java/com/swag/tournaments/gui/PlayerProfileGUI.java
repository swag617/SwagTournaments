package com.swag.tournaments.gui;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.database.TournamentRepository;
import com.swag.tournaments.model.PlayerTypeStats;
import com.swag.tournaments.model.TournamentType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Feature 1 (Personal Profile Card): a single-screen summary of a player's all-time
 * tournament record — overall totals plus a per-{@link TournamentType} breakdown.
 * Read-only for v1: the only interactive element is the Close button.
 */
@SuppressWarnings("deprecation")
public class PlayerProfileGUI extends GUIBase {

    // 3x2 grid: FISHING/FARMING/COMBAT on row 2, MINING/ECONOMY/CUSTOM on row 3
    // (indices follow TournamentType's declared enum order).
    private static final int[] TYPE_ROW_SLOTS = {19, 21, 23, 28, 30, 32};

    private final SwagTournaments plugin;
    private final Player player;
    private final GUIListener guiListener;
    private final OfflinePlayer target;

    // Loaded async then set on main thread before opening
    private Map<String, Object> overallStats;
    private Map<TournamentType, PlayerTypeStats> typeStats;

    public PlayerProfileGUI(SwagTournaments plugin, Player player, GUIListener guiListener) {
        this(plugin, player, guiListener, player);
    }

    public PlayerProfileGUI(SwagTournaments plugin, Player player, GUIListener guiListener, OfflinePlayer target) {
        this.plugin = plugin;
        this.player = player;
        this.guiListener = guiListener;
        this.target = target;
    }

    /**
     * Loads stats async then opens the GUI on the main thread.
     * Call this instead of open() directly.
     */
    public void openAsync() {
        TournamentRepository repo = plugin.getTournamentRepository();
        UUID targetUuid = target.getUniqueId();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Object> stats = repo.getPlayerStats(targetUuid);
            Map<TournamentType, PlayerTypeStats> types = repo.getPlayerTypeStats(targetUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                this.overallStats = stats;
                this.typeStats = types;
                guiListener.register(player, this);
                open(player);
            });
        });
    }

    @Override
    public void buildGUI() {
        String targetName = target.getName() != null ? target.getName() : target.getUniqueId().toString();
        boolean self = target.getUniqueId().equals(player.getUniqueId());

        String title = ChatColor.DARK_AQUA + "" + ChatColor.BOLD
                + (self ? "Your Tournament Profile" : targetName + "'s Profile");
        inventory = Bukkit.createInventory(null, 54, title);

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        setItem(4, buildHeaderSkull(targetName));
        setItem(13, buildLifetimeRewardsItem());

        for (TournamentType type : TournamentType.values()) {
            int slot = TYPE_ROW_SLOTS[type.ordinal()];
            setItem(slot, buildTypeItem(type));
        }

        setItem(49, createItem(Material.BARRIER, "&cClose"));
    }

    private ItemStack buildHeaderSkull(String targetName) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        if (skull.getItemMeta() instanceof SkullMeta meta) {
            meta.setOwningPlayer(target);
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6" + targetName));

            List<String> lore = new ArrayList<>();
            if (overallStats == null) {
                lore.add(ChatColor.GRAY + "No tournament history yet.");
            } else {
                lore.add(ChatColor.GRAY + "Tournaments Entered: " + ChatColor.WHITE + overallStats.get("tournaments_entered"));
                lore.add(ChatColor.GRAY + "Tournaments Won: " + ChatColor.WHITE + overallStats.get("tournaments_won"));
                lore.add(ChatColor.GOLD + "1st Places: " + ChatColor.WHITE + overallStats.get("total_first_places"));
                lore.add(ChatColor.GRAY + "2nd Places: " + ChatColor.WHITE + overallStats.get("total_second_places"));
                lore.add(ChatColor.YELLOW + "3rd Places: " + ChatColor.WHITE + overallStats.get("total_third_places"));
                double bestScore = ((Number) overallStats.getOrDefault("best_score_ever", 0.0)).doubleValue();
                lore.add(ChatColor.GRAY + "Best Score Ever: " + ChatColor.WHITE + formatScore(bestScore));
            }
            meta.setLore(lore);
            skull.setItemMeta(meta);
        }
        return skull;
    }

    private ItemStack buildLifetimeRewardsItem() {
        double lifetimeMoney = overallStats == null ? 0.0
                : ((Number) overallStats.getOrDefault("lifetime_rewards_money", 0.0)).doubleValue();

        return createItem(Material.GOLD_INGOT, "&6Lifetime Rewards",
                "&7Total money earned from",
                "&7tournament placements and",
                "&7participation rewards.",
                "",
                "&e$" + String.format("%.2f", lifetimeMoney));
    }

    private ItemStack buildTypeItem(TournamentType type) {
        PlayerTypeStats stats = typeStats == null ? null : typeStats.get(type);
        if (stats == null) {
            stats = new PlayerTypeStats(type, 0, 0, 0, 0, 0, 0.0);
        }

        List<String> lore = new ArrayList<>();
        lore.add("&7Entered: &f" + stats.entered());
        lore.add("&7Won: &f" + stats.won());
        lore.add("&61st: &f" + stats.firstPlaces()
                + " &72nd: &f" + stats.secondPlaces()
                + " &e3rd: &f" + stats.thirdPlaces());
        lore.add("&7Best Score: &f" + formatScore(stats.bestScore()));

        return createItem(typeIcon(type), "&b" + prettyName(type), lore.toArray(new String[0]));
    }

    private Material typeIcon(TournamentType type) {
        return switch (type) {
            case FISHING -> Material.FISHING_ROD;
            case FARMING -> Material.WHEAT;
            case COMBAT -> Material.IRON_SWORD;
            case MINING -> Material.DIAMOND_PICKAXE;
            case ECONOMY -> Material.EMERALD;
            case CUSTOM -> Material.NETHER_STAR;
        };
    }

    private String prettyName(TournamentType type) {
        String name = type.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 49) {
            player.closeInventory();
        }
    }

    private String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) return String.valueOf((long) score);
        return String.format("%.2f", score);
    }
}
