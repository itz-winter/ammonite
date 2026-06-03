package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.storage.FileStorageManager;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Warn command for warning users
 */
public class WarnCommandSimple implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        // Check permissions
        if (!PermissionManager.hasPermission(event.getMember(), "mod.warn")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Insufficient Permissions",
                    "You need the `mod.warn` permission to warn members.")).setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                : "No reason provided";

        Member targetMember = event.getGuild().getMember(targetUser);
        if (targetMember == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("User Not Found",
                    "This user is not in the server!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        if (targetUser.isBot()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Invalid Target",
                    "You cannot warn bots!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Check if the moderator can interact with the target
        if (!PermissionUtils.canInteractWith(event.getMember(), targetMember)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Cannot Warn",
                    "You cannot warn this user due to role hierarchy!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
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

        event.replyEmbeds(EmbedUtils.createSuccessEmbed("User Warned", description)).queue();
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
