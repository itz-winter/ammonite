package com.serverbot.commands.configuration;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.PermissionUtils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

/**
 * Points command to enable/disable the economy system
 */
public class PointsCommandFixed implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }

        Member member = event.getMember();
        if (!PermissionUtils.hasManageServerPermissions(member)) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Insufficient Permissions", "You need Manage Server permissions to use this command."))
                    .setEphemeral(true).queue();
            return;
        }

        String action = event.getOption("action").getAsString().toLowerCase();
        boolean enable = action.equals("enable");

        try {
            updatePointsSettings(event.getGuild().getId(), enable);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Economy " + (enable ? "Enabled" : "Disabled"),
                    "The points/economy system has been " + (enable ? "enabled" : "disabled") + " for this server."))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Configuration Failed", "Failed to update economy settings: " + e.getMessage())).setEphemeral(true)
                    .queue();
        }
    }

    private void updatePointsSettings(String guildId, boolean enabled) throws Exception {
        // Update guild settings using file storage
        ServerBot.getStorageManager().updateGuildSettings(guildId, "enableEconomy", enabled);
    }

    public static CommandData getCommandData() {
        return Commands.slash("points", "Enable or disable the economy system")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "Enable or disable", true)
                                .addChoice("Enable", "enable")
                                .addChoice("Disable", "disable"));
    }

    @Override
    public String getName() {
        return "points";
    }

    @Override
    public String getDescription() {
        return "Enable or disable the economy system";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean requiresPermissions() {
        return true;
    }
}
