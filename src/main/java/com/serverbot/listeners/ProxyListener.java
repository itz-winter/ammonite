package com.serverbot.listeners;

import com.serverbot.ServerBot;
import com.serverbot.services.ProxyService;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener for proxy system events
 * Intercepts messages and proxies them through webhooks if they match proxy
 * tags
 */
public class ProxyListener extends ListenerAdapter {

    private static final Logger logger = LoggerFactory.getLogger(ProxyListener.class);
    private final ProxyService proxyService;

    public ProxyListener() {
        this.proxyService = ServerBot.getProxyService();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots
        if (event.getAuthor().isBot()) {
            return;
        }

        // Ignore messages in threads (for now)
        if (event.isFromThread()) {
            return;
        }

        // Ignore commands
        String content = event.getMessage().getContentRaw();
        if (content.startsWith("/") || content.startsWith("!")) {
            return;
        }

        // Attempt to proxy the message
        try {
            proxyService.proxyMessage(event.getMessage()).thenAccept(result -> {
                if (result.isSuccess()) {
                    logger.debug("Successfully proxied message from user: " + event.getAuthor().getId());
                } else if (result.getErrorCode() != null) {
                    logger.warn("Failed to proxy message: " + result.getErrorCode());
                }
            }).exceptionally(ex -> {
                logger.error("Error in proxy message handling: " + ex.getMessage(), ex);
                return null;
            });
        } catch (Exception e) {
            logger.error("Error in proxy listener: " + e.getMessage(), e);
        }
    }

    @Override
    public void onMessageDelete(MessageDeleteEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        // Check if this was a proxied message
        ProxyService.ProxiedMessageData proxyData = proxyService.getProxiedMessageData(event.getMessageId());

        if (proxyData != null) {
            // The proxied message was deleted - we could optionally log this or notify the
            // user
            logger.debug("Proxied message deleted: " + event.getMessageId());
        }
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (!event.isFromGuild()) {
            return;
        }

        // Check if this reaction is on a proxied message
        ProxyService.ProxiedMessageData proxyData = proxyService.getProxiedMessageData(event.getMessageId());

        if (proxyData != null) {
            // If someone reacts with ❌ or 🗑️ and they're the original author, delete the
            // proxied message
            String emoji = event.getEmoji().getAsReactionCode();
            if ((emoji.equals("❌") || emoji.equals("🗑️")) &&
                    event.getUserId().equals(proxyData.getAuthorId())) {

                event.getChannel().deleteMessageById(event.getMessageId()).queue(
                        success -> logger.debug("Deleted proxied message via reaction"),
                        failure -> logger.debug("Failed to delete proxied message: " + failure.getMessage()));
            }
        }
    }
}
