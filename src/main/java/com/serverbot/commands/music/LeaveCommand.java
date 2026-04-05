package com.serverbot.commands.music;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.MusicManager;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Leave command - makes the bot leave the voice channel, stopping all playback.
 */
public class LeaveCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MusicManager musicManager = MusicManager.getInstance();

        if (!musicManager.isConnected(event.getGuild())) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Not Connected",
                "I'm not currently in a voice channel."
            )).setEphemeral(true).queue();
            return;
        }

        musicManager.leaveChannel(event.getGuild());

        event.replyEmbeds(EmbedUtils.createMusicEmbed(
            "👋 Disconnected",
            "Left the voice channel and cleared the queue."
        )).queue();
    }

    public static CommandData getCommandData() {
        return Commands.slash("leave", "Makes the bot leave the voice channel.");
    }

    @Override
    public String getName() { return "leave"; }

    @Override
    public String getDescription() { return "Makes the bot leave the voice channel."; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.MUSIC; }
}
