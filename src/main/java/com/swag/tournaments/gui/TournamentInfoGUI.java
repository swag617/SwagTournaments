package com.swag.tournaments.gui;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.RewardTier;
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
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class TournamentInfoGUI extends GUIBase {

    private final SwagTournaments plugin;
    private final Player player;
    private final GUIListener guiListener;
    private final TournamentTemplate template;

    public TournamentInfoGUI(SwagTournaments plugin, Player player,
                             GUIListener guiListener, TournamentTemplate template) {
        this.plugin = plugin;
        this.player = player;
        this.guiListener = guiListener;
        this.template = template;
    }

    @Override
    public void buildGUI() {
        inventory = Bukkit.createInventory(null, 54,
                ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "Tournament Info");

        fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
        boolean isActive = active != null
                && active.getTemplate().getId().equals(template.getId());

        // Center info item at slot 22
        setItem(22, buildInfoItem(isActive, active));

        // Rewards summary at slot 20
        setItem(20, buildRewardsItem());

        // Schedule summary at slot 24
        setItem(24, buildScheduleItem());

        // Back button at slot 45
        setItem(45, createItem(Material.ARROW, "&eBack to List"));

        // Leaderboard button at slot 49
        setItem(49, createItem(Material.BOOK, "&eView Leaderboard",
                isActive ? "&7See live standings" : "&7Not currently active"));

        // Admin buttons
        if (player.hasPermission("swagtournaments.admin")) {
            if (isActive) {
                setItem(53, createItem(Material.RED_CONCRETE, "&cStop Tournament",
                        "&7Stop the active tournament"));
            } else {
                setItem(53, createItem(Material.LIME_CONCRETE, "&aStart Now",
                        "&7Start this tournament immediately",
                        "&8(30 minute default duration)"));
            }
        }
    }

    private ItemStack buildInfoItem(boolean isActive, TournamentInstance active) {
        List<String> lore = new ArrayList<>();
        lore.add("&8" + template.getType().name() + " • " + template.getScoringMode().name());
        lore.add("");
        if (!template.getDescription().isEmpty()) {
            lore.add("&7" + template.getDescription());
            lore.add("");
        }
        lore.add("&7Scoring: &f" + template.getScoreFormula());
        if (template.getTargetScore() > 0) {
            lore.add("&7Target Score: &f" + (int) template.getTargetScore());
        }
        lore.add("");

        if (isActive && active != null) {
            long rem = active.getTimeRemainingSeconds();
            lore.add("&aStatus: &2ACTIVE");
            lore.add("&eTime Remaining: &f" + formatTime(rem));
            lore.add("&ePlayers: &f" + active.getParticipantCount());

            List<com.swag.tournaments.model.TournamentParticipant> leaders = active.getLeaderboard();
            if (!leaders.isEmpty()) {
                lore.add("&eLeader: &f" + leaders.get(0).getPlayerName()
                        + " &7(" + formatScore(leaders.get(0).getScore()) + ")");
            }
        } else {
            lore.add("&7Status: &8IDLE");
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

    private ItemStack buildRewardsItem() {
        List<String> lore = new ArrayList<>();
        Map<Integer, RewardTier> rewards = template.getRewards();
        for (int place = 1; place <= 3; place++) {
            RewardTier tier = rewards.get(place);
            if (tier != null) {
                String label = switch (place) {
                    case 1 -> "&6#1";
                    case 2 -> "&7#2";
                    case 3 -> "&e#3";
                    default -> "&f#" + place;
                };
                lore.add(label + " &f$" + String.format("%.0f", tier.getMoney()));
                if (!tier.getCommands().isEmpty()) {
                    lore.add("  &8+" + tier.getCommands().size() + " command(s)");
                }
                if (!tier.getItems().isEmpty()) {
                    lore.add("  &8+" + tier.getItems().size() + " item(s)");
                }
            }
        }
        RewardTier participation = rewards.get(0);
        if (participation != null) {
            lore.add("&aParticipation reward included");
        }
        if (lore.isEmpty()) {
            lore.add("&8No rewards configured");
        }
        return createItem(Material.CHEST, "&eRewards", lore.toArray(new String[0]));
    }

    private ItemStack buildScheduleItem() {
        List<String> lore = new ArrayList<>();
        if (template.getCronSlots().isEmpty()) {
            lore.add("&8No scheduled slots");
        } else {
            for (var slot : template.getCronSlots()) {
                String day = slot.getDay() == null ? "Daily" : slot.getDay().name();
                lore.add("&7" + day + " at &f" + slot.getTime()
                        + " &8(" + slot.getDurationMinutes() + "m)");
            }
        }
        if (template.isInRotation()) {
            lore.add("&aIn auto-rotation &8(weight: " + template.getRotationWeight() + ")");
        }
        return createItem(Material.CLOCK, "&eSchedule", lore.toArray(new String[0]));
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();

        if (slot == 45) {
            TournamentListGUI listGui = new TournamentListGUI(plugin, player, guiListener);
            guiListener.register(player, listGui);
            listGui.open(player);
            return;
        }

        if (slot == 49) {
            TournamentLeaderboardGUI lbGui = new TournamentLeaderboardGUI(
                    plugin, player, guiListener, template);
            guiListener.register(player, lbGui);
            lbGui.open(player);
            return;
        }

        if (slot == 53 && player.hasPermission("swagtournaments.admin")) {
            TournamentInstance active = plugin.getTournamentManager().getCurrentInstance();
            boolean isActive = active != null
                    && active.getTemplate().getId().equals(template.getId());

            if (isActive) {
                plugin.getTournamentManager().stopTournament(player);
                player.closeInventory();
            } else {
                boolean started = plugin.getTournamentManager()
                        .startTournament(template, 30, "ADMIN");
                if (started) {
                    player.sendMessage(ChatColor.GREEN + "Tournament started!");
                    player.closeInventory();
                } else {
                    player.sendMessage(ChatColor.RED + "Could not start tournament — another may be active.");
                }
            }
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
