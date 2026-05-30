package com.serverbot.listeners;

import com.serverbot.services.ReactionRoleService;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Listener for handling reaction role events
 */
public class ReactionRoleListener extends ListenerAdapter {

    private final ReactionRoleService reactionRoleService;

    public ReactionRoleListener() {
        this.reactionRoleService = ReactionRoleService.getInstance();
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        // Ignore bot reactions
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        // Only handle guild reactions
        if (!event.isFromGuild()) {
            return;
        }

        String guildId = event.getGuild().getId();
        String messageId = event.getMessageId();
        String userId = event.getUserId();
        String emoji = getEmojiString(event.getEmoji());

        reactionRoleService.handleReactionAdd(guildId, messageId, userId, emoji);
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        // Ignore bot reactions
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }

        // Only handle guild reactions
        if (!event.isFromGuild()) {
            return;
        }

        String guildId = event.getGuild().getId();
        String messageId = event.getMessageId();
        String userId = event.getUserId();
        String emoji = getEmojiString(event.getEmoji());

        reactionRoleService.handleReactionRemove(guildId, messageId, userId, emoji);
    }

    private String getEmojiString(Emoji emoji) {
        if (emoji.getType() == Emoji.Type.UNICODE) {
            return emoji.getName();
        } else {
            // Return formatted string <:name:id> or <a:name:id> to match storage keys
            CustomEmoji custom = (CustomEmoji) emoji;
            return "<" + (custom.isAnimated() ? "a" : "") + ":" + custom.getName() + ":" + custom.getId() + ">";
        }
    }
}