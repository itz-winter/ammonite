package com.serverbot.listeners;

import com.serverbot.music.MusicManager;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for voice state changes to clean up music state when the bot is
 * disconnected from a voice channel by Discord (kicked, channel deleted, etc.)
 *
 * Without this, stale GuildMusicManager state causes JDA to attempt to
 * re-open the audio connection on every future API call, producing a
 * rapid join/leave loop.
 */
public class VoiceStateListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(VoiceStateListener.class);

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        // Only care about the bot's own voice state changes
        if (!event.getMember().getUser().equals(event.getJDA().getSelfUser())) {
            return;
        }

        Guild guild = event.getGuild();

        // Bot left a channel (channelLeft != null means it was in one before)
        if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            logger.info("Bot was disconnected from voice channel '{}' in guild '{}' — cleaning up music state",
                    event.getChannelLeft().getName(), guild.getName());
            MusicManager.getInstance().cleanupGuild(guild);
        }
    }
}
