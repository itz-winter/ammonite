package com.serverbot.commands.leveling;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.utils.EmbedUtils;
import com.serverbot.utils.LeaderboardRenderer;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Leaderboard command to show XP/level rankings
 */
public class LeaderboardCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Guild Only", "This command can only be used in servers.")).setEphemeral(true).queue();
            return;
        }
        String guildId = event.getGuild().getId();
        event.deferReply().queue();
        event.getHook().editOriginal(LeaderboardRenderer.buildXpPage(event.getJDA(), guildId, 0, event.getUser().getId())).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("leaderboard", "Show the XP/level leaderboard");
    }

    public static CommandData getLbCommandData() {
        return Commands.slash("lb", "Show the XP/level leaderboard (short form)");
    }

    @Override
    public String getName() {
        return "leaderboard";
    }

    @Override
    public String getDescription() {
        return "Show the XP/level leaderboard";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.LEVELING;
    }
}
