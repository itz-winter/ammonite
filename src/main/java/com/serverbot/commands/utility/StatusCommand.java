package com.serverbot.commands.utility;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Status message command for setting custom bot status message
 */
public class StatusCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Check if user is bot owner
        if (!PermissionUtils.isBotOwner(event.getUser())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Bot Owner Only", "Only the bot owner can change the bot status."
            )).setEphemeral(true).queue();
            return;
        }

        // Handle status message - if blank/null, clear status; otherwise set it
        if (event.getOption("message") == null || event.getOption("message").getAsString().isBlank()) {
            handleClearStatus(event);
        } else {
            handleSetStatus(event);
        }
    }

    private void handleSetStatus(SlashCommandInteractionEvent event) {
        String statusMessage = event.getOption("message").getAsString();

        try {
            Activity activity = Activity.customStatus(statusMessage);
            event.getJDA().getPresence().setActivity(activity);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Status Updated", "Bot status has been set to:\n**" + statusMessage + "**"
            )).setEphemeral(true).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Status Update Failed", "Failed to update bot status: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    private void handleClearStatus(SlashCommandInteractionEvent event) {
        try {
            event.getJDA().getPresence().setActivity(null);
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Status Cleared", "Bot status has been cleared."
            )).setEphemeral(true).queue();
        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Status Clear Failed", "Failed to clear bot status: " + e.getMessage()
            )).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("statusmsg", "Set bot custom status message")
                .addOption(OptionType.STRING, "message", "Status message (leave blank to clear)", false);
    }

    @Override
    public String getName() {
        return "statusmsg";
    }

    @Override
    public String getDescription() {
        return "Set bot custom status message";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }

    @Override
    public boolean isOwnerOnly() {
        return true;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }
}
