package com.serverbot.utils;

import com.serverbot.ServerBot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Utility for sending DMs that respects the per-guild "dmNotifications" toggle.
 * <p>
 * Usage:
 * 
 * <pre>
 * // Normal DM (respects guild toggle):
 * DmUtils.sendDm(guild, user, embed);
 *
 * // Forced DM (always sends — use ONLY for punishment notifications to the
 * // target):
 * DmUtils.sendForcedDm(user, embed);
 * </pre>
 */
public final class DmUtils {

    private static final Logger logger = LoggerFactory.getLogger(DmUtils.class);

    private DmUtils() {
    } // utility class

    /**
     * Check whether DM notifications are enabled for a guild.
     */
    public static boolean areDmsEnabled(String guildId) {
        Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
        Object val = settings.get("dmNotifications");
        // Default to true if not set
        return val == null || Boolean.TRUE.equals(val);
    }

    /**
     * Check whether DM notifications are enabled for a guild.
     */
    public static boolean areDmsEnabled(Guild guild) {
        return areDmsEnabled(guild.getId());
    }

    /**
     * Send a DM to a user, but only if the guild has DM notifications enabled.
     * Silently skips if DMs are disabled for that guild.
     */
    public static void sendDm(Guild guild, User user, MessageEmbed embed) {
        sendDm(guild, user, embed, null, null);
    }

    /**
     * Send a DM to a user with success/failure callbacks, respecting the guild
     * toggle.
     */
    public static void sendDm(Guild guild, User user, MessageEmbed embed,
            Consumer<Void> onSuccess, Consumer<Throwable> onFailure) {
        if (guild != null && !areDmsEnabled(guild)) {
            logger.debug("DM notifications disabled for guild {}, skipping DM to {}", guild.getId(), user.getId());
            return;
        }
        user.openPrivateChannel().queue(dm -> {
            dm.sendMessageEmbeds(embed).queue(
                    msg -> {
                        if (onSuccess != null)
                            onSuccess.accept(null);
                    },
                    err -> {
                        logger.debug("Failed to DM user {}: {}", user.getId(), err.getMessage());
                        if (onFailure != null)
                            onFailure.accept(err);
                    });
        }, err -> {
            logger.debug("Failed to open DM channel for user {}: {}", user.getId(), err.getMessage());
            if (onFailure != null)
                onFailure.accept(err);
        });
    }

    /**
     * Send a DM that ALWAYS sends regardless of guild toggle.
     * Use ONLY for punishment notifications to the punished user.
     */
    public static void sendForcedDm(User user, MessageEmbed embed) {
        sendForcedDm(user, embed, null, null);
    }

    /**
     * Send a forced DM with callbacks.
     */
    public static void sendForcedDm(User user, MessageEmbed embed,
            Consumer<Void> onSuccess, Consumer<Throwable> onFailure) {
        user.openPrivateChannel().queue(dm -> {
            dm.sendMessageEmbeds(embed).queue(
                    msg -> {
                        if (onSuccess != null)
                            onSuccess.accept(null);
                    },
                    err -> {
                        logger.debug("Failed to DM user {}: {}", user.getId(), err.getMessage());
                        if (onFailure != null)
                            onFailure.accept(err);
                    });
        }, err -> {
            logger.debug("Failed to open DM channel for user {}: {}", user.getId(), err.getMessage());
            if (onFailure != null)
                onFailure.accept(err);
        });
    }
}
