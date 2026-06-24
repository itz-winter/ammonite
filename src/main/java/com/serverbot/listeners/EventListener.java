package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.services.PunishmentNotificationService;
import com.serverbot.services.PunishmentNotificationService.PunishmentType;
import com.serverbot.services.SchedulerService;
import com.serverbot.utils.DmUtils;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildLeaveEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Handles general Discord events
 */
public class EventListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(EventListener.class);

    // Cooldown tracking for XP/points gain to prevent spam
    private final Map<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN = TimeUnit.MINUTES.toMillis(1); // 1 minute cooldown

    // Anti-spam tracking
    private final Map<String, List<Long>> messageTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> warningCounts = new ConcurrentHashMap<>();

    @Override
    public void onReady(ReadyEvent event) {
        logger.info("Bot is ready! Logged in as: {}", event.getJDA().getSelfUser().getName());
        logger.info("Serving {} guilds", event.getJDA().getGuilds().size());
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) {
        logger.info("Joined guild: {} (ID: {}) with {} members",
                event.getGuild().getName(),
                event.getGuild().getId(),
                event.getGuild().getMemberCount());
    }

    @Override
    public void onGuildLeave(GuildLeaveEvent event) {
        logger.info("Left guild: {} (ID: {})",
                event.getGuild().getName(),
                event.getGuild().getId());
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        logger.debug("Member joined {}: {}",
                event.getGuild().getName(),
                event.getMember().getUser().getName());

        handleWelcomeMessage(event);
        handleAutoRole(event);
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        logger.debug("Member left {}: {}",
                event.getGuild().getName(),
                event.getUser().getName());
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Don't process bot messages
        if (event.getAuthor().isBot()) {
            return;
        }

        // Only process messages from guilds
        if (!event.isFromGuild()) {
            return;
        }

        // Handle anti-spam
        if (handleAntiSpam(event)) {
            return; // Message was spam and handled
        }

        // Handle XP and points gain
        handleXpAndPointsGain(event);
    }

    private boolean handleAntiSpam(MessageReceivedEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Check if auto-moderation is enabled
            Boolean automodEnabled = (Boolean) settings.get("enableAutomod");
            if (automodEnabled == null || !automodEnabled) {
                return false;
            }

            // Administrator bypass: members with the bypass permission (admins get it
            // automatically) skip antispam
            Member memberEarly = event.getMember();
            if (memberEarly != null && PermissionManager.hasPermission(memberEarly, "automod.antispam.bypass")) {
                return false;
            }

            // Check if auto-delete is enabled
            Boolean autoDelete = (Boolean) settings.getOrDefault("antiSpamAutoDelete", false);

            String userId = event.getAuthor().getId();
            String userKey = guildId + ":" + userId;
            long currentTime = System.currentTimeMillis();

            // Get anti-spam settings — handle Long, Integer, and Double (common from JSON
            // parsers)
            Object maxMessagesObj = settings.getOrDefault("antiSpamMessageLimit", 10);
            int maxMessages = ((Number) maxMessagesObj).intValue();
            long timeWindow = 10000L; // 10 seconds

            // Track message times
            messageTimes.computeIfAbsent(userKey, k -> new ArrayList<>()).add(currentTime);
            List<Long> times = messageTimes.get(userKey);

            // Remove old messages outside the time window
            times.removeIf(time -> (currentTime - time) > timeWindow);

            // Check if user is spamming
            if (times.size() > maxMessages) {
                String action = (String) settings.getOrDefault("antiSpamPunishment", "warn");

                // Delete the message if auto-delete is enabled
                if (autoDelete != null && autoDelete) {
                    event.getMessage().delete().queue(null, throwable -> {
                    });
                }

                // Apply punishment
                Member member = event.getMember();
                if (member != null) {
                    applyAntiSpamPunishment(event, member, action, settings);
                }

                return true; // Spam detected and handled
            }

            return false;
        } catch (Exception e) {
            logger.warn("Error in anti-spam check: {}", e.getMessage());
            return false;
        }
    }

    private void applyAntiSpamPunishment(MessageReceivedEvent event, Member member, String action,
            Map<String, Object> settings) {
        try {
            String guildId = event.getGuild().getId();
            String userId = member.getId();
            String reason = "Anti-spam violation";

            switch (action.toLowerCase()) {
                case "warn" -> {
                    // Add warning to database
                    ServerBot.getStorageManager().addWarning(guildId, userId, reason,
                            event.getJDA().getSelfUser().getId());

                    // Send punishment notification
                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                            guildId, userId, PunishmentType.AUTOMOD, reason, null, "Automod");
                }
                case "mute" -> {
                    // Get mute duration from settings (default 5 minutes)
                    Object durationObj = settings.getOrDefault("antiSpamMuteDuration", 300000L);
                    long muteDuration = durationObj instanceof Long ? (Long) durationObj
                            : ((Integer) durationObj).longValue();

                    if (event.getGuild().getSelfMember().canInteract(member)) {
                        member.timeoutFor(muteDuration, TimeUnit.MILLISECONDS).queue(
                                success -> {
                                    // Log the mute
                                    logAntiSpamAction(guildId, userId, "MUTE", reason, muteDuration + "ms");

                                    // Send punishment notification
                                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                                            guildId, userId, PunishmentType.MUTE, reason,
                                            Duration.ofMillis(muteDuration), "Automod");
                                },
                                throwable -> {
                                });
                    }
                }
                case "timeout" -> {
                    // Get timeout duration from settings (default 10 minutes)
                    Object durationObj = settings.getOrDefault("antiSpamTimeoutDuration", 600000L);
                    long timeoutDuration = durationObj instanceof Long ? (Long) durationObj
                            : ((Integer) durationObj).longValue();

                    if (event.getGuild().getSelfMember().canInteract(member)) {
                        member.timeoutFor(timeoutDuration, TimeUnit.MILLISECONDS).queue(
                                success -> {
                                    // Log the timeout
                                    logAntiSpamAction(guildId, userId, "TIMEOUT", reason, timeoutDuration + "ms");

                                    // Send punishment notification
                                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                                            guildId, userId, PunishmentType.TIMEOUT, reason,
                                            Duration.ofMillis(timeoutDuration), "Automod");
                                },
                                throwable -> {
                                });
                    }
                }
                case "kick" -> {
                    if (event.getGuild().getSelfMember().canInteract(member)) {
                        event.getGuild().kick(member).reason(reason).queue(
                                success -> {
                                    // Log the kick
                                    logAntiSpamAction(guildId, userId, "KICK", reason, null);

                                    // Send punishment notification
                                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                                            guildId, userId, PunishmentType.KICK, reason, null, "Automod");
                                },
                                throwable -> {
                                });
                    }
                }
                case "ban" -> {
                    // Get ban duration from settings (default 1 day)
                    Object durationObj = settings.getOrDefault("antiSpamBanDuration", 86400000L);
                    long banDuration = durationObj instanceof Long ? (Long) durationObj
                            : ((Integer) durationObj).longValue();

                    if (event.getGuild().getSelfMember().canInteract(member)) {
                        event.getGuild().ban(member.getUser(), 0, TimeUnit.SECONDS).reason(reason).queue(
                                success -> {
                                    // Log the ban
                                    logAntiSpamAction(guildId, userId, "BAN", reason, banDuration + "ms");

                                    // Send punishment notification
                                    PunishmentNotificationService.getInstance().sendPunishmentNotification(
                                            guildId, userId, PunishmentType.BAN, reason,
                                            Duration.ofMillis(banDuration), "Automod");

                                    // Schedule unban if temporary
                                    if (banDuration > 0) {
                                        long unbanTimestamp = System.currentTimeMillis() + banDuration;
                                        SchedulerService.getInstance().scheduleUnban(guildId, userId, reason,
                                                unbanTimestamp);
                                    }
                                },
                                throwable -> {
                                });
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Error applying anti-spam punishment: {}", e.getMessage());
        }
    }

    private void logAntiSpamAction(String guildId, String userId, String actionType, String reason, String duration) {
        try {
            // Create moderation log entry
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("type", actionType);
            logEntry.put("userId", userId);
            logEntry.put("moderatorId", "SYSTEM"); // System action
            logEntry.put("reason", reason);
            logEntry.put("duration", duration);
            logEntry.put("timestamp", System.currentTimeMillis());

            // Store moderation log
            ServerBot.getStorageManager().addModerationLog(guildId, logEntry);
        } catch (Exception e) {
            logger.warn("Failed to log anti-spam action: {}", e.getMessage());
        }
    }

    private void handleWelcomeMessage(GuildMemberJoinEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            Boolean welcomeEnabled = (Boolean) settings.get("welcomeEnabled");
            String channelId = (String) settings.get("welcomeChannelId");
            Boolean dmEnabled = (Boolean) settings.get("welcomeDMEnabled");

            // Handle channel welcome message
            if (welcomeEnabled != null && welcomeEnabled && channelId != null) {
                TextChannel welcomeChannel = event.getGuild().getTextChannelById(channelId);
                if (welcomeChannel != null) {
                    String message = (String) settings.getOrDefault("welcomeMessage",
                            "Welcome to {server}, {user}! We're glad to have you here. 🎉");
                    String colorHex = (String) settings.getOrDefault("welcomeEmbedColor", "#00FF00");

                    // Replace placeholders
                    message = message.replace("{user}", event.getUser().getAsMention())
                            .replace("{username}", event.getUser().getEffectiveName())
                            .replace("{server}", event.getGuild().getName())
                            .replace("{membercount}", String.valueOf(event.getGuild().getMemberCount()));

                    EmbedBuilder embed = new EmbedBuilder()
                            .setTitle("👋 Welcome!")
                            .setDescription(message)
                            .setColor(Integer.parseInt(colorHex.substring(1), 16))
                            .setThumbnail(event.getUser().getAvatarUrl())
                            .setFooter("Member #" + event.getGuild().getMemberCount());

                    welcomeChannel.sendMessageEmbeds(embed.build()).queue(null, throwable -> {
                        logger.warn("Failed to send welcome message: {}", throwable.getMessage());
                    });
                }
            }

            // Handle DM welcome message
            if (dmEnabled != null && dmEnabled) {
                // Get DM-specific message or fall back to channel message
                String dmMessage = (String) settings.get("welcomeDMMessage");
                if (dmMessage == null) {
                    dmMessage = (String) settings.getOrDefault("welcomeMessage",
                            "Welcome to {server}, {user}! We're glad to have you here. 🎉");
                }

                String colorHex = (String) settings.getOrDefault("welcomeEmbedColor", "#00FF00");

                // Replace placeholders
                dmMessage = dmMessage.replace("{user}", event.getUser().getAsMention())
                        .replace("{username}", event.getUser().getEffectiveName())
                        .replace("{server}", event.getGuild().getName())
                        .replace("{membercount}", String.valueOf(event.getGuild().getMemberCount()));

                EmbedBuilder dmEmbed = new EmbedBuilder()
                        .setTitle("👋 Welcome to " + event.getGuild().getName() + "!")
                        .setDescription(dmMessage)
                        .setColor(Integer.parseInt(colorHex.substring(1), 16))
                        .setThumbnail(event.getGuild().getIconUrl())
                        .setFooter("You are member #" + event.getGuild().getMemberCount());

                // Send DM (respects guild DM toggle)
                DmUtils.sendDm(event.getGuild(), event.getUser(), dmEmbed.build(),
                        v -> logger.info("Sent welcome DM to user {} in guild {}",
                                event.getUser().getId(), event.getGuild().getId()),
                        error -> logger.warn("Failed to send welcome DM to user {}: {}",
                                event.getUser().getId(), error.getMessage()));
            }
        } catch (Exception e) {
            logger.warn("Error sending welcome message: {}", e.getMessage());
        }
    }

    private void handleAutoRole(GuildMemberJoinEvent event) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            String autoRoleId = (String) settings.get("welcomeAutoRoleId");
            if (autoRoleId != null) {
                Role autoRole = event.getGuild().getRoleById(autoRoleId);
                if (autoRole != null && event.getGuild().getSelfMember().canInteract(autoRole)) {
                    event.getGuild().addRoleToMember(event.getMember(), autoRole).queue(null, throwable -> {
                        logger.warn("Failed to assign auto-role: {}", throwable.getMessage());
                    });
                }
            }
        } catch (Exception e) {
            logger.warn("Error assigning auto-role: {}", e.getMessage());
        }
    }

    private void handleXpAndPointsGain(MessageReceivedEvent event) {
        try {
            String guildId = event.getGuild().getId();
            String userId = event.getAuthor().getId();
            String channelId = event.getChannel().getId();
            String key = guildId + ":" + userId;

            // Check cooldown to prevent XP/point spam
            long currentTime = System.currentTimeMillis();
            Long lastTime = lastMessageTime.get(key);

            if (lastTime != null && (currentTime - lastTime) < MESSAGE_COOLDOWN) {
                return; // Still on cooldown
            }

            // Update last message time
            lastMessageTime.put(key, currentTime);

            // Check if systems are enabled FIRST — bail out early if disabled
            Map<String, Object> guildSettings = ServerBot.getStorageManager().getGuildSettings(guildId);
            boolean levelingEnabled = Boolean.TRUE.equals(guildSettings.get("enableLeveling"));
            boolean economyEnabled = Boolean.TRUE.equals(guildSettings.get("enableEconomy"));
            if (!levelingEnabled && !economyEnabled) return;

            // Get configured amounts from guild settings
            long xpPerMessage = ServerBot.getStorageManager().getXpPerMessage(guildId);
            long pointsPerMessage = ServerBot.getStorageManager().getPointsPerMessage(guildId);

            // Check excluded channels
            if (levelingEnabled && xpPerMessage > 0) {
                @SuppressWarnings("unchecked")
                java.util.List<String> excludedChannels = (java.util.List<String>) guildSettings.get("xpExcludedChannels");
                @SuppressWarnings("unchecked")
                java.util.List<String> excludedUsers = (java.util.List<String>) guildSettings.get("xpExcludedUsers");
                boolean isExcluded = (excludedChannels != null && excludedChannels.contains(channelId)) ||
                                     (excludedUsers != null && excludedUsers.contains(userId));
                
                int oldLevel = ServerBot.getStorageManager().getLevel(guildId, userId);
                ServerBot.getStorageManager().addExperience(guildId, userId, xpPerMessage);
                int newLevel = ServerBot.getStorageManager().getLevel(guildId, userId);

                // Send level-up message as reply to the message that triggered it
                if (newLevel > oldLevel && !isExcluded) {
                    sendLevelUpReply(event, newLevel);
                }
            }

            // Award points if economy is enabled
            if (economyEnabled && pointsPerMessage > 0) {
                ServerBot.getStorageManager().addBalance(guildId, userId, pointsPerMessage);
            }

        } catch (Exception e) {
            logger.warn("Error handling XP/points gain for user {} in guild {}: {}",
                    event.getAuthor().getId(), event.getGuild().getId(), e.getMessage());
        }
    }

    private void sendLevelUpReply(MessageReceivedEvent event, int level) {
        try {
            String guildId = event.getGuild().getId();
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            String levelUpMessage = (String) settings.get("levelUpMessage");
            if (levelUpMessage == null) levelUpMessage = "🎉 Congratulations {user}! You've reached **level {level}** in {server}!";

            String lvlMsg = levelUpMessage
                .replace("{level}", String.valueOf(level))
                .replace("{user}", event.getAuthor().getAsMention())
                .replace("{username}", event.getAuthor().getName())
                .replace("{server}", event.getGuild().getName());

            event.getMessage().reply(lvlMsg).queue(null, err -> {});
        } catch (Exception e) {
            logger.warn("Failed to send level-up reply: {}", e.getMessage());
        }
    }
}
