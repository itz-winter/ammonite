package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.utils.CustomEmojis;
import com.serverbot.utils.SafeRestAction;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.audit.ActionType;
import net.dv8tion.jda.api.audit.AuditLogEntry;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateTimeOutEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.channel.ChannelCreateEvent;
import net.dv8tion.jda.api.events.channel.ChannelDeleteEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateNameEvent;
import net.dv8tion.jda.api.events.channel.update.ChannelUpdateTopicEvent;
import net.dv8tion.jda.api.events.role.RoleCreateEvent;
import net.dv8tion.jda.api.events.role.RoleDeleteEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateNameEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdateColorEvent;
import net.dv8tion.jda.api.events.role.update.RoleUpdatePermissionsEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateNameEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateDescriptionEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateIconEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateBannerEvent;
import net.dv8tion.jda.api.events.guild.update.GuildUpdateVanityCodeEvent;
import net.dv8tion.jda.api.events.guild.scheduledevent.ScheduledEventCreateEvent;
import net.dv8tion.jda.api.events.guild.scheduledevent.ScheduledEventDeleteEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateAvatarEvent;
import net.dv8tion.jda.api.events.user.update.UserUpdateGlobalNameEvent;
import net.dv8tion.jda.api.events.message.MessageBulkDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
// Explicit import to resolve ambiguity with java.awt.List
import java.util.List;
import java.util.ArrayList;

/**
 * Handles automatic logging of Discord events to configured log channels
 */
public class AutoLogListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(AutoLogListener.class);

    // Message cache (Vencord-style: cache on receive, read on delete/edit)

    /**
     * Cached metadata for a recently received message, used for delete/edit
     * logging.
     */
    private record CachedMessage(String content, String authorMention, String authorId, String authorTag,
            List<String> attachmentUrls, List<String> embedDescriptions) {
    }

    /**
     * Lightweight info collected at deletion time (IDs only available in
     * bulk-delete).
     */
    private record DeletedMessageInfo(String id, String authorMention, String authorTag, String content) {
    }

    private static final int MSG_CACHE_SIZE = 10_000;
    @SuppressWarnings("serial")
    private static final Map<String, CachedMessage> messageCache = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedMessage> eldest) {
                    return size() > MSG_CACHE_SIZE;
                }
            });

    // Track message IDs that are sent by the bot to log channels to avoid logging
    // their deletions
    private static final Set<String> logMessageIds = ConcurrentHashMap.newKeySet();

    // Suppress individual MessageDeleteEvents for messages being batch-purged by
    // the bot
    private static final Set<String> suppressedDeleteIds = ConcurrentHashMap.newKeySet();

    /**
     * Track a log message ID to prevent logging its deletion.
     * This is called when the bot sends a log message.
     * 
     * @param messageId The ID of the log message to track
     */
    public static void trackLogMessage(String messageId) {
        logMessageIds.add(messageId);

        // Prevent memory leak - keep only the last 5000 log message IDs
        if (logMessageIds.size() > 5000) {
            Iterator<String> iterator = logMessageIds.iterator();
            for (int i = 0; i < 1000 && iterator.hasNext(); i++) {
                iterator.next();
                iterator.remove();
            }
        }
    }

    /**
     * Tell the listener to silently suppress individual MessageDeleteEvents for
     * these
     * message IDs. Called by PurgeCommand before doing one-by-one deletion of old
     * messages so they don't each create a separate delete log entry.
     */
    public static void suppressBatchDelete(Collection<String> messageIds) {
        suppressedDeleteIds.addAll(messageIds);
        // Safety cap to prevent memory leak if callers never drain the set
        if (suppressedDeleteIds.size() > 50_000) {
            Iterator<String> it = suppressedDeleteIds.iterator();
            for (int i = 0; i < 10_000 && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
        }
    }

    // Message caching

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (!event.isFromGuild())
            return;
        Message msg = event.getMessage();
        User author = msg.getAuthor();
        messageCache.put(msg.getId(), new CachedMessage(
                msg.getContentRaw(),
                author.getAsMention(),
                author.getId(),
                author.getName(),
                msg.getAttachments().stream()
                        .map(a -> "[" + a.getFileName() + "](" + a.getUrl() + ")")
                        .collect(java.util.stream.Collectors.toList()),
                msg.getEmbeds().stream()
                        .map(e -> e.getDescription() != null ? truncateText(e.getDescription(), 200) : "*[embed]*")
                        .collect(java.util.stream.Collectors.toList())));
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        if (!event.isFromGuild() || event.getAuthor().isBot()) {
            return;
        }

        Message message = event.getMessage();
        String messageId = message.getId();
        String newContent = message.getContentRaw();

        // Look up the baseline content we captured when the message was first received
        CachedMessage cached = messageCache.get(messageId);
        String previousContent = (cached != null) ? cached.content() : null;

        // Content is identical → embed unfurl, pin, or flag change — not a real edit
        if (newContent.equals(previousContent))
            return;

        if (previousContent == null) {
            // No baseline in cache (bot restarted, message pre-dates session).
            // We can't produce a before/after diff, so just store the current content
            // and wait for the next genuine edit before logging.
            messageCache.put(messageId, new CachedMessage(
                    newContent,
                    event.getAuthor().getAsMention(),
                    event.getAuthor().getId(),
                    event.getAuthor().getName(),
                    List.of(), List.of()));
            return;
        }

        // Update the cache with the new content
        messageCache.put(messageId, new CachedMessage(
                newContent, cached.authorMention(), cached.authorId(), cached.authorTag(),
                cached.attachmentUrls(), cached.embedDescriptions()));

        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "message", "edits")) {
            return;
        }

        TextChannel logChannel = getLogChannel(event.getGuild(), "message");
        if (logChannel == null)
            return;

        String jumpLink = "https://discord.com/channels/"
                + event.getGuild().getId() + "/" + event.getChannel().getId() + "/" + messageId;

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle(CustomEmojis.NOTE + " Message Edited")
                .setUrl(jumpLink) // clicking the title jumps to the message
                .addField("User", event.getAuthor().getAsMention(), true)
                .addField("Channel", event.getChannel().getAsMention(), true)
                .addField("Before",
                        previousContent.isEmpty() ? "*[No text content]*" : truncateText(previousContent, 1024), false)
                .addField("After",
                        newContent.isEmpty() ? "*[No text content]*" : truncateText(newContent, 1024), false)
                .setFooter("User ID: " + event.getAuthor().getId() + " | Message ID: " + messageId)
                .setTimestamp(OffsetDateTime.now());

        SafeRestAction.queue(
                logChannel.sendMessageEmbeds(embed.build()),
                "log message edit event",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        String messageId = event.getMessageId();

        // Don't log deletion of the bot's own log messages
        if (logMessageIds.remove(messageId)) {
            return;
        }

        // Don't log proxy system deletions
        if (ServerBot.getProxyService().isOriginalMessageBeingProxied(messageId)) {
            return;
        }

        // Part of a bot-initiated batch purge — will be logged as a group instead
        if (suppressedDeleteIds.remove(messageId)) {
            messageCache.remove(messageId);
            return;
        }

        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "message", "deletes")) {
            messageCache.remove(messageId);
            return;
        }
        if (isExcludedFromLogging(guildId, "message", event.getChannel().getId(), null)) {
            messageCache.remove(messageId);
            return;
        }

        TextChannel logChannel = getLogChannel(event.getGuild(), "message");
        if (logChannel == null) {
            messageCache.remove(messageId);
            return;
        }

        CachedMessage cached = messageCache.remove(messageId);

        // If we never cached this message and bot deletions aren't configured to be logged, skip it
        if (cached == null && !com.serverbot.ServerBot.getStorageManager().areBotDeletionsLogged(guildId)) {
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle(CustomEmojis.TRASH + " Message Deleted")
                .addField("Channel", event.getChannel().getAsMention(), true)
                .addField("Message ID", messageId, true);

        if (cached != null) {
            embed.addField("Author", cached.authorMention(), true);
            String content = cached.content();
            embed.addField("Content",
                    content.isEmpty() ? "*[No text content]*" : truncateText(content, 1024), false);
            if (!cached.attachmentUrls().isEmpty()) {
                embed.addField("Attachments", String.join("\n", cached.attachmentUrls()), false);
            }
            if (!cached.embedDescriptions().isEmpty()) {
                embed.addField("Embeds", String.join("\n", cached.embedDescriptions()), false);
            }
            embed.setFooter("Author ID: " + cached.authorId());
        } else {
            embed.setFooter("Content unavailable (not cached)");
        }

        embed.setTimestamp(OffsetDateTime.now());

        // Enrich with audit log to show who deleted the message (if it was someone
        // else)
        SafeRestAction.queue(
                event.getGuild().retrieveAuditLogs().type(ActionType.MESSAGE_DELETE).limit(1),
                "retrieve audit logs for message delete",
                auditLogs -> {
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        if (entry.getTargetIdLong() == event.getMessageIdLong()) {
                            User deletedBy = entry.getUser();
                            if (deletedBy != null && (cached == null || !deletedBy.getId().equals(cached.authorId()))) {
                                embed.addField("Deleted by", deletedBy.getAsMention(), true);
                            }
                        }
                    }
                    SafeRestAction.queue(
                            logChannel.sendMessageEmbeds(embed.build()),
                            "log message delete",
                            success -> trackLogMessage(success.getId()));
                });
    }

    @Override
    public void onMessageBulkDelete(MessageBulkDeleteEvent event) {
        // MessageBulkDeleteEvent always originates from a guild channel

        List<String> ids = event.getMessageIds();

        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "message", "deletes")) {
            ids.forEach(messageCache::remove);
            return;
        }

        TextChannel logChannel = getLogChannel(event.getGuild(), "message");
        if (logChannel == null) {
            ids.forEach(messageCache::remove);
            return;
        }

        // Collect cached info for each deleted message
        List<DeletedMessageInfo> infos = new ArrayList<>();
        for (String id : ids) {
            CachedMessage cached = messageCache.remove(id);
            infos.add(new DeletedMessageInfo(
                    id,
                    cached != null ? cached.authorMention() : null,
                    cached != null ? cached.authorTag() : null,
                    cached != null ? cached.content() : null));
        }

        // Try to identify who triggered the bulk delete via audit log
        SafeRestAction.queue(
                event.getGuild().retrieveAuditLogs().type(ActionType.MESSAGE_BULK_DELETE).limit(1),
                "retrieve audit logs for bulk delete",
                auditLogs -> {
                    User executor = null;
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        // For MESSAGE_BULK_DELETE, target_id is the channel
                        if (entry.getTargetIdLong() == event.getChannel().getIdLong()) {
                            executor = entry.getUser();
                        }
                    }
                    sendGroupedDeleteLog(logChannel, event.getChannel().getAsMention(), infos, executor);
                });
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "member", "joins")) {
            return;
        }
        if (isExcludedFromLogging(guildId, "member", null, event.getUser().getId())) return;

        TextChannel logChannel = getLogChannel(event.getGuild(), "member");
        if (logChannel == null)
            return;

        User user = event.getUser();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("📥 Member Joined")
                .setThumbnail(user.getAvatarUrl())
                .addField("User", user.getAsMention(), true)
                .addField("Username", user.getName(), true)
                .addField("Account Created",
                        user.getTimeCreated().format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")), true)
                .addField("Member Count", String.valueOf(event.getGuild().getMemberCount()), true)
                .setFooter("User ID: " + user.getId())
                .setTimestamp(OffsetDateTime.now());

        SafeRestAction.queue(
                logChannel.sendMessageEmbeds(embed.build()),
                "log member join event",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "member", "leaves")) {
            return;
        }

        TextChannel logChannel = getLogChannel(event.getGuild(), "member");
        if (logChannel == null)
            return;

        User user = event.getUser();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("📤 Member Left")
                .setThumbnail(user.getAvatarUrl())
                .addField("User", user.getAsMention(), true)
                .addField("Username", user.getName(), true)
                .addField("Member Count", String.valueOf(event.getGuild().getMemberCount()), true)
                .setFooter("User ID: " + user.getId())
                .setTimestamp(OffsetDateTime.now());

        SafeRestAction.queue(
                logChannel.sendMessageEmbeds(embed.build()),
                "log member leave event",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onGuildBan(GuildBanEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "moderation", "bans")) {
            return;
        }

        TextChannel logChannel = getLogChannel(event.getGuild(), "moderation");
        if (logChannel == null)
            return;

        User user = event.getUser();

        // Check audit logs to see who issued the ban.
        // If it was the bot itself, skip logging here because the command
        // (e.g. BanCommand) already logged via AutoLogUtils.logBan().
        SafeRestAction.queue(
                event.getGuild().retrieveAuditLogs().type(ActionType.BAN).limit(1),
                "retrieve audit logs for ban",
                auditLogs -> {
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        if (entry.getTargetIdLong() == user.getIdLong()) {
                            User bannedBy = entry.getUser();
                            // If the ban was issued by the bot, the command already logged it
                            if (bannedBy != null
                                    && bannedBy.getIdLong() == event.getGuild().getSelfMember().getIdLong()) {
                                return; // Skip duplicate log
                            }
                        }
                    }

                    // This ban was not issued by the bot (e.g. manual Discord ban), so log it
                    EmbedBuilder embed = new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle(CustomEmojis.ERROR + " Member Banned")
                            .setThumbnail(user.getAvatarUrl())
                            .addField("User", user.getAsMention(), true)
                            .addField("Username", user.getName(), true)
                            .setFooter("User ID: " + user.getId())
                            .setTimestamp(OffsetDateTime.now());

                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        if (entry.getTargetIdLong() == user.getIdLong()) {
                            User bannedBy = entry.getUser();
                            String reason = entry.getReason();

                            if (bannedBy != null) {
                                embed.addField("Banned by", bannedBy.getAsMention(), true);
                            }
                            if (reason != null && !reason.isEmpty()) {
                                embed.addField("Reason", reason, false);
                            }
                        }
                    }
                    SafeRestAction.queue(
                            logChannel.sendMessageEmbeds(embed.build()),
                            "log ban with moderator",
                            success -> trackLogMessage(success.getId()));
                });
    }

    @Override
    public void onGuildUnban(GuildUnbanEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "moderation", "bans")) {
            return;
        }

        TextChannel logChannel = getLogChannel(event.getGuild(), "moderation");
        if (logChannel == null)
            return;

        User user = event.getUser();

        // Check audit logs to see who issued the unban.
        // If it was the bot itself, skip logging here because the command
        // (e.g. UnbanCommand) already logged via AutoLogUtils.logUnban().
        SafeRestAction.queue(
                event.getGuild().retrieveAuditLogs().type(ActionType.UNBAN).limit(1),
                "retrieve audit logs for unban",
                auditLogs -> {
                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        if (entry.getTargetIdLong() == user.getIdLong()) {
                            User unbannedBy = entry.getUser();
                            if (unbannedBy != null
                                    && unbannedBy.getIdLong() == event.getGuild().getSelfMember().getIdLong()) {
                                return; // Skip duplicate log
                            }
                        }
                    }

                    // This unban was not issued by the bot, so log it
                    EmbedBuilder embed = new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle(CustomEmojis.SUCCESS + " Member Unbanned")
                            .setThumbnail(user.getAvatarUrl())
                            .addField("User", user.getAsMention(), true)
                            .addField("Username", user.getName(), true)
                            .setFooter("User ID: " + user.getId())
                            .setTimestamp(OffsetDateTime.now());

                    if (!auditLogs.isEmpty()) {
                        AuditLogEntry entry = auditLogs.get(0);
                        if (entry.getTargetIdLong() == user.getIdLong()) {
                            User unbannedBy = entry.getUser();
                            if (unbannedBy != null) {
                                embed.addField("Unbanned by", unbannedBy.getAsMention(), true);
                            }
                        }
                    }
                    SafeRestAction.queue(
                            logChannel.sendMessageEmbeds(embed.build()),
                            "log unban with moderator",
                            success -> trackLogMessage(success.getId()));
                });
    }

    @Override
    public void onGuildMemberUpdateTimeOut(GuildMemberUpdateTimeOutEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "moderation", "mutes")) {
            return;
        }

        TextChannel logChannel = getLogChannel(event.getGuild(), "moderation");
        if (logChannel == null)
            return;

        Member member = event.getMember();
        User user = member.getUser();

        // Check audit logs to see who issued the timeout.
        // If it was the bot itself, skip logging here because the command
        // (e.g. TimeoutCommand) already logged via AutoLogUtils.logTimeout().
        SafeRestAction.queue(
                event.getGuild().retrieveAuditLogs().type(ActionType.MEMBER_UPDATE).limit(5),
                "retrieve audit logs for timeout",
                auditLogs -> {
                    for (AuditLogEntry entry : auditLogs) {
                        if (entry.getTargetIdLong() == user.getIdLong()) {
                            User moderator = entry.getUser();
                            // If issued by the bot, the command already logged it
                            if (moderator != null
                                    && moderator.getIdLong() == event.getGuild().getSelfMember().getIdLong()) {
                                return; // Skip duplicate log
                            }
                            break;
                        }
                    }

                    // This timeout was not issued by the bot, so log it
                    EmbedBuilder embed = new EmbedBuilder()
                            .setThumbnail(user.getAvatarUrl())
                            .addField("User", user.getAsMention(), true)
                            .addField("Username", user.getName(), true)
                            .setFooter("User ID: " + user.getId())
                            .setTimestamp(OffsetDateTime.now());

                    if (event.getNewTimeOutEnd() != null) {
                        embed.setColor(Color.RED)
                                .setTitle(CustomEmojis.WARN + " Member Timed Out")
                                .addField("Timeout Until",
                                        event.getNewTimeOutEnd()
                                                .format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm:ss")),
                                        true);
                    } else {
                        embed.setColor(Color.GREEN)
                                .setTitle(CustomEmojis.SUCCESS + " Member Timeout Removed");
                    }

                    for (AuditLogEntry entry : auditLogs) {
                        if (entry.getTargetIdLong() == user.getIdLong()) {
                            User moderator = entry.getUser();
                            String reason = entry.getReason();

                            if (moderator != null) {
                                embed.addField("Moderator", moderator.getAsMention(), true);
                            }
                            if (reason != null && !reason.isEmpty()) {
                                embed.addField("Reason", reason, false);
                            }
                            break;
                        }
                    }
                    SafeRestAction.queue(
                            logChannel.sendMessageEmbeds(embed.build()),
                            "log timeout with moderator",
                            success -> trackLogMessage(success.getId()));
                });
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "voice", null)) {
            return;
        }

        TextChannel logChannel = getLogChannel(event.getGuild(), "voice");
        if (logChannel == null)
            return;

        Member member = event.getMember();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.CYAN)
                .addField("User", member.getAsMention(), true)
                .setFooter("User ID: " + member.getId())
                .setTimestamp(OffsetDateTime.now());

        if (event.getChannelJoined() != null && event.getChannelLeft() == null) {
            // User joined a voice channel
            embed.setTitle("🔊 Voice Channel Joined")
                    .addField("Channel", event.getChannelJoined().getName(), true);
        } else if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            // User left a voice channel
            embed.setTitle("🔇 Voice Channel Left")
                    .addField("Channel", event.getChannelLeft().getName(), true);
        } else if (event.getChannelJoined() != null && event.getChannelLeft() != null) {
            // User moved between voice channels
            embed.setTitle(CustomEmojis.REFRESH + " Voice Channel Moved")
                    .addField("From", event.getChannelLeft().getName(), true)
                    .addField("To", event.getChannelJoined().getName(), true);
        }

        SafeRestAction.queue(
                logChannel.sendMessageEmbeds(embed.build()),
                "log voice channel event",
                success -> trackLogMessage(success.getId()));
    }

    // ── Channel logging ──────────────────────────────────────────────────────────

    @Override
    public void onChannelCreate(ChannelCreateEvent event) {
        if (!event.isFromGuild()) return;
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "channel", "create")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "channel");
        if (logChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("📝 Channel Created")
                .addField("Channel", event.getChannel().getAsMention(), true)
                .addField("Type", event.getChannelType().name(), true)
                .setFooter("Channel ID: " + event.getChannel().getId())
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log channel create",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onChannelDelete(ChannelDeleteEvent event) {
        if (!event.isFromGuild()) return;
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "channel", "delete")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "channel");
        if (logChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("🗑️ Channel Deleted")
                .addField("Name", "#" + event.getChannel().getName(), true)
                .addField("Type", event.getChannelType().name(), true)
                .setFooter("Channel ID: " + event.getChannel().getId())
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log channel delete",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onChannelUpdateName(ChannelUpdateNameEvent event) {
        if (!event.isFromGuild()) return;
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "channel", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "channel");
        if (logChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("✏️ Channel Renamed")
                .addField("Channel", event.getChannel().getAsMention(), true)
                .addField("Before", "#" + event.getOldValue(), true)
                .addField("After", "#" + event.getNewValue(), true)
                .setFooter("Channel ID: " + event.getChannel().getId())
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log channel rename",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onChannelUpdateTopic(ChannelUpdateTopicEvent event) {
        if (!event.isFromGuild()) return;
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "channel", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "channel");
        if (logChannel == null) return;
        String oldTopic = event.getOldValue() != null ? event.getOldValue() : "*none*";
        String newTopic = event.getNewValue() != null ? event.getNewValue() : "*none*";
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("✏️ Channel Topic Changed")
                .addField("Channel", event.getChannel().getAsMention(), true)
                .addField("Before", truncateText(oldTopic, 512), false)
                .addField("After", truncateText(newTopic, 512), false)
                .setFooter("Channel ID: " + event.getChannel().getId())
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log channel topic",
                success -> trackLogMessage(success.getId()));
    }

    // ── Role logging ─────────────────────────────────────────────────────────────

    @Override
    public void onRoleCreate(RoleCreateEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "role", "create")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "role");
        if (logChannel == null) return;
        net.dv8tion.jda.api.entities.Role role = event.getRole();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(role.getColor() != null ? role.getColor() : Color.GREEN)
                .setTitle("🏷️ Role Created")
                .addField("Role", role.getAsMention(), true)
                .addField("Name", role.getName(), true)
                .setFooter("Role ID: " + role.getId())
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log role create",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onRoleDelete(RoleDeleteEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "role", "delete")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "role");
        if (logChannel == null) return;
        net.dv8tion.jda.api.entities.Role role = event.getRole();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("🗑️ Role Deleted")
                .addField("Name", role.getName(), true)
                .setFooter("Role ID: " + role.getId())
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log role delete",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onRoleUpdateName(RoleUpdateNameEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "role", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "role");
        if (logChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("✏️ Role Renamed")
                .addField("Role", event.getRole().getAsMention(), true)
                .addField("Before", event.getOldName(), true)
                .addField("After", event.getNewName(), true)
                .setFooter("Role ID: " + event.getRole().getId())
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log role rename",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onRoleUpdateColor(RoleUpdateColorEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "role", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "role");
        if (logChannel == null) return;
        String oldHex = event.getOldColor() != null ? String.format("#%06X", event.getOldColor().getRGB() & 0xFFFFFF) : "*none*";
        String newHex = event.getNewColor() != null ? String.format("#%06X", event.getNewColor().getRGB() & 0xFFFFFF) : "*none*";
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(event.getNewColor() != null ? event.getNewColor() : Color.ORANGE)
                .setTitle("🎨 Role Color Changed")
                .addField("Role", event.getRole().getAsMention(), true)
                .addField("Before", oldHex, true)
                .addField("After", newHex, true)
                .setFooter("Role ID: " + event.getRole().getId())
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log role color",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onRoleUpdatePermissions(RoleUpdatePermissionsEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "role", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "role");
        if (logChannel == null) return;
        long added = event.getNewPermissionsRaw() & ~event.getOldPermissionsRaw();
        long removed = event.getOldPermissionsRaw() & ~event.getNewPermissionsRaw();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("🔐 Role Permissions Changed")
                .addField("Role", event.getRole().getAsMention(), true);
        if (added != 0)
            embed.addField("Permissions Added", net.dv8tion.jda.api.Permission.getPermissions(added).toString(), false);
        if (removed != 0)
            embed.addField("Permissions Removed", net.dv8tion.jda.api.Permission.getPermissions(removed).toString(), false);
        embed.setFooter("Role ID: " + event.getRole().getId()).setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log role perms",
                success -> trackLogMessage(success.getId()));
    }

    // ── Server (guild) logging ────────────────────────────────────────────────────

    @Override
    public void onGuildUpdateName(GuildUpdateNameEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "server", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "server");
        if (logChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("🏠 Server Renamed")
                .addField("Before", event.getOldName(), true)
                .addField("After", event.getNewName(), true)
                .setFooter("Guild ID: " + guildId)
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log guild rename",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onGuildUpdateDescription(GuildUpdateDescriptionEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "server", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "server");
        if (logChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("📝 Server Description Changed")
                .addField("Before", event.getOldDescription() != null ? truncateText(event.getOldDescription(), 512) : "*none*", false)
                .addField("After", event.getNewDescription() != null ? truncateText(event.getNewDescription(), 512) : "*none*", false)
                .setFooter("Guild ID: " + guildId)
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log guild desc",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onGuildUpdateIcon(GuildUpdateIconEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "server", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "server");
        if (logChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("🖼️ Server Icon Changed")
                .addField("New Icon", event.getNewIconUrl() != null ? "[View](" + event.getNewIconUrl() + ")" : "*removed*", true)
                .setFooter("Guild ID: " + guildId)
                .setTimestamp(OffsetDateTime.now());
        if (event.getNewIconUrl() != null)
            embed.setThumbnail(event.getNewIconUrl());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log guild icon",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onGuildUpdateBanner(GuildUpdateBannerEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "server", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "server");
        if (logChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("🖼️ Server Banner Changed")
                .addField("New Banner", event.getNewBannerUrl() != null ? "[View](" + event.getNewBannerUrl() + ")" : "*removed*", true)
                .setFooter("Guild ID: " + guildId)
                .setTimestamp(OffsetDateTime.now());
        if (event.getNewBannerUrl() != null)
            embed.setImage(event.getNewBannerUrl());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log guild banner",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onGuildUpdateVanityCode(GuildUpdateVanityCodeEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "server", "update")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "server");
        if (logChannel == null) return;
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setTitle("🔗 Server Vanity URL Changed")
                .addField("Before", event.getOldVanityCode() != null ? "discord.gg/" + event.getOldVanityCode() : "*none*", true)
                .addField("After", event.getNewVanityCode() != null ? "discord.gg/" + event.getNewVanityCode() : "*removed*", true)
                .setFooter("Guild ID: " + guildId)
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log guild vanity",
                success -> trackLogMessage(success.getId()));
    }

    // ── Scheduled event logging ───────────────────────────────────────────────────

    @Override
    public void onScheduledEventCreate(ScheduledEventCreateEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "event", "create")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "event");
        if (logChannel == null) return;
        net.dv8tion.jda.api.entities.ScheduledEvent se = event.getScheduledEvent();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setTitle("📅 Scheduled Event Created")
                .addField("Name", se.getName(), true)
                .addField("Start", String.format("<t:%d:F>", se.getStartTime().toEpochSecond()), true)
                .setFooter("Event ID: " + se.getId())
                .setTimestamp(OffsetDateTime.now());
        if (se.getDescription() != null && !se.getDescription().isBlank())
            embed.addField("Description", truncateText(se.getDescription(), 512), false);
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log scheduled event create",
                success -> trackLogMessage(success.getId()));
    }

    @Override
    public void onScheduledEventDelete(ScheduledEventDeleteEvent event) {
        String guildId = event.getGuild().getId();
        if (!isAutoLogEnabled(guildId, "event", "delete")) return;
        TextChannel logChannel = getLogChannel(event.getGuild(), "event");
        if (logChannel == null) return;
        net.dv8tion.jda.api.entities.ScheduledEvent se = event.getScheduledEvent();
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(Color.RED)
                .setTitle("🗑️ Scheduled Event Deleted")
                .addField("Name", se.getName(), true)
                .setFooter("Event ID: " + se.getId())
                .setTimestamp(OffsetDateTime.now());
        SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log scheduled event delete",
                success -> trackLogMessage(success.getId()));
    }

    // ── User logging (pfp + display name) ────────────────────────────────────────

    @Override
    public void onUserUpdateAvatar(UserUpdateAvatarEvent event) {
        // For each mutual guild, check if logging is enabled
        event.getUser().getMutualGuilds().forEach(guild -> {
            if (!isAutoLogEnabled(guild.getId(), "member", "updates")) return;
            TextChannel logChannel = getLogChannel(guild, "member");
            if (logChannel == null) return;
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setTitle("🖼️ User Avatar Changed")
                    .addField("User", event.getUser().getAsMention() + " (`" + event.getUser().getName() + "`)", true)
                    .addField("New Avatar", event.getNewAvatarUrl() != null ? "[View](" + event.getNewAvatarUrl() + ")" : "*removed*", true)
                    .setFooter("User ID: " + event.getUser().getId())
                    .setTimestamp(OffsetDateTime.now());
            if (event.getOldAvatarUrl() != null)
                embed.setThumbnail(event.getOldAvatarUrl());
            if (event.getNewAvatarUrl() != null)
                embed.setImage(event.getNewAvatarUrl());
            SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log avatar change",
                    success -> trackLogMessage(success.getId()));
        });
    }

    @Override
    public void onUserUpdateGlobalName(UserUpdateGlobalNameEvent event) {
        event.getUser().getMutualGuilds().forEach(guild -> {
            if (!isAutoLogEnabled(guild.getId(), "member", "updates")) return;
            TextChannel logChannel = getLogChannel(guild, "member");
            if (logChannel == null) return;
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setTitle("✏️ User Display Name Changed")
                    .addField("User", event.getUser().getAsMention() + " (`" + event.getUser().getName() + "`)", true)
                    .addField("Before", event.getOldValue() != null ? event.getOldValue() : "*none*", true)
                    .addField("After", event.getNewValue() != null ? event.getNewValue() : "*removed*", true)
                    .setFooter("User ID: " + event.getUser().getId())
                    .setTimestamp(OffsetDateTime.now());
            SafeRestAction.queue(logChannel.sendMessageEmbeds(embed.build()), "log displayname change",
                    success -> trackLogMessage(success.getId()));
        });
    }

    /**
     * Checks if auto-logging is enabled for a specific event type
     */
    private static boolean isAutoLogEnabled(String guildId, String action, String actionType) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);

            // Check for specific setting first (e.g., "autoLog_message_deletes")
            if (actionType != null) {
                String specificKey = "autoLog_" + action.toLowerCase() + "_" + actionType.toLowerCase();
                Boolean specificSetting = (Boolean) settings.get(specificKey);
                if (specificSetting != null) {
                    return specificSetting;
                }
            }

            // Check for general setting (e.g., "autoLog_message")
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
     * Gets the configured log channel for a guild and specific log type
     */
    private static TextChannel getLogChannel(Guild guild, String logType) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guild.getId());

            // Check for type-specific log channel first (from /logging command)
            if (logType != null) {
                String typeChannelId = (String) settings.get(logType + "_log_channel");
                if (typeChannelId != null) {
                    TextChannel logChannel = guild.getTextChannelById(typeChannelId);
                    if (logChannel != null && logChannel.canTalk()) {
                        return logChannel;
                    }
                }
            }

            // Fall back to global log channel (legacy)
            String logChannelId = (String) settings.get("logChannelId");
            if (logChannelId == null) {
                return null;
            }

            TextChannel logChannel = guild.getTextChannelById(logChannelId);
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
     * Truncates text to a maximum length for embed fields
     */
    private static String truncateText(String text, int maxLength) {
        if (text == null)
            return "";
        if (text.length() <= maxLength)
            return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * Check if a user/channel/category/role should be excluded from logging.
     */
    protected static boolean isExcludedFromLogging(String guildId, String logType, String channelId, String userId) {
        try {
            Map<String, Object> settings = ServerBot.getStorageManager().getGuildSettings(guildId);
            @SuppressWarnings("unchecked")
            java.util.List<String> excludedChannels = (java.util.List<String>) settings.get("logExcludedChannels_" + logType);
            if (excludedChannels != null && channelId != null && excludedChannels.contains(channelId)) return true;
            @SuppressWarnings("unchecked")
            java.util.List<String> excludedUsers = (java.util.List<String>) settings.get("logExcludedUsers_" + logType);
            if (excludedUsers != null && userId != null && excludedUsers.contains(userId)) return true;
            // Also check global exclusions across all log types
            @SuppressWarnings("unchecked")
            java.util.List<String> globalExcludedChannels = (java.util.List<String>) settings.get("logExcludedChannels_all");
            if (globalExcludedChannels != null && channelId != null && globalExcludedChannels.contains(channelId)) return true;
            @SuppressWarnings("unchecked")
            java.util.List<String> globalExcludedUsers = (java.util.List<String>) settings.get("logExcludedUsers_all");
            if (globalExcludedUsers != null && userId != null && globalExcludedUsers.contains(userId)) return true;
        } catch (Exception e) {
            logger.trace("Error checking log exclusions: {}", e.getMessage());
        }
        return false;
    }

    // Grouped delete log (shared by onMessageBulkDelete and logOldMessagePurge)

    /**
     * Logs a batch of deleted messages as a paginated multi-embed entry.
     * The first Discord message contains a header embed + up to 9 content pages (10
     * messages
     * per page); further pages spill into additional messages automatically.
     */
    private static void sendGroupedDeleteLog(TextChannel logChannel,
            String channelMention,
            List<DeletedMessageInfo> messages,
            User executor) {
        int total = messages.size();

        EmbedBuilder header = new EmbedBuilder()
                .setColor(new Color(0x9B59B6))
                .setTitle("🗑️ Bulk Message Delete")
                .addField("Channel", channelMention, true)
                .addField("Messages Deleted", String.valueOf(total), true);

        if (executor != null) {
            header.addField("Deleted by", executor.getAsMention(), true);
            header.setFooter("Deleted by " + executor.getName() + " | ID: " + executor.getId());
        } else {
            header.setFooter("Bulk delete");
        }
        header.setTimestamp(OffsetDateTime.now());

        // Build one embed per page of up to 10 messages
        List<EmbedBuilder> pages = new ArrayList<>();
        EmbedBuilder page = null;
        for (int i = 0; i < messages.size(); i++) {
            if (i % 10 == 0) {
                if (page != null)
                    pages.add(page);
                page = new EmbedBuilder().setColor(new Color(0x9B59B6));
            }
            DeletedMessageInfo info = messages.get(i);
            String fieldName = (info.authorTag() != null) ? "by " + info.authorTag() : "Message #" + (i + 1);
            String content = (info.content() != null && !info.content().isEmpty())
                    ? truncateText(info.content(), 200)
                    : "*[content unavailable]*";
            page.addField(fieldName, content + "\n`" + info.id() + "`", false);
        }
        if (page != null)
            pages.add(page);

        sendEmbedBatch(logChannel, header, pages, 0);
    }

    /**
     * Sends up to 10 embeds (header on first call + content pages) per Discord
     * message.
     */
    private static void sendEmbedBatch(TextChannel logChannel, EmbedBuilder header,
            List<EmbedBuilder> pages, int pageOffset) {
        List<net.dv8tion.jda.api.entities.MessageEmbed> batch = new ArrayList<>();
        if (pageOffset == 0)
            batch.add(header.build());
        int limit = pageOffset == 0 ? 9 : 10; // header takes one slot in first message
        int end = Math.min(pageOffset + limit, pages.size());
        for (int i = pageOffset; i < end; i++)
            batch.add(pages.get(i).build());
        if (batch.isEmpty())
            return;
        SafeRestAction.queue(
                logChannel.sendMessageEmbeds(batch),
                "log grouped delete embeds",
                success -> {
                    trackLogMessage(success.getId());
                    if (end < pages.size())
                        sendEmbedBatch(logChannel, header, pages, end);
                });
    }

    /**
     * Log a set of messages that were individually deleted as part of a
     * bot-initiated purge
     * (old messages that could not be bulk-deleted). Called by PurgeCommand after
     * all
     * individual deletions finish.
     */
    public static void logOldMessagePurge(Guild guild, String channelMention,
            List<Message> messages, User executor) {
        if (!isAutoLogEnabled(guild.getId(), "message", "deletes"))
            return;
        TextChannel logChannel = getLogChannel(guild, "message");
        if (logChannel == null)
            return;

        List<DeletedMessageInfo> infos = new ArrayList<>();
        for (Message m : messages) {
            infos.add(new DeletedMessageInfo(
                    m.getId(),
                    m.getAuthor().getAsMention(),
                    m.getAuthor().getName(),
                    m.getContentRaw()));
        }
        sendGroupedDeleteLog(logChannel, channelMention, infos, executor);
    }
}
