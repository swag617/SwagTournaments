package com.swag.tournaments.integration;

import com.SwagDev.SwagAPI.api.IEventBusService;
import com.SwagDev.SwagAPI.events.SwagCrossPluginMessageEvent;
import com.swag.tournaments.SwagTournaments;
import com.swag.tournaments.model.TournamentInstance;
import com.swag.tournaments.model.TournamentParticipant;
import com.swag.tournaments.model.TournamentTemplate;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends tournament lifecycle announcements via SwagAPI's shared {@link IEventBusService},
 * publishing to the {@code discordutils:notify} channel. DiscordUtils (if installed, with
 * a matching {@code webhooks.tournaments} entry configured) picks it up and posts it — no
 * compile-time dependency on DiscordUtils' internal classes or shaded JDA classpath.
 *
 * <p>This replaces an earlier tight coupling that imported DiscordUtils' {@code DiscordBot}
 * and shaded JDA classes directly against a vendored {@code libs/DiscordUtils-*.jar}, which
 * meant any DiscordUtils update could break this plugin's compile. All methods are safe
 * no-ops if the event bus or a configured webhook isn't available.</p>
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

    public DiscordIntegration(SwagTournaments plugin) {
        this.plugin = plugin;
    }

    public void sendTournamentStart(TournamentInstance instance) {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) return;

        TournamentTemplate template = instance.getTemplate();
        List<Field> fields = new ArrayList<>();
        fields.add(new Field("Type", template.getType().name(), true));
        fields.add(new Field("Mode", template.getScoringMode().name(), true));
        fields.add(new Field("Duration", instance.getTimeRemainingSeconds() / 60 + " minutes", true));

        publish(
                "Tournament Started: " + stripColor(template.getDisplayName()),
                template.getDescription().isEmpty() ? "A new tournament is underway!" : template.getDescription(),
                typeColor(template),
                fields
        );
    }

    public void sendTournamentEnd(TournamentInstance instance) {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) return;

        TournamentTemplate template = instance.getTemplate();
        List<TournamentParticipant> ranked = instance.getLeaderboard();

        String description;
        List<Field> fields = new ArrayList<>();
        if (ranked.isEmpty()) {
            description = "No participants this time.";
        } else {
            StringBuilder desc = new StringBuilder();
            String[] medals = {"🥇", "🥈", "🥉"};
            int show = Math.min(3, ranked.size());
            for (int i = 0; i < show; i++) {
                TournamentParticipant p = ranked.get(i);
                desc.append(medals[i]).append(" **").append(p.getPlayerName()).append("** — ")
                        .append(formatScore(p.getScore())).append("\n");
            }
            description = desc.toString();
            fields.add(new Field("Total Participants", String.valueOf(ranked.size()), true));
        }

        publish("Tournament Ended: " + stripColor(template.getDisplayName()), description, typeColor(template), fields);
    }

    public void sendLiveUpdate(TournamentInstance instance) {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) return;
        if (!plugin.getConfig().getBoolean("discord.live-updates-enabled", false)) return;

        TournamentTemplate template = instance.getTemplate();
        List<TournamentParticipant> ranked = instance.getLeaderboard();

        String description;
        int show = Math.min(5, ranked.size());
        if (show == 0) {
            description = "No scores yet.";
        } else {
            StringBuilder desc = new StringBuilder();
            for (int i = 0; i < show; i++) {
                TournamentParticipant p = ranked.get(i);
                desc.append("#").append(i + 1).append(" **").append(p.getPlayerName())
                        .append("** — ").append(formatScore(p.getScore())).append("\n");
            }
            long rem = instance.getTimeRemainingSeconds();
            desc.append("\n*").append(rem / 60).append("m ").append(rem % 60).append("s remaining*");
            description = desc.toString();
        }

        publish("Live Standings: " + stripColor(template.getDisplayName()), description, typeColor(template), List.of());
    }

    /**
     * Feature 2 (Hall of Fame): announces a genuine new all-time-best score for a template.
     * Only called by TournamentManager after the conditional DB upsert confirms a real
     * improvement (or first-ever completion) — never a no-op re-announce.
     */
    public void sendHallOfFameRecord(TournamentTemplate template, String playerName, double score) {
        if (!plugin.getConfig().getBoolean("discord.enabled", true)) return;

        List<Field> fields = new ArrayList<>();
        fields.add(new Field("Player", playerName, true));
        fields.add(new Field("Score", formatScore(score), true));
        fields.add(new Field("Type", template.getType().name(), true));

        publish(
                "🏆 New Hall of Fame Record: " + stripColor(template.getDisplayName()),
                "**" + playerName + "** set a new all-time high score of **" + formatScore(score) + "**!",
                typeColor(template),
                fields
        );
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private record Field(String name, String value, boolean inline) {}

    private void publish(String title, String description, Color color, List<Field> fields) {
        RegisteredServiceProvider<IEventBusService> rsp =
                Bukkit.getServicesManager().getRegistration(IEventBusService.class);
        if (rsp == null) return;

        String webhookName = plugin.getConfig().getString("discord.webhook-name", "tournaments");

        Map<String, Object> data = new HashMap<>();
        data.put("webhook", webhookName);
        data.put("title", title);
        data.put("description", description);
        data.put("color", color.getRGB() & 0xFFFFFF);
        data.put("username", "Tournaments");
        if (!fields.isEmpty()) {
            List<Map<String, Object>> fieldMaps = new ArrayList<>();
            for (Field f : fields) {
                Map<String, Object> fm = new HashMap<>();
                fm.put("name", f.name());
                fm.put("value", f.value());
                fm.put("inline", f.inline());
                fieldMaps.add(fm);
            }
            data.put("fields", fieldMaps);
        }

        rsp.getProvider().publish(new SwagCrossPluginMessageEvent(
                "discordutils:notify", "SwagTournaments", data, null));
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
