package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.services.PunishmentNotificationService;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.PermissionUtils;
import com.serverbot.utils.AutoLogUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Warn command for warning users
 */
public class WarnCommand implements SlashCommand {

    private static final String USAGE = "/warn <@user> [reason]";
    private static final String EXAMPLE = "/warn @John Spamming in chat";

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage(
                    "Guild Only", "This command can only be used in servers.", USAGE)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Check permissions
        if (!PermissionManager.hasPermission(event.getMember(), "mod.warn")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Insufficient Permissions",
                    "You need the `mod.warn` permission to use this command.\n\n" +
                            "Ask a server admin to grant you the `mod.warn` permission."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                : "No reason provided";

        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage("User Not Found",
                    "This user is not in the server!", USAGE, EXAMPLE)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (targetUser.isBot()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbedWithUsage("Invalid Target",
                    "You cannot warn bots!", USAGE, EXAMPLE)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Check if the moderator can interact with the target
        if (!PermissionUtils.canInteractWith(event.getMember(), targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Cannot Warn",
                    "You cannot warn this user due to role hierarchy!\n\n" +
                            "Your highest role must be above the target user's highest role."))
                    .setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        FileStorageManager storage = ServerBot.getStorageManager();
        String guildId = event.getGuild().getId();
        String userId = targetUser.getId();
        String moderatorId = event.getUser().getId();

        // Add the warning
        storage.addWarning(guildId, userId, reason, moderatorId);
        int warningCount = storage.getWarningCount(guildId, userId);

        String description = String.format(
                "**User:** %s (%s)\n" +
                        "**Reason:** %s\n" +
                        "**Warning Count:** %d\n" +
                        "**Moderator:** %s",
                targetUser.getName(), targetUser.getId(),
                reason, warningCount, event.getUser().getName());

        event.replyEmbeds(EmbedUtils.createWarningEmbed("User Warned", description)).queue();

        // Send DM notification if configured
        PunishmentNotificationService.getInstance().sendPunishmentNotification(
                event.getGuild().getId(),
                targetUser.getId(),
                PunishmentNotificationService.PunishmentType.WARN,
                reason,
                null, // No duration for warnings
                event.getMember().getEffectiveName());

        // Log to AutoLog channel
        AutoLogUtils.logWarn(event.getGuild(), targetUser, event.getUser(), reason);
    }

    @Override
    public String getName() {
        return "warn";
    }

    @Override
    public String getDescription() {
        return "Warn a user";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MODERATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    public static CommandData getCommandData() {
        return Commands.slash("warn", "Warn a user")
                .addOption(OptionType.USER, "user", "User to warn", true)
                .addOption(OptionType.STRING, "reason", "Reason for the warning", false);
    }
}
