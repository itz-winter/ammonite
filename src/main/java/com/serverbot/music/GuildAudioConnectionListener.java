package com.serverbot.music;

import net.dv8tion.jda.api.audio.hooks.ConnectionListener;
import net.dv8tion.jda.api.audio.hooks.ConnectionStatus;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Monitors the voice connection status for a single guild and cleans up
 * music state when the bot is permanently disconnected.
 *
 * JDA's {@link ConnectionStatus} has a {@link ConnectionStatus#shouldReconnect()} method:
 * - true  → JDA will auto-reconnect (transient error, region change, session resume, etc.)
 *            — no cleanup needed; just let JDA handle it
 * - false → permanent disconnect (kicked, channel deleted, not-connected, shutdown)
 *            — clean up our music state
 *
 * This is the correct place to handle disconnect cleanup because it gets the exact
 * reason from JDA's internal voice WebSocket, rather than inferring it from the
 * GuildVoiceUpdateEvent stream (which fires for every reconnect attempt and is
 * ambiguous during auto-reconnect sequences).
 */
public class GuildAudioConnectionListener implements ConnectionListener {

    private static final Logger logger = LoggerFactory.getLogger(GuildAudioConnectionListener.class);

    private final Guild guild;

    public GuildAudioConnectionListener(Guild guild) {
        this.guild = guild;
    }

    @Override
    public void onStatusChange(ConnectionStatus status) {
        logger.debug("Voice connection status for '{}': {}", guild.getName(), status);

        if (!status.shouldReconnect()) {
            // Only clean up on statuses that JDA will NOT auto-reconnect from.
            // This excludes ERROR_LOST_CONNECTION, ERROR_CANNOT_RESUME, etc. —
            // those are handled by JDA's auto-reconnect internally.
            // We only act on: NOT_CONNECTED, DISCONNECTED_KICKED_FROM_CHANNEL,
            // DISCONNECTED_CHANNEL_DELETED, DISCONNECTED_REMOVED_FROM_GUILD,
            // DISCONNECTED_LOST_PERMISSION, DISCONNECTED_AUTHENTICATION_FAILURE,
            // DISCONNECTED_REMOVED_DURING_RECONNECT, SHUTTING_DOWN.
            switch (status) {
                case NOT_CONNECTED:
                    // This fires during a normal /leave or bot restart — no-op if already cleaned
                    logger.debug("Voice NOT_CONNECTED for '{}' — state already clean", guild.getName());
                    break;
                case SHUTTING_DOWN:
                    // JDA shutdown — bot is going offline, no need to clean anything
                    logger.debug("Voice SHUTTING_DOWN for '{}' — skipping cleanup", guild.getName());
                    break;
                default:
                    // Genuine permanent disconnect: kicked, channel deleted, lost permission, etc.
                    logger.info("Voice permanently disconnected from '{}' (reason: {}) — cleaning up music state",
                            guild.getName(), status);
                    MusicManager.getInstance().cleanupGuild(guild);
                    break;
            }
        }
    }
}
