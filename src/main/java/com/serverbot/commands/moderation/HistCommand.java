package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * History command to view user moderation history
 */
public class HistCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.history")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `mod.history` permission to view user history."))
                    .setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();

        try {
            List<Map<String, Object>> moderationLogs = ServerBot.getStorageManager()
                    .getModerationLogs(event.getGuild().getId());

            // Filter logs for this user
            List<Map<String, Object>> userHistory = moderationLogs.stream()
                    .filter(log -> targetUser.getId().equals(log.get("userId")))
                    .sorted((a, b) -> {
                        // Handle both Long and Double timestamp types
                        long timestampA = getLongValue(a.get("timestamp"));
                        long timestampB = getLongValue(b.get("timestamp"));
                        return Long.compare(timestampB, timestampA);
                    })
                    .limit(20) // Show last 20 entries
                    .toList();

            if (userHistory.isEmpty()) {
                event.replyEmbeds(EmbedUtils.createInfoEmbed(
                        "No Moderation History",
                        targetUser.getAsMention() + " has no recorded moderation actions.")).queue();
                return;
            }

            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.WARNING_COLOR)
                    .setTitle("📋 Moderation History")
                    .setDescription("Recent moderation actions for " + targetUser.getAsMention())
                    .setThumbnail(targetUser.getAvatarUrl());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());

            int displayCount = Math.min(userHistory.size(), 10);

            for (int i = 0; i < displayCount; i++) {
                Map<String, Object> log = userHistory.get(i);
                long timestamp = getLongValue(log.get("timestamp"));
                String type = (String) log.get("type");
                String reason = (String) log.get("reason");
                String moderatorId = (String) log.get("moderatorId");

                String moderatorName = "Unknown";
                try {
                    User mod = event.getJDA().getUserById(moderatorId);
                    if (mod != null)
                        moderatorName = mod.getName();
                } catch (Exception ignored) {
                }

                String dateStr = formatter.format(Instant.ofEpochMilli(timestamp));

                String fieldTitle = getActionEmoji(type) + " " + type + " (#" + (i + 1) + ")";
                String fieldValue = "**Reason:** " + reason + "\n" +
                        "**Moderator:** " + moderatorName + "\n" +
                        "**Date:** " + dateStr;

                embed.addField(fieldTitle, fieldValue, false);
            }

            embed.addField("📊 Statistics",
                    "**Total Records:** " + userHistory.size() + "\n" +
                            "**User:** " + targetUser.getName(),
                    true);

            if (userHistory.size() > displayCount) {
                embed.setFooter("Showing " + displayCount + " most recent entries out of " + userHistory.size());
            }

            event.replyEmbeds(embed.build()).queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "History Error",
                    "Failed to retrieve moderation history: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    private long getLongValue(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Double) {
            return ((Double) value).longValue();
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else {
            return 0L;
        }
    }

    private String getActionEmoji(String actionType) {
        return switch (actionType.toUpperCase()) {
            case "WARN", "WARNING" -> "⚠️";
            case "MUTE", "TIMEOUT" -> "🔇";
            case "KICK" -> "👢";
            case "BAN" -> "🔨";
            case "UNBAN" -> "🔓";
            case "UNMUTE" -> "🔊";
            default -> "📋";
        };
    }

    public static CommandData getCommandData() {
        return Commands.slash("hist", "View user's moderation history")
                .addOption(OptionType.USER, "user", "The user to check history for", true);
    }

    @Override
    public String getName() {
        return "hist";
    }

    @Override
    public String getDescription() {
        return "View user's moderation history";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
