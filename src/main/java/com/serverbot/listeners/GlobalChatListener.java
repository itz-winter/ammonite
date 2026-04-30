package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.models.GlobalChatChannel;
import com.serverbot.services.GlobalChatService;
import com.serverbot.utils.PermissionManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Listens for messages in linked channels and relays them
 * to all other linked channels of the same global chat via webhooks.
 * Also relays reactions and reply context across linked channels.
 */
public class GlobalChatListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(GlobalChatListener.class);

    // Rate limiting: userId -> last relay timestamp
    private static final Map<String, Long> userCooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = TimeUnit.SECONDS.toMillis(3); // 3 second cooldown per user

    // Content limits
    private static final int MAX_CONTENT_LENGTH = 2000; // Discord message limit
    private static final int MAX_ATTACHMENTS = 5;

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots and webhooks
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;
        // Only guild messages
        if (!event.isFromGuild()) return;

        GlobalChatService service = ServerBot.getGlobalChatService();
        if (service == null) return;

        String textChannelId = event.getChannel().getId();
        GlobalChatChannel gc = service.getGlobalChannelByTextChannel(textChannelId);
        if (gc == null) return; // not a linked channel

        // Permission check: globalchat.use
        Member member = event.getMember();
        if (member != null && !PermissionManager.hasPermission(member, "globalchat.use")) {
            return; // silently ignore — user lacks permission
        }

        // Check if source server is muted
        if (gc.isServerMuted(event.getGuild().getId())) {
            event.getMessage().reply("🔇 This server is currently muted from the **" + gc.getName() + "** global chat and cannot send messages.")
                    .queue(null, err -> {});
            return;
        }

        // Rate limiting: 1 message per 3 seconds per user
        String userId = event.getAuthor().getId();
        long now = System.currentTimeMillis();
        Long lastSend = userCooldowns.get(userId);
        if (lastSend != null && (now - lastSend) < COOLDOWN_MS) {
            long remainingSeconds = (COOLDOWN_MS - (now - lastSend) + 999) / 1000;
            event.getMessage().reply("⏳ You're sending messages too fast! Please wait **" + remainingSeconds + "** second(s) before sending another global chat message.")
                    .queue(null, err -> {});
            return;
        }
        userCooldowns.put(userId, now);

        Message message = event.getMessage();
        String content = message.getContentRaw();

        // Skip empty messages (e.g. image-only, sticker-only)
        if (content.isEmpty() && message.getAttachments().isEmpty()) return;

        // Enforce content length limit
        if (content.length() > MAX_CONTENT_LENGTH) {
            content = content.substring(0, MAX_CONTENT_LENGTH);
        }

        // Append attachment URLs to content (limited to MAX_ATTACHMENTS)
        StringBuilder sb = new StringBuilder(content);
        int attachmentCount = 0;
        for (Message.Attachment att : message.getAttachments()) {
            if (attachmentCount >= MAX_ATTACHMENTS) break;
            if (sb.length() > 0) sb.append("\n");
            sb.append(att.getUrl());
            attachmentCount++;
        }

        String authorName = member != null ? member.getEffectiveName() : event.getAuthor().getName();

        String avatarUrl = event.getAuthor().getEffectiveAvatarUrl();

        // Check if the message is a reply
        String replyContent = null;
        String replyAuthor = null;
        String referencedMessageId = null;
        Message referencedMessage = message.getReferencedMessage();
        if (referencedMessage != null) {
            replyContent = referencedMessage.getContentRaw();
            if (replyContent.isEmpty() && !referencedMessage.getAttachments().isEmpty()) {
                replyContent = "[attachment]";
            }
            // For webhook messages (relayed), extract the original author from the display name
            if (referencedMessage.isWebhookMessage()) {
                replyAuthor = referencedMessage.getAuthor().getName();
            } else {
                Member replyMember = referencedMessage.getMember();
                replyAuthor = replyMember != null ? replyMember.getEffectiveName() : referencedMessage.getAuthor().getName();
            }
            referencedMessageId = referencedMessage.getId();
        }

        // Extract placeholders for global chat format
        String username = event.getAuthor().getName();
        String globalDisplayName = event.getAuthor().getGlobalName(); // may be null

        // Collect pronoun roles from the member
        String pronouns = "";
        if (member != null) {
            StringBuilder pronounBuilder = new StringBuilder();
            for (net.dv8tion.jda.api.entities.Role role : member.getRoles()) {
                String roleName = role.getName().toLowerCase();
                if (roleName.contains("/") || com.serverbot.commands.utility.PronounsCommand.isPronounRole(roleName)) {
                    if (pronounBuilder.length() > 0) pronounBuilder.append(", ");
                    pronounBuilder.append(role.getName());
                }
            }
            pronouns = pronounBuilder.toString();
        }

        service.relayMessage(textChannelId, authorName, avatarUrl, sb.toString(), event.getJDA(),
                replyContent, replyAuthor, message.getId(), username, globalDisplayName, pronouns, referencedMessageId);
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        // Ignore bots
        if (event.getUser() != null && event.getUser().isBot()) return;
        if (!event.isFromGuild()) return;

        GlobalChatService service = ServerBot.getGlobalChatService();
        if (service == null) return;

        String textChannelId = event.getChannel().getId();
        GlobalChatChannel gc = service.getGlobalChannelByTextChannel(textChannelId);
        if (gc == null) return; // not a linked channel

        // Check if this message is tracked (either source or relayed)
        if (!service.isGlobalChatMessage(event.getMessageId())) return;

        try {
            service.relayReaction(event.getMessageId(), textChannelId, event.getEmoji(), event.getJDA());
        } catch (Exception e) {
            logger.warn("Failed to relay reaction: {}", e.getMessage());
        }
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) return;

        GlobalChatService service = ServerBot.getGlobalChatService();
        if (service == null) return;

        String textChannelId = event.getChannel().getId();
        GlobalChatChannel gc = service.getGlobalChannelByTextChannel(textChannelId);
        if (gc == null) return; // not a linked channel

        String messageId = event.getMessageId();

        // Skip if this deletion was triggered by the bot itself (prevents cascading loops)
        if (service.isDeletePending(messageId)) return;

        // Check if this message is tracked (either source or relayed)
        if (!service.isGlobalChatMessage(messageId)) return;

        try {
            service.deleteRelayedMessages(messageId, textChannelId, event.getJDA());
        } catch (Exception e) {
            logger.warn("Failed to relay message deletion: {}", e.getMessage());
        }
    }
}
