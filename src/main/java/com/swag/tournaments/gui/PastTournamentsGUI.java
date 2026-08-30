package com.swag.tournaments.gui;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.database.TournamentRepository;
import com.swag.tournaments.model.TournamentType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class PastTournamentsGUI extends GUIBase {

    private static final int PAGE_SIZE = 28;
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MM/dd/yy HH:mm");

    // Category filter row — row 0 (slots 0-8), which fillBorder() otherwise leaves as plain
    // unused border glass. One slot per TournamentType, in declaration order. Click to filter
    // to that category, click the same one again to clear (mirrors ShopGUI's rarity-filter
    // row in SwagFarming — the established toggle-filter convention in this ecosystem).
    private static final int[] FILTER_SLOTS = {1, 2, 3, 4, 5, 6};

    private final SwagTournaments plugin;
    private final Player player;
    private final GUIListener guiListener;
    private int page;
    private final TournamentType filter;

    // Loaded async then set on main thread before opening
    private List<Map<String, Object>> rows = new ArrayList<>();
    private int totalRows = 0;

    public PastTournamentsGUI(SwagTournaments plugin, Player player, GUIListener guiListener) {
        this(plugin, player, guiListener, 0, null);
    }

    public PastTournamentsGUI(SwagTournaments plugin, Player player, GUIListener guiListener, int page) {
        this(plugin, player, guiListener, page, null);
    }

    public PastTournamentsGUI(SwagTournaments plugin, Player player, GUIListener guiListener,
                               int page, TournamentType filter) {
        this.plugin = plugin;
        this.player = player;
        this.guiListener = guiListener;
        this.page = page;
        this.filter = filter;
    }

    /**
     * Loads data async then opens the GUI on the main thread.
     * Call this instead of open() directly.
     */
    public void openAsync() {
        TournamentRepository repo = plugin.getTournamentRepository();
        int currentPage = page;
        TournamentType currentFilter = filter;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Map<String, Object>> data = repo.getHistory(currentPage, PAGE_SIZE, currentFilter);
            Bukkit.getScheduler().runTask(plugin, () -> {
                this.rows = data;
                guiListener.register(player, this);
                open(player);
            });
        });
    }

    @Override
    public void buildGUI() {
        inventory = Bukkit.createInventory(null, 54,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Past Tournaments");

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        // Category filter row (overwrites the plain border glass fillBorder() just placed
        // in row 0 at these specific slots).
        TournamentType[] types = TournamentType.values();
        for (int i = 0; i < Math.min(types.length, FILTER_SLOTS.length); i++) {
            TournamentType t = types[i];
            boolean selected = t == filter;
            setItem(FILTER_SLOTS[i], createItem(filterMaterial(t),
                    (selected ? "&a[" : "&7") + capitalize(t.name()) + (selected ? "&a]" : ""),
                    "&7Click to filter by " + capitalize(t.name()).toLowerCase()));
        }

        // Content slots: rows 1-4, columns 1-7 (slots 10-16, 19-25, 28-34, 37-43)
        int[] contentSlots = buildContentSlots();

        for (int i = 0; i < contentSlots.length && i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            setItem(contentSlots[i], buildHistoryItem(row));
        }

        if (rows.isEmpty()) {
            setItem(22, createItem(Material.PAPER, "&7No past tournaments",
                    filter != null ? "&8None recorded yet for " + capitalize(filter.name())
                                   : "&8None recorded yet"));
        }

        // Navigation
        if (page > 0) {
            setItem(45, createItem(Material.ARROW, "&ePrevious Page"));
        }
        setItem(49, createItem(Material.BARRIER, "&cClose"));
        if (rows.size() == PAGE_SIZE) {
            setItem(53, createItem(Material.ARROW, "&eNext Page"));
        }
    }

    private Material filterMaterial(TournamentType type) {
        return switch (type) {
            case FISHING -> Material.FISHING_ROD;
            case FARMING -> Material.WHEAT;
            case COMBAT -> Material.DIAMOND_SWORD;
            case MINING -> Material.DIAMOND_PICKAXE;
            case ECONOMY -> Material.GOLD_INGOT;
            case CUSTOM -> Material.NETHER_STAR;
        };
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private int[] buildContentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int row = 0; row < 4; row++) {
            for (int col = 1; col <= 7; col++) {
                slots.add(row * 9 + 9 + col);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private ItemStack buildHistoryItem(Map<String, Object> row) {
        String templateId = String.valueOf(row.getOrDefault("template_id", "?"));
        long startedAt = ((Number) row.getOrDefault("started_at", 0L)).longValue();
        String winnerUuid = (String) row.get("winner_uuid");
        double winnerScore = ((Number) row.getOrDefault("winner_score", 0.0)).doubleValue();
        int participantCount = ((Number) row.getOrDefault("participant_count", 0)).intValue();
        String status = String.valueOf(row.getOrDefault("status", "ENDED"));

        // Resolve display name from template if loaded
        String displayName = plugin.getTemplateManager()
                .getTemplate(templateId)
                .map(t -> t.getDisplayName())
                .orElse(templateId);

        Material icon = plugin.getTemplateManager()
                .getTemplate(templateId)
                .map(t -> t.getIconMaterial())
                .orElse(Material.PAPER);

        String dateStr = DATE_FMT.format(new Date(startedAt));

        List<String> lore = new ArrayList<>();
        lore.add("&7Date: &f" + dateStr);
        lore.add("&7Status: &f" + status);
        lore.add("&7Participants: &f" + participantCount);

        if (winnerUuid != null) {
            String winnerName = resolvePlayerName(winnerUuid);
            lore.add("&6Winner: &e" + winnerName + " &7(" + formatScore(winnerScore) + ")");
        } else {
            lore.add("&8No winner recorded");
        }

        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&6" + displayName));
            meta.setLore(lore.stream()
                    .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                    .toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String resolvePlayerName(String uuidStr) {
        try {
            return Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuidStr)).getName();
        } catch (Exception e) {
            return uuidStr.substring(0, Math.min(8, uuidStr.length())) + "...";
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == 45 && page > 0) {
            PastTournamentsGUI newGui = new PastTournamentsGUI(plugin, player, guiListener, page - 1, filter);
            newGui.openAsync();
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 53 && rows.size() == PAGE_SIZE) {
            PastTournamentsGUI newGui = new PastTournamentsGUI(plugin, player, guiListener, page + 1, filter);
            newGui.openAsync();
            return;
        }

        // Category filter row — mirrors ShopGUI's rarity-filter toggle exactly: clicking the
        // already-selected category clears it back to "All", clicking a different one selects
        // it. Always resets to page 0 since the previous page number may not exist under the
        // new filter.
        TournamentType[] types = TournamentType.values();
        for (int i = 0; i < Math.min(types.length, FILTER_SLOTS.length); i++) {
            if (slot != FILTER_SLOTS[i]) continue;
            TournamentType newFilter = (types[i] == filter) ? null : types[i];
            PastTournamentsGUI newGui = new PastTournamentsGUI(plugin, player, guiListener, 0, newFilter);
            newGui.openAsync();
            return;
        }
    }

    private String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) return String.valueOf((long) score);
        return String.format("%.2f", score);
    }
}
