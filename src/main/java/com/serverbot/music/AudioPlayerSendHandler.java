package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

/**
 * Bridge between LavaPlayer's AudioPlayer and JDA's AudioSendHandler.
 *
 * KEY DESIGN:
 *  - canProvide() ALWAYS returns true. JDA stops sending packets if this ever returns false,
 *    which causes Discord to close the voice WebSocket (code 4014 / idle timeout), triggering
 *    a reconnect loop that looks like the bot joining and leaving constantly.
 *  - When no track is playing, provide20MsAudio() returns a proper Opus silence frame
 *    (0xF8, 0xFF, 0xFE). An empty ByteBuffer is NOT valid; Discord needs at least one
 *    valid Opus packet to keep the UDP stream alive.
 *  - isOpus() returns true so JDA sends our bytes as-is without re-encoding.
 */
public class AudioPlayerSendHandler implements AudioSendHandler {

    /**
     * Standard single-channel Opus silence frame (20 ms, 48 kHz).
     * This is the exact byte sequence Discord expects when no audio is playing.
     */
    private static final ByteBuffer SILENCE_FRAME = ByteBuffer.wrap(new byte[]{(byte) 0xF8, (byte) 0xFF, (byte) 0xFE});

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
        // MUST always return true — returning false causes JDA to stop the UDP stream,
        // Discord closes the voice connection, and JDA auto-reconnects → infinite loop.
        return true;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        if (lastProvideResult) {
            // LavaPlayer gave us a real Opus-encoded frame
            buffer.flip();
            return buffer;
        }
        // No audio — send a valid Opus silence frame so Discord keeps the connection alive.
        // Rewind each time so the position is always at 0 for the reader.
        return SILENCE_FRAME.duplicate();
    }

    @Override
    public boolean isOpus() {
        // Tell JDA our bytes are already Opus-encoded (both real frames and silence).
        return true;
    }
}
