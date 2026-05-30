package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.container.mp3.Mp3AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import com.sedmelluq.discord.lavaplayer.tools.io.PersistentHttpStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

/**
 * Audio track for Newgrounds audio.
 * Streams the MP3 directly from Newgrounds' CDN using LavaPlayer's MP3
 * container.
 */
public class NewgroundsAudioTrack extends DelegatedAudioTrack {

    private static final Logger logger = LoggerFactory.getLogger(NewgroundsAudioTrack.class);

    private final String mp3Url;
    private final NewgroundsAudioSourceManager sourceManager;

    public NewgroundsAudioTrack(AudioTrackInfo trackInfo, String mp3Url, NewgroundsAudioSourceManager sourceManager) {
        super(trackInfo);
        this.mp3Url = mp3Url;
        this.sourceManager = sourceManager;
    }

    public String getMp3Url() {
        return mp3Url;
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        logger.debug("Processing Newgrounds track: {} -> {}", trackInfo.title, mp3Url);

        try (HttpInterface httpInterface = sourceManager.getHttpInterface()) {
            try (PersistentHttpStream stream = new PersistentHttpStream(httpInterface, new URI(mp3Url), null)) {
                // Newgrounds audio files are always MP3, so use the MP3 probe directly.
                // This avoids container detection overhead and handles the stream correctly.
                processDelegate(new Mp3AudioTrack(trackInfo, stream), executor);
            }
        }
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new NewgroundsAudioTrack(trackInfo, mp3Url, sourceManager);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
