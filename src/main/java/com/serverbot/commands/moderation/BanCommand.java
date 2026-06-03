package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.SchedulerService;
import com.serverbot.services.PunishmentNotificationService;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.TimeUtils;
import com.serverbot.utils.AutoLogUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Ban command for banning users with optional duration
 */
public class BanCommand implements SlashCommand {

    private static final String USAGE = "/ban <@user> [duration] [reason]";
    private static final String EXAMPLE = "/ban @John 7d Repeated rule violations";

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "Guild Only", "This command can only be used in servers.", USAGE)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.ban")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `mod.ban` permission to use this command.\n\n" +
                            "Ask a server admin to grant you the `mod.ban` permission."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        String durationStr = event.getOption("duration") != null ? event.getOption("duration").getAsString() : null;
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                : "No reason provided";

        Member targetMember = event.getGuild().getMember(target);

        // Check if target is in guild and can be banned
        if (targetMember != null) {
            if (!moderator.canInteract(targetMember)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Cannot Ban User",
                        "You cannot ban this user due to role hierarchy.\n\n" +
                                "Your highest role must be above the target user's highest role."))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }

            if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Cannot Ban User",
                        "I cannot ban this user due to role hierarchy.\n\n" +
                                "Move my role higher than the target user's highest role."))
                        .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }
        }

        // Parse duration if provided
        final Duration banDuration;
        if (durationStr != null) {
            banDuration = TimeUtils.parseDuration(durationStr);
            if (banDuration == null) {
                event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                        "Invalid Duration",
                        "Please provide a valid duration format.\n\n" +
                                "**Valid formats:** `1d`, `2h`, `30m`, `1w`, `12h30m`\n" +
                                "**Units:** `s`=seconds, `m`=minutes, `h`=hours, `d`=days, `w`=weeks",
                        USAGE, EXAMPLE)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                return;
            }
        } else {
            banDuration = null;
        }

        // Defer reply since ban operation is async and may take longer than 3 seconds
        event.deferReply().queue();

        // Execute ban
        event.getGuild().ban(target, 7, TimeUnit.DAYS).reason(reason)
                .queue(success -> {
                    // Send confirmation
                    String durationText = banDuration != null ? TimeUtils.formatDuration(banDuration) : "Permanent";
                    event.getHook().sendMessageEmbeds(EmbedUtils.createModerationEmbed(
                            "User Banned", target, moderator.getUser(), reason + "\n**Duration:** " + durationText))
                            .queue();

                    // Send DM notification if configured
                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                            event.getGuild().getId(),
                            target.getId(),
                            PunishmentNotificationService.PunishmentType.BAN,
                            reason,
                            banDuration,
                            moderator.getEffectiveName());

                    // Schedule unban if temporary
                    if (banDuration != null) {
                        scheduleUnban(event.getGuild().getId(), target.getId(), banDuration, moderator.getId(), reason);
                    }

                    // Log to file storage and mod log channel
                    logBan(event.getGuild().getId(), target.getId(), moderator.getId(), reason, banDuration);

                    // Log to AutoLog channel
                    AutoLogUtils.logBan(event.getGuild(), target, moderator.getUser(), reason, banDuration);

                }, error -> {
                    event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Ban Failed", "Failed to ban user: " + error.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                });
    }

    private void scheduleUnban(String guildId, String userId, Duration duration, String moderatorId, String reason) {
        try {
            long unbanTimestamp = System.currentTimeMillis() + duration.toMillis();
            SchedulerService.getInstance().scheduleUnban(guildId, userId, reason, unbanTimestamp);

            // Also store in file storage for backup/tracking
            Map<String, Object> tempPunishment = new HashMap<>();
            tempPunishment.put("guildId", guildId);
            tempPunishment.put("userId", userId);
            tempPunishment.put("punishmentType", "BAN");
            tempPunishment.put("expiresAt", unbanTimestamp);
            tempPunishment.put("moderatorId", moderatorId);
            tempPunishment.put("reason", reason);
            tempPunishment.put("createdAt", System.currentTimeMillis());

            // Store in file storage with a unique key
            String key = guildId + ":" + userId + ":" + System.currentTimeMillis();
            ServerBot.getStorageManager().storeTempPunishment(key, tempPunishment);
        } catch (Exception e) {
            // Log error but don't fail the ban
            System.err.println("Failed to schedule unban: " + e.getMessage());
        }
    }

    private void logBan(String guildId, String userId, String moderatorId, String reason, Duration duration) {
        try {
            // Create moderation log entry
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("type", "BAN");
            logEntry.put("userId", userId);
            logEntry.put("moderatorId", moderatorId);
            logEntry.put("reason", reason);
            logEntry.put("duration", duration != null ? duration.toString() : "PERMANENT");
            logEntry.put("timestamp", System.currentTimeMillis());

            // Store moderation log
            ServerBot.getStorageManager().addModerationLog(guildId, logEntry);
        } catch (Exception e) {
            System.err.println("Failed to log ban: " + e.getMessage());
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("ban", "Ban a user from the server")
                .addOption(OptionType.USER, "user", "User to ban", true)
                .addOption(OptionType.STRING, "duration", "Duration of ban (e.g., 7d, 2h, 30m)", false)
                .addOption(OptionType.STRING, "reason", "Reason for the ban", false);
    }

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public String getDescription() {
        return "Ban a user from the server";
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
