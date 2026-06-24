package com.serverbot.commands.economy;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.LeaderboardRenderer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Balance top command — shows the points leaderboard with page navigation.
 */
public class BaltopCommand implements SlashCommand {

    @Override
    public String getName() { return "baltop"; }

    @Override
    public String getDescription() { return "Shows the points leaderboard"; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.ECONOMY; }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }
        String guildId = event.getGuild().getId();
        event.deferReply().queue();
        event.getHook().editOriginal(LeaderboardRenderer.buildPointsPage(event.getJDA(), guildId, 0, event.getUser().getId())).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("baltop", "Shows the points leaderboard");
    }
}
