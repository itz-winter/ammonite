package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.TimeUtils;
import com.serverbot.utils.AutoLogUtils;
import com.serverbot.services.PunishmentNotificationService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.Duration;

/**
 * Timeout command using Discord's built-in timeout functionality
 */
public class TimeoutCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.timeout")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `mod.timeout` permission to use this command.")).setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        Member targetMember = event.getGuild().getMember(targetUser);

        if (targetMember == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "User Not Found", "This user is not in the server.")).setEphemeral(true).queue();
            return;
        }

        // Check if bot can timeout this member
        if (!event.getGuild().getSelfMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Bot Permissions",
                    "I need the 'Moderate Members' permission to timeout users.")).setEphemeral(true).queue();
            return;
        }

        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Cannot Timeout",
                    "I cannot timeout this user. They may have higher permissions than me.")).setEphemeral(true)
                    .queue();
            return;
        }

        // Don't let users timeout themselves or users with higher/equal permissions
        if (targetUser.getId().equals(moderator.getUser().getId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Target", "You cannot timeout yourself.")).setEphemeral(true).queue();
            return;
        }

        if (!moderator.canInteract(targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Cannot Timeout",
                    "You cannot timeout this user. They have higher or equal permissions.")).setEphemeral(true).queue();
            return;
        }

        try {
            String durationStr = event.getOption("duration").getAsString();
            String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                    : "No reason provided";

            // Parse duration using standardized TimeUtils
            Duration timeoutDuration = TimeUtils.parseDuration(durationStr);
            if (timeoutDuration == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Invalid Duration",
                        "Please provide a valid duration (e.g., 1h, 30m, 2d, 1h30m, 10m360s).\n" +
                                "Supported units: s (seconds), m (minutes), h (hours), d (days)"))
                        .setEphemeral(true).queue();
                return;
            }

            // Discord timeout limits: max 28 days
            if (timeoutDuration.toDays() > 28) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Duration Too Long",
                        "Timeout duration cannot exceed 28 days.")).setEphemeral(true).queue();
                return;
            }

            String durationText = TimeUtils.formatDuration(timeoutDuration);

            // Apply timeout
            targetMember.timeoutFor(timeoutDuration).reason(reason).queue(
                    success -> {
                        // Log the timeout
                        try {
                            String guildId = event.getGuild().getId();
                            ServerBot.getStorageManager().logModerationAction(
                                    guildId,
                                    targetUser.getId(),
                                    moderator.getUser().getId(),
                                    "TIMEOUT",
                                    reason,
                                    durationText);
                        } catch (Exception e) {
                            // Log error but don't fail the command
                            System.err.println("Failed to log timeout action: " + e.getMessage());
                        }

                        // Log to AutoLog channel
                        AutoLogUtils.logTimeout(event.getGuild(), targetUser, moderator.getUser(), reason,
                                timeoutDuration);

                        // Send DM notification if enabled
                        PunishmentNotificationService.getInstance().sendPunishmentNotification(
                                event.getGuild().getId(),
                                targetUser.getId(),
                                PunishmentNotificationService.PunishmentType.TIMEOUT,
                                reason,
                                timeoutDuration,
                                durationText);

                        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                                "User Timed Out",
                                "**User:** " + targetUser.getAsMention() + "\n" +
                                        "**Duration:** " + durationText + "\n" +
                                        "**Reason:** " + reason + "\n" +
                                        "**Moderator:** " + moderator.getUser().getAsMention()))
                                .queue();
                    },
                    error -> {
                        event.replyEmbeds(EmbedUtils.createErrorEmbed(
                                "Timeout Failed",
                                "Failed to timeout user: " + error.getMessage())).setEphemeral(true).queue();
                    });

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Timeout Failed",
                    "An error occurred while timing out the user: " + e.getMessage())).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("timeout", "Timeout a user using Discord's timeout feature")
                .addOption(OptionType.USER, "user", "User to timeout", true)
                .addOption(OptionType.STRING, "duration", "Duration (e.g., 1h, 30m, 2d, 1h30m, 10m360s)", true)
                .addOption(OptionType.STRING, "reason", "Reason for timeout", false);
    }

    @Override
    public String getName() {
        return "timeout";
    }

    @Override
    public String getDescription() {
        return "Timeout a user using Discord's built-in timeout feature";
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
