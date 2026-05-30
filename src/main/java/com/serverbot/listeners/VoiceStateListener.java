package com.serverbot.listeners;

import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logs bot voice state changes for diagnostics.
 *
 * Music state cleanup is handled by
 * {@link com.serverbot.music.GuildAudioConnectionListener},
 * which listens to JDA's internal ConnectionStatus and uses
 * {@link net.dv8tion.jda.api.audio.hooks.ConnectionStatus#shouldReconnect()} to
 * distinguish
 * transient reconnects from genuine permanent disconnects. Do NOT add cleanup
 * logic here —
 * GuildVoiceUpdateEvent fires for every auto-reconnect attempt and cannot
 * reliably
 * distinguish "bot was kicked" from "bot is reconnecting to a new voice
 * server".
 */
public class VoiceStateListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(VoiceStateListener.class);

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        // Only log the bot's own voice state changes
        if (!event.getMember().getUser().equals(event.getJDA().getSelfUser())) {
            return;
        }

        if (event.getChannelJoined() != null && event.getChannelLeft() == null) {
            logger.debug("Bot joined voice channel '{}' in '{}'",
                    event.getChannelJoined().getName(), event.getGuild().getName());
        } else if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            logger.debug("Bot left voice channel '{}' in '{}'",
                    event.getChannelLeft().getName(), event.getGuild().getName());
        } else if (event.getChannelLeft() != null && event.getChannelJoined() != null) {
            logger.debug("Bot moved from '{}' to '{}' in '{}'",
                    event.getChannelLeft().getName(), event.getChannelJoined().getName(),
                    event.getGuild().getName());
        }
    }
}
