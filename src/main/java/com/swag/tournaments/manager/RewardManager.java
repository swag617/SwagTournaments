package com.swag.tournaments.manager;

import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.database.TournamentRepository;
import com.swag.tournaments.model.RewardTier;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentParticipant;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitScheduler;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

@SuppressWarnings("deprecation")
public class RewardManager {

    private final SwagTournaments plugin;
    private final Logger log;
    private Economy economy;

    public RewardManager(SwagTournaments plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public void initialize() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            economy = rsp.getProvider();
            log.info("Vault Economy hooked for tournament rewards.");
        } else {
            log.info("Vault Economy not found — money rewards will be skipped.");
        }
    }

    /**
     * Distributes rewards for the top-N placements plus the participation tier (place 0).
     * Must be called on the main thread (gives items, runs commands, sends messages).
     *
     * @param instance     the ended tournament
     * @param ranked       leaderboard sorted highest-first
     * @param repository   for marking rewards given (async call happens internally)
     */
    public void distribute(TournamentInstance instance, List<TournamentParticipant> ranked,
                           TournamentRepository repository) {
        Map<Integer, RewardTier> rewards = instance.getTemplate().getRewards();

        for (int i = 0; i < ranked.size(); i++) {
            TournamentParticipant p = ranked.get(i);
            int place = i + 1;

            RewardTier tier = rewards.get(place);
            if (tier != null) {
                giveReward(p, tier, place, instance.getInstanceId(), repository);
            }

            // Participation tier (place = 0) for everyone
            RewardTier participation = rewards.get(0);
            if (participation != null) {
                giveReward(p, participation, 0, instance.getInstanceId(), repository);
            }
        }
    }

    private void giveReward(TournamentParticipant p, RewardTier tier, int place,
                            long instanceId, TournamentRepository repository) {
        Player player = Bukkit.getPlayer(p.getPlayerUuid());
        String playerName = p.getPlayerName();

        if (economy != null && tier.getMoney() > 0) {
            if (player != null) {
                economy.depositPlayer(player, tier.getMoney());
                player.sendMessage(plugin.getChatPrefix() + ChatColor.GREEN + "You received " + ChatColor.YELLOW
                        + "$" + String.format("%.2f", tier.getMoney())
                        + ChatColor.GREEN + " for "
                        + (place == 0 ? "participating" : "placing #" + place) + "!");
            } else {
                // Player offline — deposit by name (Vault supports this)
                economy.depositPlayer(Bukkit.getOfflinePlayer(p.getPlayerUuid()), tier.getMoney());
            }
        }

        for (String cmd : tier.getCommands()) {
            String resolved = cmd.replace("{player}", playerName)
                    .replace("{place}", String.valueOf(place));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }

        if (player != null) {
            for (ItemStack item : tier.getItems()) {
                // addItem returns Map<Integer, ItemStack>: slot-index → leftover stack
                player.getInventory().addItem(item.clone()).values().forEach(leftover ->
                        player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            }
        }

        // Mark reward given async so we don't block the main thread on DB
        UUID uuid = p.getPlayerUuid();
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTaskAsynchronously(plugin, () ->
                repository.markRewardGiven(instanceId, uuid));
    }
}
