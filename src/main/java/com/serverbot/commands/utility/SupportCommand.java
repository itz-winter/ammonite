package com.serverbot.commands.utility;

import com.serverbot.ServerBot;
import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

// grab support link from config.json

/**
 * Support command for providing support link to users
 */

public class SupportCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String supportLink = ServerBot.getConfigManager().getConfig().getSupportServerInvite();

        if (supportLink == null || supportLink.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Support Link Not Set", "The support link is invalid or has been removed.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "Support Link", "You can get support for the bot by visiting the following link:\n" + supportLink)).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
    }

    @Override
    public String getName() {
        return "support";
    }

    @Override
    public String getDescription() {
        return "Get the support link for the bot";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.UTILITY;
    }

    @Override
    public boolean isGuildOnly() {
        return false;
    }
}