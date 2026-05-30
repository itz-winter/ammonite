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
 * Levels command to enable/disable the leveling system
 */
public class LevelsCommand implements SlashCommand {

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
            updateLevelsSettings(event.getGuild().getId(), enable);

            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                    "Leveling " + (enable ? "Enabled" : "Disabled"),
                    "The leveling/XP system has been " + (enable ? "enabled" : "disabled") + " for this server."))
                    .queue();

        } catch (Exception e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Configuration Failed", "Failed to update leveling settings: " + e.getMessage())).setEphemeral(true)
                    .queue();
        }
    }

    private void updateLevelsSettings(String guildId, boolean enabled) throws Exception {
        // Update guild settings using the file storage manager
        ServerBot.getStorageManager().updateGuildSettings(guildId, "enableLeveling", enabled);
    }

    public static CommandData getCommandData() {
        return Commands.slash("levels", "Enable or disable the leveling system")
                .addOptions(
                        new OptionData(OptionType.STRING, "action", "Enable or disable", true)
                                .addChoice("Enable", "enable")
                                .addChoice("Disable", "disable"));
    }

    @Override
    public String getName() {
        return "levels";
    }

    @Override
    public String getDescription() {
        return "Enable or disable the leveling system";
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
