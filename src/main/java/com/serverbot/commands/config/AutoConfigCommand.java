package com.serverbot.commands.config;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.listeners.AutoConfigListener;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * /autoconfig — launches the guided bot setup wizard for server owners.
 */
public class AutoConfigCommand implements SlashCommand {

    @Override
    public String getName() {
        return "autoconfig";
    }

    @Override
    public String getDescription() {
        return "Launch the guided bot configuration wizard";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.CONFIGURATION;
    }

    @Override
    public boolean requiresPermissions() {
        return false;
    }

    public static CommandData getCommandData() {
        return Commands.slash("autoconfig", "Launch the guided bot configuration wizard");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Error",
                    "This command must be used in a server.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Only the server owner should run setup
        if (!event.getUser().getId().equals(event.getGuild().getOwnerId())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed("Owner Only",
                    "Only the server owner can run the setup wizard.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        // Start the setup wizard using the shared listener logic
        AutoConfigListener.sendSetupPrompt(event.getChannel().asTextChannel(), event.getGuild());

        event.replyEmbeds(EmbedUtils.createSuccessEmbed("Setup Wizard",
                "The configuration wizard has been started in this channel!")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }
}
