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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@SuppressWarnings("deprecation")
public class AdminPanelGUI extends GUIBase {

    private static final int[] TEMPLATE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34
    };

    private final SwagTournaments plugin;
    private final Player player;
    private final GUIListener guiListener;
    private List<TournamentTemplate> templates;

    public AdminPanelGUI(SwagTournaments plugin, Player player, GUIListener guiListener) {
        this.plugin = plugin;
        this.player = player;
        this.guiListener = guiListener;
    }

    @Override
    public void buildGUI() {
        inventory = Bukkit.createInventory(null, 54,
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "Admin Panel");

        fillBorder(Material.RED_STAINED_GLASS_PANE);

        Collection<TournamentTemplate> all = plugin.getTemplateManager().getTemplates();
        templates = new ArrayList<>(all);

        // Status item
        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
        if (active != null) {
            long rem = active.getTimeRemainingSeconds();
            setItem(4, createItem(Material.NETHER_STAR,
                    "&6Active: &e" + active.getTemplate().getDisplayName(),
                    "&7Type: &f" + active.getTemplate().getType().name(),
                    "&7Players: &f" + active.getParticipantCount(),
                    "&7Time Left: &f" + formatTime(rem)));
        } else {
            setItem(4, createItem(Material.COMPARATOR, "&7No Active Tournament",
                    "&8All systems idle"));
        }

        // Template slots
        for (int i = 0; i < TEMPLATE_SLOTS.length && i < templates.size(); i++) {
            setItem(TEMPLATE_SLOTS[i], buildTemplateItem(templates.get(i)));
        }

        // Stop button (slot 50) — only lit if active
        if (active != null) {
            setItem(50, createItem(Material.RED_CONCRETE, "&cStop Tournament",
                    "&7Stop the currently active tournament"));
        } else {
            ItemStack dimmed = createItem(Material.RED_STAINED_GLASS_PANE, "&8Stop Tournament",
                    "&8No active tournament to stop");
            setItem(50, dimmed);
        }

        // Reload button (slot 52)
        setItem(52, createItem(Material.COMMAND_BLOCK, "&eReload Templates",
                "&7Reloads all tournament YAML files"));

        // Close button (slot 49)
        setItem(49, createItem(Material.BARRIER, "&cClose"));
    }

    private ItemStack buildTemplateItem(TournamentTemplate template) {
        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
        boolean isActive = active != null
                && active.getTemplate().getId().equals(template.getId());

        List<String> lore = new ArrayList<>();
        lore.add("&8" + template.getType().name() + " • " + template.getScoringMode().name());
        if (isActive) {
            lore.add("&aStatus: &2ACTIVE");
            lore.add("&7Click to view info");
        } else {
            lore.add("&7Status: &8IDLE");
            lore.add("&eLeft-click: &fStart (30m)");
            lore.add("&eRight-click: &fView Info");
        }

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

        if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (slot == 50) {
            if (plugin.getTournamentManager().isActive()) {
                plugin.getTournamentManager().stopTournament(player);
                player.closeInventory();
            } else {
                player.sendMessage(ChatColor.RED + "No active tournament to stop.");
            }
            return;
        }

        if (slot == 52) {
            plugin.getTemplateManager().reloadAll();
            player.sendMessage(ChatColor.GREEN + "Templates reloaded: "
                    + plugin.getTemplateManager().getTemplates().size() + " loaded.");
            // Rebuild GUI to reflect reloaded templates
            AdminPanelGUI newGui = new AdminPanelGUI(plugin, player, guiListener);
            guiListener.register(player, newGui);
            newGui.open(player);
            return;
        }

        // Template slots
        for (int i = 0; i < TEMPLATE_SLOTS.length; i++) {
            if (slot == TEMPLATE_SLOTS[i] && i < templates.size()) {
                TournamentTemplate template = templates.get(i);

                if (event.isRightClick()) {
                    TournamentInfoGUI infoGui = new TournamentInfoGUI(plugin, player, guiListener, template);
                    guiListener.register(player, infoGui);
                    infoGui.open(player);
                } else {
                    // Left-click: start immediately for 30 minutes
                    boolean started = plugin.getTournamentManager()
                            .startTournament(template, 30, "ADMIN");
                    if (started) {
                        player.sendMessage(ChatColor.GREEN + "Tournament '"
                                + template.getDisplayName() + "' started for 30 minutes.");
                        player.closeInventory();
                    } else {
                        player.sendMessage(ChatColor.RED
                                + "Could not start — another tournament may be active.");
                    }
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
