package com.serverbot.utils;

import com.serverbot.ServerBot;
import com.serverbot.listeners.AutoLogListener;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Utility class for sending moderation actions to AutoLog channels
 */
public class AutoLogUtils {

    // Import custom emojis for consistent formatting
    private static final String EMOJI_BAN = CustomEmojis.MOD_BAN;
    private static final String EMOJI_KICK = CustomEmojis.WARN;
    private static final String EMOJI_MUTE = CustomEmojis.WARN;
    private static final String EMOJI_TIMEOUT = CustomEmojis.WARN;
    private static final String EMOJI_WARN = CustomEmojis.WARN;
    private static final String EMOJI_UNBAN = CustomEmojis.SUCCESS;
    private static final String EMOJI_UNMUTE = CustomEmojis.SUCCESS;
    private static final String EMOJI_UNWARN = CustomEmojis.SUCCESS;

    private static final Logger logger = LoggerFactory.getLogger(AutoLogUtils.class);

    /**
     * Log a ban action to the AutoLog channel
     */
    public static void logBan(Guild guild, User targetUser, User moderator, String reason, Duration duration) {
        if (!isAutoLogEnabled(guild.getId(), "moderation", "bans")) {
            return;
        }

        TextChannel logChannel = getLogChannel(guild, "moderation", "bans");
        if (logChannel == null)
            return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle(EMOJI_BAN + " User Banned")
                .setThumbnail(targetUser.getAvatarUrl())
                .addField("User", targetUser.getAsMention(), true)
                .addField("Username", targetUser.getName(), true)
                .addField("Moderator", moderator.getAsMention(), true)
                .addField("Reason", reason, false)
                .setFooter("User ID: " + targetUser.getId() + " | Moderator ID: " + moderator.getId())
                .setTimestamp(OffsetDateTime.now());

        if (duration != null) {
            embed.addField("Duration", formatDuration(duration), true);
        } else {
            embed.addField("Duration", "Permanent", true);
        }

        logChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    AutoLogListener.trackLogMessage(success.getId());
                    logger.debug("Logged ban for user {} to AutoLog channel", targetUser.getId());
                },
                error -> logger.warn("Failed to log ban to AutoLog channel: {}", error.getMessage()));
    }

    /**
     * Log a kick action to the AutoLog channel
     */
    public static void logKick(Guild guild, User targetUser, User moderator, String reason) {
        if (!isAutoLogEnabled(guild.getId(), "moderation", "kicks")) {
            return;
        }

        TextChannel logChannel = getLogChannel(guild, "moderation", "kicks");
        if (logChannel == null)
            return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle(EMOJI_KICK + " User Kicked")
                .setThumbnail(targetUser.getAvatarUrl())
                .addField("User", targetUser.getAsMention(), true)
                .addField("Username", targetUser.getName(), true)
                .addField("Moderator", moderator.getAsMention(), true)
                .addField("Reason", reason, false)
                .setFooter("User ID: " + targetUser.getId() + " | Moderator ID: " + moderator.getId())
                .setTimestamp(OffsetDateTime.now());

        logChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    AutoLogListener.trackLogMessage(success.getId());
                    logger.debug("Logged kick for user {} to AutoLog channel", targetUser.getId());
                },
                error -> logger.warn("Failed to log kick to AutoLog channel: {}", error.getMessage()));
    }

    /**
     * Log a mute action to the AutoLog channel
     */
    public static void logMute(Guild guild, User targetUser, User moderator, String reason, Duration duration) {
        if (!isAutoLogEnabled(guild.getId(), "moderation", "mutes")) {
            return;
        }

        TextChannel logChannel = getLogChannel(guild, "moderation", "mutes");
        if (logChannel == null)
            return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GRAY)
                .setTitle(EMOJI_MUTE + " User Muted")
                .setThumbnail(targetUser.getAvatarUrl())
                .addField("User", targetUser.getAsMention(), true)
                .addField("Username", targetUser.getName(), true)
                .addField("Moderator", moderator.getAsMention(), true)
                .addField("Duration", formatDuration(duration), true)
                .addField("Reason", reason, false)
                .setFooter("User ID: " + targetUser.getId() + " | Moderator ID: " + moderator.getId())
                .setTimestamp(OffsetDateTime.now());

        logChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    AutoLogListener.trackLogMessage(success.getId());
                    logger.debug("Logged mute for user {} to AutoLog channel", targetUser.getId());
                },
                error -> logger.warn("Failed to log mute to AutoLog channel: {}", error.getMessage()));
    }

    /**
     * Log a timeout action to the AutoLog channel
     */
    public static void logTimeout(Guild guild, User targetUser, User moderator, String reason, Duration duration) {
        if (!isAutoLogEnabled(guild.getId(), "moderation", "mutes")) { // Use same setting as mutes
            return;
        }

        TextChannel logChannel = getLogChannel(guild, "moderation", "mutes");
        if (logChannel == null)
            return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle(EMOJI_TIMEOUT + " User Timed Out")
                .setThumbnail(targetUser.getAvatarUrl())
                .addField("User", targetUser.getAsMention(), true)
                .addField("Username", targetUser.getName(), true)
                .addField("Moderator", moderator.getAsMention(), true)
                .addField("Duration", formatDuration(duration), true)
                .addField("Reason", reason, false)
                .setFooter("User ID: " + targetUser.getId() + " | Moderator ID: " + moderator.getId())
                .setTimestamp(OffsetDateTime.now());

        logChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    AutoLogListener.trackLogMessage(success.getId());
                    logger.debug("Logged timeout for user {} to AutoLog channel", targetUser.getId());
                },
                error -> logger.warn("Failed to log timeout to AutoLog channel: {}", error.getMessage()));
    }

    /**
     * Log a warn action to the AutoLog channel
     */
    public static void logWarn(Guild guild, User targetUser, User moderator, String reason) {
        if (!isAutoLogEnabled(guild.getId(), "moderation", "warns")) {
            return;
        }

        TextChannel logChannel = getLogChannel(guild, "moderation", "warns");
        if (logChannel == null)
            return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle(EMOJI_WARN + " User Warned")
                .setThumbnail(targetUser.getAvatarUrl())
                .addField("User", targetUser.getAsMention(), true)
                .addField("Username", targetUser.getName(), true)
                .addField("Moderator", moderator.getAsMention(), true)
                .addField("Reason", reason, false)
                .setFooter("User ID: " + targetUser.getId() + " | Moderator ID: " + moderator.getId())
                .setTimestamp(OffsetDateTime.now());

        logChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    AutoLogListener.trackLogMessage(success.getId());
                    logger.debug("Logged warning for user {} to AutoLog channel", targetUser.getId());
                },
                error -> logger.warn("Failed to log warning to AutoLog channel: {}", error.getMessage()));
    }

    /**
     * Log an unban action to the AutoLog channel
     */
    public static void logUnban(Guild guild, User targetUser, User moderator, String reason) {
        if (!isAutoLogEnabled(guild.getId(), "moderation", "bans")) {
            return;
        }

        TextChannel logChannel = getLogChannel(guild, "moderation", "bans");
        if (logChannel == null)
            return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle(EMOJI_UNBAN + " User Unbanned")
                .setThumbnail(targetUser.getAvatarUrl())
                .addField("User", targetUser.getAsMention(), true)
                .addField("Username", targetUser.getName(), true)
                .addField("Moderator", moderator.getAsMention(), true)
                .addField("Reason", reason, false)
                .setFooter("User ID: " + targetUser.getId() + " | Moderator ID: " + moderator.getId())
                .setTimestamp(OffsetDateTime.now());

        logChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    AutoLogListener.trackLogMessage(success.getId());
                    logger.debug("Logged unban for user {} to AutoLog channel", targetUser.getId());
                },
                error -> logger.warn("Failed to log unban to AutoLog channel: {}", error.getMessage()));
    }

    /**
     * Log an unmute action to the AutoLog channel
     */
    public static void logUnmute(Guild guild, User targetUser, User moderator, String reason) {
        if (!isAutoLogEnabled(guild.getId(), "moderation", "mutes")) {
            return;
        }

        TextChannel logChannel = getLogChannel(guild, "moderation", "mutes");
        if (logChannel == null)
            return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle(EMOJI_UNMUTE + " User Unmuted")
                .setThumbnail(targetUser.getAvatarUrl())
                .addField("User", targetUser.getAsMention(), true)
                .addField("Username", targetUser.getName(), true)
                .addField("Moderator", moderator.getAsMention(), true)
                .addField("Reason", reason, false)
                .setFooter("User ID: " + targetUser.getId() + " | Moderator ID: " + moderator.getId())
                .setTimestamp(OffsetDateTime.now());

        logChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    AutoLogListener.trackLogMessage(success.getId());
                    logger.debug("Logged unmute for user {} to AutoLog channel", targetUser.getId());
                },
                error -> logger.warn("Failed to log unmute to AutoLog channel: {}", error.getMessage()));
    }

    /**
     * Log an unwarn action to the AutoLog channel
     */
    public static void logUnwarn(Guild guild, User targetUser, User moderator, String reason,
            String originalWarningReason) {
        if (!isAutoLogEnabled(guild.getId(), "moderation", "warns")) {
            return;
        }

        TextChannel logChannel = getLogChannel(guild, "moderation", "warns");
        if (logChannel == null)
            return;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle(EMOJI_UNWARN + " Warning Removed")
                .setThumbnail(targetUser.getAvatarUrl())
                .addField("User", targetUser.getAsMention(), true)
                .addField("Username", targetUser.getName(), true)
                .addField("Moderator", moderator.getAsMention(), true)
                .addField("Reason for Removal", reason, false)
                .addField("Original Warning", originalWarningReason, false)
                .setFooter("User ID: " + targetUser.getId() + " | Moderator ID: " + moderator.getId())
                .setTimestamp(OffsetDateTime.now());

        logChannel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    AutoLogListener.trackLogMessage(success.getId());
                    logger.debug("Logged unwarn for user {} to AutoLog channel", targetUser.getId());
                },
                error -> logger.warn("Failed to log unwarn to AutoLog channel: {}", error.getMessage()));
    }

    /**
     * Checks if auto-logging is enabled for a specific event type
     */
    private static boolean isAutoLogEnabled(String guildId, String action, String actionType) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Check for specific setting first (e.g., "autoLog_moderation_bans")
            if (actionType != null) {
                String specificKey = "autoLog_" + action.toLowerCase() + "_" + actionType.toLowerCase();
                Boolean specificSetting = (Boolean) settings.get(specificKey);
                if (specificSetting != null) {
                    return specificSetting;
                }
            }

            // Check for general setting (e.g., "autoLog_moderation")
            String generalKey = "autoLog_" + action.toLowerCase();
            Boolean generalSetting = (Boolean) settings.get(generalKey);
            if (generalSetting != null) {
                return generalSetting;
            }

            // Check for "all events" setting
            Boolean allSetting = (Boolean) settings.get("autoLog_all");
            if (allSetting != null) {
                return allSetting;
            }

            return false; // Default to disabled

        } catch (Exception e) {
            logger.warn("Error checking auto-log settings for guild {}: {}", guildId, e.getMessage());
            return false;
        }
    }

    /**
     * Gets the configured log channel for a guild.
     *
     * This method checks multiple storage keys for backward compatibility and
     * for the newer per-type keys that the `/logging` and `/logchannel` commands
     * may write:
     * 1. Per-subtype key set by `LogChannelCommand`: `logChannel_<actionType>`
     * 2. Broad key set by `LoggingCommand`: `<action>_log_channel` (e.g.
     * `moderation_log_channel`)
     * 3. Per-action key older format: `logChannel_<action>`
     * 4. Legacy fallback: `logChannelId`
     */
    private static TextChannel getLogChannel(Guild guild, String action, String actionType) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guild.getId());
            String channelId = null;

            // 1) Per-subtype (e.g. "logChannel_bans") created by LogChannelCommand
            if (actionType != null) {
                Object v = settings.get("logChannel_" + actionType);
                if (v instanceof String)
                    channelId = (String) v;
            }

            // 2) Broad action key (e.g. "moderation_log_channel") set by LoggingCommand
            if (channelId == null) {
                Object v = settings.get(action + "_log_channel");
                if (v instanceof String)
                    channelId = (String) v;
            }

            // 3) Older per-action key (e.g. "logChannel_moderation")
            if (channelId == null) {
                Object v = settings.get("logChannel_" + action);
                if (v instanceof String)
                    channelId = (String) v;
            }

            // 4) Legacy fallback key (logChannelId)
            if (channelId == null) {
                Object v = settings.get("logChannelId");
                if (v instanceof String)
                    channelId = (String) v;
            }

            if (channelId == null)
                return null;

            TextChannel logChannel = guild.getTextChannelById(channelId);
            if (logChannel == null || !logChannel.canTalk()) {
                return null;
            }

            return logChannel;

        } catch (Exception e) {
            logger.warn("Error getting log channel for guild {}: {}", guild.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Format a duration for display
     */
    private static String formatDuration(Duration duration) {
        if (duration == null)
            return "Unknown";

        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
