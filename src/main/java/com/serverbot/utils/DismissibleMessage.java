package com.serverbot.utils;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.actionrow.ActionRow;

import java.util.concurrent.TimeUnit;

/**
 * Utility class for creating dismissible messages that can only be deleted by
 * the command sender
 */
public class DismissibleMessage {

    // Button ID prefix for dismiss buttons
    public static final String DISMISS_BUTTON_PREFIX = "dismiss_msg_";

    /**
     * Sends a dismissible embed message that only the sender can dismiss
     * 
     * @param channel  The channel to send the message to
     * @param embed    The embed to send
     * @param senderId The ID of the user who sent the command (only they can
     *                 dismiss)
     */
    public static void send(MessageChannel channel, MessageEmbed embed, String senderId) {
        Button dismissButton = Button.danger(DISMISS_BUTTON_PREFIX + senderId, "🗑️ Dismiss");

        channel.sendMessageEmbeds(embed)
                .setComponents(ActionRow.of(dismissButton))
                .queue();
    }

    /**
     * Sends a dismissible embed that auto-deletes after a timeout
     * 
     * @param channel           The channel to send the message to
     * @param embed             The embed to send
     * @param senderId          The ID of the user who sent the command
     * @param autoDeleteSeconds Seconds until auto-delete (0 = no auto-delete)
     */
    public static void send(MessageChannel channel, MessageEmbed embed, String senderId, int autoDeleteSeconds) {
        Button dismissButton = Button.danger(DISMISS_BUTTON_PREFIX + senderId, "🗑️ Dismiss");

        channel.sendMessageEmbeds(embed)
                .setComponents(ActionRow.of(dismissButton))
                .queue(message -> {
                    if (autoDeleteSeconds > 0) {
                        message.delete().queueAfter(autoDeleteSeconds, TimeUnit.SECONDS,
                                success -> {
                                },
                                failure -> {
                                }); // Silently ignore if message already deleted
                    }
                });
    }

    /**
     * Sends a dismissible message with text content
     * 
     * @param channel  The channel to send the message to
     * @param content  The text content to send
     * @param senderId The ID of the user who sent the command
     */
    public static void sendText(MessageChannel channel, String content, String senderId) {
        Button dismissButton = Button.danger(DISMISS_BUTTON_PREFIX + senderId, "🗑️ Dismiss");

        channel.sendMessage(content)
                .setComponents(ActionRow.of(dismissButton))
                .queue();
    }

    /**
     * Sends an ephemeral-like message that auto-deletes after viewing
     * Only the command sender sees a dismiss button
     * 
     * @param channel  The channel to send the message to
     * @param embed    The embed to send
     * @param senderId The ID of the user who sent the command
     */
    public static void sendSensitive(MessageChannel channel, MessageEmbed embed, String senderId) {
        // For sensitive info, auto-delete after 60 seconds
        send(channel, embed, senderId, 60);
    }

    /**
     * Extracts the sender ID from a dismiss button ID
     * 
     * @param buttonId The button ID
     * @return The sender ID, or null if not a valid dismiss button
     */
    public static String extractSenderId(String buttonId) {
        if (buttonId != null && buttonId.startsWith(DISMISS_BUTTON_PREFIX)) {
            return buttonId.substring(DISMISS_BUTTON_PREFIX.length());
        }
        return null;
    }

    /**
     * Checks if a button ID is a dismiss button
     * 
     * @param buttonId The button ID to check
     * @return true if it's a dismiss button
     */
    public static boolean isDismissButton(String buttonId) {
        return buttonId != null && buttonId.startsWith(DISMISS_BUTTON_PREFIX);
    }

    /**
     * Sends a dismissible success embed
     * 
     * @param channel     The channel to send the message to
     * @param title       The embed title
     * @param description The embed description
     * @param senderId    The ID of the user who sent the command
     */
    public static void sendSuccess(MessageChannel channel, String title, String description, String senderId) {
        send(channel, EmbedUtils.createSuccessEmbed(title, description), senderId);
    }

    /**
     * Sends a dismissible error embed
     * 
     * @param channel     The channel to send the message to
     * @param title       The embed title
     * @param description The embed description
     * @param senderId    The ID of the user who sent the command
     */
    public static void sendError(MessageChannel channel, String title, String description, String senderId) {
        send(channel, EmbedUtils.createErrorEmbed(title, description), senderId);
    }

    /**
     * Sends a dismissible info embed
     * 
     * @param channel     The channel to send the message to
     * @param title       The embed title
     * @param description The embed description
     * @param senderId    The ID of the user who sent the command
     */
    public static void sendInfo(MessageChannel channel, String title, String description, String senderId) {
        send(channel, EmbedUtils.createInfoEmbed(title, description), senderId);
    }

    /**
     * Sends a dismissible warning embed
     * 
     * @param channel     The channel to send the message to
     * @param title       The embed title
     * @param description The embed description
     * @param senderId    The ID of the user who sent the command
     */
    public static void sendWarning(MessageChannel channel, String title, String description, String senderId) {
        send(channel, EmbedUtils.createWarningEmbed(title, description), senderId);
    }

    /**
     * Replies to a slash command interaction with a dismissible embed
     * 
     * @param event     The slash command interaction event
     * @param embed     The embed to send
     * @param ephemeral Whether the message should be ephemeral
     */
    public static void reply(SlashCommandInteractionEvent event, MessageEmbed embed, boolean ephemeral) {
        String senderId = event.getUser().getId();
        Button dismissButton = Button.danger(DISMISS_BUTTON_PREFIX + senderId, "🗑️ Dismiss");

        if (ephemeral) {
            // Ephemeral messages can't have action rows with dismiss buttons in the same
            // way
            // Just reply ephemeral without dismiss button
            event.replyEmbeds(embed).setEphemeral(true).queue();
        } else {
            event.replyEmbeds(embed)
                    .setComponents(ActionRow.of(dismissButton))
                    .queue();
        }
    }

    /**
     * Replies to a slash command interaction with a dismissible embed
     * (non-ephemeral with dismiss button)
     * 
     * @param event The slash command interaction event
     * @param embed The embed to send
     */
    public static void reply(SlashCommandInteractionEvent event, MessageEmbed embed) {
        reply(event, embed, false);
    }
}
