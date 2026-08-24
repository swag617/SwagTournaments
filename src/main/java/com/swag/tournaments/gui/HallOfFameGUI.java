package com.swag.tournaments.gui;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.database.TournamentRepository;
import com.swag.tournaments.model.TournamentTemplate;
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

/**
 * Feature 2 (Hall of Fame): a permanent record of the best-ever winning score for each
 * tournament TEMPLATE — not type, not one flat global ranking, since different templates use
 * different {@link com.swag.tournaments.model.ScoringMode}s and their raw scores aren't
 * comparable across templates. Only a handful of templates exist (6 bundled + any custom
 * ones added via the web editor), so unlike {@link PastTournamentsGUI} this doesn't need
 * DB-paginated async loading — a single small async fetch on open is enough, following this
 * codebase's "DB reads happen off the main thread" convention regardless of table size.
 * Read-only for v1, mirroring {@link PlayerProfileGUI}: the only interactive element is Close.
 */
@SuppressWarnings("deprecation")
public class HallOfFameGUI extends GUIBase {

    // 2 rows x 7 columns (slots 10-16, 19-25) — 14 slots, comfortably ahead of the 6
    // currently-bundled templates while still being a simple flat (non-paginated) grid.
    private static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("MM/dd/yy HH:mm");

    private final SwagTournaments plugin;
    private final Player player;
    private final GUIListener guiListener;

    // Loaded async then set on main thread before opening
    private List<TournamentTemplate> templates;
    private Map<String, Map<String, Object>> records;

    public HallOfFameGUI(SwagTournaments plugin, Player player, GUIListener guiListener) {
        this.plugin = plugin;
        this.player = player;
        this.guiListener = guiListener;
    }

    /**
     * Loads all template records async then opens the GUI on the main thread.
     * Call this instead of open() directly.
     */
    public void openAsync() {
        TournamentRepository repo = plugin.getTournamentRepository();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<String, Map<String, Object>> data = repo.getAllTemplateRecords();
            Bukkit.getScheduler().runTask(plugin, () -> {
                this.records = data;
                this.templates = new ArrayList<>(plugin.getTemplateManager().getTemplates());
                guiListener.register(player, this);
                open(player);
            });
        });
    }

    @Override
    public void buildGUI() {
        inventory = Bukkit.createInventory(null, 54,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Hall of Fame");

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        if (templates == null) templates = new ArrayList<>();
        if (records == null) records = Map.of();

        for (int i = 0; i < CONTENT_SLOTS.length && i < templates.size(); i++) {
            TournamentTemplate template = templates.get(i);
            setItem(CONTENT_SLOTS[i], buildRecordItem(template));
        }

        if (templates.isEmpty()) {
            setItem(22, createItem(Material.PAPER, "&7No templates found"));
        }

        setItem(49, createItem(Material.BARRIER, "&cClose"));
    }

    private ItemStack buildRecordItem(TournamentTemplate template) {
        Map<String, Object> record = records.get(template.getId());

        List<String> lore = new ArrayList<>();
        lore.add("&8" + template.getType().name() + " • " + template.getScoringMode().name());
        lore.add("");

        if (record == null) {
            lore.add("&7No record yet");
            lore.add("&8Be the first to win this tournament!");
        } else {
            String holderName = resolvePlayerName(record);
            double score = ((Number) record.getOrDefault("score", 0.0)).doubleValue();
            long achievedAt = ((Number) record.getOrDefault("achieved_at", 0L)).longValue();

            lore.add("&6Record Holder: &e" + holderName);
            lore.add("&6Score: &e" + formatScore(score));
            lore.add("&7Achieved: &f" + DATE_FMT.format(new Date(achievedAt)));
        }

        ItemStack item = new ItemStack(record == null ? Material.GRAY_DYE : template.getIconMaterial());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                    "&6" + template.getDisplayName()));
            meta.setLore(lore.stream()
                    .map(l -> ChatColor.translateAlternateColorCodes('&', l))
                    .toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String resolvePlayerName(Map<String, Object> record) {
        String stored = (String) record.get("player_name");
        if (stored != null && !stored.isEmpty()) return stored;
        String uuidStr = (String) record.get("player_uuid");
        if (uuidStr == null) return "Unknown";
        try {
            return Bukkit.getOfflinePlayer(java.util.UUID.fromString(uuidStr)).getName();
        } catch (Exception e) {
            return uuidStr.substring(0, Math.min(8, uuidStr.length())) + "...";
        }
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
