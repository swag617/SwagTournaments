package com.swag.tournaments.gui;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentTemplate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("deprecation")
public class TournamentListGUI extends GUIBase {

    private static final int[] CONTENT_SLOTS = {10, 11, 12, 13, 14, 15, 16};
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("EEE HH:mm");

    private final SwagTournaments plugin;
    private final Player player;
    private final GUIListener guiListener;
    private int page;
    private List<TournamentTemplate> templates;

    public TournamentListGUI(SwagTournaments plugin, Player player, GUIListener guiListener) {
        this(plugin, player, guiListener, 0);
    }

    public TournamentListGUI(SwagTournaments plugin, Player player, GUIListener guiListener, int page) {
        this.plugin = plugin;
        this.player = player;
        this.guiListener = guiListener;
        this.page = page;
    }

    @Override
    public void buildGUI() {
        inventory = Bukkit.createInventory(null, 54,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Tournaments");

        Collection<TournamentTemplate> all = plugin.getTemplateManager().getTemplates();
        templates = new ArrayList<>(all);

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        int totalPages = Math.max(1, (int) Math.ceil(templates.size() / (double) CONTENT_SLOTS.length));
        int start = page * CONTENT_SLOTS.length;

        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= templates.size()) break;
            TournamentTemplate template = templates.get(idx);
            setItem(CONTENT_SLOTS[i], buildTemplateItem(template));
        }

        // Navigation row
        if (page > 0) {
            setItem(45, createItem(Material.ARROW, "&ePrevious Page", "&7Page " + page + " of " + totalPages));
        }

        setItem(49, createItem(Material.BARRIER, "&cClose"));

        if ((page + 1) < totalPages) {
            setItem(53, createItem(Material.ARROW, "&eNext Page", "&7Page " + (page + 2) + " of " + totalPages));
        }

        // Status item in slot 4
        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
        if (active != null && active.getTemplate() != null) {
            long rem = active.getTimeRemainingSeconds();
            setItem(4, createItem(Material.NETHER_STAR,
                    "&6Active: &e" + active.getTemplate().getDisplayName(),
                    "&7Type: &f" + active.getTemplate().getType().name(),
                    "&7Participants: &f" + active.getParticipantCount(),
                    "&7Time Left: &f" + formatTime(rem)));
        } else {
            setItem(4, createItem(Material.COMPASS, "&7No Active Tournament",
                    "&8Browse templates below"));
        }
    }

    private ItemStack buildTemplateItem(TournamentTemplate template) {
        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
        boolean isActive = active != null
                && active.getTemplate().getId().equals(template.getId());

        String statusBadge = isActive ? "&a[ACTIVE]" : "&7[IDLE]";

        List<String> lore = new ArrayList<>();
        lore.add("&8" + template.getType().name() + " • " + template.getScoringMode().name());
        lore.add(statusBadge);
        if (!template.getDescription().isEmpty()) {
            lore.add("&7" + template.getDescription());
        }
        if (isActive) {
            long rem = active.getTimeRemainingSeconds();
            lore.add("&eTime Left: &f" + formatTime(rem));
            lore.add("&ePlayers: &f" + active.getParticipantCount());
        } else {
            // Show next schedule if any cron slots exist
            template.getCronSlots().stream().findFirst().ifPresent(slot -> {
                String day = slot.getDay() == null ? "Daily" : slot.getDay().name();
                lore.add("&eNext: &f" + day + " " + slot.getTime());
            });
        }
        lore.add("");
        lore.add("&eClick to view details");

        ItemStack item = new ItemStack(template.getIconMaterial());
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

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        // Navigation
        if (slot == 45 && page > 0) {
            page--;
            TournamentListGUI newGui = new TournamentListGUI(plugin, player, guiListener, page);
            guiListener.register(player, newGui);
            newGui.open(player);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(
                plugin.getTemplateManager().getTemplates().size() / (double) CONTENT_SLOTS.length));
        if (slot == 53 && (page + 1) < totalPages) {
            page++;
            TournamentListGUI newGui = new TournamentListGUI(plugin, player, guiListener, page);
            guiListener.register(player, newGui);
            newGui.open(player);
            return;
        }

        // Content slots
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            if (slot == CONTENT_SLOTS[i]) {
                int idx = page * CONTENT_SLOTS.length + i;
                if (idx < templates.size()) {
                    TournamentTemplate template = templates.get(idx);
                    TournamentInfoGUI infoGui = new TournamentInfoGUI(plugin, player, guiListener, template);
                    guiListener.register(player, infoGui);
                    infoGui.open(player);
                }
                return;
            }
        }
    }

    private String formatTime(long seconds) {
        if (seconds >= 3600) return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        if (seconds >= 60) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return seconds + "s";
    }
}
