package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.managers.AudioManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central manager for all music playback across guilds.
 * Handles audio source registration, guild manager lifecycle, and voice connections.
 */
public class MusicManager {

    private static final Logger logger = LoggerFactory.getLogger(MusicManager.class);
    private static MusicManager instance;

    private final AudioPlayerManager playerManager;
    private final Map<Long, GuildMusicManager> musicManagers;

    private MusicManager() {
        this.playerManager = new DefaultAudioPlayerManager();
        this.musicManagers = new ConcurrentHashMap<>();

        // Register all remote sources (YouTube, SoundCloud, Bandcamp, Vimeo, Twitch, HTTP)
        AudioSourceManagers.registerRemoteSources(playerManager);
        // Register local file source
        AudioSourceManagers.registerLocalSource(playerManager);

        logger.info("MusicManager initialized with all audio sources");
    }

    public static synchronized MusicManager getInstance() {
        if (instance == null) {
            instance = new MusicManager();
        }
        return instance;
    }

    /**
     * Get or create the GuildMusicManager for a guild.
     */
    public synchronized GuildMusicManager getGuildMusicManager(Guild guild) {
        long guildId = guild.getIdLong();
        GuildMusicManager musicManager = musicManagers.get(guildId);

        if (musicManager == null) {
            musicManager = new GuildMusicManager(playerManager);
            musicManagers.put(guildId, musicManager);
        }

        guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
        return musicManager;
    }

    /**
     * Join a voice channel.
     * @return true if successfully joined or already in the channel
     */
    public boolean joinChannel(AudioChannel channel) {
        AudioManager audioManager = channel.getGuild().getAudioManager();
        try {
            audioManager.openAudioConnection(channel);
            return true;
        } catch (Exception e) {
            logger.error("Failed to join voice channel: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Leave the voice channel in a guild.
     */
    public void leaveChannel(Guild guild) {
        AudioManager audioManager = guild.getAudioManager();
        audioManager.closeAudioConnection();

        // Clean up the music manager
        GuildMusicManager musicManager = musicManagers.remove(guild.getIdLong());
        if (musicManager != null) {
            musicManager.destroy();
        }
    }

    /**
     * Check if the bot is connected to a voice channel in the guild.
     */
    public boolean isConnected(Guild guild) {
        return guild.getAudioManager().isConnected();
    }

    /**
     * Get the voice channel the bot is currently in for a guild.
     */
    public AudioChannel getConnectedChannel(Guild guild) {
        return guild.getAudioManager().getConnectedChannel();
    }

    /**
     * Load and play a track or playlist.
     * @param query URL or search query
     * @param guild the guild to play in
     * @param callback callback for result handling
     */
    public void loadAndPlay(String query, Guild guild, MusicLoadCallback callback) {
        GuildMusicManager musicManager = getGuildMusicManager(guild);

        // If not a URL, prefix with ytsearch: for YouTube search
        String identifier = query;
        if (!query.startsWith("http://") && !query.startsWith("https://")) {
            identifier = "ytsearch:" + query;
        }

        playerManager.loadItemOrdered(musicManager, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                callback.onTrackLoaded(track);
                musicManager.getScheduler().queue(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    // Search result — play the first result
                    AudioTrack track = playlist.getTracks().get(0);
                    callback.onTrackLoaded(track);
                    musicManager.getScheduler().queue(track);
                } else {
                    // Actual playlist
                    callback.onPlaylistLoaded(playlist);
                }
            }

            @Override
            public void noMatches() {
                callback.onNoMatches();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                callback.onLoadFailed(exception.getMessage());
            }
        });
    }

    /**
     * Load a playlist with index range selection, then queue tracks.
     * @param query URL of the playlist
     * @param guild the guild
     * @param startIndex 1-based start index (inclusive)
     * @param endIndex 1-based end index (inclusive), -1 for end
     * @param callback result callback
     */
    public void loadPlaylistWithRange(String query, Guild guild, int startIndex, int endIndex, MusicLoadCallback callback) {
        GuildMusicManager musicManager = getGuildMusicManager(guild);

        playerManager.loadItemOrdered(musicManager, query, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                // Single track, just queue it
                callback.onTrackLoaded(track);
                musicManager.getScheduler().queue(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    AudioTrack track = playlist.getTracks().get(0);
                    callback.onTrackLoaded(track);
                    musicManager.getScheduler().queue(track);
                    return;
                }

                List<AudioTrack> allTracks = playlist.getTracks();
                int size = allTracks.size();

                // Convert 1-based to 0-based, clamp to valid range
                int start = Math.max(0, startIndex - 1);
                int end = (endIndex <= 0 || endIndex > size) ? size : endIndex;

                if (start >= size) {
                    callback.onNoMatches();
                    return;
                }

                List<AudioTrack> selectedTracks = allTracks.subList(start, end);
                List<AudioTrack> queuedTracks = new ArrayList<>(selectedTracks);

                for (AudioTrack track : queuedTracks) {
                    musicManager.getScheduler().queue(track);
                }

                callback.onPlaylistRangeLoaded(playlist.getName(), queuedTracks, start + 1, end, size);
            }

            @Override
            public void noMatches() {
                callback.onNoMatches();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                callback.onLoadFailed(exception.getMessage());
            }
        });
    }

    public AudioPlayerManager getPlayerManager() {
        return playerManager;
    }

    /**
     * Callback interface for music loading results.
     */
    public interface MusicLoadCallback {
        void onTrackLoaded(AudioTrack track);
        void onPlaylistLoaded(AudioPlaylist playlist);
        default void onPlaylistRangeLoaded(String playlistName, List<AudioTrack> tracks, int start, int end, int total) {
            // Default: treat as full playlist load — override for range-specific behavior
        }
        void onNoMatches();
        void onLoadFailed(String message);
    }
}
