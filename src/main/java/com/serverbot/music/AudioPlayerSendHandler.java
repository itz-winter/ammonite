package com.serverbot.music;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;

/**
 * Bridge between LavaPlayer's AudioPlayer and JDA's AudioSendHandler.
 *
 * KEY DESIGN:
 *  - canProvide() ALWAYS returns true. This ensures JDA's DefaultSendSystem continuously
 *    sends UDP packets (real audio or silence), which keeps the voice connection alive
 *    during the initial DAVE handshake and while the bot is idle in a channel.
 *
 *  - When the player is paused or has no track, provide20MsAudio() returns a standard
 *    Opus silence frame (0xF8, 0xFF, 0xFE). This is the minimal valid Opus packet
 *    that represents 20ms of silence.
 *
 *  - RESUME FIX: When the player transitions from paused → playing (or from no-audio
 *    → audio), Discord clients need a short burst of silence frames BEFORE real audio
 *    to reset their Opus decoder state. We inject 5 silence frames (~100ms) on this
 *    transition. Without this, Discord may not decode audio after a pause because the
 *    Opus stream was interrupted mid-frame.
 *
 *  - isOpus() returns true so JDA sends our bytes as-is without re-encoding.
 */
public class AudioPlayerSendHandler implements AudioSendHandler {

    /**
     * Standard Opus silence frame — a valid 20ms Opus packet containing no audio data.
     * Defined by RFC 6716 §3.1 as the minimal Opus packet.
     */
    private static final byte[] SILENCE_BYTES = { (byte) 0xF8, (byte) 0xFF, (byte) 0xFE };

    /**
     * Number of silence frames to inject when transitioning from no-audio → audio.
     * 5 frames = 100ms — enough for Discord clients to reset their Opus decoder.
     */
    private static final int SILENCE_BURST_COUNT = 5;

    private final AudioPlayer audioPlayer;
    private final ByteBuffer buffer;
    private final MutableAudioFrame frame;

    /** Whether the last canProvide() call got real audio from LavaPlayer. */
    private boolean lastProvideResult;

    /**
     * Counts down silence frames to inject after a no-audio → audio transition.
     * When > 0, provide20MsAudio() returns silence even though real audio is available.
     */
    private int silenceBurstRemaining;

    public AudioPlayerSendHandler(AudioPlayer audioPlayer) {
        this.audioPlayer = audioPlayer;
        this.buffer = ByteBuffer.allocate(1024);
        this.frame = new MutableAudioFrame();
        this.frame.setBuffer(buffer);
        this.lastProvideResult = false;
        this.silenceBurstRemaining = 0;
    }

    @Override
    public boolean canProvide() {
        buffer.clear();
        boolean provided = audioPlayer.provide(frame);

        // Detect no-audio → audio transition: if we were NOT providing audio on the
        // previous call but now we are, inject a silence burst so Discord's Opus
        // decoder resets before receiving real audio frames.
        if (provided && !lastProvideResult) {
            silenceBurstRemaining = SILENCE_BURST_COUNT;
        }

        lastProvideResult = provided;
        // Always return true — send silence when there's no real audio to keep
        // the UDP stream alive and the voice connection healthy.
        return true;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        // If we're in a silence burst (transition period), send silence instead of
        // the real audio frame. The real frame is discarded — LavaPlayer will
        // produce the next one on the next canProvide() call.
        if (silenceBurstRemaining > 0) {
            silenceBurstRemaining--;
            return ByteBuffer.wrap(SILENCE_BYTES);
        }

        if (lastProvideResult) {
            // Real audio data was written into buffer by audioPlayer.provide(frame)
            buffer.flip();
            return buffer;
        }

        // No audio available — send standard Opus silence frame
        return ByteBuffer.wrap(SILENCE_BYTES);
    }

    @Override
    public boolean isOpus() {
        // Tell JDA our bytes are already Opus-encoded (both real frames and silence).
        return true;
    }
}
