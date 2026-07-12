package com.swag.tournaments.integration;

import com.swag.discordutils.DiscordUtils;
import com.swag.discordutils.discord.DiscordBot;
import com.swag.discordutils.lib.jda.api.entities.MessageEmbed;
import com.swag.discordutils.lib.jda.api.entities.channel.concrete.TextChannel;
import com.swag.discordutils.util.CleanEmbedBuilder;
import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentParticipant;
import com.swag.tournaments.model.TournamentTemplate;
import org.bukkit.Bukkit;

import java.awt.Color;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sends tournament lifecycle announcements via DiscordUtils / JDA.
 * All methods are safe no-ops if initialization failed.
 */
public class DiscordIntegration {

    private static final Map<String, Color> TYPE_COLORS = Map.of(
            "FISHING", new Color(0x33, 0x99, 0xFF),
            "FARMING", new Color(0x44, 0xBB, 0x44),
            "COMBAT",  new Color(0xFF, 0x44, 0x44),
            "MINING",  new Color(0xAA, 0xAA, 0xAA),
            "ECONOMY", new Color(0xFF, 0xDD, 0x00),
            "CUSTOM",  new Color(0xAA, 0x44, 0xAA)
    );

    private final SwagTournaments plugin;
    private DiscordBot discordBot;
    private boolean available;

    public DiscordIntegration(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        try {
            DiscordUtils discordUtils = (DiscordUtils) Bukkit.getPluginManager().getPlugin("DiscordUtils");
            if (discordUtils == null) {
                return;
            }
            discordBot = discordUtils.getDiscordBot();
            available = discordBot != null && discordBot.isReady();
        } catch (Throwable t) {
            plugin.getLogger().warning("DiscordIntegration: failed to hook DiscordUtils: " + t.getMessage());
            available = false;
        }
    }

    public void sendTournamentStart(TournamentInstance instance) {
        if (!available || discordBot == null) return;
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) return;

        TournamentTemplate template = instance.getTemplate();
        Color color = typeColor(template);

        CleanEmbedBuilder builder = new CleanEmbedBuilder()
                .setTitle("Tournament Started: " + stripColor(template.getDisplayName()))
                .setDescription(template.getDescription().isEmpty()
                        ? "A new tournament is underway!"
                        : template.getDescription())
                .setColor(color)
                .addField("Type", template.getType().name(), true)
                .addField("Mode", template.getScoringMode().name(), true)
                .addField("Duration", instance.getTimeRemainingSeconds() / 60 + " minutes", true)
                .setTimestamp(Instant.now());

        sendToConfiguredChannel(template, builder.build());
    }

    public void sendTournamentEnd(TournamentInstance instance) {
        if (!available || discordBot == null) return;
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) return;

        TournamentTemplate template = instance.getTemplate();
        List<TournamentParticipant> ranked = instance.getLeaderboard();
        Color color = typeColor(template);

        CleanEmbedBuilder builder = new CleanEmbedBuilder()
                .setTitle("Tournament Ended: " + stripColor(template.getDisplayName()))
                .setColor(color)
                .setTimestamp(Instant.now());

        if (ranked.isEmpty()) {
            builder.setDescription("No participants this time.");
        } else {
            StringBuilder desc = new StringBuilder();
            String[] medals = {"🥇", "🥈", "🥉"};
            int show = Math.min(3, ranked.size());
            for (int i = 0; i < show; i++) {
                TournamentParticipant p = ranked.get(i);
                desc.append(medals[i]).append(" **").append(p.getPlayerName()).append("** — ")
                        .append(formatScore(p.getScore())).append("\n");
            }
            builder.setDescription(desc.toString());
            builder.addField("Total Participants", String.valueOf(ranked.size()), true);
        }

        sendToConfiguredChannel(template, builder.build());
    }

    public void sendLiveUpdate(TournamentInstance instance) {
        if (!available || discordBot == null) return;
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("discord.live-updates-enabled", false)) return;

        TournamentTemplate template = instance.getTemplate();
        List<TournamentParticipant> ranked = instance.getLeaderboard();
        Color color = typeColor(template);

        CleanEmbedBuilder builder = new CleanEmbedBuilder()
                .setTitle("Live Standings: " + stripColor(template.getDisplayName()))
                .setColor(color)
                .setTimestamp(Instant.now());

        int show = Math.min(5, ranked.size());
        if (show == 0) {
            builder.setDescription("No scores yet.");
        } else {
            StringBuilder desc = new StringBuilder();
            for (int i = 0; i < show; i++) {
                TournamentParticipant p = ranked.get(i);
                desc.append("#").append(i + 1).append(" **").append(p.getPlayerName())
                        .append("** — ").append(formatScore(p.getScore())).append("\n");
            }
            long rem = instance.getTimeRemainingSeconds();
            desc.append("\n*").append(rem / 60).append("m ").append(rem % 60).append("s remaining*");
            builder.setDescription(desc.toString());
        }

        sendToConfiguredChannel(template, builder.build());
    }

    private void sendToConfiguredChannel(TournamentTemplate template, MessageEmbed embed) {
        int serverIndex = plugin.getConfig().getInt("discord.announcements-server", 1);
        String channelId = plugin.getConfig().getString("discord.announcements-channel-id", "");

        if (channelId.isEmpty() || channelId.equalsIgnoreCase("YOUR_CHANNEL_ID")) return;

        try {
            TextChannel channel = discordBot.getTextChannel(serverIndex, channelId);
            if (channel == null) return;
            // Queue fires async on JDA thread pool — safe from main thread
            channel.sendMessageEmbeds(embed).queue();
        } catch (Throwable t) {
            plugin.getLogger().warning("DiscordIntegration: failed to send embed: " + t.getMessage());
        }
    }

    private Color typeColor(TournamentTemplate template) {
        String colorHex = plugin.getConfig().getString(
                "discord.colors." + template.getType().name(), "#FFFFFF");
        try {
            return Color.decode(colorHex);
        } catch (NumberFormatException e) {
            return TYPE_COLORS.getOrDefault(template.getType().name(), Color.WHITE);
        }
    }

    private String stripColor(String text) {
        return text.replaceAll("(?i)§[0-9a-fk-orx]", "").replaceAll("&[0-9a-fk-orxA-FK-ORX]", "");
    }

    private String formatScore(double score) {
        if (score == Math.floor(score) && !Double.isInfinite(score)) return String.valueOf((long) score);
        return String.format("%.2f", score);
    }
}
