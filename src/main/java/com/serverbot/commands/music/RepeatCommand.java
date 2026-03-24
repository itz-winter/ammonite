package com.serverbot.commands.music;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.GuildMusicManager;
import com.serverbot.music.MusicManager;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Repeat command - toggles repeat mode for the current track.
 */
public class RepeatCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MusicManager musicManager = MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Not Playing", "The bot is not currently playing music."
            )).setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        boolean newState = !gmm.getScheduler().isRepeating();
        gmm.getScheduler().setRepeating(newState);

        if (newState) {
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "🔁 Repeat Enabled",
                "The current track will now repeat."
            )).queue();
        } else {
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "🔁 Repeat Disabled",
                "Repeat mode has been turned off."
            )).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("repeat", "Sets whether the playlist should repeat.");
    }

    @Override
    public String getName() { return "repeat"; }

    @Override
    public String getDescription() { return "Sets whether the playlist should repeat."; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.MUSIC; }
}
