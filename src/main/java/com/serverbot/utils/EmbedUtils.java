package com.serverbot.utils;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;
import java.time.Instant;

/**
 * Utility class for creating consistent embeds across the bot
 */
public class EmbedUtils {

    // Bot color scheme
    public static final Color SUCCESS_COLOR = new Color(46, 204, 113);
    public static final Color ERROR_COLOR = new Color(231, 76, 60);
    public static final Color WARNING_COLOR = new Color(241, 196, 15);
    public static final Color INFO_COLOR = new Color(52, 152, 219);
    public static final Color DEFAULT_COLOR = new Color(116, 125, 141);

    /**
     * Creates a success embed
     */
    public static MessageEmbed createSuccessEmbed(String title, String description) {
        return new EmbedBuilder()
                .setColor(SUCCESS_COLOR)
                .setTitle(CustomEmojis.SUCCESS + " " + title)
                .setDescription(description)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Creates an error embed
     */
    public static MessageEmbed createErrorEmbed(String title, String description) {
        return new EmbedBuilder()
                .setColor(ERROR_COLOR)
                .setTitle(CustomEmojis.ERROR + " " + title)
                .setDescription(description)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Creates an error embed with correct usage hint
     * 
     * @param title       The error title
     * @param description The error description
     * @param usage       The correct command usage (e.g., "/warn @user [reason]")
     */
    public static MessageEmbed createErrorEmbedWithUsage(String title, String description, String usage) {
        return new EmbedBuilder()
                .setColor(ERROR_COLOR)
                .setTitle(CustomEmojis.ERROR + " " + title)
                .setDescription(description)
                .addField("📝 Correct Usage", "`" + usage + "`", false)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Creates an error embed with correct usage hint and example
     * 
     * @param title       The error title
     * @param description The error description
     * @param usage       The correct command usage (e.g., "/warn @user [reason]")
     * @param example     An example of correct usage (e.g., "/warn @John Spamming
     *                    in chat")
     */
    public static MessageEmbed createErrorEmbedWithUsage(String title, String description, String usage,
            String example) {
        return new EmbedBuilder()
                .setColor(ERROR_COLOR)
                .setTitle(CustomEmojis.ERROR + " " + title)
                .setDescription(description)
                .addField("📝 Correct Usage", "`" + usage + "`", false)
                .addField("💡 Example", "`" + example + "`", false)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Creates a warning embed
     */
    public static MessageEmbed createWarningEmbed(String title, String description) {
        return new EmbedBuilder()
                .setColor(WARNING_COLOR)
                .setTitle(CustomEmojis.WARN + " " + title)
                .setDescription(description)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Creates an info embed
     */
    public static MessageEmbed createInfoEmbed(String title, String description) {
        return new EmbedBuilder()
                .setColor(INFO_COLOR)
                .setTitle(CustomEmojis.INFO + " " + title)
                .setDescription(description)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Creates a default embed
     */
    public static MessageEmbed createDefaultEmbed(String title, String description) {
        return new EmbedBuilder()
                .setColor(DEFAULT_COLOR)
                .setTitle(title)
                .setDescription(description)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Creates a music embed (success color, no emoji prefix).
     * Use this for music commands that already include an emoji in the title.
     */
    public static MessageEmbed createMusicEmbed(String title, String description) {
        return new EmbedBuilder()
                .setColor(SUCCESS_COLOR)
                .setTitle(title)
                .setDescription(description)
                .setTimestamp(Instant.now())
                .build();
    }

    /**
     * Creates a moderation action embed
     */
    public static MessageEmbed createModerationEmbed(String action, User target, User moderator, String reason) {
        return new EmbedBuilder()
                .setColor(WARNING_COLOR)
                .setTitle(CustomEmojis.WARN + " " + action)
                .addField("Target", target.getAsMention(), true)
                .addField("Moderator", moderator.getAsMention(), true)
                .addField("Reason", reason != null ? reason : "No reason provided", false)
                .setTimestamp(Instant.now())
                .setFooter("UID: " + target.getId() + " | Moderator UID: " + moderator.getId())
                .build();
    }

    /**
     * Creates an embed builder with default settings
     */
    public static EmbedBuilder createEmbedBuilder() {
        return new EmbedBuilder()
                .setColor(DEFAULT_COLOR)
                .setTimestamp(Instant.now());
    }

    /**
     * Creates an embed builder with specified color
     */
    public static EmbedBuilder createEmbedBuilder(Color color) {
        return new EmbedBuilder()
                .setColor(color)
                .setTimestamp(Instant.now());
    }

    // ── Shareable ephemeral helpers ───────────────────────────────────────────

    /** The share button for an ephemeral message owned by userId. */
    public static Button shareButton(String userId) {
        return Button.secondary("share_req:" + userId, "\uD83D\uDCE4 Share");
    }

    /**
     * Replies ephemerally with an embed and a share button.
     * Works for SlashCommandInteractionEvent, ButtonInteractionEvent, ModalInteractionEvent, etc.
     */
    public static void replyEphemeral(IReplyCallback event, MessageEmbed embed) {
        event.replyEmbeds(embed)
                .setEphemeral(true)
                .setComponents(ActionRow.of(shareButton(event.getUser().getId())))
                .queue();
    }

    /**
     * Replies ephemerally with a plain-text message wrapped in an info embed
     * and a share button.
     */
    public static void replyEphemeralText(IReplyCallback event, String text) {
        replyEphemeral(event, createInfoEmbed("", text));
    }

    /**
     * Sends an ephemeral embed via a deferred interaction hook, with a share button.
     * Pass the original user's ID so the share button is correctly owned.
     */
    public static void sendEphemeral(InteractionHook hook, String userId, MessageEmbed embed) {
        hook.sendMessageEmbeds(embed)
                .setComponents(ActionRow.of(shareButton(userId)))
                .queue();
    }

    /**
     * Sends an ephemeral plain-text message via a deferred interaction hook,
     * wrapped in an info embed with a share button.
     */
    public static void sendEphemeralText(InteractionHook hook, String userId, String text) {
        sendEphemeral(hook, userId, createInfoEmbed("", text));
    }
}
