package com.serverbot.commands.moderation;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.AutoLogUtils;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unwarn command to remove user warnings
 */
public class UnwarnCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member moderator = event.getMember();
        if (!PermissionManager.hasPermission(moderator, "mod.unwarn")) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need the `mod.unwarn` permission to remove warnings."))
                    .setEphemeral(true).queue();
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString()
                : "No reason provided";

        try {
            List<Map<String, Object>> warnings = ServerBot.getStorageManager().getWarnings(event.getGuild().getId(),
                    targetUser.getId());

            if (warnings.isEmpty()) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "No Warnings", targetUser.getName() + " has no active warnings to remove.")).setEphemeral(true)
                        .queue();
                return;
            }

            // Remove the most recent warning
            Map<String, Object> removedWarning = warnings.get(warnings.size() - 1);

            // Clear all warnings and re-add all but the last one
            ServerBot.getStorageManager().clearWarnings(event.getGuild().getId(), targetUser.getId());

            for (int i = 0; i < warnings.size() - 1; i++) {
                Map<String, Object> warning = warnings.get(i);
                ServerBot.getStorageManager().addWarning(
                        event.getGuild().getId(),
                        targetUser.getId(),
                        (String) warning.get("reason"),
                        (String) warning.get("moderatorId"));
            }

            // Log the unwarn action
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("type", "UNWARN");
            logEntry.put("userId", targetUser.getId());
            logEntry.put("moderatorId", moderator.getId());
            logEntry.put("reason", reason);
            logEntry.put("removedWarningReason", removedWarning.get("reason"));
            logEntry.put("timestamp", System.currentTimeMillis());

            ServerBot.getStorageManager().addModerationLog(event.getGuild().getId(), logEntry);

            // Log to AutoLog channel
            AutoLogUtils.logUnwarn(event.getGuild(), targetUser, moderator.getUser(), reason,
                    (String) removedWarning.get("reason"));

            int remainingWarnings = warnings.size() - 1;
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Warning Removed",
                    "Successfully removed a warning from " + targetUser.getAsMention() +
                            "\n**Removed Warning:** " + removedWarning.get("reason") +
                            "\n**Reason for Removal:** " + reason +
                            "\n**Remaining Warnings:** " + remainingWarnings +
                            "\n**Moderator:** " + moderator.getAsMention()))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Unwarn Failed",
                    "Failed to remove warning: " + e.getMessage())).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("unwarn", "Remove a warning from a user")
                .addOption(OptionType.USER, "user", "The user to remove warning from", true)
                .addOption(OptionType.STRING, "reason", "Reason for removing the warning", false);
    }

    @Override
    public String getName() {
        return "unwarn";
    }

    @Override
    public String getDescription() {
        return "Remove a warning from a user";
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
