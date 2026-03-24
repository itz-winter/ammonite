package com.serverbot.commands.music;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.MusicManager;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

/**
 * Join command - makes the bot join the user's current voice channel.
 */
public class JoinCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) return;

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Not in Voice Channel",
                "You need to be in a voice channel for me to join."
            )).setEphemeral(true).queue();
            return;
        }

        AudioChannel channel = voiceState.getChannel();
        MusicManager musicManager = MusicManager.getInstance();

        if (musicManager.isConnected(event.getGuild())) {
            AudioChannel currentChannel = musicManager.getConnectedChannel(event.getGuild());
            if (currentChannel != null && currentChannel.getIdLong() == channel.getIdLong()) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Already Connected",
                    "I'm already in your voice channel!"
                )).setEphemeral(true).queue();
                return;
            }
        }

        if (musicManager.joinChannel(channel)) {
            event.replyEmbeds(EmbedUtils.createSuccessEmbed(
                "🔊 Joined",
                "Connected to **" + channel.getName() + "**"
            )).queue();
        } else {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Connection Failed",
                "Failed to join **" + channel.getName() + "**. Check bot permissions."
            )).setEphemeral(true).queue();
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("join", "Makes the bot join your voice channel.");
    }

    @Override
    public String getName() { return "join"; }

    @Override
    public String getDescription() { return "Makes the bot join your voice channel."; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.MUSIC; }
}
