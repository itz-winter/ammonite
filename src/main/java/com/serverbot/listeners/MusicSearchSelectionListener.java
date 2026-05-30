package com.serverbot.listeners;

import com.serverbot.commands.music.PlayCommand;
import com.serverbot.music.GuildMusicManager;
import com.serverbot.music.MusicManager;
import com.serverbot.music.MusicUtils;
import com.serverbot.utils.EmbedUtils;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

/**
 * Listener that handles the search result selection dropdown from the /play
 * command.
 * When a user picks a track from the StringSelectMenu, this listener queues it.
 */
public class MusicSearchSelectionListener extends ListenerAdapter {

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("play_search_select"))
            return;

        // Build the lookup key: "guildId:userId"
        String key = event.getGuild().getId() + ":" + event.getUser().getId();
        PlayCommand.PendingSearch pending = PlayCommand.consumeSearchResults(key);

        if (pending == null) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Selection Expired",
                    "This search has expired. Please run `/play` again.")).setEphemeral(true).queue();
            return;
        }

        // Parse the selected index
        int index;
        try {
            index = Integer.parseInt(event.getValues().get(0));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Selection", "Something went wrong. Please try again.")).setEphemeral(true).queue();
            return;
        }

        List<AudioTrack> tracks = pending.tracks();
        if (index < 0 || index >= tracks.size()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Invalid Selection", "Something went wrong. Please try again.")).setEphemeral(true).queue();
            return;
        }

        AudioTrack track = tracks.get(index);
        AudioChannel voiceChannel = pending.channel();
        MusicManager musicManager = MusicManager.getInstance();

        // Join voice channel if not already connected
        if (!musicManager.isConnected(event.getGuild())) {
            if (!musicManager.joinChannel(voiceChannel)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                        "Connection Failed",
                        "Failed to join your voice channel. Check bot permissions.")).setEphemeral(true).queue();
                return;
            }
        } else {
            // Already connected — move to the user's channel if different
            AudioChannel connected = musicManager.getConnectedChannel(event.getGuild());
            if (connected != null && !connected.getId().equals(voiceChannel.getId())) {
                event.getGuild().getAudioManager().openAudioConnection(voiceChannel);
            }
        }

        // Queue the selected track
        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
        boolean startedPlaying = gmm.getScheduler().queue(track);

        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                .setTitle("🎵 Added to Queue")
                .setDescription(MusicUtils.formatTrack(track));
        if (startedPlaying) {
            embed.addField("Status", "Now playing", true);
        } else {
            embed.addField("Position", "#" + (gmm.getScheduler().getQueueSize()) + " in queue", true);
        }

        // Edit the original search message: replace the dropdown with the "now playing"
        // embed
        event.editMessageEmbeds(embed.build()).setComponents().queue();
    }
}
