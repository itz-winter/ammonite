package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
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

        // Register the working YouTube source from youtube-source plugin (dev.lavalink.youtube).
        // This replaces LavaPlayer's broken built-in YouTube source which YouTube blocks.
        // allowSearch=true enables ytsearch: queries, allowDirectVideoIds/PlaylistIds
        // enables direct URL resolution.
        YoutubeAudioSourceManager ytSourceManager = new YoutubeAudioSourceManager(
                /*allowSearch=*/ true,
                /*allowDirectVideoIds=*/ true,
                /*allowDirectPlaylistIds=*/ true
        );
        playerManager.registerSourceManager(ytSourceManager);
        logger.info("Registered youtube-source plugin (dev.lavalink.youtube) with search enabled");

        // Register all other remote sources EXCEPT the broken built-in YouTube source.
        // This keeps SoundCloud, Bandcamp, Vimeo, Twitch, and HTTP sources working.
        AudioSourceManagers.registerRemoteSources(playerManager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);

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
            // Only set the handler when first creating — resetting it on every call
            // can interrupt an active audio connection.
            guild.getAudioManager().setSendingHandler(musicManager.getSendHandler());
        }

        return musicManager;
    }

    /**
     * Join a voice channel.
     * @return true if successfully joined or already in the channel
     */
    public boolean joinChannel(AudioChannel channel) {
        Guild guild = channel.getGuild();
        AudioManager audioManager = guild.getAudioManager();
        try {
            // Ensure the sending handler is set BEFORE opening the connection,
            // otherwise JDA will immediately disconnect due to no audio handler.
            GuildMusicManager musicManager = getGuildMusicManager(guild);
            audioManager.setSendingHandler(musicManager.getSendHandler());
            audioManager.setSelfDeafened(true);
            // Keep auto-reconnect ENABLED. Discord's voice gateway frequently sends
            // a close-and-reconnect instruction (code 4014/4015) during the initial
            // handshake, telling the client to reconnect to a different voice server.
            // With auto-reconnect disabled, JDA would give up on the first close,
            // causing the bot to immediately leave after joining. Our silence-frame
            // AudioPlayerSendHandler keeps the UDP stream alive, so a reconnect loop
            // will NOT occur even with auto-reconnect on.
            audioManager.setAutoReconnect(true);
            // Use JDA's ConnectionListener to detect permanent disconnects (kicked,
            // channel deleted, etc.) rather than watching GuildVoiceUpdateEvents.
            // ConnectionStatus.shouldReconnect() correctly distinguishes transient
            // errors (where JDA retries) from permanent ones (where we must clean up).
            audioManager.setConnectionListener(new GuildAudioConnectionListener(guild));
            audioManager.openAudioConnection(channel);
            logger.info("Opened audio connection to channel: {} in guild: {}", channel.getName(), guild.getName());
            return true;
        } catch (Exception e) {
            logger.error("Failed to join voice channel: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Leave the voice channel in a guild.
     */
    public void leaveChannel(Guild guild) {
        AudioManager audioManager = guild.getAudioManager();
        audioManager.closeAudioConnection();
        cleanupGuild(guild);
    }

    /**
     * Clean up all music state for a guild without closing the audio connection.
     * Called when the bot is disconnected externally (kicked, channel deleted, etc.)
     */
    public synchronized void cleanupGuild(Guild guild) {
        GuildMusicManager musicManager = musicManagers.remove(guild.getIdLong());
        if (musicManager != null) {
            musicManager.destroy();
            logger.info("Cleaned up music state for guild: {}", guild.getName());
        }
        // Clear the sending handler so JDA doesn't try to reuse a destroyed player
        guild.getAudioManager().setSendingHandler(null);
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

        // Determine the identifier to search/load
        String identifier = query;
        boolean isSearch = false;
        if (!query.startsWith("http://") && !query.startsWith("https://")) {
            identifier = "ytsearch:" + query;
            isSearch = true;
        }

        final boolean isSearchQuery = isSearch;
        final String originalQuery = query;

        logger.info("Loading track: {} (search={})", identifier, isSearch);

        playerManager.loadItemOrdered(musicManager, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                logger.info("Track loaded: {} by {}", track.getInfo().title, track.getInfo().author);
                // Notify the callback only — queuing is the caller's responsibility so it
                // can join the voice channel first before starting playback.
                callback.onTrackLoaded(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    if (playlist.getTracks().isEmpty()) {
                        logger.warn("Search returned empty results for: {}", originalQuery);
                        callback.onNoMatches();
                        return;
                    }
                    AudioTrack track = playlist.getTracks().get(0);
                    logger.info("Search result loaded: {} by {}", track.getInfo().title, track.getInfo().author);
                    // Treat the top search result as a single track load
                    callback.onTrackLoaded(track);
                } else {
                    logger.info("Playlist loaded: {} with {} tracks", playlist.getName(), playlist.getTracks().size());
                    callback.onPlaylistLoaded(playlist);
                }
            }

            @Override
            public void noMatches() {
                // If YouTube search found nothing, try SoundCloud as fallback
                if (isSearchQuery) {
                    logger.info("YouTube search found no matches, trying SoundCloud for: {}", originalQuery);
                    String scIdentifier = "scsearch:" + originalQuery;
                    playerManager.loadItemOrdered(musicManager, scIdentifier, new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack track) {
                            logger.info("SoundCloud track loaded: {}", track.getInfo().title);
                            callback.onTrackLoaded(track);
                        }
                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {
                            if (playlist.isSearchResult() && !playlist.getTracks().isEmpty()) {
                                AudioTrack track = playlist.getTracks().get(0);
                                logger.info("SoundCloud search result: {}", track.getInfo().title);
                                callback.onTrackLoaded(track);
                            } else {
                                callback.onNoMatches();
                            }
                        }
                        @Override
                        public void noMatches() {
                            logger.warn("No matches found on YouTube or SoundCloud for: {}", originalQuery);
                            callback.onNoMatches();
                        }
                        @Override
                        public void loadFailed(FriendlyException exception) {
                            logger.warn("SoundCloud fallback also failed for: {} - {}", originalQuery, exception.getMessage());
                            callback.onNoMatches();
                        }
                    });
                } else {
                    logger.warn("No matches found for URL: {}", originalQuery);
                    callback.onNoMatches();
                }
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                logger.error("Failed to load track: {} - {}", originalQuery, exception.getMessage());
                // If YouTube load failed on a search, try SoundCloud as fallback
                if (isSearchQuery) {
                    logger.info("YouTube load failed, trying SoundCloud for: {}", originalQuery);
                    String scIdentifier = "scsearch:" + originalQuery;
                    playerManager.loadItemOrdered(musicManager, scIdentifier, new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack track) {
                            callback.onTrackLoaded(track);
                        }
                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {
                            if (playlist.isSearchResult() && !playlist.getTracks().isEmpty()) {
                                AudioTrack track = playlist.getTracks().get(0);
                                callback.onTrackLoaded(track);
                            } else {
                                callback.onLoadFailed(exception.getMessage());
                            }
                        }
                        @Override
                        public void noMatches() {
                            callback.onLoadFailed(exception.getMessage());
                        }
                        @Override
                        public void loadFailed(FriendlyException scException) {
                            callback.onLoadFailed(exception.getMessage());
                        }
                    });
                } else {
                    callback.onLoadFailed(exception.getMessage());
                }
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
                // Single track — let the caller queue it after joining voice
                callback.onTrackLoaded(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.isSearchResult()) {
                    AudioTrack track = playlist.getTracks().get(0);
                    callback.onTrackLoaded(track);
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

                // Let the caller queue after joining voice
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
