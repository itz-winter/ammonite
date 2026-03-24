package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Manages the audio track queue and playback scheduling for a guild.
 * Handles queue operations, repeat modes, and shuffle functionality.
 */
public class TrackScheduler extends AudioEventAdapter {

    private final AudioPlayer player;
    private final Queue<AudioTrack> queue;
    private boolean repeating = false;
    private boolean shuffling = false;
    private AudioTrack currentTrack;

    public TrackScheduler(AudioPlayer player) {
        this.player = player;
        this.queue = new LinkedList<>();
    }

    /**
     * Add a track to the queue, or start playing if nothing is currently playing.
     */
    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.offer(track);
        } else {
            currentTrack = track;
        }
    }

    /**
     * Start the next track, stopping the current one if playing.
     */
    public void nextTrack() {
        AudioTrack next;
        if (shuffling && queue.size() > 1) {
            List<AudioTrack> trackList = new ArrayList<>(queue);
            Collections.shuffle(trackList);
            queue.clear();
            queue.addAll(trackList);
        }
        next = queue.poll();
        if (next != null) {
            player.startTrack(next, false);
            currentTrack = next;
        } else {
            player.startTrack(null, false);
            currentTrack = null;
        }
    }

    /**
     * Skip a number of tracks.
     * @param count number of tracks to skip (1 = skip current)
     * @return the number of tracks actually skipped
     */
    public int skip(int count) {
        int skipped = 0;
        // Skip count-1 from the queue, then call nextTrack for the final one
        for (int i = 0; i < count - 1 && !queue.isEmpty(); i++) {
            queue.poll();
            skipped++;
        }
        nextTrack();
        skipped++; // Count the current track skip
        return skipped;
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason.mayStartNext) {
            if (repeating) {
                player.startTrack(track.makeClone(), false);
                currentTrack = track.makeClone();
            } else {
                nextTrack();
            }
        }
    }

    /**
     * Get the current queue as a list.
     */
    public List<AudioTrack> getQueue() {
        return new ArrayList<>(queue);
    }

    /**
     * Get the currently playing track.
     */
    public AudioTrack getCurrentTrack() {
        return player.getPlayingTrack();
    }

    /**
     * Clear the queue.
     */
    public void clearQueue() {
        queue.clear();
    }

    /**
     * Get the queue size.
     */
    public int getQueueSize() {
        return queue.size();
    }

    public boolean isRepeating() {
        return repeating;
    }

    public void setRepeating(boolean repeating) {
        this.repeating = repeating;
    }

    public boolean isShuffling() {
        return shuffling;
    }

    public void setShuffling(boolean shuffling) {
        this.shuffling = shuffling;
    }

    public AudioPlayer getPlayer() {
        return player;
    }
}
