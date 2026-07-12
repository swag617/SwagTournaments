package com.swag.tournaments.gui;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.database.TournamentRepository;
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

    private final SwagTournaments plugin;
    private final Player player;
    private final GUIListener guiListener;
    private int page;

    // Loaded async then set on main thread before opening
    private List<Map<String, Object>> rows = new ArrayList<>();
    private int totalRows = 0;

    public PastTournamentsGUI(SwagTournaments plugin, Player player, GUIListener guiListener) {
        this(plugin, player, guiListener, 0);
    }

    public PastTournamentsGUI(SwagTournaments plugin, Player player, GUIListener guiListener, int page) {
        this.plugin = plugin;
        this.player = player;
        this.guiListener = guiListener;
        this.page = page;
    }

    /**
     * Loads data async then opens the GUI on the main thread.
     * Call this instead of open() directly.
     */
    public void openAsync() {
        TournamentRepository repo = plugin.getTournamentRepository();
        int currentPage = page;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Map<String, Object>> data = repo.getHistory(currentPage, PAGE_SIZE);
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

        // Content slots: rows 1-4, columns 1-7 (slots 10-16, 19-25, 28-34, 37-43)
        int[] contentSlots = buildContentSlots();

        for (int i = 0; i < contentSlots.length && i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            setItem(contentSlots[i], buildHistoryItem(row));
        }

        if (rows.isEmpty()) {
            setItem(22, createItem(Material.PAPER, "&7No past tournaments", "&8None recorded yet"));
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
            page--;
            PastTournamentsGUI newGui = new PastTournamentsGUI(plugin, player, guiListener, page);
            newGui.openAsync();
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot == 53 && rows.size() == PAGE_SIZE) {
            page++;
            PastTournamentsGUI newGui = new PastTournamentsGUI(plugin, player, guiListener, page);
            newGui.openAsync();
        }
    }

    private String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) return String.valueOf((long) score);
        return String.format("%.2f", score);
    }
}
