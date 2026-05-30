package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.PermissionUtils;
import com.serverbot.utils.TimeUtils;
import com.serverbot.utils.AutoLogUtils;
import com.serverbot.services.PunishmentNotificationService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.Duration;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Mute command for muting users temporarily
 */
public class MuteCommand implements SlashCommand {

    private static final String USAGE = "/mute <@user> [duration] [reason]";
    private static final String EXAMPLE = "/mute @John 1h Spamming in chat";

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "Guild Only", "This command can only be used in servers.", USAGE)).setEphemeral(true).queue();
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.mute")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `mod.mute` permission to use this command.\n\n" +
                            "Ask a server admin to grant you the `mod.mute` permission."))
                    .setEphemeral(true).queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        String durationStr = event.getOption("duration") != null ? event.getOption("duration").getAsString() : "1h";
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                : "No reason provided";

        Member targetMember = event.getGuild().getMember(target);

        if (targetMember == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "User Not Found", "This user is not in the server.", USAGE, EXAMPLE)).setEphemeral(true).queue();
            return;
        }

        if (!PermissionUtils.canInteractWith(moderator, targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Cannot Mute User",
                    "You cannot mute this user due to role hierarchy.\n\n" +
                            "Your highest role must be above the target user's highest role."))
                    .setEphemeral(true).queue();
            return;
        }

        if (!PermissionUtils.botCanInteractWith(event.getGuild(), targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Cannot Mute User",
                    "I cannot mute this user due to role hierarchy.\n\n" +
                            "Move my role higher than the target user's highest role."))
                    .setEphemeral(true).queue();
            return;
        }

        // Parse duration
        final Duration muteDuration = TimeUtils.parseDuration(durationStr);
        if (muteDuration == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "Invalid Duration",
                    "Please provide a valid duration format.\n\n" +
                            "**Valid formats:** `1d`, `2h`, `30m`, `1w`, `12h30m`\n" +
                            "**Units:** `s`=seconds, `m`=minutes, `h`=hours, `d`=days, `w`=weeks",
                    USAGE, EXAMPLE)).setEphemeral(true).queue();
            return;
        }

        // Find or create mute role
        Role muteRole = findMuteRole(event.getGuild());
        if (muteRole == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Mute Role Missing",
                    "Could not find or create a mute role. Please create a role named 'Muted' and configure its permissions."))
                    .setEphemeral(true).queue();
            return;
        }

        // Defer reply since mute operation is async and may take longer than 3 seconds
        event.deferReply().queue();

        // Apply mute
        event.getGuild().addRoleToMember(targetMember, muteRole).reason(reason)
                .queue(success -> {
                    // Send confirmation
                    String durationText = TimeUtils.formatDuration(muteDuration);
                    event.getHook().sendMessageEmbeds(EmbedUtils.createModerationEmbed(
                            "User Muted", target, moderator.getUser(),
                            reason + "\n**Duration:** " + durationText)).queue();

                    // Schedule unmute and log action
                    scheduleMute(event.getGuild().getId(), target.getId(), muteDuration, moderator.getId(), reason);
                    logMute(event.getGuild().getId(), target.getId(), moderator.getId(), reason, muteDuration);

                    // Log to AutoLog channel
                    AutoLogUtils.logMute(event.getGuild(), target, moderator.getUser(), reason, muteDuration);

                    // Send DM notification if enabled
                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                            event.getGuild().getId(),
                            target.getId(),
                            PunishmentNotificationService.PunishmentType.MUTE,
                            reason,
                            muteDuration,
                            durationText);

                }, error -> {
                    event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Mute Failed", "Failed to mute user: " + error.getMessage())).setEphemeral(true).queue();
                });
    }

    private Role findMuteRole(net.dv8tion.jda.api.entities.Guild guild) {
        // Look for existing mute role
        Role existingRole = guild.getRolesByName("Muted", true).stream()
                .findFirst()
                .orElse(null);

        if (existingRole != null) {
            return existingRole;
        }

        // Create mute role if it doesn't exist
        try {
            Role muteRole = guild.createRole()
                    .setName("Muted")
                    .setColor(0x808080) // Gray color
                    .setMentionable(false)
                    .setHoisted(false)
                    .reason("Automatically created by ServerBot for muting users")
                    .complete();

            // Configure permissions for all channels
            guild.getTextChannels().forEach(channel -> {
                channel.getManager().putRolePermissionOverride(muteRole.getIdLong(), null,
                        EnumSet.of(Permission.MESSAGE_SEND, Permission.MESSAGE_ADD_REACTION,
                                Permission.MESSAGE_SEND_IN_THREADS, Permission.CREATE_PUBLIC_THREADS,
                                Permission.CREATE_PRIVATE_THREADS))
                        .queue(null, throwable -> {
                        });
            });

            guild.getVoiceChannels().forEach(channel -> {
                channel.getManager().putRolePermissionOverride(muteRole.getIdLong(), null,
                        EnumSet.of(Permission.VOICE_SPEAK)).queue(null, throwable -> {
                        });
            });

            return muteRole;
        } catch (Exception e) {
            System.err.println("Failed to create mute role: " + e.getMessage());
            return null;
        }
    }

    private void scheduleMute(String guildId, String userId, Duration duration, String moderatorId, String reason) {
        try {
            // Create a unique key for this temporary punishment
            String uniqueKey = guildId + ":" + userId + ":MUTE:" + System.currentTimeMillis();

            // Create temp punishment data
            Map<String, Object> tempPunishment = new HashMap<>();
            tempPunishment.put("guildId", guildId);
            tempPunishment.put("userId", userId);
            tempPunishment.put("punishmentType", "MUTE");
            tempPunishment.put("expiresAt", System.currentTimeMillis() + duration.toMillis());
            tempPunishment.put("moderatorId", moderatorId);
            tempPunishment.put("reason", reason);
            tempPunishment.put("createdAt", System.currentTimeMillis());

            // Store the temporary punishment
            ServerBot.getStorageManager().storeTempPunishment(uniqueKey, tempPunishment);

            System.out
                    .println("Scheduled mute removal for user " + userId + " in " + duration.toMinutes() + " minutes");
        } catch (Exception e) {
            System.err.println("Failed to schedule unmute: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void logMute(String guildId, String userId, String moderatorId, String reason, Duration duration) {
        try {
            // Create moderation log entry
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("type", "MUTE");
            logEntry.put("userId", userId);
            logEntry.put("moderatorId", moderatorId);
            logEntry.put("reason", reason);
            logEntry.put("duration", duration.toString());
            logEntry.put("timestamp", System.currentTimeMillis());

            // Store moderation log
            ServerBot.getStorageManager().addModerationLog(guildId, logEntry);
        } catch (Exception e) {
            System.err.println("Failed to log mute: " + e.getMessage());
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("mute", "Mute a user temporarily")
                .addOption(OptionType.USER, "user", "User to mute", true)
                .addOption(OptionType.STRING, "duration", "Duration of mute (e.g., 1h, 30m, 2d)", false)
                .addOption(OptionType.STRING, "reason", "Reason for the mute", false);
    }

    @Override
    public String getName() {
        return "mute";
    }

    @Override
    public String getDescription() {
        return "Mute a user temporarily";
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
