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
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Collections;

/**
 * Play command - searches and plays music from YouTube, SoundCloud, Spotify,
 * and direct URLs.
 * For text queries, shows a selection menu with up to 5 results.
 * For direct URLs (YouTube, Spotify, etc.), plays immediately.
 * Supports playlists with index range selection (e.g. "4:10", "5:", ":9").
 */
public class PlayCommand implements SlashCommand {

    // Store pending search results keyed by "guildId:userId" for the select menu
    // callback
    private static final Map<String, PendingSearch> pendingSearchResults = new ConcurrentHashMap<>();

    /**
     * Pending search data: the list of result tracks + the voice channel the user
     * was in.
     */
    public record PendingSearch(List<AudioTrack> tracks, AudioChannel channel) {
    }

    /**
     * Get and remove a pending search result for a given key.
     */
    public static PendingSearch consumeSearchResults(String key) {
        return pendingSearchResults.remove(key);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null)
            return;

        GuildVoiceState voiceState = member.getVoiceState();
        if (voiceState == null || !voiceState.inAudioChannel()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Not in Voice Channel",
                    "You need to be in a voice channel to use this command.")).setEphemeral(true).setComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary("share_req:" + event.getUser().getId(), "\uD83D\uDCE4 Share"))).queue();
            return;
        }

        AudioChannel channel = voiceState.getChannel();
        String query = event.getOption("query", OptionMapping::getAsString);
        String indexStr = event.getOption("index", "", OptionMapping::getAsString);

        if (query == null || query.isBlank()) {
            event.replyEmbeds(EmbedUtils.createErrorEmbed(
                    "Missing Query",
                    "Please provide a URL or search query.\nUsage: `/play <url or search> [index]`")).setEphemeral(true)
                    .queue();
            return;
        }

        MusicManager musicManager = MusicManager.getInstance();
        boolean isUrl = query.startsWith("http://") || query.startsWith("https://");

        // Multi-URL mode
        // If the query contains multiple space-separated URLs, queue them all.
        String[] tokens = query.trim().split("\\s+");
        boolean isMultiUrl = tokens.length > 1 &&
                java.util.Arrays.stream(tokens).allMatch(t -> t.startsWith("http://") || t.startsWith("https://"));

        if (isMultiUrl) {
            event.deferReply().queue();
            AtomicInteger pending = new AtomicInteger(tokens.length);
            AtomicInteger addedCount = new AtomicInteger(0);
            List<String> failedUrls = Collections.synchronizedList(new ArrayList<>());

            for (String url : tokens) {
                musicManager.loadAndPlay(url, event.getGuild(),
                        new MusicManager.MusicLoadCallback() {
                            @Override
                            public void onTrackLoaded(AudioTrack track) {
                                if (!joinBeforePlay(musicManager, channel, event)) {
                                    failedUrls.add(track.getInfo().uri);
                                } else {
                                    GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                                    gmm.getScheduler().queue(track);
                                    addedCount.incrementAndGet();
                                }
                                checkDone();
                            }

                            @Override
                            public void onPlaylistLoaded(AudioPlaylist playlist) {
                                if (!joinBeforePlay(musicManager, channel, event)) {
                                    failedUrls.add(url);
                                } else {
                                    GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                                    for (AudioTrack t : playlist.getTracks())
                                        gmm.getScheduler().queue(t);
                                    addedCount.addAndGet(playlist.getTracks().size());
                                }
                                checkDone();
                            }

                            @Override
                            public void onNoMatches() {
                                failedUrls.add(url);
                                checkDone();
                            }

                            @Override
                            public void onLoadFailed(String message) {
                                failedUrls.add(url);
                                checkDone();
                            }

                            private void checkDone() {
                                if (pending.decrementAndGet() == 0) {
                                    int added = addedCount.get();
                                    int failed = failedUrls.size();
                                    StringBuilder desc = new StringBuilder();
                                    desc.append("**").append(added).append("** track(s) added to the queue.");
                                    if (failed > 0) {
                                        desc.append("\n**").append(failed).append("** failed to load.");
                                    }
                                    EmbedBuilder embed = EmbedUtils.createEmbedBuilder(
                                            failed > 0 && added == 0 ? EmbedUtils.ERROR_COLOR
                                                    : EmbedUtils.SUCCESS_COLOR)
                                            .setTitle("🎵 Queued " + tokens.length + " URLs")
                                            .setDescription(desc.toString());
                                    event.getHook().sendMessageEmbeds(embed.build()).queue();
                                }
                            }
                        });
            }
            return;
        }

        // Defer the reply immediately — track loading can take several seconds.
        // We intentionally do NOT join the voice channel yet: joining before we know
        // a track exists means the bot sits in the channel with no audio, which
        // causes Discord to close the voice WebSocket almost immediately.
        event.deferReply().queue();

        // Check if index range is specified — use range loading for playlists
        if (!indexStr.isBlank()) {
            int[] range = MusicUtils.parseIndexRange(indexStr);
            musicManager.loadPlaylistWithRange(query, event.getGuild(), range[0], range[1],
                    new MusicManager.MusicLoadCallback() {
                        @Override
                        public void onTrackLoaded(AudioTrack track) {
                            // Track is ready — join first, then the scheduler will start playing
                            if (!joinBeforePlay(musicManager, channel, event))
                                return;
                            GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                            boolean startedPlaying = gmm.getScheduler().queue(track);
                            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                    .setTitle("🎵 Added to Queue")
                                    .setDescription(MusicUtils.formatTrack(track));
                            if (startedPlaying) {
                                embed.addField("Status", "Now playing", true);
                            } else {
                                embed.addField("Position", "#" + gmm.getScheduler().getQueueSize() + " in queue", true);
                            }
                            event.getHook().sendMessageEmbeds(embed.build()).queue();
                        }

                        @Override
                        public void onPlaylistLoaded(AudioPlaylist playlist) {
                            if (!joinBeforePlay(musicManager, channel, event))
                                return;
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
                        public void onPlaylistRangeLoaded(String playlistName, List<AudioTrack> tracks, int start,
                                int end, int total) {
                            if (!joinBeforePlay(musicManager, channel, event))
                                return;
                            GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                            for (AudioTrack track : tracks) {
                                gmm.getScheduler().queue(track);
                            }
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
                                    "No Results", "No matches found for: `" + query + "`")).queue();
                        }

                        @Override
                        public void onLoadFailed(String message) {
                            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                                    "Load Failed", "Failed to load track: " + message)).queue();
                        }
                    });
        } else if (isUrl) {
            // Direct URL — load immediately (YouTube, Spotify, SoundCloud, etc.)
            musicManager.loadAndPlay(query, event.getGuild(),
                    new MusicManager.MusicLoadCallback() {
                        @Override
                        public void onTrackLoaded(AudioTrack track) {
                            // Join voice only now that we know the track resolved successfully
                            if (!joinBeforePlay(musicManager, channel, event))
                                return;
                            GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                            boolean startedPlaying = gmm.getScheduler().queue(track);
                            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                    .setTitle("🎵 Added to Queue")
                                    .setDescription(MusicUtils.formatTrack(track));
                            if (startedPlaying) {
                                embed.addField("Status", "Now playing", true);
                            } else {
                                embed.addField("Position", "#" + (gmm.getScheduler().getQueueSize()) + " in queue",
                                        true);
                            }
                            event.getHook().sendMessageEmbeds(embed.build()).queue();
                        }

                        @Override
                        public void onPlaylistLoaded(AudioPlaylist playlist) {
                            if (!joinBeforePlay(musicManager, channel, event))
                                return;
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
                                    "No Results", "No matches found for: `" + query + "`")).queue();
                        }

                        @Override
                        public void onLoadFailed(String message) {
                            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                                    "Load Failed", "Failed to load track: " + message)).queue();
                        }
                    });
        } else {
            // Text search query — show a selection menu with up to 5 results
            musicManager.loadSearchResults(query, event.getGuild(),
                    new MusicManager.SearchResultsCallback() {
                        @Override
                        public void onSearchResults(List<AudioTrack> tracks) {
                            if (tracks.isEmpty()) {
                                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                                        "No Results", "No matches found for: `" + query + "`")).queue();
                                return;
                            }

                            // If only 1 result, play it directly
                            if (tracks.size() == 1) {
                                AudioTrack track = tracks.get(0);
                                if (!joinBeforePlay(musicManager, channel, event))
                                    return;
                                GuildMusicManager gmm = musicManager.getGuildMusicManager(event.getGuild());
                                boolean startedPlaying = gmm.getScheduler().queue(track);
                                EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.SUCCESS_COLOR)
                                        .setTitle("🎵 Added to Queue")
                                        .setDescription(MusicUtils.formatTrack(track));
                                if (startedPlaying) {
                                    embed.addField("Status", "Now playing", true);
                                } else {
                                    embed.addField("Position", "#" + (gmm.getScheduler().getQueueSize()) + " in queue",
                                            true);
                                }
                                event.getHook().sendMessageEmbeds(embed.build()).queue();
                                return;
                            }

                            // Store search results + voice channel for the select menu callback
                            String key = event.getGuild().getId() + ":" + event.getUser().getId();
                            pendingSearchResults.put(key, new PendingSearch(tracks, channel));

                            // Build the select menu
                            StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("play_search_select")
                                    .setPlaceholder("Choose a song to play")
                                    .setRequiredRange(1, 1);

                            StringBuilder description = new StringBuilder(
                                    "**Search results for:** `" + query + "`\n\n");
                            for (int i = 0; i < tracks.size(); i++) {
                                AudioTrack track = tracks.get(i);
                                String title = track.getInfo().title;
                                String author = track.getInfo().author;
                                String duration = MusicUtils.formatDuration(track.getDuration());
                                // Truncate label to 100 chars (Discord limit)
                                String label = (i + 1) + ". " + title;
                                if (label.length() > 100)
                                    label = label.substring(0, 97) + "...";
                                String desc = author + " [" + duration + "]";
                                if (desc.length() > 100)
                                    desc = desc.substring(0, 97) + "...";

                                menuBuilder.addOption(label, String.valueOf(i), desc);
                                description.append("`").append(i + 1).append(".` **").append(title).append("** — ")
                                        .append(author).append(" `[").append(duration).append("]`\n");
                            }

                            EmbedBuilder embed = EmbedUtils.createEmbedBuilder(EmbedUtils.INFO_COLOR)
                                    .setTitle("🔎 Search Results")
                                    .setDescription(description.toString())
                                    .setFooter("Select a song from the dropdown below • Expires in 30 seconds");

                            event.getHook().sendMessageEmbeds(embed.build())
                                    .addComponents(ActionRow.of(menuBuilder.build()))
                                    .queue(msg -> {
                                        // Auto-expire the search results after 30 seconds
                                        msg.editMessageComponents().queueAfter(30, TimeUnit.SECONDS,
                                                success -> pendingSearchResults.remove(key),
                                                error -> pendingSearchResults.remove(key));
                                    });
                        }

                        @Override
                        public void onNoResults() {
                            event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                                    "No Results", "No matches found for: `" + query + "`")).queue();
                        }
                    });
        }
    }

    /**
     * Join the voice channel only if not already connected.
     * Called from inside a successful load callback so we only join when we have
     * audio to play.
     * 
     * @return true if connected (or already was), false if join failed (error reply
     *         already sent)
     */
    private boolean joinBeforePlay(MusicManager musicManager, AudioChannel channel,
            SlashCommandInteractionEvent event) {
        if (!musicManager.isConnected(event.getGuild())) {
            if (!musicManager.joinChannel(channel)) {
                event.getHook().sendMessageEmbeds(EmbedUtils.createErrorEmbed(
                        "Connection Failed",
                        "Failed to join your voice channel. Check bot permissions.")).queue();
                return false;
            }
        } else {
            // Already connected — make sure we're in the caller's channel
            AudioChannel connected = musicManager.getConnectedChannel(event.getGuild());
            if (connected != null && !connected.getId().equals(channel.getId())) {
                // Move to the requester's channel
                event.getGuild().getAudioManager().openAudioConnection(channel);
            }
        }
        return true;
    }

    public static CommandData getCommandData() {
        return Commands.slash("play", "Search a video/song to play in voicechat.")
                .addOption(OptionType.STRING, "query", "URL, search query, or multiple space-separated URLs", true)
                .addOption(OptionType.STRING, "index", "Index range for playlists (e.g. 4:10, 5:, :9)", false);
    }

    @Override
    public String getName() {
        return "play";
    }

    @Override
    public String getDescription() {
        return "Search a video/song to play in voicechat.";
    }

    @Override
    public CommandCategory getCategory() {
        return CommandCategory.MUSIC;
    }
}
