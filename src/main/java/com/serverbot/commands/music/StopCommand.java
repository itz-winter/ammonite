package com.serverbot.commands.music;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.MusicManager;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Stop command - stops all music playback and clears the queue.
 */
public class StopCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MusicManager musicManager = MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Not Playing", "The bot is not currently playing music."
            )).setEphemeral(true).queue();
            return;
        }

        var gmm = musicManager.getGuildMusicManager(event.getGuild());
        gmm.getScheduler().clearQueue();
        gmm.getPlayer().stopTrack();

        event.replyEmbeds(EmbedUtils.createMusicEmbed(
            "⏹️ Stopped", "Stopped playing and cleared the queue."
        )).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("stop", "Stops playing any music entirely.");
    }

    @Override
    public String getName() { return "stop"; }

    @Override
    public String getDescription() { return "Stops playing any music entirely."; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.MUSIC; }
}
