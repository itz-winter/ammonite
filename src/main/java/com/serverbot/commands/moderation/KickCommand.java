package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.services.PunishmentNotificationService;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.AutoLogUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.HashMap;
import java.util.Map;

/**
 * Kick command for removing users from the server
 */
public class KickCommand implements SlashCommand {

    private static final String USAGE = "/kick <@user> [reason]";
    private static final String EXAMPLE = "/kick @John Breaking server rules";

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "Guild Only", "This command can only be used in servers.", USAGE)).setEphemeral(true).queue();
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.kick")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions",
                    "You need the `mod.kick` permission to use this command.\n\n" +
                            "Ask a server admin to grant you the `mod.kick` permission."))
                    .setEphemeral(true).queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                : "No reason provided";

        Member targetMember = event.getGuild().getMember(target);

        if (targetMember == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "User Not Found", "This user is not in the server.", USAGE, EXAMPLE)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (!moderator.canInteract(targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Cannot Kick User",
                    "You cannot kick this user due to role hierarchy.\n\n" +
                            "Your highest role must be above the target user's highest role."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (!event.getGuild().getSelfMember().canInteract(targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Cannot Kick User",
                    "I cannot kick this user due to role hierarchy.\n\n" +
                            "Move my role higher than the target user's highest role."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Defer reply since kick operation is async and may take longer than 3 seconds
        event.deferReply().queue();

        // Execute kick
        event.getGuild().kick(targetMember).reason(reason)
                .queue(success -> {
                    event.getHook().sendMessageEmbeds(EmbedUtils.createModerationEmbed(
                            "User Kicked", target, moderator.getUser(), reason)).queue();

                    // Send DM notification if configured
                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                            event.getGuild().getId(),
                            target.getId(),
                            PunishmentNotificationService.PunishmentType.KICK,
                            reason,
                            null, // No duration for kicks
                            moderator.getEffectiveName());

                    // Log to database and AutoLog channel
                    logKick(event.getGuild().getId(), target.getId(), moderator.getId(), reason);
                    AutoLogUtils.logKick(event.getGuild(), target, moderator.getUser(), reason);

                }, error -> {
                    event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Kick Failed", "Failed to kick user: " + error.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
                });
    }

    private void logKick(String guildId, String userId, String moderatorId, String reason) {
        try {
            // Create moderation log entry
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("type", "KICK");
            logEntry.put("userId", userId);
            logEntry.put("moderatorId", moderatorId);
            logEntry.put("reason", reason);
            logEntry.put("timestamp", System.currentTimeMillis());

            // Store moderation log
            ServerBot.getStorageManager().addModerationLog(guildId, logEntry);
        } catch (Exception e) {
            System.err.println("Failed to log kick: " + e.getMessage());
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("kick", "Kick a user from the server")
                .addOption(OptionType.USER, "user", "User to kick", true)
                .addOption(OptionType.STRING, "reason", "Reason for the kick", false);
    }

    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public String getDescription() {
        return "Kick a user from the server";
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
