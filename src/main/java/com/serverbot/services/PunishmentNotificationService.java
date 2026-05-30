package com.serverbot.services;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.serverbot.ServerBot;
import com.serverbot.utils.EmbedUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for handling DM notifications for punishment actions
 * Supports configurable DM messages with appeal buttons
 */
public class PunishmentNotificationService {

    private static PunishmentNotificationService instance;
    private final Gson gson = new Gson();
    private final File dataDir;

    // Error codes for punishment notifications (300-600 series)
    public static final String PN01_GUILD_NOT_FOUND = "300";
    public static final String PN02_USER_NOT_FOUND = "300";
    public static final String PN03_DM_DISABLED = "600";
    public static final String PN04_DM_FAILED = "400";
    public static final String PN05_APPEAL_CHANNEL_NOT_FOUND = "300";
    public static final String PN06_CONFIG_ERROR = "400";
    public static final String PN07_INVALID_PUNISHMENT_TYPE = "101";
    public static final String PN08_PERMISSION_DENIED = "200";
    public static final String PN09_SETTINGS_SAVE_FAILED = "400";
    public static final String PN10_SETTINGS_LOAD_FAILED = "400";

    private PunishmentNotificationService() {
        this.dataDir = new File("data/punishment_notifications");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    public static synchronized PunishmentNotificationService getInstance() {
        if (instance == null) {
            instance = new PunishmentNotificationService();
        }
        return instance;
    }

    /**
     * Send DM notification for a punishment action
     */
    public CompletableFuture<Boolean> sendPunishmentNotification(String guildId, String userId,
            PunishmentType type, String reason, Duration duration, String moderatorName) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                PunishmentDMSettings settings = getDMSettings(guildId);
                if (!settings.isEnabled()) {
                    return false; // DM notifications disabled
                }

                Guild guild = ServerBot.getJda().getGuildById(guildId);
                if (guild == null) {
                    ServerBot.getLogger().error("Guild not found for punishment notification: {}", guildId);
                    return false;
                }

                User user = ServerBot.getJda().getUserById(userId);
                if (user == null) {
                    ServerBot.getLogger().error("User not found for punishment notification: {}", userId);
                    return false;
                }

                return sendDMNotification(guild, user, type, reason, duration, moderatorName, settings);

            } catch (Exception e) {
                ServerBot.getLogger().error("Error sending punishment notification: {}", e.getMessage(), e);
                return false;
            }
        });
    }

    /**
     * Send the actual DM notification
     */
    private boolean sendDMNotification(Guild guild, User user, PunishmentType type, String reason,
            Duration duration, String moderatorName, PunishmentDMSettings settings) {

        try {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(getColorForPunishment(type))
                    .setTitle("🚨 " + type.getDisplayName() + " Notification")
                    .setDescription(buildNotificationMessage(guild, type, reason, duration, moderatorName, settings))
                    .addField("Server", guild.getName(), true)
                    .addField("Reason", reason, true)
                    .setTimestamp(OffsetDateTime.now())
                    .setFooter("If you believe this action was taken in error, you can appeal using the button below.");

            if (duration != null) {
                embed.addField("Duration", formatDuration(duration), true);
            }

            // Create appeal button if appeal channel is configured
            final ActionRow actionRow;
            if (settings.getAppealChannelId() != null && !settings.getAppealChannelId().isEmpty()) {
                Button appealButton = Button
                        .secondary("appeal:" + guild.getId() + ":" + user.getId() + ":" + type.name(),
                                "📝 Submit Appeal")
                        .withEmoji(Emoji.fromUnicode("📝"));
                actionRow = ActionRow.of(appealButton);
            } else {
                actionRow = null;
            }

            // Send DM
            if (actionRow != null) {
                user.openPrivateChannel()
                        .flatMap(channel -> channel.sendMessageEmbeds(embed.build()).addComponents(actionRow))
                        .queue(
                                success -> ServerBot.getLogger().info("Sent punishment DM to user {} for {}",
                                        user.getId(), type),
                                failure -> ServerBot.getLogger().warn("Failed to send punishment DM to user {}: {}",
                                        user.getId(), failure.getMessage()));
            } else {
                user.openPrivateChannel()
                        .flatMap(channel -> channel.sendMessageEmbeds(embed.build()))
                        .queue(
                                success -> ServerBot.getLogger().info("Sent punishment DM to user {} for {}",
                                        user.getId(), type),
                                failure -> ServerBot.getLogger().warn("Failed to send punishment DM to user {}: {}",
                                        user.getId(), failure.getMessage()));
            }

            return true;

        } catch (Exception e) {
            ServerBot.getLogger().error("Error sending DM notification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Build the notification message using default templates
     */
    private String buildNotificationMessage(Guild guild, PunishmentType type, String reason,
            Duration duration, String moderatorName, PunishmentDMSettings settings) {

        String template = getDefaultMessage(type);

        return template
                .replace("{server}", guild.getName())
                .replace("{type}", type.getDisplayName())
                .replace("{reason}", reason)
                .replace("{duration}", duration != null ? formatDuration(duration) : "Permanent")
                .replace("{moderator}", moderatorName)
                .replace("{user}", "<@{user_id}>"); // Will be replaced when sending
    }

    /**
     * Get default messages for punishment types
     */
    private String getDefaultMessage(PunishmentType type) {
        return switch (type) {
            case BAN -> "You have been **banned** from **{server}**.\n\n" +
                    "**Reason:** {reason}\n" +
                    "**Duration:** {duration}\n" +
                    "**Moderator:** {moderator}\n\n" +
                    "This action was taken to maintain the safety and integrity of our community.";

            case KICK -> "You have been **kicked** from **{server}**.\n\n" +
                    "**Reason:** {reason}\n" +
                    "**Moderator:** {moderator}\n\n" +
                    "You may rejoin the server, but please ensure you follow our rules.";

            case WARN -> "You have received a **warning** in **{server}**.\n\n" +
                    "**Reason:** {reason}\n" +
                    "**Moderator:** {moderator}\n\n" +
                    "Please review our server rules and adjust your behavior accordingly.";

            case MUTE -> "You have been **muted** in **{server}**.\n\n" +
                    "**Reason:** {reason}\n" +
                    "**Duration:** {duration}\n" +
                    "**Moderator:** {moderator}\n\n" +
                    "During this time, you will not be able to send messages or speak in voice channels.";

            case TIMEOUT -> "You have been **timed out** in **{server}**.\n\n" +
                    "**Reason:** {reason}\n" +
                    "**Duration:** {duration}\n" +
                    "**Moderator:** {moderator}\n\n" +
                    "During this timeout, you will not be able to send messages, speak in voice channels, or react to messages.";

            case AUTOMOD -> "Your message in **{server}** was automatically removed by the automod system.\n\n" +
                    "**Reason:** {reason}\n" +
                    "**Moderator:** Automod\n\n" +
                    "Alert staff if you believe this was a mistake.";
        };
    }

    /**
     * Get color for punishment type
     */
    private Color getColorForPunishment(PunishmentType type) {
        return switch (type) {
            case BAN -> Color.RED;
            case KICK -> Color.ORANGE;
            case WARN -> Color.YELLOW;
            case MUTE -> Color.MAGENTA;
            case TIMEOUT -> Color.BLUE;
            case AUTOMOD -> Color.ORANGE;
        };
    }

    /**
     * Format duration for display
     */
    private String formatDuration(Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)
            sb.append(days).append("d ");
        if (hours > 0)
            sb.append(hours).append("h ");
        if (minutes > 0)
            sb.append(minutes).append("m");

        return sb.toString().trim();
    }

    /**
     * Get DM settings for a guild
     */
    public PunishmentDMSettings getDMSettings(String guildId) {
        File file = new File(dataDir, guildId + "_dm_settings.json");
        if (!file.exists()) {
            return new PunishmentDMSettings(); // Default settings
        }

        try (FileReader reader = new FileReader(file)) {
            return gson.fromJson(reader, PunishmentDMSettings.class);
        } catch (IOException e) {
            ServerBot.getLogger().error("Failed to load DM settings for guild {}: {}", guildId, e.getMessage());
            return new PunishmentDMSettings();
        }
    }

    /**
     * Save DM settings for a guild
     */
    public void saveDMSettings(String guildId, PunishmentDMSettings settings) {
        File file = new File(dataDir, guildId + "_dm_settings.json");
        try (FileWriter writer = new FileWriter(file)) {
            gson.toJson(settings, writer);
        } catch (IOException e) {
            ServerBot.getLogger().error("Failed to save DM settings for guild {}: {}", guildId, e.getMessage());
        }
    }

    /**
     * Punishment types that support DM notifications
     */
    public enum PunishmentType {
        BAN("Ban"),
        KICK("Kick"),
        WARN("Warning"),
        MUTE("Mute"),
        TIMEOUT("Timeout"),
        AUTOMOD("Automod Action");

        private final String displayName;

        PunishmentType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Settings class for DM notifications
     */
    public static class PunishmentDMSettings {
        private boolean enabled = false;
        private String appealChannelId = null;

        public PunishmentDMSettings() {
            // Default: disabled
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAppealChannelId() {
            return appealChannelId;
        }

        public void setAppealChannelId(String appealChannelId) {
            this.appealChannelId = appealChannelId;
        }
    }
}