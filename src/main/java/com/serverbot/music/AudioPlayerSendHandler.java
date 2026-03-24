package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

/**
 * Bridge between LavaPlayer's AudioPlayer and JDA's AudioSendHandler.
 * This allows LavaPlayer audio output to be sent through JDA's voice connection.
 *
 * IMPORTANT: canProvide() always returns true so that JDA keeps sending audio packets
 * to Discord's voice server. When no track is playing, silence (empty Opus frame) is sent.
 * If canProvide() returned false when idle, Discord would receive no packets and
 * disconnect the bot after a timeout, causing a join/leave reconnect loop.
 */
public class AudioPlayerSendHandler implements AudioSendHandler {

    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;
    private boolean lastProvideResult;

    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        this.buffer = ByteBuffer.allocate(1024);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
        this.lastProvideResult = false;
    }

    @Override
    public boolean canProvide() {
        buffer.clear();
        lastProvideResult = audioPlayer.provide(frame);
        // Always return true to keep the voice connection alive.
        // When no audio is available, provide20MsAudio sends silence.
        return true;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        if (lastProvideResult) {
            buffer.flip();
            return buffer;
        }
        // No audio data available — return empty buffer (silence)
        buffer.clear();
        buffer.flip();
        return buffer;
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
