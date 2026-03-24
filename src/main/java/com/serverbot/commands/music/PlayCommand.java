package com.serverbot.commands.music;

import com.serverbot.commands.CommandCategory;
import com.serverbot.commands.SlashCommand;
import com.serverbot.music.MusicManager;
import com.serverbot.music.MusicUtils;
import com.serverbot.music.GuildMusicManager;
import com.serverbot.utils.EmbedUtils;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.List;

/**
 * Play command - searches and plays music from YouTube, SoundCloud, and direct URLs.
 * Supports playlists with index range selection (e.g. "4:10", "5:", ":9").
 */
public class PlayCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) return;

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Not in Voice Channel",
                "You need to be in a voice channel to use this command."
            )).setEphemeral(true).queue();
            return;
        }

        AudioChannel channel = voiceState.getChannel();
        String query = event.getOption("query", OptionMapping::getAsString);
        String indexStr = event.getOption("index", "", OptionMapping::getAsString);

        if (query == null || query.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                "Missing Query",
                "Please provide a URL or search query.\nUsage: `/play <url or search> [index]`"
            )).setEphemeral(true).queue();
            return;
        }

        // Join the voice channel if not already connected
        MusicManager musicManager = MusicManager.getInstance();
        if (!musicManager.isConnected(event.getGuild())) {
            if (!musicManager.joinChannel(channel)) {
                event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Connection Failed",
                    "Failed to join your voice channel. Check bot permissions."
                )).setEphemeral(true).queue();
                return;
            }
        }

        event.deferReply().queue();

        // Check if index range is specified — use range loading for playlists
        if (!indexStr.isBlank()) {
            int[] range = MusicUtils.parseIndexRange(indexStr);
            musicManager.loadPlaylistWithRange(query, event.getGuild(), range[0], range[1],
                new MusicManager.MusicLoadCallback() {
                    @Override
                    public void onTrackLoaded(AudioTrack track) {
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Added to Queue")
                                .setDescription(MusicUtils.formatTrack(track));
                        event.getHook().sendMessageEmbeds(embed.build()).queue();
                    }

                    @Override
                    public void onPlaylistLoaded(AudioPlaylist playlist) {
                        // Shouldn't happen with range loading, but handle it
                        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                        List<AudioTrack> tracks = playlist.getTracks();
                        for (AudioTrack track : tracks) {
                            gmm.getScheduler().queue(track);
                        }
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Playlist Added")
                                .setDescription("**" + playlist.getName() + "**")
                                .addField("Tracks", String.valueOf(tracks.size()), true);
                        event.getHook().sendMessageEmbeds(embed.build()).queue();
                    }

                    @Override
                    public void onPlaylistRangeLoaded(String playlistName, List<AudioTrack> tracks, int start, int end, int total) {
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Playlist Added (Range)")
                                .setDescription("**" + playlistName + "**")
                                .addField("Selected", "Tracks " + start + "-" + end + " of " + total, true)
                                .addField("Added", tracks.size() + " tracks", true);
                        event.getHook().sendMessageEmbeds(embed.build()).queue();
                    }

                    @Override
                    public void onNoMatches() {
                        event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "No Results", "No matches found for: `" + query + "`"
                        )).queue();
                    }

                    @Override
                    public void onLoadFailed(String message) {
                        event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Load Failed", "Failed to load track: " + message
                        )).queue();
                    }
                });
        } else {
            // Normal load — single track or full playlist
            musicManager.loadAndPlay(query, event.getGuild(),
                new MusicManager.MusicLoadCallback() {
                    @Override
                    public void onTrackLoaded(AudioTrack track) {
                        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                        int position = gmm.getScheduler().getQueueSize();
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Added to Queue")
                                .setDescription(MusicUtils.formatTrack(track));
                        if (position > 0) {
                            embed.addField("Position", "#" + (position + 1) + " in queue", true);
                        } else {
                            embed.addField("Status", "Now playing", true);
                        }
                        event.getHook().sendMessageEmbeds(embed.build()).queue();
                    }

                    @Override
                    public void onPlaylistLoaded(AudioPlaylist playlist) {
                        GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                        List<AudioTrack> tracks = playlist.getTracks();
                        for (AudioTrack track : tracks) {
                            gmm.getScheduler().queue(track);
                        }
                        EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                .setTitle("🎵 Playlist Added")
                                .setDescription("**" + playlist.getName() + "**")
                                .addField("Tracks Added", String.valueOf(tracks.size()), true)
                                .addField("First Track", MusicUtils.formatTrack(tracks.get(0)), false);
                        event.getHook().sendMessageEmbeds(embed.build()).queue();
                    }

                    @Override
                    public void onNoMatches() {
                        event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "No Results", "No matches found for: `" + query + "`"
                        )).queue();
                    }

                    @Override
                    public void onLoadFailed(String message) {
                        event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                            "Load Failed", "Failed to load track: " + message
                        )).queue();
                    }
                });
        }
    }

    public static CommandData getCommandData() {
        return Commands.slash("play", "Search a video/song to play in voicechat.")
                .addOption(OptionType.STRING, "query", "URL or search query (YouTube, SoundCloud, Spotify, etc.)", true)
                .addOption(OptionType.STRING, "index", "Index range for playlists (e.g. 4:10, 5:, :9)", false);
    }

    @Override
    public String getName() { return "play"; }

    @Override
    public String getDescription() { return "Search a video/song to play in voicechat."; }

    @Override
    public CommandCategory getCategory() { return CommandCategory.MUSIC; }
}
