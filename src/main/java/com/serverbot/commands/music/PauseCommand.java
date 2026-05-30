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
 * Pause command - pauses or resumes the current music playback.
 */
public class PauseCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MusicManager musicManager = MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not Playing", "The bot is not currently playing music.")).setEphemeral(true).queue();
            return;
        }

        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        boolean isPaused = gmm.getPlayer().isPaused();
        gmm.getPlayer().setPaused(!isPaused);

        if (isPaused) {
            event.replyEmbeds(EmbedUtils.createMusicEmbed(
                    "▶️ Resumed", "Music playback has been resumed.")).queue();
        } else {
            event.replyEmbeds(EmbedUtils.createMusicEmbed(
                    "⏸️ Paused", "Music playback has been paused.")).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("pause", "Pauses or resumes the music in voicechat.");
    }

    @Override
    public String getName() {
        return "pause";
    }

    @Override
    public String getDescription() {
        return "Pauses or resumes the music in voicechat.";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MUSIC;
    }
}
