package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;

/**
 * Holder for both the AudioPlayer and TrackScheduler for a single guild.
 * Each guild gets its own instance to allow independent music playback.
 */
public class GuildMusicManager {

    private final AudioPlayer player;
    private final TrackScheduler scheduler;
    private final AudioPlayerSendHandler sendHandler;

    public GuildMusicManager(AudioPlayerManager manager) {
        this.player = manager.createPlayer();
        this.scheduler = new TrackScheduler(player);
        this.player.addListener(scheduler);
        this.sendHandler = new AudioPlayerSendHandler(player);
    }

    public AudioPlayer getPlayer() {
        return player;
    }

    public TrackScheduler getScheduler() {
        return scheduler;
    }

    public AudioPlayerSendHandler getSendHandler() {
        return sendHandler;
    }

    /**
     * Stop playback, clear queue, and clean up resources.
     */
    public void destroy() {
        scheduler.clearQueue();
        player.stopTrack();
        player.destroy();
    }
}
